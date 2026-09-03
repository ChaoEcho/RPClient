package me.kafuuneko.rpclient.feature.requestlog.presentation

import me.kafuuneko.rpclient.libs.core.IViewEvent

/** 由 Activity 执行剪贴板或 JSON 查看器导航的一次性事件。 */
sealed class RequestLogViewEvent : IViewEvent {
    data class CopyText(val text: String) : RequestLogViewEvent()

    data class OpenJson(val title: String, val json: String) : RequestLogViewEvent()

    /** 由 Activity 调起系统文档创建器，导出内容只写到用户选定的位置。 */
    data class OpenLogExporter(val fileName: String) : RequestLogViewEvent()
}
