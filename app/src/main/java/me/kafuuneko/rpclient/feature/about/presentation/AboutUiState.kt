package me.kafuuneko.rpclient.feature.about.presentation

/**
 * 关于页展示的信息。
 *
 * 这是个人自用的 fork，只保留 MIT 要求的署名与许可，以及指回上游的一行归属；
 * 不放维护者的联系方式，也不把 Issues 引到上游仓库。
 */
data class AboutUiState(
    val appVersionName: String,
    val upstreamRepoUrl: String,
    val upstreamRepoName: String
)
