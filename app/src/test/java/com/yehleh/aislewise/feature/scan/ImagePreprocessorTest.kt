package com.yehleh.aislewise.feature.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImagePreprocessorTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preprocessor = ImagePreprocessor(context)

    private fun photo(width: Int, height: Int, orientation: Int? = null): Uri {
        val file = File(context.cacheDir, "photo_${width}x$height.jpg")
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        if (orientation != null) {
            ExifInterface(file).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
                saveAttributes()
            }
        }
        return Uri.fromFile(file)
    }

    private fun decode(bytes: ByteArray) = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

    @Test fun `large photo is decoded and scaled to the max edge`() {
        val out = decode(assertNotNullAndGet(preprocessor.toJpeg(photo(4000, 3000))))
        assertEquals(1600, out.width)
        assertEquals(1200, out.height)
    }

    @Test fun `small photo keeps its size`() {
        val out = decode(assertNotNullAndGet(preprocessor.toJpeg(photo(800, 600))))
        assertEquals(800, out.width)
    }

    @Test fun `exif rotation is applied`() {
        val out = decode(assertNotNullAndGet(preprocessor.toJpeg(photo(1200, 900, ExifInterface.ORIENTATION_ROTATE_90))))
        assertEquals(900, out.width)
        assertEquals(1200, out.height)
    }

    @Test fun `non-image file is reported as unreadable`() {
        val file = File(context.cacheDir, "not_an_image.jpg").apply { writeText("hello") }
        assertNull(preprocessor.toJpeg(Uri.fromFile(file)))
    }

    private fun assertNotNullAndGet(bytes: ByteArray?): ByteArray {
        assertNotNull("photo should be readable", bytes)
        return bytes!!
    }
}
