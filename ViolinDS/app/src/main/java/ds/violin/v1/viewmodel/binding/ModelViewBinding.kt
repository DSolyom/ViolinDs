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

package ds.violin.v1.viewmodel.binding

import android.view.View
import ds.violin.v1.app.violin.PlayingViolin

interface ModelViewBinding<MODEL> : ViewBinding {

    /** lateinit, #Private */
    var rootView: View
    /** lateinit, #Private */
    var on: PlayingViolin

    fun bind(value: Any?, viewResID: Int, method: Int): Any? {
        return bind(value, viewResID, method, on, rootView)
    }

    /**
     * #Private - call this, when [rootView] and [on] is not set in the constructor
     *
     * @param model
     * @param rootView
     * @param on
     */

    fun bind(model: MODEL, rootView: View, on: PlayingViolin) {
        this.rootView = rootView
        this.on = on

        bind(model)
    }

    fun bind(model: MODEL)
}