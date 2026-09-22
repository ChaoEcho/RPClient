package me.kafuuneko.rpclient.libs.chat

import me.kafuuneko.rpclient.utils.removeThinkBlocks

/** 整条复制遵循用户的思维块设置，不意外把被隐藏的推理内容送入剪贴板。 */
fun messageClipboardText(content: String, includeThink: Boolean): String =
    if (includeThink) content else content.removeThinkBlocks()
