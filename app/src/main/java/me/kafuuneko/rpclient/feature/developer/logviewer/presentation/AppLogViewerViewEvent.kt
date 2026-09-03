package me.kafuuneko.rpclient.feature.developer.logviewer.presentation

import me.kafuuneko.rpclient.libs.core.IViewEvent

sealed class AppLogViewerViewEvent : IViewEvent {
    data class CopyText(val text: String) : AppLogViewerViewEvent()

    /** 由 Activity 调起系统文档创建器；日志不落 App 私有目录，只写用户选的位置。 */
    data class OpenLogExporter(val fileName: String) : AppLogViewerViewEvent()
}
