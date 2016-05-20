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
import ds.violin.v1.app.violin.AbsRecyclerViewAdapterEntity
import ds.violin.v1.app.violin.PlayingViolin
import ds.violin.v1.model.entity.HasParcelableData
import ds.violin.v1.model.entity.HasSerializableData
import ds.violin.v1.datasource.dataloading.DataLoading
import ds.violin.v1.model.entity.SelfLoadableModelListing
import ds.violin.v1.model.modeling.JSONArrayModelListing
import ds.violin.v1.widget.adapter.AbsHeaderedAdapter
import ds.violin.v1.widget.adapter.SectionInfo
import org.json.JSONArray
import org.json.JSONObject
import java.io.Serializable
import java.util.*

/**
 * Parcelable data for [JSONArrayAdapterEntity] to be able to save list and section data
 */
class JSONArrayAdapterDataParcelable(modelsString: String,
                                     sections: HashMap<Int, SectionInfo>,
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
    val sections: HashMap<Int, SectionInfo> = sections
    val sectionList: ArrayList<Int> = sectionList

    constructor(source: Parcel) : this(source.readString(),
            source.readSerializable() as HashMap<Int, SectionInfo>,
            source.readSerializable() as ArrayList<Int>)

    override fun writeToParcel(dest: Parcel?, flags: Int) {
        dest!!.writeString(modelsString)
        dest.writeSerializable(sections)
        dest.writeSerializable(sectionList)
    }

    override fun describeContents(): Int {
        return 0
    }
}

/**
 * abstract class for the most basic [AbsHeaderedAdapter] with data held in the form of a [JSONArrayModelListing]
 *
 * this type of adapter can be used for [IRecyclerView]s when the data is already present
 */
abstract class JSONArrayAdapter(on: PlayingViolin, models: JSONArray = JSONArray()) :
        AbsHeaderedAdapter<JSONArray, JSONObject>(on), JSONArrayModelListing {

    override var models: JSONArray = models

}

/**
 * abstract class for the most basic [AbsHeaderedAdapter] [AbsRecyclerViewAdapterEntity] with data
 * held in the form of a [JSONArrayModelListing]
 *
 * this type of adapter can be used for [IRecyclerView]s when the data requires loading
 */
abstract class JSONArrayAdapterEntity(on: PlayingViolin, dataLoader: DataLoading, models: JSONArray = JSONArray()) :
        JSONArrayAdapter(on, models), AbsRecyclerViewAdapterEntity,
        SelfLoadableModelListing<JSONArray, JSONObject>, HasParcelableData {

    override var interrupted: Boolean = false
    override var valid: Boolean = false

    override var dataLoader: DataLoading = dataLoader

    override fun dataToParcelable(): Parcelable {
        return JSONArrayAdapterDataParcelable(models.toString(), sections, sectionList)
    }

    override fun createDataFrom(parcelableData: Parcelable) {
        models = JSONArray((parcelableData as JSONArrayAdapterDataParcelable).modelsString)
        sections = parcelableData.sections
        sectionList = parcelableData.sectionList
    }
}