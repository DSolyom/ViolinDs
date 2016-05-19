/*
    Copyright 2016 Dániel Sólyom

    Licensed under the Apache License, Version 2.0 (the "License")
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

package ds.violin.v1.datasource

import ds.violin.v1.datasource.networking.*
import ds.violin.v1.datasource.base.Api
import ds.violin.v1.datasource.base.RequestDescriptor
import ds.violin.v1.util.cache.Caching

open class BaseHttpRequestSender : HttpRequestExecuting {

    /**
     * base methods and response formats
     */
    companion object {
        const val METHOD_GET: Int = 1               // normal get
        const val METHOD_POST_BODY_STRING: Int = 3  // a url encoded string is created for post body
        const val METHOD_POST: Int = 4              // normal http post
        const val METHOD_POST_RAW: Int = 5          // postParams go in as it is
        const val METHOD_PUT_BODY_STRING: Int = 6
        const val METHOD_PUT: Int = 7
        const val METHOD_PUT_RAW: Int = 8
        const val METHOD_DELETE: Int = 9

        const val FORMAT_TEXT: Int = 1
        const val FORMAT_JSON: Int = 2
    }

    override lateinit var request: RequestDescriptor<HttpParams>
    override lateinit var recipient: String

    override var connectionTimeout: Int = 30000
    override var readTimeout: Int = 60000
    override var useHttpCaches: Boolean = false
    override var externalResponseCache: Caching<RTPKey, String>? = null

    override var sending: Boolean = false
    override var interrupted: Boolean = false
    override var _postBodyStreamer: PostBodyStreamer? = null
    override var _responseReader: ResponseReader? = null

    override fun getHttpMethodFor(method: Int): String {
        return when (method) {
            METHOD_DELETE -> "GET"
            METHOD_POST_BODY_STRING, METHOD_POST, METHOD_POST_RAW -> "POST"
            METHOD_PUT_BODY_STRING, METHOD_PUT, METHOD_PUT_RAW -> "PUT"
            else -> "GET"
        }
    }

    override fun getPostBodyStreamer(method: Int, params: HttpParams): PostBodyStreamer? {
        return when (method) {
            METHOD_POST_BODY_STRING, METHOD_PUT_BODY_STRING -> PostBodyStringStreamer(params)
            METHOD_POST, METHOD_PUT -> HttpPostParamsStreamer(params)
            else -> PostStringStreamer(params)
        }
    }

    override fun getResponseReader(format: Int): ResponseReader? {
        return when (format) {
            FORMAT_TEXT -> ResponseToTextReader()
            FORMAT_JSON -> ResponseToJSONReader()
            else -> null
        }
    }
}

/**
 * class to create [RequestDescriptor]s, [HttpRequestExecuting]s and handle the session's states towards a defined [host]
 *
 * @use override to create your own network api by defining the way [RequestDescriptor] are created
 *      from requestName and params (override [AbsHttpNetwork.createDescriptor])
 *      also you may want to override [ensureState] if you need more than one session for the same host
 *
 *      use [prepareSender] to get a prepared [HttpRequestExecuting], than call [HttpRequestExecuting.execute]
 *      to start sending
 *
 *      !note: take hold of your executors when using them in the background, to be able to interrupt them
 *      !note: don't be afraid to create as many [AbsHttpNetwork] instance as you like, states are handled statically
 *      TODO: do something about that statically handled state to last through the application lifecycle
 *      TODO: for now [HttpSessionHandling.sessions] is held in Application
 *
 * @param host
 */
abstract class AbsHttpNetwork(host: String) : Api, HttpSessionHandling {

    override lateinit var state: HttpParams

    override val host: String = host
    var port: String = ""

    /**
     * load stuff from the net
     *
     * @param requestName name of the request to know how to format the [RequestDescriptor.params]
     * @param params the custom parameters
     * @return the [HttpRequestExecuting] object that doing the actual work
     */
    fun prepareSender(requestName: String,
                      params: Any?): HttpRequestExecuting? {

        val request = createDescriptor(requestName, params) ?: throw NoSuchMethodException("Could not create request")

        mergeRequestWithState(request as RequestDescriptor<HttpParams>)
        val sender = createRequestSender()
        sender.prepare(request, host + port)
        return sender
    }

    /**
     * create the [HttpRequestExecuting] used to send the request
     */
    open fun createRequestSender(): HttpRequestExecuting {
        return BaseHttpRequestSender()
    }
}