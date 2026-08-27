package me.kafuuneko.rpclient.feature.about.presentation

/** 关于页展示的外部联系与项目信息。 */
data class AboutUiState(
    val appVersionName: String,
    val githubRepoUrl: String,
    val githubRepoName: String,
    val developerEmail: String,
    val githubIssuesUrl: String = "$githubRepoUrl/issues"
)
