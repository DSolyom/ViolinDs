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
package ds.violin.v1.widget.image

import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.TransitionDrawable

class RoundedCorneredDrawable() : Drawable() {

    lateinit var state: RoundedCorneredState

    var bitmapWidth: Int = 0
    var bitmapHeight: Int = 0

    var cornerRadius: Int
        get() {
            return state.cornerRadius
        }
        set(value) {
            state.cornerRadius = value
        }

    var drawMatrix: Matrix?
        get() {
            throw UnsupportedOperationException()
        }
        set(value) {
            state.shader.setLocalMatrix(value)
        }

    constructor(bitmap: Bitmap, cornerRadius: Int) : this() {
        state = RoundedCorneredState(bitmap, cornerRadius)
        bitmapWidth = bitmap.width
        bitmapHeight = bitmap.height
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)

        state.bounds = RectF(bounds)
    }
    
    override fun draw(canvas: Canvas?) {
        canvas?.drawRoundRect(state.bounds, state.cornerRadius.toFloat(),
                state.cornerRadius.toFloat(), state.paint)
    }

    override fun getIntrinsicWidth(): Int {
        return bitmapWidth;
    }

    override fun getIntrinsicHeight(): Int {
        return bitmapHeight;
    }

    override fun getOpacity(): Int {
        return PixelFormat.TRANSLUCENT
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        state.paint.colorFilter = colorFilter
    }

    override fun setAlpha(alpha: Int) {
        state.paint.alpha = alpha
    }

    override fun getConstantState(): ConstantState? {
        return state
    }

    companion object {
        fun convertDrawable(drawable: Drawable, cornerRadius: Int): Drawable {
            if (drawable != null) {
                if (drawable is TransitionDrawable) {
                    val num = drawable.numberOfLayers

                    val drawableList = arrayOfNulls<Drawable>(num)
                    for (i in 0..num-1) {
                        drawableList[i] = convertDrawable(drawable.getDrawable(i), cornerRadius)
                    }
                    return TransitionDrawable(drawableList);
                }

                if (drawable is BitmapDrawable) {
                    return RoundedCorneredDrawable(drawable.bitmap, cornerRadius)
                } else if (drawable is ColorDrawable) {
                    return drawable
                }

                var bitmap: Bitmap?
                val width = drawable.intrinsicWidth;
                val height = drawable.intrinsicHeight;
                if (width > 0 && height > 0) {
                    bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap);
                    drawable.setBounds(0, 0, canvas.width, canvas.height);
                    drawable.draw(canvas);
                }
            }
            return drawable;
        }
    }

    class RoundedCorneredState() : ConstantState() {

        lateinit var bitmap: Bitmap
        var cornerRadius: Int = 0
        var bounds: RectF = RectF()
        lateinit var paint: Paint
        lateinit var shader: BitmapShader

        constructor(bitmap: Bitmap, cornerRadius: Int) : this() {
            this.bitmap = bitmap
            this.cornerRadius = cornerRadius
            paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
            shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            paint.shader = shader
        }

        constructor(otherState: RoundedCorneredState): this(otherState.bitmap, otherState.cornerRadius) {
            paint = Paint(otherState.paint)
            bitmap = otherState.bitmap
            cornerRadius = otherState.cornerRadius

            shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            paint.shader = shader;
            paint.style = Paint.Style.FILL_AND_STROKE;
        }

        override fun getChangingConfigurations(): Int {
            return 0
        }

        override fun newDrawable(): Drawable? {
            return RoundedCorneredDrawable()
        }
    }
}