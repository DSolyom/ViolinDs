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

package ds.violin.v1.widget

import android.content.Context
import android.os.Parcelable
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import ds.violin.v1.app.violin.RecyclerViewViolin
import ds.violin.v1.viewmodel.AbsModelRecyclerViewItemBinder
import ds.violin.v1.widget.adapter.AbsHeaderedAdapter
import ds.violin.v1.widget.adapter.SectionedAdapter

/**
 * Sticky section headers for [IRecyclerView] with [LinearLayoutManager] and [AbsHeaderedAdapter]
 *
 * @use: just add this as a view to your layout, preferable above of the top of your [IRecyclerView]
 *       and set it's [recyclerView] in [RecyclerViewViolin.play]
 */
open class StickyHeader : FrameLayout {

    /** the connected recycler view */
    var recyclerView: IRecyclerView? = null
        set(value) {
            if (field == value) {
                return
            }

            if (field != null) {
                field!!.removeOnScrollListener(onScroll)
            }
            field = value
            if (field != null) {
                field!!.addOnScrollListener(onScroll)
            }

            currentSection = null
            visibility = View.GONE
        }

    /** selected section */
    var currentSection: Int? = null

    /** */
    lateinit var onScroll: RecyclerView.OnScrollListener

    /** the [AbsModelRecyclerViewItemBinder] for the header */
    var headerBinder: AbsModelRecyclerViewItemBinder? = null
        set(value) {
            field = value
            if (field != null) {
                visibility = View.VISIBLE
            } else {
                visibility = View.GONE
            }
        }

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle) {
        init()
    }

    fun init() {

        /** start [View.GONE] just in case */
        visibility = View.GONE

        val sh = this
        onScroll = object : RecyclerView.OnScrollListener() {

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val adapter = recyclerView.adapter ?: return
                if (adapter !is SectionedAdapter) {
                    throw UnsupportedOperationException("StickyHeader only works with SectionedAdapter")
                }
                val layoutManager = recyclerView.layoutManager ?: return
                if (layoutManager !is LinearLayoutManager) {
                    throw UnsupportedOperationException("StickyHeader only works with LinearLayoutManager")
                }

                /** first item's position in the recycler view and it's section */
                var firstPosition = layoutManager.findFirstVisibleItemPosition()
                val hasHeaderView = recyclerView is IRecyclerView && recyclerView.headerView != null
                if (hasHeaderView) {
                    --firstPosition
                }
                val section = adapter.sectionFor(firstPosition)

                if (currentSection == null || section != currentSection) {

                    /** section change */
                    if (section != null) {

                        /** set new section */
                        currentSection = section

                        /** get same (type) binder from the adapter as the section's header's */
                        headerBinder = adapter.onCreateViewHolder(sh,
                                adapter.getSectionViewType(section)) as AbsModelRecyclerViewItemBinder

                        /** add the sticky header view */
                        removeAllViewsInLayout()
                        addView(headerBinder!!.rootView)
                        headerBinder!!.bind(adapter.sectionInfos[section]!!)
                        val rootView = headerBinder!!.rootView
                        val lp = rootView.layoutParams as FrameLayout.LayoutParams
                        if (lp.bottomMargin < 0) {

                            /**
                             * sometimes section header's have negative bottom margin which if left
                             * alone will render our header more or less gone
                             */
                            lp.bottomMargin = 0
                        }

                        /** we need [headerBinder.getRootView.getMeasuredHeight] valid asap for 'push out' effect */
                        rootView.measure(lp.width, lp.height)
                    } else {

                        /** remove current section - there is no section info for some of the first items */
                        currentSection = null
                        headerBinder = null
                    }

                }

                if (adapter.sectionPositions.contains(firstPosition)) {

                    /** first section should now be invisible not to see double of it */
                    recyclerView.getChildAt(0).visibility = View.INVISIBLE
                }

                /** the rest of the headers' visibility should be restored */
                for (i in 1..recyclerView.childCount-1) {
                    if (adapter.sectionPositions.contains(firstPosition + i)) {
                        recyclerView.getChildAt(i).visibility = View.VISIBLE
                    }
                }

                if (currentSection != null) {

                    /** create 'push out' effect */
                    val rootView = headerBinder!!.rootView
                    val lp = rootView.layoutParams as FrameLayout.LayoutParams
                    if (recyclerView.childCount > 1 && adapter.sectionPositions.contains(firstPosition + 1)) {
                        val secondPos = recyclerView.getChildAt(1).top
                        lp.topMargin = Math.min(0, secondPos - rootView.measuredHeight)
                    } else {
                        lp.topMargin = 0
                    }
                    rootView.layoutParams = lp
                }
            }
        }
    }
}

open class IRecyclerView : RecyclerView {

    /** header for the recycler view */
    var headerView: View? = null
        set(headerView) {
            field = headerView
            val adapter = adapter
            if (adapter != null && adapter is AbsHeaderedAdapter) {
                adapter.headerView = field
            }
        }

    /** footer for the recycler view */
    var footerView: View? = null
        set(footerView) {
            field = footerView
            val adapter = adapter
            if (adapter != null && adapter is  AbsHeaderedAdapter) {
                adapter.footerView = field
            }
        }

    private var savedState: Parcelable? = null

    constructor(context: Context) : super(context) {
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle) {
    }

    override fun setAdapter(adapter: Adapter<ViewHolder>) {
        if (adapter === getAdapter()) {
            return
        }

        if (adapter is AbsHeaderedAdapter) {

            /** set [adapter.headerView] and [adapter.footerView] if those were here before the adapter */
            adapter.headerView = headerView
            adapter.footerView = footerView
        }

        super.setAdapter(adapter)
    }

    /**
     * save the state of the [RecyclerView.LayoutManager]
     */
    fun getState(): Parcelable? {
        return layoutManager?.onSaveInstanceState() ?: null
    }

    /**
     * restore the state of the [RecyclerView.LayoutManager] - takes effect on next [onLayout]
     */
    fun restoreState(state: Parcelable?) {
        savedState = state
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        if (savedState != null) {
            val stateSave = savedState
            savedState = null
            layoutManager?.onRestoreInstanceState(stateSave)
        }
        super.onLayout(changed, l, t, r, b)
    }

}
