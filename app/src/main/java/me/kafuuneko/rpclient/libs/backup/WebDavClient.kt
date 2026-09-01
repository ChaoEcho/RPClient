package me.kafuuneko.rpclient.libs.backup

import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.MediaType.Companion.toMediaType
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import java.io.IOException
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/**
 * V1 WebDAV 存储客户端，只负责远端完整备份文件的基本存取。
 *
 * 客户端不解析或暴露 HTTP 错误正文；所有底层失败都归一化为备份领域异常。
 */
class WebDavClient(private val client: OkHttpClient) {
    /** 使用 Depth 1 的 PROPFIND 检查备份目录是否可访问。 */
    fun testConnection(config: WebDavConfig, password: String) {
        guarded {
            withResponse(
                request(
                    config = config,
                    password = password,
                    url = collectionUrl(config),
                    method = METHOD_PROPFIND,
                    body = null
                ).header("Depth", "1")
                    .build()
            ) { response ->
                requireSuccessful(response)
            }
        }
    }

    /** 按路径层级逐级创建备份目录。 */
    fun ensureCollection(config: WebDavConfig, password: String) {
        guarded {
            val baseUrl = baseUrl(config)
            val pathSegments = normalizedRemotePath(config.remotePath)
            pathSegments.indices.forEach { index ->
                val url = collectionUrl(baseUrl, pathSegments.take(index + 1))
                withResponse(
                    request(
                        config = config,
                        password = password,
                        url = url,
                        method = METHOD_MKCOL,
                        body = null
                    ).build()
                ) { response ->
                    // 已存在的 WebDAV 集合通常返回 405；继续检查后续层级即可。
                    requireSuccessful(response, allowExistingCollection = true)
                }
            }
        }
    }

    /** 列出备份目录下的 .rpbackup 文件，按修改时间降序排序（无时间戳排在最后），次级按文件名降序。 */
    fun listBackups(config: WebDavConfig, password: String): List<RemoteBackupItem> = guarded {
        val url = collectionUrl(config)
        withResponse(
            request(
                config = config,
                password = password,
                url = url,
                method = METHOD_PROPFIND,
                body = null
            ).header("Depth", "1")
                .build()
        ) { response ->
            requireSuccessful(response)
            val body = response.body ?: throw IOException("empty_response")
            parseRemoteItems(body, url)
        }
    }

    /** 将本地备份文件以文件支持的 RequestBody 上传到备份目录。 */
    fun upload(config: WebDavConfig, password: String, remoteName: String, source: File) {
        guarded {
            if (!source.isFile) {
                throw IOException("source_unavailable")
            }
            val requestBody = source.asRequestBody(BACKUP_MEDIA_TYPE)
            withResponse(
                request(
                    config = config,
                    password = password,
                    url = fileUrl(config, remoteName),
                    method = METHOD_PUT,
                    body = requestBody
                ).build()
            ) { response ->
                requireSuccessful(response)
            }
        }
    }

