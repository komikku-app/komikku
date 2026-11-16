package com.hippo.unifile

import android.net.Uri
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class UniFileTest {

    @TempDir
    lateinit var dir: File

    @Test
    fun `file operations`() {
        val root = UniFile.fromFile(dir)!!
        root.isDirectory shouldBe true

        val sub = root.createDirectory("manga")!!
        val page = sub.createFile("001.jpg")!!
        page.openOutputStream().use { it.write(byteArrayOf(1, 2, 3)) }
        page.openOutputStream(true).use { it.write(byteArrayOf(4)) }

        page.length() shouldBe 4
        page.type shouldBe "image/jpeg"
        page.name shouldBe "001.jpg"
        page.parentFile shouldBe sub
        sub.findFile("001.jpg") shouldNotBe null
        sub.findFile("missing") shouldBe null
        sub.listFiles()!!.map { it.name } shouldBe listOf("001.jpg")
        sub.listFiles { _, name -> name.endsWith(".png") }!!.size shouldBe 0
        page.openInputStream().use { it.readBytes().toList() } shouldBe listOf<Byte>(1, 2, 3, 4)

        page.renameTo("002.jpg") shouldBe true
        page.name shouldBe "002.jpg"
        File(dir, "manga/002.jpg").isFile shouldBe true

        val fromUri = UniFile.fromUri(null, Uri.fromFile(File(dir, "manga/002.jpg")))!!
        fromUri.exists() shouldBe true
        fromUri.filePath shouldBe File(dir, "manga/002.jpg").path

        val raf = fromUri.createRandomAccessFile("rw")
        raf.seek(3)
        raf.write(byteArrayOf(9))
        raf.length() shouldBe 4
        raf.close()

        sub.delete() shouldBe true
        sub.exists() shouldBe false
        UniFile.fromUri(null, Uri.parse("content://x/y")) shouldBe null
    }
}
