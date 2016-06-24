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

package ds.violin.v1.viewmodel

import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
import ds.violin.v1.app.violin.PlayingViolin
import ds.violin.v1.model.modeling.Modeling
import ds.violin.v1.viewmodel.binding.ModelViewBinding

/**
 * [AbsModelRecyclerViewItemBinder] is the [ModelViewBinding] for an [AbsHeaderedAdapter]'s item
 *
 * @use implement [ModelViewBinding.bind] as you would in any other bindings, but with dataPosition
 *      and section info and remember, it could be working with a recycled (already filled) view
 *      from another item
 */
abstract class AbsModelRecyclerViewItemBinder(on: PlayingViolin, parent: ViewGroup, rowLayoutResID: Int = 0) :
        RecyclerView.ViewHolder( { on.inflate(rowLayoutResID, parent, false) }() ), ModelViewBinding<Modeling<*, *>> {

    override var on: PlayingViolin = on
    override var rootView: View = itemView

    abstract fun bind(model: Modeling<*, *>, dataPosition: Int, section: Int)

    override fun bind(model: Modeling<*, *>) {
        throw UnsupportedOperationException()
    }
}