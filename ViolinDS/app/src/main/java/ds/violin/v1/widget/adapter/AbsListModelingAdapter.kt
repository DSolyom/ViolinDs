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

import ds.violin.v1.app.violin.PlayingViolin
import ds.violin.v1.model.modeling.ListModeling
import ds.violin.v1.model.modeling.Modeling
import ds.violin.v1.util.common.Debug
import ds.violin.v1.viewmodel.AbsModelRecyclerViewItemBinder
import ds.violin.v1.viewmodel.AbsModelSectionBinder
import ds.violin.v1.viewmodel.binding.ModelViewBinding
import java.io.Serializable
import java.util.*

/**
 * an adapter holding it's own data in a ListModeling
 */
abstract class AbsListModelingAdapter<LIST, VALUE>(on: PlayingViolin) :
        AbsHeaderedAdapter(on), ListModeling<LIST, VALUE>, SectionedAdapter {

    /**
     * section information
     */
    override var sectionInfos = ArrayList<SectionInfo>()

    /**
     * sections' position
     */
    override var sectionPositions: ArrayList<Int> = ArrayList()

    /**
     * generate sections from [HashMap]
     *
     * !note: call from ui thread for adapters already added to a recycler view as this will change
     *        the adapters [getItemCount] and behavior
     */
    open fun generateSections(sectionsInfo: LinkedHashMap<Int, Any>) {
        sectionInfos.clear()
        sectionPositions.clear()

        var offset = 0
        for (position in sectionsInfo.keys) {
            if (sectionsInfo[position] is SectionInfo) {
                sectionInfos.add(sectionsInfo[position] as SectionInfo)
            } else {
                val sectionInfo = SectionInfo()
                sectionInfos.add(sectionInfo)
                if (sectionsInfo[position] is String) {
                    sectionInfo.set("label", sectionsInfo[position] as String)
                } else {
                    sectionInfo.set("data", sectionsInfo[position] as Serializable)
                }
            }
            sectionPositions.add(position + offset)
            ++offset
        }
    }

    override fun getRealItemViewType(position: Int): Int {
        val section = sectionFor(position) ?: -1
        if (sectionPositions.contains(position)) {

            /** section header requires section header view type */
            return getSectionViewType(section)
        } else {

            /** normal item - [getItemViewType]s position is the item's position in the data */
            return getItemViewType(position - (section + 1), section)
        }
    }

    override fun onBindViewHolder(binder: AbsModelRecyclerViewItemBinder, position: Int) {
        try {
            val section = sectionFor(position) ?: -1
            if (sectionPositions.contains(position)) {

                /** section header */
                when (binder) {
                    is AbsModelSectionBinder -> binder.bind(sectionInfos[section], section)
                    is AbsModelRecyclerViewItemBinder -> binder.bind(sectionInfos[section], position, section)
                    else -> (binder as ModelViewBinding<Modeling<*, *>>).bind(sectionInfos[section]!!)
                }
            } else {
                /** normal data - [getItemViewType]'s and [binder]'s position is the item's position in the data */
                val dataPosition = position - (section + 1)
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

    override fun getRealCount(): Int {
        return size
    }

    override fun getItemCount(): Int {
        return getRealCount() + sectionPositions.size +
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