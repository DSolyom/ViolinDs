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

import android.database.Cursor
import ds.violin.v1.app.violin.PlayingViolin
import ds.violin.v1.datasource.base.DataLoading
import ds.violin.v1.model.entity.SelfLoadableListModeling
import ds.violin.v1.model.modeling.CursorListModeling
import ds.violin.v1.model.modeling.CursorModel
import ds.violin.v1.model.modeling.Modeling
import ds.violin.v1.widget.adapter.AbsHeaderedAdapter
import ds.violin.v1.widget.adapter.AbsListModelingAdapter

abstract class CursorAdapter(on: PlayingViolin, values: CursorModel = CursorModel()) :
        AbsListModelingAdapter<CursorModel, Cursor>(on), CursorListModeling {

    override var values: CursorModel = values

    override fun getItemDataModel(dataPosition: Int, section: Int): Modeling<*, *> {

        // position cursor
        get(dataPosition)

        return values
    }
}

abstract class CursorAdapterEntity(on: PlayingViolin, dataLoader: DataLoading, values: CursorModel = CursorModel()) :
        CursorAdapter(on, values), SelfLoadableListModeling<CursorModel, Cursor> {

    override var interrupted: Boolean = false
    override var valid: Boolean = false

    override var dataLoader: DataLoading = dataLoader
}

