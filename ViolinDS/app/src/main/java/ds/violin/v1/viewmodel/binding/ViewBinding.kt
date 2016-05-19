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

package ds.violin.v1.viewmodel.binding

import android.graphics.Color
import android.graphics.drawable.AnimationDrawable
import android.graphics.drawable.Drawable
import android.support.v4.content.ContextCompat
import android.support.v7.widget.RecyclerView
import android.text.Html
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.style.UnderlineSpan
import android.util.Pair
import android.view.View
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.TextView
import ds.violin.v1.Global
import ds.violin.v1.R
import ds.violin.v1.app.violin.ActivityViolin
import ds.violin.v1.app.violin.PlayingViolin
import ds.violin.v1.util.common.AnimationStarter
import ds.violin.v1.util.common.Debug
import ds.violin.v1.util.ImageDescriptor
import ds.violin.v1.widget.LazyImageView
import java.io.Serializable


/**
 * to use to bind 'transport event' to views
 */
class Transport(val target: Any,
                val data: Serializable? = null,
                val sharedViews: Array<Pair<View, String>>? = null)

interface ViewBinding {

    companion object {
        val VIEW_NONE = -1

        val DETECT = 0

        val TEXT = 1
        val TEXT_RESOURCE = 2
        val FROM_HTML = 3
        val FROM_HTML_W_LINK = 4
        val TEXT_COLOR = 5
        val TEXT_UNDERLINE = 6

        val BACKGROUND = 10
        val BACKGROUND_COLOR = 11
        val BACKGROUND_DRAWABLE = 12
        val BACKGROUND_ANIMATION = 13

        val IMAGE = 20
        val IMAGE_DRAWABLE = 21
        val IMAGE_LAZY = 22
        val IMAGE_ANIMATION_START = 23
        val IMAGE_TINT = 24

        val GONE = 30
        val INVISIBLE = 31
        val VISIBLE = 32

        val ENABLED = 35

        val CHECKBOX_CHECKED = 51

        val FORMFIELD = 60
        val FIELD_EDITTEXT = 61
        val ONKEY_LISTENER = 62

        val FIELD_SPINNER = FORMFIELD
        val FIELD_MULTICHOICESPINNER = FORMFIELD
        val FIELD_RADIOBUTTON = FORMFIELD
        val FIELD_RADIOGROUP = 70
        val FIELD_CHECKBOX = 71
        val FIELD_SLIDER = 72

        val ONCLICK_TRANSPORT = 100

        val ONCLICK_FORWARD = 102
        val ONCLICK_FORWARD_AND_CLEAR = 103
        val ONCLICK_DIALOG = 104
        val ONCLICK_DIALOG_CLOSE = 105
        val ONCLICK_RUNNABLE = 106
        val ONCLICK_GOBACK = 107
        val ONCLICK_GOBACKTO = 108

        val ONCLICK_LISTENER = 200
        val ONLONGCLICK_LISTENER = 201

        val ONFOCUSCHANGE_LISTENER = 205
        val ONTOUCH_LISTENER = 206
        val ONPOSITIONCHANGED_LISTENER = 207
        val ONSCROLL_LISTENER = 208
        val ONCHECKEDCHANGE_LISTENER = 209
        val ONBUTTONSELECT_LISTENER = 210

        val ONCLICK_SEARCH = 300

        val SELECTED_STATE = 500
        val TAG = 501

        val PADDING = 600
        val PADDING_LEFT = 601
        val PADDING_RIGHT = 602
        val PADDING_BOTTOM = 603
        val PADDING_TOP = 604

        val FIX_TILE_MODE = 10000
    }

    /**
     *
     * @param viewResID
     * @return
     */
    fun findViewById(viewResID: Int, view: View): View? {
        if (viewResID == VIEW_NONE) {
            return null
        }
        if (viewResID == 0 || view.id == viewResID) {
            return view
        }

        return view.findViewById(viewResID)
    }

