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

package ds.violin.v1.model

import ds.violin.v1.model.entity.HasSerializableData
import ds.violin.v1.model.entity.SelfLoadableListModeling
import ds.violin.v1.datasource.base.DataLoading
import ds.violin.v1.model.modeling.JSONArrayListModel
import ds.violin.v1.model.modeling.JSONArrayListModeling
import org.json.simple.JSONArray
import org.json.simple.JSONObject
import java.io.Serializable

/**
 * list entity holding it's data in a [JSONArray]
 */
open class JSONArrayEntity(dataLoader: DataLoading, models: JSONArray = JSONArray()) :
        JSONArrayListModel(models), SelfLoadableListModeling<JSONArray, JSONObject> {

    override var dataLoader: DataLoading = dataLoader
    override var valid: Boolean = false
    override var interrupted: Boolean = false
}