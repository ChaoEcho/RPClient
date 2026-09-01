package me.kafuuneko.rpclient.libs.backup

import org.junit.Assert.assertEquals
import org.junit.Test

class BackupFormattingTest {
    @Test
    fun formatBackupSize_formatsCorrectUnitsWithoutOffset() {
        assertEquals("0 B", BackupFormatting.formatBackupSize(0L))
        assertEquals("512 B", BackupFormatting.formatBackupSize(512L))
        assertEquals("1023 B", BackupFormatting.formatBackupSize(1023L))
        assertEquals("1.0 KB", BackupFormatting.formatBackupSize(1024L))
        assertEquals("1.5 KB", BackupFormatting.formatBackupSize(1536L))
        assertEquals("1.0 MB", BackupFormatting.formatBackupSize(1048576L))
        assertEquals("2.0 MB", BackupFormatting.formatBackupSize(2097152L))
        assertEquals("1.0 GB", BackupFormatting.formatBackupSize(1073741824L))
        assertEquals("1.0 TB", BackupFormatting.formatBackupSize(1099511627776L))
    }
}
