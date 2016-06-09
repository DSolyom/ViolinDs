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

package ds.violin.v1.widget

import ds.violin.v1.app.violin.PlayingViolin
import ds.violin.v1.model.modeling.Modeling
import ds.violin.v1.model.modeling.MutableListModeling
import ds.violin.v1.widget.adapter.AbsHeaderedAdapter
import java.util.*

/**
 * abstract class for the most basic [AbsHeaderedAdapter] with data held in the form of a [MutableList]
 *
 * this type of adapter can be used for [IRecyclerView]s when the data is already present
 */
abstract class ArrayListAdapter<MODEL>(on: PlayingViolin, models: MutableList<MODEL> = ArrayList()) :
        AbsHeaderedAdapter<MutableList<MODEL>, MODEL>(on), MutableListModeling<MODEL> {

    override var models: MutableList<MODEL> = models
}