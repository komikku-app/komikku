package tachiyomi.macrobenchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.uiautomator.By
import org.junit.Rule
import org.junit.Test

class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() = baselineProfileRule.collect(
        packageName = "app.komikku.benchmark",
        profileBlock = {
            pressHome()
            startActivityAndWait()

            initAppData(device, 5000L)
            Thread.sleep(10000)
            device.waitForIdle()

            device.findObject(By.descContains("Filter"))?.click()
            device.waitForIdle()
            Thread.sleep(1000)
            device.findObject(By.text("Downloaded"))?.let {
                it.click()
                device.waitForIdle()
                it.click()
                device.waitForIdle()
                it.click()
                device.waitForIdle()
            }
            device.pressBack()
            device.waitForIdle()

            device.findObject(By.desc("More options"))?.click()
            device.waitForIdle()
            device.findObject(By.text("Update category"))?.click()
            device.waitForIdle()

            // Open library manga
            device.click(200, 600)
            Thread.sleep(5000)
            device.waitForIdle()
            device.pressBack()
            device.waitForIdle()

            device.findObject(By.text("Updates"))?.click()
            Thread.sleep(1000)
            device.waitForIdle()
            device.click(500, 600)
            Thread.sleep(10000)
            device.waitForIdle()
            device.pressBack()
            device.waitForIdle()

            device.findObject(By.text("History"))?.click()
            Thread.sleep(1000)
            device.waitForIdle()
            device.click(500, 500)
            Thread.sleep(10000)
            device.waitForIdle()
            device.pressBack()
            device.waitForIdle()

            device.findObject(By.text("Browse"))?.click()
            Thread.sleep(1000)
            device.waitForIdle()
            device.click(500, 900)
            Thread.sleep(10000)
            device.waitForIdle()
            device.pressBack()
            device.waitForIdle()

            device.findObject(By.text("Feed"))?.click()
            Thread.sleep(5000)
            device.waitForIdle()
            device.click(200, 700)
            Thread.sleep(5000)
            device.waitForIdle()
            device.pressBack()
            device.waitForIdle()

            device.findObject(By.text("Extensions"))?.click()
            Thread.sleep(500)
            device.waitForIdle()
            device.findObject(By.text("Migrate"))?.click()
            Thread.sleep(500)
            device.waitForIdle()
            device.findObject(By.text("More"))?.click()
            Thread.sleep(500)
            device.waitForIdle()
            device.findObject(By.text("Settings"))?.click()
            Thread.sleep(500)
            device.waitForIdle()

            Thread.sleep(5000)
        },
    )
}
