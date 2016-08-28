/*
    Copyright 2016 Dániel Sólyom

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

package ds.violin.v1.datasource.networking

import android.text.TextUtils
import ds.violin.v1.datasource.base.RequestDescriptor
import ds.violin.v1.datasource.base.RequestExecuting
import ds.violin.v1.datasource.base.SessionHandling
import ds.violin.v1.util.cache.Caching
import ds.violin.v1.util.common.Debug
import org.json.JSONArray
import java.io.IOException
import java.io.InputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.CookieManager
import java.net.HttpCookie
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.util.*
import java.util.zip.GZIPInputStream

/**
 * data class to hold parameters of a [RequestDescriptor] or a state for a [HttpSessionHandling]
 *
 * @param cookieManager = null
 * @param headers = HashMap()
 * @param getParams = HashMap()
 * @param postParams = null
 * @param charset = "UTF-8"
 * @param fragment = ""
 */
class HttpParams(var cookieManager: CookieManager? = null,
                 val headers: MutableMap<String, String> = HashMap(),
                 val getParams: MutableMap<String, Any> = HashMap(),
                 var postParams: Any? = null,
                 var charset: Charset = Charsets.UTF_8,
                 var fragment: String = "")

/**
 * data class to hold the result of a [HttpRequestExecuting]
 *
 * @param response will hold the result body read and parsed by a [ResponseReader]
 * @param headers will hold the header's sent back
 * @param statusCode will hold the status code of the answer
 */
class HttpResult(var response: Any? = null,
                 var headers: Map<String, String>? = null,
                 var statusCode: Int? = null)

/**
 * key for caching network request+result pairs in [AbsSQLiteDatabase]
 */
class RTPKey(val recipient: String, val target: String, val params: String?)

/**
 * HttpSessionHandling is a [SessionHandling] with a state in the form of [HttpParams]
 */
interface HttpSessionHandling : SessionHandling<HttpParams> {

    /** host, best to set via constructor */
    val host: String

    companion object {
        val sessions = HashMap<String, HttpParams>()
    }

    /**
     * ensure [state]
     */
    fun ensureState() {
        if (!sessions.containsKey(host)) {
            sessions[host] = HttpParams(CookieManager())
        }
        state = sessions[host]!!
    }

    fun mergeRequestWithState(request: RequestDescriptor<HttpParams>) {
        ensureState()

        // it would be best if implementation of HttpSessionHandling would create it's
        // cookieManager in it's constructor but just to make sure
        if (state.cookieManager == null) {
            state.cookieManager = CookieManager()
        }

        val params = request.params as HttpParams

        params.cookieManager = state.cookieManager

        ParamsMerger<String, String>().merge(params.headers, state.headers)
        ParamsMerger<String, Any>().merge(params.getParams, state.getParams)
        when (params.postParams) {
            is HashMap<*, *> -> {
                if (state.postParams != null) {
                    ParamsMerger<String, Any>().merge(params.postParams as HashMap<String, Any>,
                            state.postParams as HashMap<String, Any>)
                }
            }
            null -> params.postParams = state.postParams
        }
    }

    private class ParamsMerger<K, V> {

        fun merge(left: MutableMap<K, V>?, right: MutableMap<K, V>?) {
            if (left == null || right == null) {
                return
            }
            for (key in right.keys) {
                if (!left.containsKey(key)) {
                    left[key] = right[key]!!
                }
            }
        }
    }
}

/**
 * The sending of a request for a result [HttpResult]
 *
 * !note: [HttpResult.headers] and [HttpResult.statusCode] will left unset when reading the result from cache
 *
 * @property _postBodyStreamer #Private
 * @property _responseReader #Private
 * @property connectionTimeout time limit for creating a connection
 * @property readTimeout time limit to read the server's answer
 * @property useHttpCaches use http caches?
 */
interface HttpRequestExecuting : RequestExecuting<HttpParams, String, HttpResult> {

    companion object {
        const val CACHE_MODE_ALWAYS = 0
        const val CACHE_MODE_ONLY_FOR_FAILURE = 1
    }

    /** =null, #Private */
    var _postBodyStreamer: PostBodyStreamer?
    /** =null, #Private */
    var _responseReader: ResponseReader?

    /** =null - cache for get responses */
    var externalResponseCache: Caching<RTPKey, String>?
    /** =CACHE_MODE_ALWAYS or CACHE_MODE_ONLY_FOR_FAILURE or ... */
    var externalResponseCacheMode: Int

    var connectionTimeout: Int
    var readTimeout: Int
    var useHttpCaches: Boolean

