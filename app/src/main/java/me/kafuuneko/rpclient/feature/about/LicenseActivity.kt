package me.kafuuneko.rpclient.feature.about

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.about.ui.LicenseLayout
import me.kafuuneko.rpclient.libs.core.CoreActivity

/**
 * 应用内 MIT 许可证全文。
 *
 * MIT 要求随软件分发保留版权与许可声明，跳去 GitHub 仓库并不等于随包分发，
 * 因此把仓库根的 LICENSE 拷进 res/raw 直接在应用里读。
 */
class LicenseActivity : CoreActivity() {
    @Composable
    override fun ViewContent() {
        val licenseText = remember {
            resources.openRawResource(R.raw.license)
                .bufferedReader()
                .use { it.readText() }
        }

        Surface(modifier = Modifier.fillMaxSize()) {
            LicenseLayout(licenseText = licenseText, onBack = { finish() })
        }
    }
}
