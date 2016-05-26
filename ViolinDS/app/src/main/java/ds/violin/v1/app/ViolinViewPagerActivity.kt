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
import ds.violin.v1.app.violin.ViewPagerViolin
import ds.violin.v1.viewmodel.binding.ModelViewBinding

abstract class ViolinViewPagerActivity : ViolinActivity(), ViewPagerViolin {

    override lateinit var viewPager: ViewPager
    override var tabLayout: TabLayout? = null
    override var adapter: PagerAdapter? = null
    override var adapterViewBinder: ModelViewBinding<PagerAdapter>? = null
    override var currentPage: Int = 0

    override fun play() {

        /** create your [adapter] before calling super */

        super<ViewPagerViolin>.play()
        super<ViolinActivity>.play()
    }

    override fun saveInstanceState(outState: Bundle) {
        super<ViolinActivity>.saveInstanceState(outState)
        super<ViewPagerViolin>.saveInstanceState(outState)
    }

    override fun restoreInstanceState(savedInstanceState: Bundle) {
        super<ViolinActivity>.restoreInstanceState(savedInstanceState)
        super<ViewPagerViolin>.restoreInstanceState(savedInstanceState)
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super<ViolinActivity>.onViewCreated(view, savedInstanceState)
        super<ViewPagerViolin>.onViewCreated(view, savedInstanceState)
    }



}