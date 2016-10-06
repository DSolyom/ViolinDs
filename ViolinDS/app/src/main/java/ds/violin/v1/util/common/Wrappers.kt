package ds.violin.v1.util.common

import org.json.simple.JSONArray
import org.json.simple.JSONObject
import java.io.Serializable

/**
 * wrapper for [JSONArray]s to be used as [Serializable] for Intents/Bundles, without these
 * the android will give back [ArrayList] instead
 */
class JSONArrayWrapper(values : JSONArray?) : Serializable {

    val values = values
}

/**
 * wrapper for [JSONObject]s to be used as [Serializable] for Intents/Bundles, without these
 * the android will give back [HashMap] instead
 */
class JSONObjectWrapper(values : JSONObject?) : Serializable {

    val values = values
}