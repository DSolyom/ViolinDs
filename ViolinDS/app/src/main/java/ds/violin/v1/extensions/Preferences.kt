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

package ds.violin.v1.extensions

import android.content.SharedPreferences
import android.util.Base64
import ds.violin.v1.util.common.deserializeObject
import ds.violin.v1.util.common.serializeObject
import java.io.Serializable

/** extension for [SharedPreferences] for [Serializable] values */
fun SharedPreferences.getSerializable(key: String, defaultValue: Serializable): Serializable? {

    val encodedValue = getString(key, null) ?: return defaultValue

    return deserializeObject(Base64.decode(encodedValue.toByteArray(), Base64.DEFAULT))
}

/** extension for [SharedPreferences.Editor] for [Serializable] values */
fun SharedPreferences.Editor.putSerializable(key: String, value: Serializable) {

    val encodedValue = String(Base64.encode(serializeObject(value), Base64.DEFAULT))

    putString(key, encodedValue)
}