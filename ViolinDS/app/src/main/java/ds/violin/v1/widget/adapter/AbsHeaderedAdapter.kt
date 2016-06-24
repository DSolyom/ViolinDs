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

package ds.violin.v1.widget.adapter

import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
import ds.violin.v1.app.violin.AbsRecyclerViewAdapter
import ds.violin.v1.app.violin.PlayingViolin
import ds.violin.v1.model.modeling.Modeling
import ds.violin.v1.viewmodel.AbsModelRecyclerViewItemBinder

/**
 * an adapter (for I[RecyclerView]) with header and footer
 */
abstract class AbsHeaderedAdapter(on: PlayingViolin) : AbsRecyclerViewAdapter(on) {

    companion object {
        const val VIEWTYPE_HEADER = Int.MAX_VALUE
        const val VIEWTYPE_FOOTER = Int.MIN_VALUE
        const val VIEWTYPE_DEFAULT = 0
        const val VIEWTYPE_SECTION_HEADER = Int.MAX_VALUE - 1
    }

    /** #PrivateSet - recycler view's header */
    internal var headerView: View? = null
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    /** #PrivateSet - recycler view's footer */
    internal var footerView: View? = null
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    override fun getItemViewType(position: Int): Int {
        if (position == 0 && headerView != null) {
            return VIEWTYPE_HEADER
        }
        if (footerView != null && position == itemCount - 1) {
            return VIEWTYPE_FOOTER
        }
        var position = position
        if (headerView != null) {

            /** [headerView] is not a 'real' item in this context */
            --position
        }
        return getRealItemViewType(position)
    }

    /**
     *
     */
    internal open fun getRealItemViewType(position: Int): Int {
        return VIEWTYPE_DEFAULT
    }

    final override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder? {
        return when (viewType) {
            VIEWTYPE_HEADER -> object : RecyclerView.ViewHolder(headerView) {}
            VIEWTYPE_FOOTER -> object : RecyclerView.ViewHolder(footerView) {}
            else -> createModelItemBinder(parent, viewType)
        }
    }

    final override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder.itemView === headerView || holder.itemView === footerView) {
            return;
        }
        var position = position
        if (headerView != null) {

            /** [headerView] added one to [getItemCount] */
            --position
        }
        onBindViewHolder(holder as AbsModelRecyclerViewItemBinder, position)
    }

    /**
     * create the [ModelViewBinding] for an item with [viewType] view type
     */
    abstract fun createModelItemBinder(parent: ViewGroup, viewType: Int): AbsModelRecyclerViewItemBinder

    abstract fun onBindViewHolder(binder: AbsModelRecyclerViewItemBinder, position: Int)

    /**
     * get data [Modeling] for the row to bind
     * !note: usually this creates modeling with value get(dataPosition), exception is mostly when the
     *        adapter's data is provided from another list in which case [get] usually unsupported
     */
    abstract fun getItemDataModel(dataPosition: Int, section: Int): Modeling<*, *>

}
