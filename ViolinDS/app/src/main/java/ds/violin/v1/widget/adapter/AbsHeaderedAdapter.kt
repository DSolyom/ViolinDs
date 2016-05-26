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
import ds.violin.v1.model.modeling.ModelListing
import ds.violin.v1.model.modeling.Modeling
import ds.violin.v1.model.modeling.SerializableMapModel
import ds.violin.v1.viewmodel.AbsModelRowBinder
import ds.violin.v1.viewmodel.binding.ModelViewBinding
import java.util.*

class SectionInfo(val afterOffset: Int) : SerializableMapModel()

/**
 *
 */
open class sectionHeaderAndRowBinder(sectionHeaderBinder: AbsModelRowBinder, rowBinder: AbsModelRowBinder) :
        RecyclerView.ViewHolder(rowBinder.rootView), ModelViewBinding<Modeling<*>> {

    override var rootView: View = rowBinder.rootView
    override var on: PlayingViolin = rowBinder.on

    val sectionHeaderBinder = sectionHeaderBinder
    val rowBinder = rowBinder

    override fun bind(model: Modeling<*>) {
        sectionHeaderBinder.bind(model)
        rowBinder.bind(model)
    }

}

/**
 * an adapter with headers for IRecyclerViews and [sections] (section headers)
 */
abstract class AbsHeaderedAdapter<LIST, MODEL>(on: PlayingViolin) :
        AbsRecyclerViewAdapter(on), ModelListing<LIST, MODEL> {

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

    /**
     * section information
     */
    var sections: HashMap<Int, SectionInfo> = HashMap()

    /**
     * sections' position
     */
    var sectionList: ArrayList<Int> = ArrayList()

    /**
     * generate sections from [HashMap]
     *
     * !note: call from ui thread for adapters already added to a recycler view as this will change
     *        the adapters [getItemCount] and behavior
     */
    fun generateSections(sectionsInfo: LinkedHashMap<Int, String>) {
        sections.clear()
        sectionList.clear()

        var offset = 0
        for(position in sectionsInfo.keys) {
            sections[position + offset] = SectionInfo(offset + 1)
            sections[position + offset]!!.put("label", sectionsInfo[position]!!)
            sectionList.add(position + offset)
            ++offset
        }
    }

    /**
     * return section number for [position]
     */
    fun sectionFor(position: Int): Int? {
        if (sections.isEmpty()) {
            return null
        }

        val sectionCount = sectionList.size
        var prox = (sectionCount - 1) / 2
        while(true) {
            val slp = sectionList[prox]
            if (slp <= position) {
                if (prox == sectionCount - 1 || sectionList[prox + 1] > position) {
                    return prox
                }
                prox = (sectionCount + prox + 1) / 2
            } else if (slp > position) {
                if (prox == 0) {
                    return null
                }
                if (sectionList[prox - 1] <= position) {
                    return prox - 1
                }
                prox /= 2
            }
        }
    }

    /**
     * return section 'offset' for [position] - this is the offset to be added to data position to get
     * real list position or subtract from list position to get real data position
     */
    fun sectionOffsetFor(position: Int): Int {
        val section = sectionFor(position) ?: return 0
        return sections[sectionList[section!!]]!!.afterOffset
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

            /** [headerView] added one to [getItemCount] */
            --position
        }
        if (sections.contains(position)) {

            /** section header requires section header view type */
            return getSectionHeaderViewType(position)
        } else {

            /** real item requires real item view type */
            return getRealItemViewType(position - sectionOffsetFor(position))
        }
    }

    /**
     * return real item view type for given !data! position
     */
    fun getRealItemViewType(position: Int): Int {
        return VIEWTYPE_DEFAULT
    }

    /**
     * return section view type for given !list! position
     */
    fun getSectionHeaderViewType(position: Int): Int {
        return VIEWTYPE_SECTION_HEADER
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder? {
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
        var position = position
        if (headerView != null) {

            /** [headerView] added one to [getItemCount] */
            --position
        }
        if (sections.containsKey(position)) {

            /** section header */
            (holder as ModelViewBinding<Modeling<*>>).bind(sections[position]!!)
        } else {
            /** normal data */
            val rowDataModel = getRowDataModel(position - sectionOffsetFor(position))
            (holder as ModelViewBinding<Modeling<*>>).bind(rowDataModel)
        }
    }

    /**
     * get data [Modeling] for the row to bind
     */
    open fun getRowDataModel(dataPosition: Int): Modeling<*> {
        return get(dataPosition)
    }

    override fun getItemCount(): Int {
        return size + sectionList.size +
                when (headerView) {
                    null -> 0
                    else -> 1
                } +
                when (footerView) {
                    null -> 0
                    else -> 1
                }
    }

    /**
     * create the [ModelViewBinding] for a row with [viewType] view type
     */
    abstract fun createModelRowBinder(on: PlayingViolin, parent: ViewGroup, viewType: Int): AbsModelRowBinder
}
