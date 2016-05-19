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
import ds.violin.v1.app.violin.RecyclerViewViolin
import ds.violin.v1.model.entity.SelfLoadableModelListing
import ds.violin.v1.model.modeling.ModelListing
import ds.violin.v1.model.modeling.Modeling
import ds.violin.v1.viewmodel.AbsModelRowBinder
import ds.violin.v1.viewmodel.binding.ModelViewBinding

/**
 * an adapter with headers for [RecyclerViewViolin]s
 */
abstract class AbsHeaderedAdapter<LIST, MODEL>(on: PlayingViolin) :
        AbsRecyclerViewAdapter(on), ModelListing<LIST, MODEL> {

    companion object {
        val VIEWTYPE_HEADER = Int.MAX_VALUE
        val VIEWTYPE_FOOTER = Int.MIN_VALUE
        val VIEWTYPE_DEFAULT = 0
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
            return VIEWTYPE_HEADER;
        }
        if (footerView != null && position == itemCount - 1) {
            return VIEWTYPE_FOOTER;
        }
        return VIEWTYPE_DEFAULT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) : RecyclerView.ViewHolder? {
        return when (viewType) {
            VIEWTYPE_HEADER -> object : RecyclerView.ViewHolder(headerView) {}
            VIEWTYPE_FOOTER -> object : RecyclerView.ViewHolder(footerView) {}
            else -> createModelRowBinder(on, parent, viewType)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder.itemView === headerView || holder.itemView === footerView) {
            return;
        }

        (holder as AbsModelRowBinder).bind(getRowDataModel(position))
    }

    /**
     * get data [Modeling] for the row to bind
     */
    open fun getRowDataModel(position: Int): Modeling<*> {
        return get(position)
    }

    override fun getItemCount(): Int {
        return size +
                when (headerView) {
                    null -> 0
                    else -> 1
                } +
                when (headerView) {
                    null -> 0
                    else -> 1
                }
    }

    /**
     * create the [ModelViewBinding] for a row with [viewType] view type
     */
    abstract fun createModelRowBinder(on: PlayingViolin, parent: ViewGroup, viewType: Int) : AbsModelRowBinder
}
