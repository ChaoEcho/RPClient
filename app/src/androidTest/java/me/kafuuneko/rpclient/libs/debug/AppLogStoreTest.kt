package me.kafuuneko.rpclient.libs.debug

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chibatching.kotpref.Kotpref
import me.kafuuneko.rpclient.libs.AppModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppLogStoreTest {
    private var originalEnabled = false

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Kotpref.init(context)
        AppLogStore.init(context)
        originalEnabled = AppModel.developerLoggingEnabled
        AppLogStore.clear()
    }

    @After
    fun tearDown() {
        AppModel.developerLoggingEnabled = originalEnabled
        AppLogStore.clear()
    }

    @Test
    fun loggingDisabled_dropsEverythingExceptErrors() {
        AppModel.developerLoggingEnabled = false

        AppLogStore.addLog(AppLogLevel.DEBUG, "Test", "debug line")
        AppLogStore.addLog(AppLogLevel.INFO, "Test", "info line")
        AppLogStore.addLog(AppLogLevel.WARN, "Test", "warn line")
        AppLogStore.addLog(AppLogLevel.ERROR, "Test", "error line")

        // 关闭时不应在 release 版本为每个 HTTP 请求做脱敏与缓冲；只有 ERROR 留作事后取证。
        val entries = AppLogStore.snapshot()
        assertEquals(1, entries.size)
        assertEquals(AppLogLevel.ERROR, entries.single().level)
    }

    @Test
    fun loggingEnabled_keepsEveryLevel() {
        AppModel.developerLoggingEnabled = true

        AppLogStore.addLog(AppLogLevel.DEBUG, "Test", "debug line")
        AppLogStore.addLog(AppLogLevel.INFO, "Test", "info line")

        assertEquals(2, AppLogStore.snapshot().size)
    }

    @Test
    fun secretsAreRedactedIncludingBareTokens() {
        AppModel.developerLoggingEnabled = true

        AppLogStore.addLog(
            AppLogLevel.INFO,
            "Test",
            "auth=Bearer abc.def-123 api_key: sk-secret token: tok-secret"
        )

        val message = AppLogStore.snapshot().single().message
        assertTrue(message.contains("Bearer ***"))
        assertTrue("api key must be redacted", !message.contains("sk-secret"))
        // 此前内联正则漏掉了 token，裸 token 会原样写进日志与文件。
        assertTrue("bare token must be redacted", !message.contains("tok-secret"))
    }

    @Test
    fun revisionAdvancesOnWriteSoViewersCanRefreshOnDemand() {
        AppModel.developerLoggingEnabled = true
        val before = AppLogStore.revision.value

        AppLogStore.addLog(AppLogLevel.INFO, "Test", "line")

        assertTrue(AppLogStore.revision.value > before)
    }
}
