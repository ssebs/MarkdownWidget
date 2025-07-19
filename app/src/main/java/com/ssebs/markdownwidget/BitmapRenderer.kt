package com.ssebs.markdownwidget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.widget.TextView
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.SoftBreakAddsNewLinePlugin
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tables.TableTheme
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.ImagesPlugin
import kotlin.math.min

class BitmapRenderer(val context: Context) {
    companion object {
        const val IMAGE_PAD = 10
        // Maximum bitmap dimensions to stay within memory limits
        const val MAX_BITMAP_WIDTH = 1000
        const val MAX_BITMAP_HEIGHT = 2400
        // Maximum bitmap size in bytes (roughly 10MB to be safe)
        const val MAX_BITMAP_SIZE_BYTES = 10 * 1024 * 1024
    }

    private val tableTheme = TableTheme.emptyBuilder()
        .tableBorderColor(Color.TRANSPARENT)
        .tableHeaderRowBackgroundColor(Color.TRANSPARENT)
        .tableEvenRowBackgroundColor(Color.TRANSPARENT)
        .tableOddRowBackgroundColor(Color.TRANSPARENT)
        .build()

    var markwon: Markwon = Markwon.builder(context)
        .usePlugin(TaskListPlugin.create(context))
        .usePlugin(TablePlugin.create(tableTheme))
        .usePlugin(ImagesPlugin.create())
        .usePlugin(SoftBreakAddsNewLinePlugin.create())
        .usePlugin(StrikethroughPlugin.create())
        .usePlugin(HtmlPlugin.create())
        .usePlugin(object : AbstractMarkwonPlugin() {
            override fun configureTheme(builder: MarkwonTheme.Builder) {
                builder.headingBreakHeight(1).headingTextSizeMultipliers(
                    floatArrayOf(
                        1.5f,
                        1.25f,
                        1.15f,
                        1f,
                        .83f,
                        .67f
                    )
                )
            }
        })
        .build()

    fun renderBitmap(string: String, width: Int): Bitmap {
        val paddedWidth = min(width - IMAGE_PAD * 2, MAX_BITMAP_WIDTH)

        val textView = TextView(context)
        textView.setTextColor(Color.WHITE)
        textView.textSize = 16F

        markwon.setMarkdown(textView, string)

        // Properly measure with width constraints
        val widthMeasureSpec = android.view.View.MeasureSpec.makeMeasureSpec(paddedWidth, android.view.View.MeasureSpec.EXACTLY)
        val heightMeasureSpec = android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
        textView.measure(widthMeasureSpec, heightMeasureSpec)

        // Limit the height to prevent excessive memory usage
        val measuredHeight = min(textView.measuredHeight, MAX_BITMAP_HEIGHT)

        // Calculate bitmap size and ensure it's within limits
        val estimatedSize = paddedWidth * measuredHeight * 2 // 2 bytes per pixel for RGB_565
        if (estimatedSize > MAX_BITMAP_SIZE_BYTES) {
            // Scale down if too large
            val scaleFactor = kotlin.math.sqrt(MAX_BITMAP_SIZE_BYTES.toDouble() / estimatedSize)
            val scaledWidth = (paddedWidth * scaleFactor).toInt()
            val scaledHeight = (measuredHeight * scaleFactor).toInt()

            return createScaledBitmap(textView, paddedWidth, measuredHeight, scaledWidth, scaledHeight)
        }

        textView.layout(0, 0, paddedWidth, measuredHeight)

        // Use RGB_565 instead of ARGB_8888 to reduce memory usage by 50%
        val bitmap = Bitmap.createBitmap(paddedWidth, measuredHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT) // Set background color since RGB_565 doesn't support transparency
        textView.draw(canvas)

        return bitmap
    }

    private fun createScaledBitmap(textView: TextView, originalWidth: Int, originalHeight: Int,
                                   scaledWidth: Int, scaledHeight: Int): Bitmap {
        // Re-measure and layout with proper constraints
        val widthMeasureSpec = android.view.View.MeasureSpec.makeMeasureSpec(originalWidth, android.view.View.MeasureSpec.EXACTLY)
        val heightMeasureSpec = android.view.View.MeasureSpec.makeMeasureSpec(originalHeight, android.view.View.MeasureSpec.EXACTLY)
        textView.measure(widthMeasureSpec, heightMeasureSpec)
        textView.layout(0, 0, originalWidth, originalHeight)

        // Create original bitmap
        val originalBitmap = Bitmap.createBitmap(originalWidth, originalHeight, Bitmap.Config.ARGB_8888)
        val originalCanvas = Canvas(originalBitmap)
        originalCanvas.drawColor(Color.TRANSPARENT)
        textView.draw(originalCanvas)

        // Scale down
        val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, scaledWidth, scaledHeight, true)
        originalBitmap.recycle() // Clean up original bitmap

        return scaledBitmap
    }

    // Alternative method that truncates content instead of scaling
    fun renderBitmapTruncated(string: String, width: Int, maxLines: Int = 50): Bitmap {
        val paddedWidth = min(width - IMAGE_PAD * 2, MAX_BITMAP_WIDTH)

        val textView = TextView(context)
        textView.setTextColor(Color.WHITE)
        textView.textSize = 15F
        textView.maxLines = maxLines
        textView.ellipsize = android.text.TextUtils.TruncateAt.END

        markwon.setMarkdown(textView, string)

        // Properly measure with width constraints
        val widthMeasureSpec = android.view.View.MeasureSpec.makeMeasureSpec(paddedWidth, android.view.View.MeasureSpec.EXACTLY)
        val heightMeasureSpec = android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
        textView.measure(widthMeasureSpec, heightMeasureSpec)

        val measuredHeight = min(textView.measuredHeight, MAX_BITMAP_HEIGHT)
        textView.layout(0, 0, paddedWidth, measuredHeight)

        val bitmap = Bitmap.createBitmap(paddedWidth, measuredHeight, Bitmap.Config.RGB_565)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLACK)
        textView.draw(canvas)

        return bitmap
    }
}