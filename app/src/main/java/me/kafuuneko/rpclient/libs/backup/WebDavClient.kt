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
import java.io.File
import java.io.IOException
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import javax.xml.parsers.SAXParserFactory

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
            if (response.code == HTTP_NOT_FOUND) {
                return@withResponse emptyList()
            }
            requireSuccessful(response)
            val body = response.body ?: throw BackupException.WebDavInvalidResponse()
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
        return try {
            val items = mutableListOf<RemoteBackupItem>()
            var rootName: String? = null
            var currentResponse: ParsedResponse? = null
            var depth = 0
            var resourceTypeDepth = -1
            var capturedName: String? = null
            val capturedText = StringBuilder()

            val handler = object : DefaultHandler() {
                override fun startElement(
                    uri: String?,
                    localName: String?,
                    qName: String?,
                    attributes: Attributes?
                ) {
                    depth += 1
                    val name = localName.orEmpty().ifEmpty {
                        qName.orEmpty().substringAfter(':')
                    }
                    if (rootName == null) rootName = name
                    when {
                        name == DAV_RESPONSE -> {
                            if (currentResponse != null) {
                                throw IllegalArgumentException("nested_response")
                            }
                            currentResponse = ParsedResponse()
                        }

                        currentResponse != null -> when (name) {
                            DAV_HREF, DAV_CONTENT_LENGTH, DAV_LAST_MODIFIED -> {
                                capturedName = name
                                capturedText.setLength(0)
                            }

                            DAV_RESOURCE_TYPE -> resourceTypeDepth = depth
                            DAV_COLLECTION -> if (resourceTypeDepth >= 0) {
                                currentResponse?.isCollection = true
                            }
                        }
                    }
                }

                override fun characters(ch: CharArray, start: Int, length: Int) {
                    if (capturedName != null) capturedText.append(ch, start, length)
                }

                override fun endElement(uri: String?, localName: String?, qName: String?) {
                    val name = localName.orEmpty().ifEmpty {
                        qName.orEmpty().substringAfter(':')
                    }
                    val parsed = currentResponse
                    if (name == capturedName && parsed != null) {
                        val value = capturedText.toString().trim()
                        when (name) {
                            DAV_HREF -> parsed.href = value
                            DAV_CONTENT_LENGTH -> parsed.size = value.toLongOrNull() ?: 0L
                            DAV_LAST_MODIFIED -> parsed.modifiedAt = parseRfc1123(value)
                        }
                        capturedName = null
                        capturedText.setLength(0)
                    }
                    if (name == DAV_RESOURCE_TYPE && depth == resourceTypeDepth) {
                        resourceTypeDepth = -1
                    }
                    if (name == DAV_RESPONSE) {
                        val response = currentResponse
                            ?: throw IllegalArgumentException("response_without_start")
                        addRemoteItem(items, collectionUrl, response)
                        currentResponse = null
                    }
                    depth -= 1
                }
            }

            val factory = SAXParserFactory.newInstance().apply {
                isNamespaceAware = true
                isValidating = false
                setFeatureSafely("http://xml.org/sax/features/external-general-entities", false)
                setFeatureSafely("http://xml.org/sax/features/external-parameter-entities", false)
                setFeatureSafely("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            }
            body.byteStream().use { input ->
                factory.newSAXParser().parse(input, handler)
            }
            if (rootName != DAV_MULTI_STATUS || currentResponse != null) {
                throw IllegalArgumentException("invalid_multistatus")
            }
            items.sortedWith(
                compareByDescending<RemoteBackupItem> { it.modifiedAt != null }
                    .thenByDescending { it.modifiedAt ?: Long.MIN_VALUE }
                    .thenByDescending { it.name }
            )
        } catch (error: BackupException) {
            throw error
        } catch (_: Exception) {
            throw BackupException.WebDavInvalidResponse()
        }
    }

    private fun addRemoteItem(
        items: MutableList<RemoteBackupItem>,
        collectionUrl: HttpUrl,
        parsed: ParsedResponse
    ) {
        val href = parsed.href ?: return
        if (parsed.isCollection || href.endsWith('/')) return
        val remoteName = remoteNameFromHref(collectionUrl, href) ?: return
        if (!remoteName.endsWith(BackupContract.FILE_EXTENSION)) return
        items += RemoteBackupItem(
            name = remoteName,
            href = href,
            size = parsed.size,
            modifiedAt = parsed.modifiedAt
        )
    }

    private fun SAXParserFactory.setFeatureSafely(name: String, enabled: Boolean) {
        runCatching { setFeature(name, enabled) }
    }

    private class ParsedResponse {
        var href: String? = null
        var isCollection = false
        var size = 0L
        var modifiedAt: Long? = null
    }

    private fun remoteNameFromHref(collectionUrl: HttpUrl, href: String): String? {
        val resolved = runCatching { collectionUrl.resolve(href.trim()) }.getOrNull() ?: return null
        val name = resolved.pathSegments.asReversed().firstOrNull { it.isNotEmpty() } ?: return null
        return runCatching { normalizedRemoteName(name) }.getOrNull()
    }

    private fun parseRfc1123(value: String): Long? {
        val formatter = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US).apply {
            isLenient = false
        }
        val position = ParsePosition(0)
        val date = formatter.parse(value, position) ?: return null
        return date.time.takeIf { position.index == value.length }
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

    private companion object {
        const val DAV_MULTI_STATUS = "multistatus"
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
        const val HTTP_NOT_FOUND = 404
        const val HTTP_METHOD_NOT_ALLOWED = 405
        val BACKUP_MEDIA_TYPE = BackupContract.MIME_TYPE.toMediaType()
    }
}
