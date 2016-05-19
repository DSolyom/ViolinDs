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

package ds.violin.v1.widget

import android.content.Context
import android.os.Parcelable
import android.support.v7.widget.RecyclerView
import android.util.AttributeSet
import android.view.View
import ds.violin.v1.widget.adapter.AbsHeaderedAdapter

class IRecyclerView : RecyclerView {

    /** header for the recycler view */
    var headerView: View? = null
        set(headerView) {
            field = headerView
            val adapter = adapter
            if (adapter != null && adapter is AbsHeaderedAdapter<*, *>) {
                adapter.headerView = field
            }
        }

    /** footer for the recycler view */
    var footerView: View? = null
        set(footerView) {
            field = footerView
            val adapter = adapter
            if (adapter != null && adapter is  AbsHeaderedAdapter<*, *>) {
                adapter.footerView = field
            }
        }

    private var savedState: Parcelable? = null

    constructor(context: Context) : super(context) {
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle) {
    }

    override fun setAdapter(adapter: Adapter<ViewHolder>) {
        if (adapter === getAdapter()) {
            return
        }

        if (adapter is  AbsHeaderedAdapter<*, *>) {
            adapter.headerView = headerView
            adapter.footerView = footerView
        }

        super.setAdapter(adapter)
    }

    /**
     * save the state of the [RecyclerView.LayoutManager]
     */
    fun getState(): Parcelable? {
        return layoutManager?.onSaveInstanceState() ?: null
    }

    /**
     * restore the state of the [RecyclerView.LayoutManager] - takes effect on next [onLayout]
     */
    fun restoreState(state: Parcelable?) {
        savedState = state
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        if (savedState != null) {
            val stateSave = savedState
            savedState = null
            layoutManager?.onRestoreInstanceState(stateSave)
        }
        super.onLayout(changed, l, t, r, b)
    }

}
