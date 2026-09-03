package me.kafuuneko.rpclient.feature.about

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.kafuuneko.rpclient.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AboutActivityIntentTest {
    @Test
    fun repositoryIntentUsesActionViewAndConfiguredUri() {
        val url = "https://github.com/KafuuNeko/RPClient"

        val intent = repositoryIntent(url)

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(url, intent.dataString)
    }

    /** MIT 要求随软件分发许可全文，因此它必须随包打进 res/raw 而不是只留一个外链。 */
    @Test
    fun licenseTextIsBundledWithTheApp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val license = context.resources.openRawResource(R.raw.license)
            .bufferedReader()
            .use { it.readText() }

        assertTrue(license.contains("MIT License"))
        assertTrue(license.contains("Copyright"))
        assertTrue(license.contains("WITHOUT WARRANTY OF ANY KIND"))
    }
}
