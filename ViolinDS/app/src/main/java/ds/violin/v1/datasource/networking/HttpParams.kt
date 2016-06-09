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

import ds.violin.v1.datasource.dataloading.Interruptable
import ds.violin.v1.datasource.networking.HttpParams
import org.json.simple.JSONArray
import org.json.simple.JSONObject
import java.io.PrintWriter
import java.net.URLEncoder
import java.nio.charset.Charset
import java.security.InvalidParameterException

/**
 * interface to stream post body into a writer
 *
 * @property contentLength length of the content or 0 if it is unknown before streaming
 */
interface PostBodyStreamer : Interruptable {

    var contentLength: Int

    /**
     * write in [writer]
     */
    fun writeTo(writer: PrintWriter)
}

/**
 * format get query path
 */
class GetSerializer {

    /**
     * return urlencoded GET query path from [params]
     */
    fun format(params: HttpParams): String {
        val ret = createKeyValueAndArrayString(params.getParams, params.charset)
        return when {
            ret.length > 0 -> "?" + ret
            else -> ""
        }
    }
}

/**
 * a [PostBodyStreamer] which writes a url encoded string created from params
 *
 * @property formattedBody is the result for the streaming after the serializing
 */
class PostBodyStringStreamer(params: HttpParams) : PostBodyStreamer {

    val formattedBody: String
    override var contentLength: Int = 0
    override var interrupted: Boolean = false

    init {
        var unencodedBody = when (params.postParams) {
            is Map<*,*>  -> createKeyValueAndArrayString(params.postParams, params.charset, false)
            else         -> params.postParams.toString()
        }
        formattedBody = URLEncoder.encode(unencodedBody, params.charset.name())
        contentLength = formattedBody.length
    }

    override fun writeTo(writer: PrintWriter) {
        writer.write(formattedBody)
    }
}

/**
 * a [PostBodyStreamer] which writes a proper http post body created from url encoded params
 *
 * @property formattedBody is the result for the streaming after the serializing
 */
class HttpPostParamsStreamer(params: HttpParams) : PostBodyStreamer {

    val formattedBody: String
    override var contentLength: Int = 0
    override var interrupted: Boolean = false

    init {
        formattedBody = createKeyValueAndArrayString(params.postParams, params.charset)
        contentLength = formattedBody.length
    }

    override fun writeTo(writer: PrintWriter) {
        writer.write(formattedBody)
    }
}

/**
 * a [PostBodyStreamer] which writes a string created from params via toString
 *
 * @property formattedBody is the result for the streaming after the serializing
 */
class PostStringStreamer(rawParams: HttpParams) : PostBodyStreamer {

    val formattedBody: String
    override var contentLength: Int = 0
    override var interrupted: Boolean = false

    init {
        formattedBody = rawParams.postParams.toString()
        contentLength = formattedBody.length
    }

    override fun writeTo(writer: PrintWriter) {
        writer.write(formattedBody)
    }
}

private fun createKeyValueAndArrayString(params: Any?, charset: Charset, encodekeyvalues: Boolean = true): String {
    when (params) {
        is String -> return URLEncoder.encode(params, charset.name())
        is JSONObject -> return createParamsFromJSONObject(params, charset, encodekeyvalues)
        is Map<*, *> -> return createParamsFromMap(params as Map<Any, Any>, charset, encodekeyvalues)
        else -> throw (InvalidParameterException())
    }
}

private fun createParamsFromJSONObject(params: JSONObject, charset: Charset, encodekeyvalues: Boolean, sofar: String = "") : String {
    var result = ""

    for (key in params.keys) {
        var param = params[key]
        result += createParamValueFrom(param, charset, encodekeyvalues, sofar,
                additionFromKey(key as String, charset, encodekeyvalues, "" == sofar))
    }

    return result
}

private fun createParamsFromJSONArray(params: JSONArray, charset: Charset, encodekeyvalues: Boolean, sofar: String = "") : String {
    var result = ""

    for (i in 0..params.size) {
        var param = params[i]
        result += createParamValueFrom(param, charset, encodekeyvalues, sofar, "[]")
    }

    return result
}

private fun createParamsFromMap(params: Map<Any, Any>, charset: Charset, encodekeyvalues: Boolean, sofar: String = "") : String {
    var result = ""

    for (key in params.keys) {
        var param = params[key]
        result += createParamValueFrom(param, charset, encodekeyvalues, sofar,
                additionFromKey(key, charset, encodekeyvalues, "" == sofar))
    }

    return result
}

private fun createParamsFromList(params: List<Any>, charset: Charset, encodekeyvalues: Boolean, sofar: String = "") : String {
    var result = ""

    for (i in 0..params.size) {
        var param = params[i]
        result += createParamValueFrom(param, charset, encodekeyvalues, sofar, "[]")
    }

    return result
}

private fun additionFromKey(key: Any, charset: Charset, encodekey: Boolean, first: Boolean) : String {
    var additionFromKey = key.toString()
    if (encodekey) {
        additionFromKey = URLEncoder.encode(additionFromKey, charset.name())
    }
    if (!first) {
        additionFromKey = "[$additionFromKey]"
    }
    return additionFromKey
}

private fun createParamValueFrom(param: Any?, charset: Charset, encodekeyvalues: Boolean, sofar: String, addition: String) : String {
    return when {
        param is JSONObject -> createParamsFromJSONObject(param, charset, encodekeyvalues, sofar + addition)
        param is JSONArray -> createParamsFromJSONArray(param, charset, encodekeyvalues, sofar + addition)
        param is Map<*,*> -> createParamsFromMap(param as Map<Any, Any>, charset, encodekeyvalues, sofar + addition)
        param is List<*> -> createParamsFromList(param as List<Any>, charset, encodekeyvalues, sofar + addition)
        encodekeyvalues -> sofar + addition + "=" + URLEncoder.encode(param.toString(), charset.name()) + "&"
        else -> sofar + addition + "=" + param.toString() + "&"
    }
}
