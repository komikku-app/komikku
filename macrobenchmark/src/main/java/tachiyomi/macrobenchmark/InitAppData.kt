package tachiyomi.macrobenchmark

import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

/**
 * Walks through the first-launch onboarding flow using UiAutomator.
 *
 * Steps that match the original comments in the setup helper:
 *  1. Click "Next"
 *  2. Click "Select a folder" → "USE THIS FOLDER" → "ALLOW"
 *  3. Click "Next"
 *  4. Click first "Grant" → "Allow from this source" → navigate Back
 *  5. Click second "Grant" → "Allow"
 *  6. Click "Next"
 *  7. Click "Get started"
 */
fun initAppData(device: UiDevice, uiTimeOut: Long) {
    // 1. First "Next"
    device.wait(Until.findObject(By.text("Next")), uiTimeOut)?.click() ?: return
    device.waitForIdle()

    // 2a. "Select a folder"
    device.wait(Until.findObject(By.text("Select a folder")), uiTimeOut)?.click()
    Thread.sleep(2 * uiTimeOut) // TODO: Should manually select correct folder here
    device.waitForIdle()
    // 2b. "USE THIS FOLDER" (system folder picker dialog)
    device.wait(Until.findObject(By.text("USE THIS FOLDER")), uiTimeOut)?.click()
    device.waitForIdle()
    // 2c. "ALLOW" (system permission dialog)
    device.wait(Until.findObject(By.text("ALLOW")), uiTimeOut)?.click()
    device.waitForIdle()

    // 3. Second "Next"
    device.wait(Until.findObject(By.text("Next")), uiTimeOut)?.click()
    Thread.sleep(1000)
    device.waitForIdle()

    // 4a. First "Grant" button
    device.wait(Until.findObjects(By.text("Grant")), uiTimeOut)?.let { grantButtons ->
        // 4b. "Allow from this source" toggle / button
        if (grantButtons.size >= 4) {
            grantButtons.getOrNull(0)?.click()
            device.waitForIdle()
            device.wait(Until.findObject(By.text("Allow from this source")), uiTimeOut)?.click()
            device.waitForIdle()
            // 4c. Navigate back to onboarding
            device.pressBack()
            device.waitForIdle()
        }

        // 5a. Second "Grant" button
        if (grantButtons.size >= 3) {
            if (grantButtons.size >= 4) {
                grantButtons.getOrNull(1)?.click()
            } else {
                grantButtons.getOrNull(0)?.click()
            }
            device.waitForIdle()
            // 5b. System permission "Allow"
            device.wait(Until.findObject(By.text("Allow")), uiTimeOut)?.click()
            device.waitForIdle()
        }
    }

    // 6. Third "Next"
    device.wait(Until.findObject(By.text("Next")), uiTimeOut)?.click()
    Thread.sleep(4 * uiTimeOut) // TODO: Should restore data backup here
    device.waitForIdle()

    // 7. "Get started"
    device.wait(Until.findObject(By.text("Get started")), uiTimeOut)?.click()
    Thread.sleep(uiTimeOut)
    device.waitForIdle()
}