    /** 流式下载远端备份文件，避免把整个文件载入内存。 */
    fun download(config: WebDavConfig, password: String, item: RemoteBackupItem, target: File) {
        guarded {
            withResponse(
                request(
                    config = config,
                    password = password,
                    url = fileUrl(config, item.name),
                    method = METHOD_GET,
                    body = null
                ).build()
            ) { response ->
                requireSuccessful(response)
                val body = response.body ?: throw IOException("empty_response")
                body.byteStream().use { input ->
                    target.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }

    /** 删除远端备份文件。 */
    fun delete(config: WebDavConfig, password: String, item: RemoteBackupItem) {
        guarded {
            withResponse(
                request(
                    config = config,
                    password = password,
                    url = fileUrl(config, item.name),
                    method = METHOD_DELETE,
                    body = null
                ).build()
            ) { response ->
                requireSuccessful(response)
            }
        }
    }

    private fun parseRemoteItems(body: ResponseBody, collectionUrl: HttpUrl): List<RemoteBackupItem> {
        // 先固定命名空间并关闭外部实体，避免服务器返回的 XML 触发外部资源访问。
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
            isXIncludeAware = false
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            runCatching { setAttribute(ACCESS_EXTERNAL_DTD, "") }
            runCatching { setAttribute(ACCESS_EXTERNAL_SCHEMA, "") }
        }
        val document = factory.newDocumentBuilder().parse(body.byteStream())
        return elementsByName(document, DAV_RESPONSE).mapNotNull { response ->
            val href = childText(response, DAV_HREF) ?: return@mapNotNull null
            if (href.endsWith("/") || isCollection(response)) {
                return@mapNotNull null
            }
            val name = remoteNameFromHref(collectionUrl, href) ?: return@mapNotNull null
            if (!name.endsWith(BackupContract.FILE_EXTENSION)) {
                return@mapNotNull null
            }
            RemoteBackupItem(
                name = name,
                href = href,
                size = childText(response, DAV_CONTENT_LENGTH)?.toLongOrNull() ?: 0L,
                modifiedAt = childText(response, DAV_LAST_MODIFIED)?.let(::parseRfc1123)
            )
        }.sortedWith(
            compareByDescending<RemoteBackupItem> { it.modifiedAt != null }
                .thenByDescending { it.modifiedAt ?: Long.MIN_VALUE }
                .thenByDescending { it.name }
        )
    }

    private fun remoteNameFromHref(collectionUrl: HttpUrl, href: String): String? {
        val resolved = runCatching { collectionUrl.resolve(href.trim()) }.getOrNull() ?: return null
        val name = resolved.pathSegments.asReversed().firstOrNull { it.isNotEmpty() } ?: return null
        return runCatching { normalizedRemoteName(name) }.getOrNull()
    }

    private fun isCollection(response: Element): Boolean {
        return elementsByName(response, DAV_RESOURCE_TYPE)
            .flatMap { elementsByName(it, DAV_COLLECTION) }
            .isNotEmpty()
    }

    private fun childText(parent: Element, localName: String): String? {
        return elementsByName(parent, localName)
            .firstOrNull()
            ?.textContent
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun parseRfc1123(value: String): Long? {
        val formatter = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US).apply {
            isLenient = false
        }
        val position = ParsePosition(0)
        val date = formatter.parse(value, position) ?: return null
        return date.time.takeIf { position.index == value.length }
    }

    private fun elementsByName(node: Node, localName: String): List<Element> {
        val namespaced = when (node) {
            is Document -> node.getElementsByTagNameNS(DAV_NAMESPACE, localName)
            is Element -> node.getElementsByTagNameNS(DAV_NAMESPACE, localName)
            else -> null
        }
        if (namespaced != null && namespaced.length > 0) {
            return namespaced.asElements()
        }

        val unqualified = when (node) {
            is Document -> node.getElementsByTagNameNS(null, localName)
            is Element -> node.getElementsByTagNameNS(null, localName)
            else -> null
        }
        if (unqualified != null && unqualified.length > 0) {
            return unqualified.asElements()
        }

        return when (node) {
            is Document -> node.getElementsByTagName(localName).asElements()
            is Element -> node.getElementsByTagName(localName).asElements()
            else -> emptyList()
        }
    }

    private fun request(
        config: WebDavConfig,
        password: String,
        url: HttpUrl,
        method: String,
        body: RequestBody?
    ): Request.Builder = Request.Builder()
        .url(url)
        .header("Authorization", Credentials.basic(config.username, password))
        .method(method, body)

    private fun execute(request: Request): Response = client.newCall(request).execute()

    private inline fun <T> withResponse(request: Request, block: (Response) -> T): T {
        val response = execute(request)
        return try {
            block(response)
        } finally {
            response.body?.close()
        }
    }

    private fun requireSuccessful(response: Response, allowExistingCollection: Boolean = false) {
        if (response.isSuccessful || (allowExistingCollection && response.code == HTTP_METHOD_NOT_ALLOWED)) {
            return
        }
        if (response.code == HTTP_UNAUTHORIZED || response.code == HTTP_FORBIDDEN) {
            throw BackupException.WebDavAuthenticationFailed()
        }
        throw BackupException.WebDavUnavailable()
    }

    private fun collectionUrl(config: WebDavConfig): HttpUrl =
        collectionUrl(baseUrl(config), normalizedRemotePath(config.remotePath))

    private fun collectionUrl(baseUrl: HttpUrl, pathSegments: List<String>): HttpUrl =
        baseUrl.newBuilder()
            .encodedPath(baseUrl.encodedPath.trimEnd('/').ifEmpty { "/" })
            .apply {
                pathSegments.forEach(::addPathSegment)
                addPathSegment("")
            }
            .build()

    private fun fileUrl(config: WebDavConfig, remoteName: String): HttpUrl {
        val baseUrl = baseUrl(config)
        return baseUrl.newBuilder()
            .encodedPath(baseUrl.encodedPath.trimEnd('/').ifEmpty { "/" })
            .apply {
                normalizedRemotePath(config.remotePath).forEach(::addPathSegment)
                addPathSegment(normalizedRemoteName(remoteName))
            }
            .build()
    }

    private fun baseUrl(config: WebDavConfig): HttpUrl {
        val url = config.baseUrl.trim().toHttpUrl()
        require(url.scheme == "http" || url.scheme == "https")
        require(url.username.isEmpty() && url.password.isEmpty())
        return url.newBuilder()
            .query(null)
            .fragment(null)
            .encodedPath(url.encodedPath.trimEnd('/').ifEmpty { "/" })
            .build()
    }

    private fun normalizedRemotePath(path: String): List<String> {
        return path.trim()
            .split('/')
            .filter(String::isNotEmpty)
            .onEach(::validatePathSegment)
    }

    private fun normalizedRemoteName(name: String): String {
        require(name.isNotEmpty())
        require(name != "." && name != "..")
        require('/' !in name && '\\' !in name)
        require(name.none(Char::isISOControl))
        return name
    }

    private fun validatePathSegment(segment: String) {
        require(segment != "." && segment != "..")
        require('\\' !in segment)
        require(segment.none(Char::isISOControl))
    }

    private fun <T> guarded(block: () -> T): T {
        return try {
            block()
        } catch (error: BackupException) {
            throw error
        } catch (_: Exception) {
            throw BackupException.WebDavUnavailable()
        }
    }

    private fun org.w3c.dom.NodeList.asElements(): List<Element> {
        return (0 until length).mapNotNull { item(it) as? Element }
    }

    private companion object {
        const val ACCESS_EXTERNAL_DTD = "http://javax.xml.XMLConstants/property/accessExternalDTD"
        const val ACCESS_EXTERNAL_SCHEMA = "http://javax.xml.XMLConstants/property/accessExternalSchema"
        const val DAV_NAMESPACE = "DAV:"
        const val DAV_RESPONSE = "response"
        const val DAV_HREF = "href"
        const val DAV_RESOURCE_TYPE = "resourcetype"
        const val DAV_COLLECTION = "collection"
        const val DAV_CONTENT_LENGTH = "getcontentlength"
        const val DAV_LAST_MODIFIED = "getlastmodified"
        const val METHOD_PROPFIND = "PROPFIND"
        const val METHOD_MKCOL = "MKCOL"
        const val METHOD_PUT = "PUT"
        const val METHOD_GET = "GET"
        const val METHOD_DELETE = "DELETE"
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val HTTP_METHOD_NOT_ALLOWED = 405
        val BACKUP_MEDIA_TYPE = BackupContract.MIME_TYPE.toMediaType()
    }
}
