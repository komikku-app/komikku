package mihon.core.archive

import android.content.Context
import com.hippo.unifile.UniFile
import java.io.File

/**
 * KMK: desktop counterparts of the Android `UniFile` archive helpers (same signatures). Desktop
 * `UniFile`s are always local files.
 */
fun UniFile.archiveReader(context: Context) = ArchiveReader(localFile())

fun UniFile.epubReader(context: Context) = EpubReader(archiveReader(context))

private fun UniFile.localFile(): File = File(filePath ?: error("Not a local file: $uri"))
