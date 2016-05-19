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

import ds.violin.v1.datasource.dataloading.BackgroundDataLoading
import ds.violin.v1.datasource.dataloading.SingleUseBackgroundWorkerThread
import java.util.*

/**
 * the most basic setup for a [BackgroundDataLoading] without the actual implementation of [load]
 */
abstract class AbsBackgroundDataLoader(loadId: String? = null) : BackgroundDataLoading {

    override val loadId: String? = loadId
    override var worker: SingleUseBackgroundWorkerThread? = null
    override var loadingParams: MutableMap<String, Any> = HashMap()
    override var loading: Boolean = false
    override var interrupted: Boolean = false

    override var result: Any? = null
    override var error: Throwable? = null
    override var owner: ((Any?, Throwable?) -> Unit)? = null

}