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

package ds.violin.v1.widget

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.widget.ImageView
import ds.violin.v1.R
import ds.violin.v1.util.ImageDescriptor
import ds.violin.v1.widget.image.LazyLoadingImageView
import ds.violin.v1.widget.image.OnImageSetListener
import ds.violin.v1.widget.image.RoundedCorneredDrawable
import org.json.simple.JSONArray
import org.json.simple.JSONValue

/**
 * an [ImageView] with lazy loading - with default, loading and error images
 * and also with rounded corners if [cornerRadius] is > 0
 *
 * !note: still has some issues when any image dimension is smaller than the view because of the various scale types
 */
open class LazyImageView : ImageView, LazyLoadingImageView {

    override var onImageSetListener: OnImageSetListener? = null
    override var isAnimated: Boolean = false
    override var defaultResID: Int = 0
    override var defaultIsAnimated: Boolean = false
    override var defaultScaleType: ScaleType? = null
    override var loadingResID: Int = 0
    override var loadingIsAnimated: Boolean = false
    override var loadingScaleType: ScaleType? = null
    override var errorResID: Int = 0
    override var errorIsAnimated: Boolean = false
    override var errorScaleType: ScaleType? = null
    override var alwaysLoadInBackground: Boolean = false
    override var shouldScaleImageSize: Boolean = false
    override var setBitmapAnimation: Any? = null
    override var originalVisibility: Int? = null
    override lateinit var originalScaleType: ScaleType
    override var loadedFromCache: Boolean = false
    override lateinit var descriptor: ImageDescriptor
    override var completion: ((ImageDescriptor, Bitmap?, Throwable?) -> Unit)? = null
    /** corner radius */
    var cornerRadius: Int = 0
    /** scale type of the round cornered drawable */
    var roundCorneredScaleType: ScaleType

    var haveFrame: Boolean = false

    constructor(context: Context) : this(context, null)

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {

        /** corner radius */
        val a = context.obtainStyledAttributes(attrs, R.styleable.LazyImageView, defStyleAttr, 0)
        cornerRadius = a.getDimensionPixelSize(R.styleable.LazyImageView_cornerRadius, 0)
        a.recycle()

        /** initialize [LazyLoadingImageView] */
        readAttributes(context, attrs, defStyleAttr, 0)

        /** save scale type */
        roundCorneredScaleType = originalScaleType
    }


    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) :
    super(context, attrs, defStyleAttr, defStyleRes) {

        /** corner radius */
        val a = context.obtainStyledAttributes(attrs, R.styleable.LazyImageView, defStyleAttr, defStyleRes)
        cornerRadius = a.getDimensionPixelSize(R.styleable.LazyImageView_cornerRadius, 0)
        a.recycle()

        /** initialize [LazyLoadingImageView] */
        readAttributes(context, attrs, defStyleAttr, defStyleRes)

        /** save scale type */
        roundCorneredScaleType = originalScaleType
    }

    override fun setImageDrawable(drawable: Drawable) {
        if (cornerRadius > 0 && drawable is BitmapDrawable) {

            /** has rounded corners - convert [drawable] to [RoundedCorneredDrawable] */
            val drawable = RoundedCorneredDrawable.convertDrawable(drawable, cornerRadius) as RoundedCorneredDrawable

            roundCorneredScaleType = scaleType
            configureDrawableBounds()
            scaleType = ScaleType.FIT_XY

            super.setImageDrawable(drawable)
        } else {

            /** no corner - act normal */
            super.setImageDrawable(drawable)
        }
    }

    override fun setImageResource(resID: Int) {
        setImageDrawable(context.resources.getDrawable(resID, null))
    }

    override fun setFrame(l: Int, t: Int, r: Int, b: Int): Boolean {
        val ret = super.setFrame(l, t, r, b)
        if (cornerRadius > 0) {
            haveFrame = true
            configureDrawableBounds()
        }
        return ret
    }

    /**
     * for [RoundedCorneredDrawable] when [cornerRadius] > 0
     */
    fun configureDrawableBounds() {
        if (drawable == null || drawable !is RoundedCorneredDrawable || !haveFrame) {
            return
        }

        val drawable = drawable as RoundedCorneredDrawable

        val vwidth = width - paddingLeft - paddingRight
        val vheight = height - paddingTop - paddingBottom
        drawable.setBounds(0, 0, vwidth, vheight)

        val matrix = createScaleTypeMatrix(drawable, roundCorneredScaleType, false)
        drawable.drawMatrix = matrix
    }

    override fun onDraw(canvas: Canvas) {
        super<LazyLoadingImageView>.onDraw(canvas)
        super<ImageView>.onDraw(canvas)
    }
}