    override fun executeForResult(): HttpResult? {
        interrupted = false

        val httpURLConnection: HttpURLConnection? = null
        var responseStream: InputStream?
        var result: HttpResult? = null

        val params = request.params as HttpParams
        val getParams = GetSerializer().format(params) + params.fragment
        val finalUrl = recipient + request.target + getParams

        val httpMethod = getHttpMethodFor(request.method!!)

        _responseReader = getResponseReader(request.resultFormat!!)


        try {

            /** try cache for GET requests */
            if (httpMethod == "GET" && externalResponseCache != null && externalResponseCacheMode != CACHE_MODE_ONLY_FOR_FAILURE) {

                val cachedResult = externalResponseCache!!.get(RTPKey(recipient, request.target as String, getParams))
                if (cachedResult != null) {

                    /**
                     * got cached results - these should've come from the same type [ResponseReader]
                     * so this must work
                     */
                    return HttpResult(response = _responseReader!!.stringToResponse(cachedResult))
                }
            }

            /** sending starts here */
            Debug.logD("HttpRequesting", "opening connection to: " + finalUrl)

            val connection = URL(finalUrl).openConnection() as HttpURLConnection
            connection.requestMethod = httpMethod
            connection.connectTimeout = connectionTimeout
            connection.readTimeout = readTimeout
            connection.useCaches = useHttpCaches
            connection.doOutput = params.postParams != null
            connection.doInput = true

            /** set user agent */
            connection.setRequestProperty("User-Agent", System.getProperty("http.agent"))

            /** set headers */
            for (key in params.headers.keys) {
                connection.setRequestProperty(key, params.headers[key])
            }

            /** set cookies */
            synchronized(params.cookieManager!!) {
                if (!params.cookieManager!!.cookieStore.cookies.isEmpty()) {
                    connection.setRequestProperty("Cookie",
                            TextUtils.join(";", params.cookieManager!!.cookieStore.cookies))
                }
            }

            _postBodyStreamer = getPostBodyStreamer(request.method!!, params)
            if (params.postParams != null && _postBodyStreamer != null) {

                // sending body
                val length = _postBodyStreamer!!.contentLength
                if (length > 0) {
                    connection.setRequestProperty("Content-Length", "" + length)
                }

                val wr = PrintWriter(OutputStreamWriter(connection.outputStream, params.charset), true)
                _postBodyStreamer!!.writeTo(wr)
                wr.close()
            }

            /** server answer - status code */
            result = HttpResult(headers = HashMap())

            try {
                result.statusCode = connection.responseCode
            } catch (e: IOException) {

                // when the response code is 401 but the server had not gave the WWW-Authenticate header
                // first getResponseCode would throw an IOException
                result.statusCode = connection.responseCode
            }

            /** read cookies */
            val headerFields = connection.headerFields
            val cookiesHeader = headerFields["Set-Cookie"]

            synchronized(params.cookieManager!!) {
                if (cookiesHeader != null) {
                    for (cookie in cookiesHeader) {
                        params.cookieManager!!.cookieStore.add(null, HttpCookie.parse(cookie)[0])
                    }
                }
            }

            /** headers */
            var i = 0
            while (true) {
                val key = connection.getHeaderFieldKey(i) ?: break
                val value = connection.getHeaderField(i) ?: break

                (result.headers as HashMap<String, String>)[key] = value

                ++i
            }

            /** read response */
            try {
                responseStream = connection.inputStream
            } catch (e: IOException) {
                responseStream = connection.errorStream
            }

            val responseEncoding = connection.contentEncoding
            if (responseEncoding != null && responseEncoding.compareTo("gzip", true) != 0) {
                responseStream = GZIPInputStream(responseStream)
            }

            result.response = _responseReader!!
                    .readResponse(responseStream!!, params.charset)

            /** try to save the response to external cache for GET requests */
            if (httpMethod == "GET" && externalResponseCache != null && result.response != null) {
                val responseAsString = _responseReader!!.responseToString(result.response!!)
                if (responseAsString != null) {
                    externalResponseCache!!.put(RTPKey(recipient, request.target as String, getParams), responseAsString)
                }
            }

            return result
        } catch(error: Throwable) {

            try {
                _postBodyStreamer?.interrupt()
                _responseReader?.interrupt()
                httpURLConnection?.disconnect()
            } catch(_: Throwable) {
            }

            if (result != null && externalResponseCacheMode == CACHE_MODE_ONLY_FOR_FAILURE) {
                val cachedResult = externalResponseCache!!.get(RTPKey(recipient, request.target as String, getParams))
                if (cachedResult != null) {

                    /**
                     * got cached results - these should've come from the same type [ResponseReader]
                     * so this must work
                     */
                    return HttpResult(response = _responseReader!!.stringToResponse(cachedResult))
                }
            }

            throw error
        }
    }

    override fun interrupt() {
        super.interrupt()
        _postBodyStreamer?.interrupt()
        _responseReader?.interrupt()
    }

    /**
     * should return the http method (GET, POST, ...) for given [HttpRequestExecuting.method]
     *
     * @property method a [HttpRequestExecuting.method]
     */
    fun getHttpMethodFor(method: Int): String

    /**
     * should return the [PostBodyStreamer] for given [HttpRequestExecuting.method]
     *
     * @property method a [HttpRequestExecuting.method]
     * @property params [HttpParams] passed in case it is formatted when the streamer is created
     */
    fun getPostBodyStreamer(method: Int, params: HttpParams): PostBodyStreamer?

    /**
     * should return the [ResponseReader] to pre format the incoming response
     *
     * @property format a [HttpRequestExecuting.resultFormat]
     */
    fun getResponseReader(format: Int): ResponseReader?
}
