package me.kafuuneko.rpclient.libs.backup

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Locale

class WebDavClientTest {
    @Test
    fun listBackups_parsesNamespaceAwareMultistatusAndFiltersEntries() {
        val requests = mutableListOf<Request>()
        val client = clientWithInterceptor { request ->
            requests += request
            response(
                request,
                code = 207,
                body = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <d:multistatus xmlns:d="DAV:">
                        <d:response>
                            <d:href>/RPClient/backups/</d:href>
                            <d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop></d:propstat>
                        </d:response>
                        <d:response>
                            <d:href>first.rpbackup</d:href>
                            <d:propstat><d:prop>
                                <d:resourcetype/>
                                <d:getcontentlength>42</d:getcontentlength>
                                <d:getlastmodified>Wed, 21 Oct 2015 07:28:00 GMT</d:getlastmodified>
                            </d:prop></d:propstat>
                        </d:response>
                        <d:response>
                            <d:href>https://other.example/archive.rpbackup</d:href>
                            <d:propstat><d:prop>
                                <d:resourcetype/>
                                <d:getcontentlength>7</d:getcontentlength>
                                <d:getlastmodified>not a date</d:getlastmodified>
                            </d:prop></d:propstat>
                        </d:response>
                        <d:response>
                            <d:href>notes.txt</d:href>
                            <d:propstat><d:prop><d:resourcetype/></d:prop></d:propstat>
                        </d:response>
                    </d:multistatus>
                """.trimIndent()
            )
        }

        val items = WebDavClient(client).listBackups(
            WebDavConfig("https://example.test", "alice", "/RPClient/backups/"),
            "secret"
        )

        assertEquals(2, items.size)
        assertEquals("first.rpbackup", items[0].name)
        assertEquals("first.rpbackup", items[0].href)
        assertEquals(42L, items[0].size)
        assertEquals(
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US)
                .parse("Wed, 21 Oct 2015 07:28:00 GMT")!!.time,
            items[0].modifiedAt
        )
        assertEquals("archive.rpbackup", items[1].name)
        assertEquals("https://other.example/archive.rpbackup", items[1].href)
        assertEquals(7L, items[1].size)
        assertEquals(null, items[1].modifiedAt)

        assertEquals("PROPFIND", requests.single().method)
        assertEquals("1", requests.single().header("Depth"))
        assertEquals("Basic ${Base64.getEncoder().encodeToString("alice:secret".toByteArray())}", requests.single().header("Authorization"))
    }

    @Test
    fun listBackups_sortsByModifiedAtDescendingThenNameDescendingWithNullLast() {
        val client = clientWithInterceptor { request ->
            response(
                request,
                code = 207,
                body = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <d:multistatus xmlns:d="DAV:">
                        <d:response>
                            <d:href>old.rpbackup</d:href>
                            <d:propstat><d:prop>
                                <d:resourcetype/>
                                <d:getcontentlength>10</d:getcontentlength>
                                <d:getlastmodified>Wed, 21 Oct 2015 07:28:00 GMT</d:getlastmodified>
                            </d:prop></d:propstat>
                        </d:response>
                        <d:response>
                            <d:href>null-a.rpbackup</d:href>
                            <d:propstat><d:prop>
                                <d:resourcetype/>
                                <d:getcontentlength>10</d:getcontentlength>
                            </d:prop></d:propstat>
                        </d:response>
                        <d:response>
                            <d:href>new-z.rpbackup</d:href>
                            <d:propstat><d:prop>
                                <d:resourcetype/>
                                <d:getcontentlength>10</d:getcontentlength>
                                <d:getlastmodified>Wed, 21 Oct 2026 07:28:00 GMT</d:getlastmodified>
                            </d:prop></d:propstat>
                        </d:response>
                        <d:response>
                            <d:href>new-a.rpbackup</d:href>
                            <d:propstat><d:prop>
                                <d:resourcetype/>
                                <d:getcontentlength>10</d:getcontentlength>
                                <d:getlastmodified>Wed, 21 Oct 2026 07:28:00 GMT</d:getlastmodified>
                            </d:prop></d:propstat>
                        </d:response>
                        <d:response>
                            <d:href>null-z.rpbackup</d:href>
                            <d:propstat><d:prop>
                                <d:resourcetype/>
                                <d:getcontentlength>10</d:getcontentlength>
                            </d:prop></d:propstat>
                        </d:response>
                    </d:multistatus>
                """.trimIndent()
            )
        }

        val items = WebDavClient(client).listBackups(
            WebDavConfig("https://example.test", "alice", "/RPClient/backups/"),
            "secret"
        )

        assertEquals(listOf("new-z.rpbackup", "new-a.rpbackup", "old.rpbackup", "null-z.rpbackup", "null-a.rpbackup"), items.map { it.name })
    }


    @Test
    fun listBackups_acceptsDifferentDavNamespacePrefixes() {
        val client = clientWithInterceptor { request ->
            response(
                request,
                code = 207,
                body = """
                    <x:multistatus xmlns:x="DAV:">
                        <x:response>
                            <x:href>prefixed.rpbackup</x:href>
                            <x:propstat><x:prop><x:resourcetype/></x:prop></x:propstat>
                        </x:response>
                    </x:multistatus>
                """.trimIndent()
            )
        }

        val items = WebDavClient(client).listBackups(
            WebDavConfig("https://example.test", "alice", "/backups/"),
            "secret"
        )

        assertEquals(listOf("prefixed.rpbackup"), items.map { it.name })
    }

