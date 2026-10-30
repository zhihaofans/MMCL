package dev.mmcl.core.version

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VersionSupportTest {
    private fun entry(id: String, type: String = "release") =
        VersionManifestEntry(id = id, type = type, url = "https://example.invalid/$id.json")

    private val manifest =
        VersionManifest(
            latest = LatestVersions(release = "26.3", snapshot = "26.4-snapshot-6"),
            versions =
                listOf(
                    entry("26.4-snapshot-6", type = "snapshot"),
                    entry("26.3"),
                    entry("26.2"),
                    entry("1.21.8"),
                    entry("1.20.1"),
                ),
        )

    @Test
    fun `系列号解析`() {
        assertEquals("26", VersionSupport.seriesOf("26.3"))
        assertEquals("26", VersionSupport.seriesOf("26.4-snapshot-6"))
        assertEquals("1.21", VersionSupport.seriesOf("1.21.8"))
        assertEquals("1.20", VersionSupport.seriesOf("1.20.1"))
    }

    @Test
    fun `最新系列由最新正式版决定`() {
        assertEquals("26", VersionSupport.latestSeries(manifest))
    }

    @Test
    fun `同系列正式版受支持`() {
        val series = VersionSupport.latestSeries(manifest)

        assertTrue(VersionSupport.isSupported(entry("26.3"), series))
        assertTrue(VersionSupport.isSupported(entry("26.2"), series))
    }

    @Test
    fun `快照与旧大版本不受支持`() {
        val series = VersionSupport.latestSeries(manifest)

        assertFalse(VersionSupport.isSupported(entry("26.4-snapshot-6", type = "snapshot"), series))
        assertFalse(VersionSupport.isSupported(entry("1.21.8"), series))
        assertFalse(VersionSupport.isSupported(entry("1.20.1"), series))
    }
}
