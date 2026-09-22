package me.kafuuneko.rpclient.libs.room

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class RequestLogBackupRulesTest {
    @Test
    fun backupRules_excludeLogDatabaseAndSidecarsButNotPrimaryDatabase() {
        val root = parseXml("backup_rules.xml")
        val excludes = root.getElementsByTagName("exclude").asElements()

        assertEquals(ExpectedDatabasePaths, excludes.filter { it.getAttribute("domain") == "database" }.databasePaths())
        assertEquals(setOf(SECURE_PREFS_FILE), excludes.sharedPreferencePaths())
        assertEquals(ExpectedFilePaths, excludes.filePaths())
        assertEquals(ExpectedRootPaths, excludes.rootPaths())
        assertFalse(
            excludes.filter { it.getAttribute("domain") == "database" }.databasePaths().contains("primary.sqlite")
        )
    }

    @Test
    fun dataExtractionRules_excludeLogsAndSecureSecretsFromCloudBackupAndDeviceTransfer() {
        val root = parseXml("data_extraction_rules.xml")
        val cloud = root.getElementsByTagName("cloud-backup").item(0) as Element
        val transfer = root.getElementsByTagName("device-transfer").item(0) as Element

        assertEquals(ExpectedDatabasePaths, cloud.getElementsByTagName("exclude").asElements()
            .filter { it.getAttribute("domain") == "database" }
            .databasePaths())
        assertEquals(ExpectedDatabasePaths, transfer.getElementsByTagName("exclude").asElements()
            .filter { it.getAttribute("domain") == "database" }
            .databasePaths())
        assertEquals(
            setOf(SECURE_PREFS_FILE),
            cloud.getElementsByTagName("exclude").asElements().sharedPreferencePaths()
        )
        assertEquals(
            setOf(SECURE_PREFS_FILE),
            transfer.getElementsByTagName("exclude").asElements().sharedPreferencePaths()
        )
        assertEquals(ExpectedFilePaths, cloud.getElementsByTagName("exclude").asElements().filePaths())
        assertEquals(ExpectedFilePaths, transfer.getElementsByTagName("exclude").asElements().filePaths())
        assertEquals(ExpectedRootPaths, cloud.getElementsByTagName("exclude").asElements().rootPaths())
        assertEquals(ExpectedRootPaths, transfer.getElementsByTagName("exclude").asElements().rootPaths())
        assertTrue(root.getElementsByTagName("include").length == 0)
    }

    private fun parseXml(name: String): Element {
        val workingDirectory = File(requireNotNull(System.getProperty("user.dir")))
        val candidates = listOf(
            workingDirectory.resolve("src/main/res/xml/$name"),
            workingDirectory.resolve("app/src/main/res/xml/$name")
        )
        val source = candidates.firstOrNull(File::isFile)
            ?: error("Cannot locate $name from $workingDirectory")
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(source).documentElement
    }

    private fun org.w3c.dom.NodeList.asElements(): List<Element> {
        return (0 until length).map { item(it) as Element }
    }

    private fun List<Element>.databasePaths(): Set<String> =
        filter { it.getAttribute("domain") == "database" }
            .mapTo(mutableSetOf()) { it.getAttribute("path") }

    /** 暂存目录与数据库属于不同备份域，必须分别检查，不能先过滤数据库再断言暂存目录。 */
    private fun List<Element>.rootPaths(): Set<String> =
        filter { it.getAttribute("domain") == "root" }
            .mapTo(mutableSetOf()) { it.getAttribute("path") }

    /** 应用运行日志目录同样是可丢弃的调试数据，不应随系统备份离开设备。 */
    private fun List<Element>.filePaths(): Set<String> =
        filter { it.getAttribute("domain") == "file" }
            .mapTo(mutableSetOf()) { it.getAttribute("path") }

    private fun List<Element>.sharedPreferencePaths(): Set<String> =
        filter { it.getAttribute("domain") == "sharedpref" }
            .mapTo(mutableSetOf()) { it.getAttribute("path") }

    private companion object {
        const val SECURE_PREFS_FILE = "rpclient_secure_secrets.xml"
        const val APP_LOG_DIRECTORY = "debug"
        val ExpectedDatabasePaths = setOf(
            "request_logs.sqlite",
            "request_logs.sqlite-journal",
            "request_logs.sqlite-shm",
            "request_logs.sqlite-wal"
        )
        val ExpectedFilePaths = setOf(APP_LOG_DIRECTORY)
        val ExpectedRootPaths = setOf("app_repository/staging/")
    }
}
