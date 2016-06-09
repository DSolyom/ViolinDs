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
import org.json.simple.JSONArray
import org.json.simple.JSONObject
import org.json.simple.JSONValue
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.Charset

/**
 * interface for response reading
 */
interface ResponseReader : Interruptable {

    /**
     * read sesponse from [responseStream] response should be formatted according to [charset]
     *
     * @param responseStream
     * @param charset response should be formatted according to [charset]
     */
    fun readResponse(responseStream: InputStream, charset: Charset): Any?

    /**
     * turn response into a string so it can be cached
     *
     * @param response
     * @return response version as string or null if it can't be done
     */
    fun responseToString(response: Any): String?

    /**
     * turn string coming from cache into your response
     *
     * @param cached
     * @return the response in it's original form
     */
    fun stringToResponse(cached: String): Any?
}

/**
 * returns String from responseStream
 */
open class ResponseToTextReader : ResponseReader {

    protected val baos = ByteArrayOutputStream();

    override var interrupted: Boolean = false

    override fun readResponse(responseStream: InputStream, charset: Charset): Any? {
        do {
            var read = responseStream.copyTo(baos)
        } while (read > 0 && !interrupted)

        return baos.toString(charset.name())
    }

    override fun responseToString(response: Any): String? {
        return response as String?
    }

    override fun stringToResponse(input: String): Any? {
        return input
    }

    override fun interrupt() {
        super.interrupt()
        baos.close()
    }
}

/**
 * returns JSON from responseStream
 */
open class ResponseToJSONReader : ResponseToTextReader() {

    override fun readResponse(responseStream: InputStream, charset: Charset): Any? {
        val jsonString = (super.readResponse(responseStream, charset) as String).trim()
        return when (jsonString.first()) {
            '{' -> JSONValue.parse(jsonString) as JSONObject
            '[' -> JSONValue.parse(jsonString) as JSONArray
            else -> null
        }
    }

    override fun responseToString(response: Any): String? {
        return response.toString()
    }

    override fun stringToResponse(input: String): Any? {
        return when (input.first()) {
            '{' -> JSONValue.parse(input) as JSONObject
            '[' -> JSONValue.parse(input) as JSONArray
            else -> null
        }
    }
}
