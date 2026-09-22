package me.kafuuneko.rpclient.libs.media

/**
 * 创建原图导出文档所需的元数据，不包含内部路径或文件所有权。
 *
 * @property mimeType 解码器验证的原图 MIME 类型。
 * @property fileName 与原始字节格式匹配、带扩展名的默认文件名。
 */
data class ImageExportMetadata(val mimeType: String, val fileName: String)
