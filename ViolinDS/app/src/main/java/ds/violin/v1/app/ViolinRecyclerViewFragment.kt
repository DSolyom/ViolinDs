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

package ds.violin.v1.app

import android.os.Bundle
import android.os.Parcelable
import android.view.View
import ds.violin.v1.app.violin.*
import ds.violin.v1.model.entity.SelfLoadable
import ds.violin.v1.widget.IRecyclerView
import java.util.*

abstract class ViolinRecyclerViewFragment : ViolinFragment(), RecyclerViewViolin {

    override lateinit var recyclerView: IRecyclerView
    override val emptyViewID: Int? = null
    override var emptyView: View? = null
    override var layoutManagerState: Parcelable? = null
    override var adapter: AbsRecyclerViewAdapter? = null
    override var adapterViewBinder: RecyclerViewAdapterBinder? = null
    override val parentCanHoldHeader: Boolean = true
    override val parentCanHoldFooter: Boolean = true

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super<RecyclerViewViolin>.onViewCreated(view, savedInstanceState)
        super<ViolinFragment>.onViewCreated(view, savedInstanceState)
    }

    override fun play() {

        /** create your [adapter] and [adapterViewBinder] before calling super */

        super<RecyclerViewViolin>.play()
        super<ViolinFragment>.play()
    }

    override fun saveInstanceState(outState: Bundle) {
        super<ViolinFragment>.saveInstanceState(outState)
        super<RecyclerViewViolin>.saveInstanceState(outState)
    }

    override fun restoreInstanceState(savedInstanceState: Bundle) {
        super<ViolinFragment>.restoreInstanceState(savedInstanceState)
        super<RecyclerViewViolin>.restoreInstanceState(savedInstanceState)
    }
}