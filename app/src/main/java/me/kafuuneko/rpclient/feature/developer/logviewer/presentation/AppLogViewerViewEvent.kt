package me.kafuuneko.rpclient.feature.developer.logviewer.presentation

import me.kafuuneko.rpclient.libs.core.IViewEvent

sealed class AppLogViewerViewEvent : IViewEvent {
    data class CopyText(val text: String) : AppLogViewerViewEvent()
}
