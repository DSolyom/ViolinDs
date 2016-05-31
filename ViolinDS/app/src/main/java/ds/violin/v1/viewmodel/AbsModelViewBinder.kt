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

import android.view.View
import ds.violin.v1.app.violin.PlayingViolin
import ds.violin.v1.model.modeling.Modeling
import ds.violin.v1.viewmodel.binding.ModelViewBinding

/**
 * basic [ModelViewBinding] for any [Modeling]
 */
abstract class AbsModelViewBinder(on: PlayingViolin, view: View) : ModelViewBinding<Modeling<*>?> {

    override var on: PlayingViolin = on
    override var rootView: View = view
}