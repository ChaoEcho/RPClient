package me.kafuuneko.rpclient.feature.about

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.net.toUri
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.about.presentation.AboutUiState
import me.kafuuneko.rpclient.feature.about.ui.AboutLayout
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.core.CoreActivity

/** 关于页面宿主：版本信息、许可证全文入口与上游归属。 */
class AboutActivity : CoreActivity() {
    @Composable
    override fun ViewContent() {
        val uiState = remember {
            AboutUiState(
                appVersionName = packageManager
                    .getPackageInfo(packageName, 0)
                    .versionName
                    ?: getString(R.string.unknown_version),
                upstreamRepoUrl = AppModel.GITHUB_REPO,
                upstreamRepoName = "KafuuNeko/RPClient"
            )
        }

        Surface(modifier = Modifier.fillMaxSize()) {
            AboutLayout(
                uiState = uiState,
                onBack = { finish() },
                onOpenLicense = { startActivity(Intent(this, LicenseActivity::class.java)) },
                onOpenUpstream = { safeOpenUrl(uiState.upstreamRepoUrl) }
            )
        }
    }

    /** 安全打开外部超链接，若无可用浏览器则回退至复制链接到剪贴板。 */
    private fun safeOpenUrl(url: String) {
        try {
            startActivity(repositoryIntent(url))
        } catch (_: ActivityNotFoundException) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(
                ClipData.newPlainText(getString(R.string.about), url)
            )
            Toast.makeText(this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
        }
    }
}

/** 构造只包含公开 URI 的外部浏览 Intent。 */
internal fun repositoryIntent(url: String): Intent = Intent(Intent.ACTION_VIEW, url.toUri())
