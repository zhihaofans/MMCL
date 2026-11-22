package dev.mmcl.plugin.source.bmclapi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BmclapiVersionSourceTest {
    private val source = BmclapiVersionSource()

    @Test
    fun `Mojang 主机替换为 BMCLAPI`() {
        assertEquals(
            "https://bmclapi2.bangbang93.com/v1/packages/abc123/26.3.json",
            source.resolveUrl("https://piston-meta.mojang.com/v1/packages/abc123/26.3.json"),
        )
        assertEquals(
            "https://bmclapi2.bangbang93.com/v1/objects/def456/client.jar",
            source.resolveUrl("https://piston-data.mojang.com/v1/objects/def456/client.jar"),
        )
        assertEquals(
            "https://bmclapi2.bangbang93.com/mc/game/version_manifest.json",
            source.resolveUrl("https://launchermeta.mojang.com/mc/game/version_manifest.json"),
        )
        assertEquals(
            "https://bmclapi2.bangbang93.com/versions/1.7.10/1.7.10.jar",
            source.resolveUrl("https://launcher.mojang.com/versions/1.7.10/1.7.10.jar"),
        )
    }

    @Test
    fun `库与资源走各自的子路径`() {
        assertEquals(
            "https://bmclapi2.bangbang93.com/maven/at/yawk/lz4/lz4-java/1.10.1/lz4-java-1.10.1.jar",
            source.resolveUrl(
                "https://libraries.minecraft.net/at/yawk/lz4/lz4-java/1.10.1/lz4-java-1.10.1.jar",
            ),
        )
        assertEquals(
            "https://bmclapi2.bangbang93.com/assets/ab/cdef",
            source.resolveUrl("https://resources.download.minecraft.net/ab/cdef"),
        )
    }

    @Test
    fun `不认识或已经镜像的地址不改写`() {
        assertNull(source.resolveUrl("https://bmclapi2.bangbang93.com/v1/packages/abc/1.json"))
        assertNull(source.resolveUrl("https://example.com/file.jar"))
    }

    @Test
    fun `资源对象地址按 SHA-1 前两位分桶`() {
        val hash = "b62ca8ec10d07e6bf5ac8dae0c8c1d2e6a1e3356"

        assertEquals("https://bmclapi2.bangbang93.com/assets/b6/$hash", source.assetObjectUrl(hash))
        assertNull(source.assetObjectUrl("too-short"))
    }

    @Test
    fun `展示名与来源标注跟随语言`() {
        val chinese = BmclapiVersionSource(localeProvider = { "zh-CN" })
        val english = BmclapiVersionSource(localeProvider = { "en" })

        assertEquals("BMCLAPI 国内镜像", chinese.displayName)
        assertEquals("BMCLAPI mirror", english.displayName)
        assertEquals("资源来自 BMCLAPI（bangbang93）", chinese.attribution)
        assertEquals("Downloads served by BMCLAPI (bangbang93)", english.attribution)
    }

    @Test
    fun `ServiceLoader 声明的类真实存在`() {
        val stream = javaClass.getResourceAsStream("/META-INF/services/dev.mmcl.api.plugin.Plugin")
        checkNotNull(stream) { "缺少 META-INF/services 声明" }
        val names = stream.bufferedReader().use { it.readLines() }.filter { it.isNotBlank() }

        assertTrue(names.isNotEmpty())
        for (name in names) {
            // 类名写错时这里会立刻炸，避免装到真实环境才发现
            assertEquals(BmclapiSourcePlugin::class.java.name, name.trim())
        }
    }

    @Test
    fun `镜像优先于官方源`() {
        assertEquals(10, source.priority)
        assertEquals("bmclapi", source.id)
    }

    @Test
    fun `Fabric Maven 地址会改写到 BMCLAPI`() {
        val mirror = BmclapiUrlMirror()

        assertEquals(
            "https://bmclapi2.bangbang93.com/maven/net/fabricmc/fabric-loader/0.20.1/fabric-loader-0.20.1.jar",
            mirror.resolve("https://maven.fabricmc.net/net/fabricmc/fabric-loader/0.20.1/fabric-loader-0.20.1.jar"),
        )
        assertEquals(null, mirror.resolve("https://piston-meta.mojang.com/26.3.json"))
    }
}
