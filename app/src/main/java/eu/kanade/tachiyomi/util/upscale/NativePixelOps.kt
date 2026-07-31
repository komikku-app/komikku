package eu.kanade.tachiyomi.util.upscale

import android.graphics.Bitmap
import java.nio.ByteBuffer

object NativePixelOps {
    init {
        System.loadLibrary("aiupscaler")
    }

    external fun writeBitmapToBufferNHWC(
        bitmap: Bitmap,
        directBuffer: ByteBuffer,
        bufferPixelOffset: Int,
        tileSize: Int
    )

    external fun writeBitmapToBufferNCHW(
        bitmap: Bitmap,
        directBuffer: ByteBuffer,
        bufferPixelOffset: Int,
        tileSize: Int
    )

    external fun readBufferToBitmapNHWC(
        directBuffer: ByteBuffer,
        bufferPixelOffset: Int,
        targetBitmap: Bitmap,
        outSize: Int
    )

    external fun readBufferToBitmapNCHW(
        directBuffer: ByteBuffer,
        bufferPixelOffset: Int,
        targetBitmap: Bitmap,
        outSize: Int
    )
}
