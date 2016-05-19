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

package ds.violin.examples

import ds.violin.v1.app.ViolinFragment
import ds.violin.v1.datasource.AbsBackgroundDataLoader
import ds.violin.v1.datasource.dataloading.DataLoading
import ds.violin.v1.model.SerializableMapEntity
import ds.violin.v1.model.modeling.Modeling
import ds.violin.v1.viewmodel.AbsBasicModelViewBinder
import ds.violin.v1.viewmodel.binding.ViewBinding

class CountingPIFragment : ViolinFragment() {

    override var Id: String = "counting_pi"
    override val layoutResID: Int? = R.layout.counting_pi_fragment

    val countPiEntity = object : SerializableMapEntity() {

        override var dataLoader: DataLoading = object : AbsBackgroundDataLoader("counting_pi") {

            override fun load(completion: (Any?, Throwable?) -> Unit) {
                val Pi = countPi(5000000);

                try {
                    // just making sure there is time to rotate the phone
                    Thread.sleep(5000);
                } catch (e: InterruptedException) {
                    ;
                }

                completion(hashMapOf("Pi" to Pi), null)
            }

            fun countPi(n: Int): Double {

                // ok this has nothing to do with the framework :P
                var sequenceFormula = 0.0
                for(counter in 1..n-1 step 2) {
                    sequenceFormula += ((1.0 / (2.0 * counter - 1)) - (1.0 / (2.0 * counter + 1)));
                }
                val pi = 4 * sequenceFormula;
                return pi;
            }

        }
    }

    override fun play() {
        registerEntity("counting_pi", countPiEntity) { entity, error ->
            object : AbsBasicModelViewBinder(this, rootView!!) {

                override fun bind(model: Modeling<*>?) {
                    bind(model!!.get("Pi"), R.id.tv_pi, ViewBinding.TEXT)
                }

            }.bind(entity as Modeling<*>)
        }

        super.play()
    }
}