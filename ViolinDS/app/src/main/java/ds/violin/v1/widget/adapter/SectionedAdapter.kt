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
import ds.violin.v1.app.violin.PlayingViolin
import ds.violin.v1.model.modeling.ListModeling
import ds.violin.v1.model.modeling.Modeling
import ds.violin.v1.model.modeling.SerializableMapModel
import ds.violin.v1.util.common.Debug
import ds.violin.v1.viewmodel.AbsModelRecyclerViewItemBinder
import ds.violin.v1.viewmodel.AbsModelSectionHeaderBinder
import ds.violin.v1.viewmodel.binding.ModelViewBinding
import java.util.*

/**
 *
 */
open class SectionInfo() : SerializableMapModel()

interface SectionedAdapter {

    /** = ArrayList(), section information */
    var sectionInfos: ArrayList<SectionInfo>

    /** = ArrayList(), sections' position */
    var sectionPositions: ArrayList<Int>

    /**
     * return section number for [position]
     */
    fun sectionFor(position: Int): Int? {
        if (sectionPositions.isEmpty()) {
            return null
        }

        val sectionCount = sectionPositions.size
        var prox = (sectionCount - 1) / 2
        while(true) {
            val slp = sectionPositions[prox]
            if (slp <= position) {
                if (prox == sectionCount - 1 || sectionPositions[prox + 1] > position) {
                    return prox
                }
                prox = (sectionCount + prox + 1) / 2
            } else if (slp > position) {
                if (prox == 0) {
                    return null
                }
                if (sectionPositions[prox - 1] <= position) {
                    return prox - 1
                }
                prox /= 2
            }
        }
    }

    /**
     * return section 'offset' for [position] - this is the offset to be added to data position to get
     * real list position or subtract from list position to get real data position (without counting the header)
     */
    fun sectionOffsetFor(position: Int): Int {
        val section = sectionFor(position) ?: return 0
        return section + 1
    }

    /**
     * return real item view type for given !data! position and section
     *
     * !note: meaning of the dataPosition may differ in implementation
     */
    fun getItemViewType(dataPosition: Int, section: Int): Int {
        return AbsHeaderedAdapter.VIEWTYPE_DEFAULT
    }

    /**
     * return section view type for given !list! position
     */
    fun getSectionHeaderViewType(position: Int): Int {
        return AbsHeaderedAdapter.VIEWTYPE_SECTION_HEADER
    }
}

/**
 * an adapter with headers and with data separated by sections
 *
 * [AbsMultiDataAdapter] handles it's data separated in sections
 */
abstract class AbsMultiDataAdapter(on: PlayingViolin,
                                   sectionData: Array<ListModeling<*, *>>,
                                   sectionInfos: ArrayList<SectionInfo>) :
        AbsHeaderedAdapter(on), SectionedAdapter {

    val values: Array<ListModeling<*, *>> = sectionData

    override var sectionInfos = sectionInfos

    override var sectionPositions = ArrayList<Int>()

    override fun getRealItemViewType(position: Int): Int {
        val section = sectionOffsetFor(position)
        if (sectionPositions.contains(position)) {

            /** section header requires section header view type */
            return getSectionHeaderViewType(section)
        } else {

            /** normal item - [getItemViewType]s position is the position in the item's section */
            val dataPosition = position - sectionPositions[section] - 1
            return getItemViewType(dataPosition, section)
        }
    }

    override fun onBindViewHolder(binder: AbsModelRecyclerViewItemBinder, position: Int) {
        try {
            val section = sectionOffsetFor(position)
            if (sectionPositions.contains(position)) {

                /** section header */
                when (binder) {
                    is AbsModelSectionHeaderBinder -> binder.bind(sectionInfos[section], section)
                    is AbsModelRecyclerViewItemBinder -> binder.bind(sectionInfos[section], position, section)
                    else -> (binder as ModelViewBinding<Modeling<*, *>>).bind(sectionInfos[section]!!)
                }
            } else {
                /** normal data - [getItemDataModel]'s and [binder]'s position is the position in the item's section */
                val dataPosition = position - sectionPositions[section] - 1
                val rowDataModel = getItemDataModel(dataPosition, section)
                if (binder is AbsModelRecyclerViewItemBinder) {
                    binder.bind(rowDataModel, dataPosition, section)
                } else {
                    (binder as ModelViewBinding<Modeling<*, *>>).bind(rowDataModel)
                }
            }
        } catch(e: Throwable) {
            Debug.logException(e)
        }
    }

    override fun getItemCount(): Int {
        var count = 0
        val max = values.size - 1
        for (i in 0..max) {
            sectionPositions.add(count)
            count += values[i].size + 1 // +1 = the section header
        }
        return count +
                when (headerView) {
                    null -> 0
                    else -> 1
                } +
                when (footerView) {
                    null -> 0
                    else -> 1
                }
    }
}