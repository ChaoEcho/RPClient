package me.kafuuneko.rpclient.libs.media

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts

/** 按每次原图的真实格式创建文档，避免通配 MIME 导致系统无法补全扩展名。 */
class CreateImageDocumentContract : ActivityResultContract<ImageExportMetadata, Uri?>() {
    /**
     * 使用已校验的原图元数据调起系统文档创建器。
     *
     * @param context 页面宿主上下文，仅供系统 Intent 构造使用。
     * @param input 原图的具体 MIME 和默认文件名。
     * @return 带有具体文件类型和名称的创建文档 Intent。
     */
    override fun createIntent(context: Context, input: ImageExportMetadata): Intent =
        ActivityResultContracts.CreateDocument(input.mimeType).createIntent(context, input.fileName)

    /**
     * 仅接受系统确认的保存目标。
     *
     * @param resultCode 系统选择器结果码。
     * @param intent 系统返回的数据。
     * @return 用户确认的文档 URI；取消或未返回目标时为 null。
     */
    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}
