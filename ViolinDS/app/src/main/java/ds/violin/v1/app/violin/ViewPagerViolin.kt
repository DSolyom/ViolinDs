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

package ds.violin.v1.app.violin

import android.os.Bundle
import android.support.design.widget.TabLayout
import android.support.v4.view.PagerAdapter
import android.support.v4.view.ViewPager
import android.view.View
import ds.violin.v1.model.entity.SelfLoadable
import ds.violin.v1.util.common.Debug
import ds.violin.v1.viewmodel.binding.ModelViewBinding

interface ViewPagerViolin {

    companion object {
        const val TAG_VP_STATE = "__viewpagerviolin_vp_state_"
    }

    /** #Protected - the view pager's resource id */
    val viewPagerID: Int
    /** lateinit - the view pager - set in [onViewCreated] */
    var viewPager: ViewPager
    /** #Protected - the sliding tab's resource id */
    val tabLayoutID: Int?
    /** =null - optional tab layout - set in [onViewCreated] */
    var tabLayout: TabLayout?
    /** =null - set before [ViewPagerViolin.play] */
    var adapter: PagerAdapter?
    /** =null - set before [ViewPagerViolin.play] or the default binder will be used */
    var adapterViewBinder: ModelViewBinding<PagerAdapter>?
    /** = 0 (or as you like) - currently selected page in the view pager */
    var currentPage: Int

    /**
     * finding the [viewPager]
     * !note: call before other Violin's onViewCreated
     */
    fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        viewPager = (this as PlayingViolin).findViewById(viewPagerID) as ViewPager
        if (tabLayoutID != null) {
            tabLayout = this.findViewById(tabLayoutID!!) as TabLayout
        }
    }

    /**
     * registering adapter if it is a [SelfLoadable] entity so no need to do that manually
     * this must be called before [LoadingViolin.play]
     */
    fun play() {
        if (!(this as PlayingViolin).played) {
            if (adapterViewBinder == null) {
                adapterViewBinder = BasicViewPagerBinder()
            }
            if (adapter is SelfLoadable && this is LoadingViolin) {

                /** register adapter for loading */
                registerEntity(RecyclerViewViolin.TAG_ENTITY_ADAPTER, adapter as SelfLoadable) { adapter, error ->
                    onAdapterLoadFinished(error)
                }
            } else {

                /** just bind the adapter (via [onAdapterLoadFinished]) */
                onAdapterLoadFinished(null)
            }
        }
    }

    /**
     * called when [adapter] load is finished, either it was successful or not
     */
    fun onAdapterLoadFinished(error: Throwable?) {
        if (error == null) {

            /** bind the [adapter] to the [viewPager] */
            adapterViewBinder!!.bind(this.adapter!!, viewPager, this as PlayingViolin)

            /** restore view pager's saved position */
            viewPager.currentItem = currentPage
        } else {
            Debug.logException(error)
        }
    }

    /**
     * get [viewPager]'s last selected page
     */
    fun restoreInstanceState(savedInstanceState: Bundle) {
        try {
            currentPage = savedInstanceState.getSerializable(TAG_VP_STATE) as Int
        } catch(e: Throwable) {
            ;
        }
    }

    /**
     * save [viewPager]'s current page
     */
    fun saveInstanceState(outState: Bundle) {
        outState.putSerializable(TAG_VP_STATE, currentPage)
    }
}

/**
 * the default view pager binder - binds the [PagerAdapter] and the [TabLayout] (if present) to the view pager
 */
open class BasicViewPagerBinder : ModelViewBinding<PagerAdapter> {

    override lateinit var rootView: View
    override lateinit var on: PlayingViolin

    override fun bind(adapter: PagerAdapter) {

        val viewPager = rootView as ViewPager

        if (viewPager.adapter != adapter) {
            viewPager.adapter = adapter

            (on as ViewPagerViolin).tabLayout?.setupWithViewPager(viewPager)
            viewPager.addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {

                override fun onPageSelected(position: Int) {
                    (on as ViewPagerViolin).currentPage = position
                }
            })
        } else {
            adapter.notifyDataSetChanged()
        }
    }


}