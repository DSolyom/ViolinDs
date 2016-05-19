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

package ds.violin.v1.datasource.base

import ds.violin.v1.datasource.dataloading.Interruptable

/**
 * data class for describing a request
 */
data class RequestDescriptor<RD>(val target: Any,
                                 val params: RD?,
                                 val method: Int? = null,
                                 val resultFormat: Int? = null)

/**
 * interface for sending a request described by a [RequestDescriptor]
 * !note: single use - create new for the next request unless it is sure that the previous is done
 *
 * @param RD type of the [RequestDescriptor]'s parameters
 * @param RECIPIENT the recipient of the request
 * @param RESULT type of the result
 */
interface RequestExecuting<RD, RECIPIENT, RESULT> : Interruptable {

    /** =false */
    var sending: Boolean
    /** lateinit, #Protected */
    var request: RequestDescriptor<RD>
    /** lateinit, #Protected */
    var recipient: RECIPIENT

    /**
     * prepare - set request and recipient
     *
     * @param request to send
     * @param recipient recipient of the request
     */
    fun prepare(request: RequestDescriptor<RD>, recipient: RECIPIENT) {
        this.request = request
        this.recipient = recipient
    }

    /**
     * execute a request described by a [RequestDescriptor]
     *
     * @param completion called when request is executed and response is received or an [error] has occurred
     * @return true if the request has been sent, false if it was interrupted
     */
    fun execute(completion: (response: RESULT?, error: Throwable?) -> Unit): Boolean {
        if (sending) {
            return false
        }

        synchronized(this) {
            if (interrupted) {
                return false
            }
            sending = true
            interrupted = false
        }

        var response: RESULT? = null
        var error: Throwable? = null
        try {
            response = executeForResult()
        } catch(e: Throwable) {
            error = e
        }

        synchronized(this) {
            if (interrupted) {
                return false
            }
            completion(response, error)
        }
        return true
    }

    /**
     * #Protected
     * the actual executing of the request
     */
    fun executeForResult(): RESULT?

    override fun interrupt() {
        synchronized(this) {
            super.interrupt()
            sending = false
        }
    }
}

/**
 * api for networking - can create a [RequestDescriptor] from type and custom parameters
 */
interface Api {

    /**
     * should create a [RequestDescriptor] from type and custom parameters
     *
     * @param requestName name of the request to know how to format the [params]
     * @param params the custom parameters
     * @return
     */
    fun createDescriptor(requestName: String, params: Any?): RequestDescriptor<*>?
}