    fun bind(value: Any?, viewResID: Int, method: Int, on: PlayingViolin, rootView: View): Any? {

        val view = findViewById(viewResID, rootView)

        if (view == null) {

            // this is just a warning
            // this way it's safe to create general template filling methods in data objects
            // even when the layout does not support some part of the data
            Debug.logW("template", "View (" + Integer.toHexString(viewResID) + ") not found for!")
            return null
        }

        return bind(value, view, method, on)
    }

    fun bind(value: Any?, view: View, method: Int, on: PlayingViolin): Any? {
        try {
            when (method) {
                TEXT -> {
                    if (value != null) {
                        (view as TextView).text = value.toString()
                    } else {
                        (view as TextView).text = ""
                    }
                }

                TEXT_RESOURCE -> {

                    // if not set - show nothing
                    if ((value as Int) != 0) {
                        (view as TextView).setText(value)
                    } else {
                        (view as TextView).text = ""
                    }
                }

                FROM_HTML_W_LINK,
                FROM_HTML -> {
                    if (method == FROM_HTML_W_LINK) {
                        (view as TextView).movementMethod = LinkMovementMethod.getInstance()
                    }

                    var text: String
                    if (value is Int) {
                        text = view.context!!.getString(value)
                    } else {
                        text = value as String
                    }
                    (view as TextView).text = Html.fromHtml(text)
                }

                TEXT_COLOR -> {
                    if (value is Int) {
                        (view as TextView).setTextColor(value)
                    } else {
                        (view as TextView).setTextColor(Color.parseColor(value.toString()))
                    }
                }

                TEXT_UNDERLINE -> {
                    val text = (view as TextView).text
                    if (text.length == 0) {
                        return null
                    }
                    val sText = SpannableString(text)
                    sText.setSpan(UnderlineSpan(), 0, text.length, 0)
                    view.setText(sText, TextView.BufferType.SPANNABLE)

                }

                BACKGROUND -> {
                    view.setBackgroundResource(value as Int)
                }

                BACKGROUND_COLOR -> {
                    view.setBackgroundColor(value as Int)
                }

                BACKGROUND_DRAWABLE -> {
                    view.background = value as Drawable
                }

                BACKGROUND_ANIMATION -> {

                    view.setBackgroundResource(value as Int)
                    val animation = view.background as AnimationDrawable
                    AnimationStarter.start(animation)
                }

                IMAGE -> {
                    val resource = value as Int?

                    if (resource != null) {
                        (view as ImageView).setImageResource(resource)
                    } else {
                        (view as ImageView).setImageResource(R.drawable.x_empty)
                    }

                }

                IMAGE_DRAWABLE -> {
                    val resource = value as Drawable?

                    if (resource != null) {
                        (view as ImageView).setImageDrawable(resource)
                    } else {
                        (view as ImageView).setImageResource(R.drawable.x_empty)
                    }
                }

                IMAGE_LAZY -> {

                    if (value == null || (value is String && value.length == 0)) {
                        (view as ImageView).setImageResource(R.drawable.x_empty)
                    } else {
                        (view as LazyImageView).loadImage(ImageDescriptor(value as String))
                    }
                }

                IMAGE_ANIMATION_START -> {

                    if (!isTrue(value)) {
                        return null
                    }

                    val drawable = (view as ImageView).drawable as AnimationDrawable

                    AnimationStarter.start(drawable)
                }

                IMAGE_TINT -> {
                    (view as ImageView).setColorFilter(ContextCompat.getColor(view.context!!, value as Int))
                }

                VISIBLE -> {

                    view.visibility =
                            when (isTrue(value)) {
                                true -> View.VISIBLE
                                else -> View.GONE
                            }
                }

                INVISIBLE -> {

                    view.visibility =
                            when (isTrue(value)) {
                                true -> View.INVISIBLE
                                else -> View.VISIBLE
                            }
                }

                GONE -> {

                    view.visibility =
                            when (isTrue(value)) {
                                true -> View.GONE
                                else -> View.VISIBLE
                            }
                }

                ENABLED -> {
                    view.isEnabled = isTrue(value)
                }

                CHECKBOX_CHECKED -> {
                    (view as CompoundButton).isChecked = isTrue(value)
                }

                ONKEY_LISTENER -> {
                    view.setOnKeyListener(value as View.OnKeyListener)
                }

                ONCLICK_TRANSPORT,
                ONCLICK_FORWARD,
                ONCLICK_FORWARD_AND_CLEAR,
                ONCLICK_GOBACK,
                ONCLICK_GOBACKTO -> {

                    val transport = value as Transport
                    view.setTag(R.integer.view_tag_click_id, on.violinActivity.Id)

                    // show another screen on click - sending data along the way
                    view.setOnClickListener { view ->

                        try {
                            val activity = Global.currentActivity as ActivityViolin?
                            if (activity == null || activity.Id != view.getTag(R.integer.view_tag_click_id)) {
                                return@setOnClickListener
                            }

                            if (activity.isActive()) {
                                when (method) {
                                    ONCLICK_TRANSPORT -> activity.transport(transport.target, transport.data, transport.sharedViews)
                                    ONCLICK_FORWARD -> activity.forward(transport.target, transport.data)
                                    ONCLICK_FORWARD_AND_CLEAR -> activity.forwardAndClear(transport.target, transport.data)
                                    ONCLICK_GOBACK -> activity.goBack(transport.data)
                                    ONCLICK_GOBACKTO -> activity.goBackTo(transport.target, transport.data)
                                }
                            }

                        } catch(e: Throwable) {
                            Debug.logException(e)

                        }

                    }
                }

                ONCLICK_RUNNABLE -> {

                    val runnable = value as Runnable

                    view.setOnClickListener {
                        try {
                            runnable.run()
                        } catch(e: Throwable) {
                            Debug.logException(e)
                        }

                    }
                }

                ONCLICK_LISTENER -> {
                    if (value == null) {
                        view.isClickable = false
                    } else if (value is View.OnClickListener) {
                        view.setOnClickListener(value)
                    } else {
                        view.setOnClickListener { (value as () -> Unit)() }
                    }
                }

                ONLONGCLICK_LISTENER -> {
                    if (value == null) {
                        view.isClickable = false
                    } else if (value is View.OnLongClickListener) {
                        view.setOnLongClickListener(value)
                    } else {
                        view.setOnLongClickListener { view -> (value as (View) -> Boolean)(view) }
                    }
                }

                ONFOCUSCHANGE_LISTENER -> {
                    view.onFocusChangeListener = value as View.OnFocusChangeListener
                }

                ONTOUCH_LISTENER -> {
                    view.setOnTouchListener(value as View.OnTouchListener)
                }

                ONSCROLL_LISTENER -> {
                    (view as RecyclerView).addOnScrollListener(value as RecyclerView.OnScrollListener)
                }

                ONCHECKEDCHANGE_LISTENER -> {
                    (view as CompoundButton).setOnCheckedChangeListener(value as CompoundButton.OnCheckedChangeListener)
                }

                SELECTED_STATE -> {
                    view.isSelected = isTrue(value)
                }

                TAG -> {
                    view.tag = value
                }

                PADDING -> {
                    val padding = value as Int
                    view.setPadding(padding, padding, padding, padding)
                }

                PADDING_LEFT -> {
                    view.setPadding(value as Int, view.paddingTop, view.paddingRight, view.paddingBottom)
                }

                PADDING_RIGHT -> {
                    view.setPadding(view.paddingLeft, view.paddingTop, value as Int, view.paddingBottom)
                }

                PADDING_TOP -> {
                    view.setPadding(view.paddingLeft,value as Int, view.paddingRight, view.paddingBottom)
                }

                PADDING_BOTTOM -> {
                    view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight,value as Int)
                }
            }
        } catch(e: Throwable) {
            Debug.logException(e)
            return null
        }

        return value
    }

    fun isTrue(value: Any?): Boolean {
        if (value == null) {
            return false
        }

        var positive: Boolean

        try {
            positive = value as Boolean
        } catch(e: ClassCastException) {
            if (value is String) {
                return value.length > 0
            } else {
                return value as Int > 0
            }
        }

        return positive
    }
}