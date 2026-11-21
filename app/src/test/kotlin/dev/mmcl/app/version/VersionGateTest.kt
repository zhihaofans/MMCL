package dev.mmcl.app.version

import dev.mmcl.core.version.LatestVersions
import dev.mmcl.core.version.VersionManifest
import dev.mmcl.core.version.VersionManifestEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VersionGateTest {
    private fun entry(id: String, type: String = "release") = VersionManifestEntry(id = id, type = type, url = "")

    private val manifest =
        VersionManifest(
            latest = LatestVersions(release = "26.3", snapshot = "26.4-snapshot-6"),
            versions =
                listOf(
                    entry("26.4-snapshot-6", "snapshot"),
                    entry("26.3"),
                    entry("26.2"),
                    entry("1.21.11"),
                ),
        )

    @Test
    fun `同系列正式版可以启动`() {
        assertTrue(isLaunchable("26.3", manifest))
        assertTrue(isLaunchable("26.2", manifest))
    }

    @Test
    fun `旧大版本与快照不允许启动`() {
        assertFalse(isLaunchable("1.21.11", manifest))
        assertFalse(isLaunchable("26.4-snapshot-6", manifest))
    }

    @Test
    fun `清单里没有的版本无法判断时放行`() {
        assertTrue(isLaunchable("26.1", manifest))
        assertTrue(isLaunchable("anything", null))
    }

    @Test
    fun `向导只列出受支持的正式版`() {
        val releases = supportedReleases(manifest)

        assertEquals(listOf("26.3", "26.2"), releases.map { it.id })
    }

    @Test
    fun `向导列表支持截断`() {
        assertEquals(listOf("26.3"), supportedReleases(manifest, limit = 1).map { it.id })
        assertEquals(2, supportedReleases(manifest, limit = 0).size)
    }
}
