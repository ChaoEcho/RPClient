package me.kafuuneko.rpclient.libs.backup

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PreparedIoResourceTest {
    @get:Rule val temp = TemporaryFolder()

    @Test
    fun cancellationDuringResultHandoffDisposesTheCompletedResource() = runBlocking<Unit> {
        val file = temp.newFile()
        var released = false
        lateinit var caller: Job
        caller = launch(start = CoroutineStart.LAZY) {
            prepareIoResource(release = { resource: java.io.File ->
                resource.delete()
                released = true
            }) {
                file.writeText("fixture sensitive payload")
                caller.cancel()
                file
            }
        }
        caller.start()
        caller.join()
        assertTrue(caller.isCancelled)
        assertTrue(released)
        assertFalse(file.exists())
    }
}
