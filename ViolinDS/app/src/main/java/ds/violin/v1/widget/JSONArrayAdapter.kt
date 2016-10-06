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

import android.os.Parcel
import android.os.Parcelable
import ds.violin.v1.app.violin.PlayingViolin
import ds.violin.v1.model.entity.HasParcelableData
import ds.violin.v1.model.entity.HasSerializableData
import ds.violin.v1.datasource.base.DataLoading
import ds.violin.v1.model.entity.SelfLoadable
import ds.violin.v1.model.entity.SelfLoadableListModeling
import ds.violin.v1.model.modeling.JSONArrayListModeling
import ds.violin.v1.model.modeling.JSONModel
import ds.violin.v1.model.modeling.Modeling
import ds.violin.v1.widget.adapter.AbsHeaderedAdapter
import ds.violin.v1.widget.adapter.AbsListModelingAdapter
import ds.violin.v1.widget.adapter.SectionInfo
import org.json.simple.JSONArray
import org.json.simple.JSONObject
import org.json.simple.JSONValue
import java.io.Serializable
import java.util.*

/**
 * Parcelable data for [JSONArrayAdapterEntity] to be able to save list and section data
 */
class JSONArrayAdapterDataParcelable(modelsString: String,
                                     sectionInfos: ArrayList<SectionInfo>,
                                     sectionList: ArrayList<Int>) : Parcelable {

    companion object {
        @JvmField final val CREATOR: Parcelable.Creator<JSONArrayAdapterDataParcelable> = object :
                Parcelable.Creator<JSONArrayAdapterDataParcelable> {
            override fun createFromParcel(source: Parcel): JSONArrayAdapterDataParcelable {
                return JSONArrayAdapterDataParcelable(source)
            }

            override fun newArray(size: Int): Array<JSONArrayAdapterDataParcelable?> {
                return arrayOfNulls(size)
            }
        }
    }

    val modelsString: String = modelsString
    val sectionInfos: ArrayList<SectionInfo> = sectionInfos
    val sectionList: ArrayList<Int> = sectionList

    constructor(source: Parcel) : this(source.readString(),
            source.readSerializable() as ArrayList<SectionInfo>,
            source.readSerializable() as ArrayList<Int>)

    override fun writeToParcel(dest: Parcel?, flags: Int) {
        dest!!.writeString(modelsString)
        dest.writeSerializable(sectionInfos)
        dest.writeSerializable(sectionList)
    }

    override fun describeContents(): Int {
        return 0
    }
}

/**
 * abstract class for the most basic [AbsHeaderedAdapter] with data held in the form of a [JSONArrayListModeling]
 * !note: the list must contain [JSONObject]s only
 *
 * this type of adapter can be used for [IRecyclerView]s when the data is already present
 */
abstract class JSONArrayAdapter(on: PlayingViolin, values: JSONArray = JSONArray()) :
        AbsListModelingAdapter<JSONArray, Any>(on), JSONArrayListModeling {

    override var values: JSONArray = values

    override fun getItemDataModel(dataPosition: Int, section: Int): Modeling<*, *> {
        return JSONModel(get(dataPosition) as JSONObject)
    }
}

/**
 * abstract class for the most basic [AbsHeaderedAdapter] with data held in the form of a
 * [JSONArrayListModeling]
 * !note: the list must contain [JSONObject]s only
 *
 * this type of adapter can be used for [IRecyclerView]s when the data requires loading
 */
abstract class JSONArrayAdapterEntity(on: PlayingViolin, dataLoader: DataLoading, values: JSONArray = JSONArray()) :
        JSONArrayAdapter(on, values), SelfLoadableListModeling<JSONArray, Any>, HasParcelableData {

    override var interrupted: Boolean = false
    override var valid: Boolean = false

    override var dataLoader: DataLoading = dataLoader

    override fun dataToParcelable(): Parcelable {
        return JSONArrayAdapterDataParcelable(values.toString(), sectionInfos, sectionPositions)
    }

    override fun createDataFrom(parcelableData: Parcelable) {
        values = JSONValue.parse((parcelableData as JSONArrayAdapterDataParcelable).modelsString) as JSONArray
        sectionInfos = parcelableData.sectionInfos
        sectionPositions = parcelableData.sectionList
    }
}