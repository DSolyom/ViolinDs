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

package ds.violin.v1.app

import android.os.Bundle
import android.support.design.widget.TabLayout
import android.support.v4.view.PagerAdapter
import android.support.v4.view.ViewPager
import android.view.View
import ds.violin.v1.app.violin.LoadingViolin
import ds.violin.v1.app.violin.ViewPagerViolin
import ds.violin.v1.model.entity.SelfLoadable
import ds.violin.v1.viewmodel.binding.ModelViewBinding
import java.io.Serializable
import java.util.*

abstract class ViolinViewPagerActivity : ViolinActivity(), ViewPagerViolin, LoadingViolin {

    override val registeredEntities: MutableMap<String, LoadingViolin.RegisteredEntity> = HashMap()
    override val situationalEntities: MutableMap<String, LoadingViolin.RegisteredEntity> = HashMap()
    override val loadingEntities: MutableList<SelfLoadable> = ArrayList()
    override var allDataLoadListener: LoadingViolin.AllDataLoadListener? = null
    override var savedStates: HashMap<String, Any> = HashMap()
    override var idsOfParcelable: ArrayList<String> = ArrayList()
    override var idsOfLoaded: ArrayList<String> = ArrayList()

    override val loadingViewID: Int? = null
    override var loadingView: View? = null

    override lateinit var viewPager: ViewPager
    override var tabLayout: TabLayout? = null
    override var adapter: PagerAdapter? = null
    override var adapterViewBinder: ModelViewBinding<PagerAdapter>? = null
    override var currentPage: Int = 0

    override fun play() {

        /** create your [adapter] before calling super */

        super<ViewPagerViolin>.play()
        super<ViolinActivity>.play()
        super<LoadingViolin>.play()
    }

    override fun saveInstanceState(outState: Bundle) {
        super<ViolinActivity>.saveInstanceState(outState)
        super<ViewPagerViolin>.saveInstanceState(outState)
        super<LoadingViolin>.saveInstanceState(outState)
    }

    override fun restoreInstanceState(savedInstanceState: Bundle) {
        super<LoadingViolin>.saveInstanceState(savedInstanceState)
        super<ViolinActivity>.restoreInstanceState(savedInstanceState)
        super<ViewPagerViolin>.restoreInstanceState(savedInstanceState)
    }

    override fun invalidateRegisteredEntities(subViolinsToo: Boolean) {
        super<LoadingViolin>.invalidateRegisteredEntities(subViolinsToo)
        super<ViolinActivity>.invalidateRegisteredEntities(subViolinsToo)
    }

    override fun onConnectionChanged(connected: Boolean) {
        super<ViolinActivity>.onConnectionChanged(connected)
        super<LoadingViolin>.onConnectionChanged(connected)
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super<ViolinActivity>.onViewCreated(view, savedInstanceState)
        super<ViewPagerViolin>.onViewCreated(view, savedInstanceState)
        super<LoadingViolin>.onViewCreated(view, savedInstanceState)
    }

    override fun goBackTo(target: Any, result: Serializable?) {
        super<LoadingViolin>.goBackTo(target, result)
        super<ViolinActivity>.goBackTo(target, result)
    }

    override fun stopEverything() {
        super<LoadingViolin>.stopEverything()
        super<ViolinActivity>.stopEverything()
    }

    override fun onResume() {
        super<ViolinActivity>.onResume()
        super<LoadingViolin>.onResume()
    }

    override fun onPause() {
        super<LoadingViolin>.onPause()
        super<ViolinActivity>.onPause()
    }

    override fun onDestroy() {
        super<LoadingViolin>.onDestroy()
        super<ViolinActivity>.onDestroy()
    }

    override fun goBack(result: Serializable?) {
        super<LoadingViolin>.goBack(result)
        super<ViolinActivity>.goBack(result)
    }
}