    @Test
    fun listBackups_returnsEmptyListWhenCollectionDoesNotExist() {
        val client = clientWithInterceptor { request -> response(request, code = 404) }

        val items = WebDavClient(client).listBackups(
            WebDavConfig("https://example.test", "alice", "/backups/"),
            "secret"
        )

        assertTrue(items.isEmpty())
    }

    @Test
    fun listBackups_rejectsMalformedSuccessfulXml() {
        val client = clientWithInterceptor { request ->
            response(request, code = 207, body = "<d:multistatus xmlns:d=\"DAV:\"><d:response>")
        }

        val error = org.junit.Assert.assertThrows(BackupException.WebDavInvalidResponse::class.java) {
            WebDavClient(client).listBackups(
                WebDavConfig("https://example.test", "alice", "/backups/"),
                "secret"
            )
        }

        assertEquals("webdav_invalid_response", error.message)
    }

    @Test
    fun ensureCollection_createsEachNormalizedPathLevel() {
        val requests = mutableListOf<Request>()
        val client = clientWithInterceptor { request ->
            requests += request
            response(request, code = 201)
        }

        WebDavClient(client).ensureCollection(
            WebDavConfig("https://example.test/dav/", "alice", "//RPClient///backups/"),
            "secret"
        )

        assertEquals(listOf("MKCOL", "MKCOL"), requests.map { it.method })
        assertEquals(
            listOf(
                "https://example.test/dav/RPClient/",
                "https://example.test/dav/RPClient/backups/"
            ),
            requests.map { it.url.toString() }
        )
    }

    @Test
    fun uploadDownloadAndDelete_useLocalNormalizedRemotePath() {
        val requests = mutableListOf<Request>()
        val source = Files.createTempFile("rpclient-webdav-source", ".rpbackup").toFile()
        val target = Files.createTempFile("rpclient-webdav-target", ".rpbackup").toFile()
        val payload = "encrypted backup".toByteArray()
        source.writeBytes(payload)
        target.writeBytes(ByteArray(0))
        val client = clientWithInterceptor { request ->
            requests += request
            when (request.method) {
                "GET" -> response(request, code = 200, body = payload)
                else -> response(request, code = if (request.method == "PUT") 201 else 204)
            }
        }

        try {
            val webDav = WebDavClient(client)
            val config = WebDavConfig("https://example.test/dav", "alice", "/RPClient/backups/")
            webDav.upload(config, "secret", "archive.rpbackup", source)
            webDav.download(
                config,
                "secret",
                RemoteBackupItem("archive.rpbackup", "https://evil.example/should-not-be-used", 0L, null),
                target
            )
            webDav.delete(
                config,
                "secret",
                RemoteBackupItem("archive.rpbackup", "https://evil.example/should-not-be-used", 0L, null)
            )

            assertEquals(listOf("PUT", "GET", "DELETE"), requests.map { it.method })
            assertEquals(
                "https://example.test/dav/RPClient/backups/archive.rpbackup",
                requests[0].url.toString()
            )
            assertEquals(requests[0].url, requests[1].url)
            assertEquals(requests[1].url, requests[2].url)
            assertTrue(requests[0].header("Authorization")!!.startsWith("Basic "))
            assertEquals(payload.size.toLong(), requests[0].body!!.contentLength())
            val uploaded = Buffer().also { requests[0].body!!.writeTo(it) }.readByteArray()
            assertArrayEquals(payload, uploaded)
            assertArrayEquals(payload, target.readBytes())
        } finally {
            source.delete()
            target.delete()
        }
    }

    @Test
    fun unauthorizedAndOtherFailures_areClassifiedWithoutResponseBody() {
        listOf(401, 403).forEach { status ->
            val authClient = clientWithInterceptor { request ->
                response(request, code = status, body = "password=secret body")
            }
            val authError = org.junit.Assert.assertThrows(BackupException.WebDavAuthenticationFailed::class.java) {
                WebDavClient(authClient).testConnection(
                    WebDavConfig("https://example.test", "alice", "/backups/"),
                    "secret"
                )
            }
            assertEquals("webdav_authentication_failed", authError.message)
            assertFalse(authError.message!!.contains("secret"))
        }

        val unavailableClient = clientWithInterceptor { request ->
            response(request, code = 500, body = "password=secret body")
        }
        val unavailableError = org.junit.Assert.assertThrows(BackupException.WebDavUnavailable::class.java) {
            WebDavClient(unavailableClient).listBackups(
                WebDavConfig("https://example.test", "alice", "/backups/"),
                "secret"
            )
        }
        assertEquals("webdav_unavailable", unavailableError.message)
        assertFalse(unavailableError.message!!.contains("secret"))
    }

    @Test
    fun ioFailure_isClassifiedWithoutUnderlyingMessage() {
        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { throw IOException("password=secret") })
            .build()

        val error = org.junit.Assert.assertThrows(BackupException.WebDavUnavailable::class.java) {
            WebDavClient(client).testConnection(
                WebDavConfig("https://example.test", "alice", "/backups/"),
                "secret"
            )
        }

        assertEquals("webdav_unavailable", error.message)
        assertTrue(error.cause == null)
    }

    private fun clientWithInterceptor(handler: (Request) -> Response): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain -> handler(chain.request()) })
            .build()

    private fun response(request: Request, code: Int, body: String): Response =
        response(request, code, body.toByteArray())

    private fun response(request: Request, code: Int, body: ByteArray? = null): Response {
        val builder = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("response")
        builder.body((body ?: ByteArray(0)).toResponseBody())
        return builder.build()
    }
}
