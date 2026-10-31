package dev.mmcl.core.download

import dev.mmcl.core.version.VersionDetail
import dev.mmcl.core.version.VersionManifestEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.slf4j.LoggerFactory

class DownloadsServiceTest {
    private val logger = LoggerFactory.getLogger("test")

    private class FakeInstaller : Installer {
        val started = mutableListOf<String>()
        val gates = mutableMapOf<String, CompletableDeferred<Unit>>()
        var failure: Throwable? = null

        fun gate(versionId: String): CompletableDeferred<Unit> = gates.getOrPut(versionId) { CompletableDeferred() }

        override suspend fun install(
            entry: VersionManifestEntry,
            forceRefresh: Boolean,
            onProgress: suspend (DownloadProgress) -> Unit,
        ): InstallResult {
            started += entry.id
            onProgress(DownloadProgress(1, 2, "file", "files"))
            gate(entry.id).await()
            failure?.let { throw it }
            return InstallResult(
                plan = InstallPlan(VersionDetail(id = entry.id), emptyList(), emptyList()),
                report = DownloadReport(1, 0, emptyList()),
            )
        }
    }

    private fun entry(id: String) = VersionManifestEntry(id = id, type = "release", url = "")

    @Test
    fun `完成后进入 COMPLETED 并带结果`() = runTest {
        val fake = FakeInstaller()
        val service = DownloadsService(fake, backgroundScope, logger)

        service.enqueue(entry("26.3"))
        runCurrent()
        assertEquals(DownloadTaskState.DOWNLOADING, service.tasks.value.single().state)
        assertEquals("files", service.tasks.value.single().progress?.phase)

        fake.gate("26.3").complete(Unit)
        advanceUntilIdle()
        runCurrent()

        val task = service.tasks.value.single()
        assertEquals(DownloadTaskState.COMPLETED, task.state)
        assertNotNull(task.result)
    }

    @Test
    fun `同版本重复入队只保留一个任务`() = runTest {
        val fake = FakeInstaller()
        val service = DownloadsService(fake, backgroundScope, logger)

        service.enqueue(entry("26.3"))
        runCurrent()
        val second = service.enqueue(entry("26.3"))

        assertEquals(DownloadTaskState.DOWNLOADING, second.state)
        assertEquals(1, service.tasks.value.size)
        assertEquals(listOf("26.3"), fake.started)
    }

    @Test
    fun `任务串行执行`() = runTest {
        val fake = FakeInstaller()
        val service = DownloadsService(fake, backgroundScope, logger)

        service.enqueue(entry("26.3"))
        service.enqueue(entry("26.2"))
        runCurrent()

        assertEquals(listOf("26.3"), fake.started)
        assertEquals(DownloadTaskState.QUEUED, service.tasks.value.first { it.versionId == "26.2" }.state)

        fake.gate("26.3").complete(Unit)
        advanceUntilIdle()
        runCurrent()

        assertEquals(listOf("26.3", "26.2"), fake.started)
        assertEquals(DownloadTaskState.DOWNLOADING, service.tasks.value.first { it.versionId == "26.2" }.state)
    }

    @Test
    fun `失败进入 FAILED 并记录原因`() = runTest {
        val fake = FakeInstaller().apply { failure = RuntimeException("boom") }
        val service = DownloadsService(fake, backgroundScope, logger)

        service.enqueue(entry("26.3"))
        runCurrent()
        fake.gate("26.3").complete(Unit)
        advanceUntilIdle()
        runCurrent()

        val task = service.tasks.value.single()
        assertEquals(DownloadTaskState.FAILED, task.state)
        assertTrue(task.error.orEmpty().contains("boom"))
    }

    @Test
    fun `取消进行中的任务`() = runTest {
        val fake = FakeInstaller()
        val service = DownloadsService(fake, backgroundScope, logger)

        service.enqueue(entry("26.3"))
        runCurrent()
        service.cancel("26.3")
        advanceUntilIdle()
        runCurrent()

        assertEquals(DownloadTaskState.CANCELLED, service.tasks.value.single().state)
        assertEquals(listOf("26.3"), fake.started)
    }

    @Test
    fun `取消排队中的任务后不会再执行`() = runTest {
        val fake = FakeInstaller()
        val service = DownloadsService(fake, backgroundScope, logger)

        service.enqueue(entry("26.3"))
        service.enqueue(entry("26.2"))
        runCurrent()
        service.cancel("26.2")

        fake.gate("26.3").complete(Unit)
        advanceUntilIdle()
        runCurrent()

        assertEquals(listOf("26.3"), fake.started)
        assertEquals(DownloadTaskState.CANCELLED, service.tasks.value.first { it.versionId == "26.2" }.state)
    }
}
