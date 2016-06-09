package ds.violin.v1.widget.image

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.widget.ImageView

/**
 * FadeInBitmapDrawable for fade in animation
 */
open class FadeInBitmapDrawable(res: Resources, bmp: Bitmap, fadingDrawable: Drawable?, fadingMatrix: Matrix?) :
        BitmapDrawable(res, bmp) {

    var fadingDrawable: Drawable? = fadingDrawable
    protected val fadingMatrix: Matrix?
    private var animationStarted: Long = 0
    protected val animationLength: Long = 220L
    private var fadingAlpha: Int = 255

    init {
        if (fadingDrawable != null) {
            this.fadingMatrix = Matrix(fadingMatrix)
            if (fadingDrawable.bounds.width() == 0) {
                this.fadingDrawable = null
            } else {
                animationStarted = SystemClock.uptimeMillis()
                invalidateSelf()
            }
        } else {
            this.fadingMatrix = null
        }
    }

    fun countAlpha() {
        val elapsedTime = SystemClock.uptimeMillis() - animationStarted
        if (elapsedTime >= animationLength) {
            fadingAlpha = 255
            animationStarted = 0L
            fadingDrawable = null
            alpha = 255
        }

        fadingAlpha = ((elapsedTime * 255) / animationLength).toInt()
    }

    override fun draw(canvas: Canvas?) {
        if (animationStarted == 0L) {
            super.draw(canvas)
            return
        }

        alpha = fadingAlpha
        super.draw(canvas)
        alpha = 255
    }

    fun drawFadingDrawable(canvas: Canvas, imageView: ImageView) {
        if (fadingDrawable == null) {
            return
        }

        countAlpha()

        if (fadingDrawable == null) {
            return
        }

        fadingDrawable!!.alpha = 255 - fadingAlpha
        if (fadingMatrix == null && imageView.paddingTop === 0 && imageView.paddingLeft === 0) {
            fadingDrawable!!.draw(canvas)
        } else {
            val saveCount = canvas.saveCount
            canvas.save()

            if (imageView.cropToPadding) {
                val scrollX = imageView.scrollX
                val scrollY = imageView.scrollY
                canvas.clipRect(scrollX + imageView.paddingLeft, scrollY + imageView.paddingTop,
                        scrollX + imageView.right - imageView.left - imageView.paddingRight,
                        scrollY + imageView.bottom - imageView.top - imageView.paddingBottom)
            }

            canvas.translate(imageView.paddingLeft.toFloat(), imageView.paddingTop.toFloat())

            if (fadingMatrix != null) {
                canvas.concat(fadingMatrix)
            }

            fadingDrawable!!.draw(canvas)
            canvas.restoreToCount(saveCount)
        }
        fadingDrawable!!.alpha = 255
    }
}