package me.kafuuneko.rpclient.feature.main.model.items

/** 首页全部内容流中可按最近活跃时间统一排序的条目。 */
sealed interface MainHomeContentItem {
    val latestTime: Long
}
