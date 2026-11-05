package ai.openonion.whatcha

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File

// A picture from the album, the way parents send the dish they cooked on WeChat. The phone shrinks it
// before it goes: the host takes 10 MB per image and a phone photo can be 8 MB before base64 adds a third.

/** What the agent reads beside the picture. */
internal fun photoPrompt(): String = "[照片]"

/** BitmapFactory decodes at 1/2, 1/4, 1/8...: the smallest such step that brings the longest side within [longest]. */
internal fun sampleSize(width: Int, height: Int, longest: Int = 1280): Int {
    var sample = 1
    while (maxOf(width, height) / sample > longest) sample *= 2
    return sample
}

/** The picture at [uri], decoded no larger than 1280 on its longest side, turned the way it was taken, saved as JPEG in the app's files. */
internal fun shrink(context: Context, uri: Uri, at: Long): File {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight) }
    val decoded = context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, options)!! }
    val orientation = context.contentResolver.openInputStream(uri)!!.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
    val out = File(context.filesDir, "photos").apply { mkdirs() }.resolve("$at.jpg")
    out.outputStream().use { upright(decoded, orientation).compress(Bitmap.CompressFormat.JPEG, 80, it) }
    return out
}

// Cameras store the sensor's picture and a note of how the phone was held; viewers that ignore the note show it sideways.
private fun upright(bitmap: Bitmap, orientation: Int): Bitmap {
    val degrees = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> return bitmap
    }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees) }, true)
}

/** The kept copy, decoded small enough for a 200dp bubble. */
internal fun thumbnail(path: String): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, longest = 600) })!!
}
