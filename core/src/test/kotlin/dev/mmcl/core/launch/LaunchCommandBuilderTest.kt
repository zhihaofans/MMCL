package dev.mmcl.core.launch

import dev.mmcl.api.Architecture
import dev.mmcl.api.OperatingSystem
import dev.mmcl.core.version.VersionDetail
import java.io.File
import java.nio.file.Path
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

class LaunchCommandBuilderTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val sharedRoot: Path = Path.of("/tmp/mmcl-launch-test")
    private val instanceDir: Path = sharedRoot.resolve("instances/26.3")
    private val javaPath: Path = Path.of("/usr/bin/java")

    private fun detail(jsonText: String = SAMPLE): VersionDetail =
        json.decodeFromString(VersionDetail.serializer(), jsonText)

    private fun request(detail: VersionDetail = detail(), playerName: String = "Player") =
        LaunchRequest(
            detail = detail,
            sharedRoot = sharedRoot,
            instanceDir = instanceDir,
            javaExecutable = javaPath,
            launcherVersion = "0.0.1",
            os = OperatingSystem.MACOS,
            arch = Architecture.ARM64,
            playerName = playerName,
        )

    @Test
    fun `拼出可执行命令与必需参数`() {
        val command = LaunchCommandBuilder.build(request())

        assertEquals(javaPath, command.executable)
        assertTrue(command.arguments.contains("-Xmx2048M"))
        assertTrue(command.arguments.contains(detail().mainClass))
        assertTrue(command.arguments.contains("-Djava.library.path=${sharedRoot.resolve("versions/1.21.8/natives")}"))
        assertTrue(command.arguments.contains("-Dlog4j.configurationFile=${sharedRoot.resolve("assets/log_configs/client-1.21.8.xml")}"))
    }

    @Test
    fun `离线账户参数正确`() {
        val command = LaunchCommandBuilder.build(request(playerName = "Steve"))
        val args = command.arguments

        assertEquals("Steve", args[args.indexOf("--username") + 1])
        assertEquals(offlineUuid("Steve"), args[args.indexOf("--uuid") + 1])
        assertEquals("0", args[args.indexOf("--accessToken") + 1])
        assertEquals("1.21.8", args[args.indexOf("--version") + 1])
        assertEquals("17", args[args.indexOf("--assetIndex") + 1])
        assertEquals(instanceDir.toString(), args[args.indexOf("--gameDir") + 1])
    }

    @Test
    fun `features 未启用的参数不会出现`() {
        val command = LaunchCommandBuilder.build(request())

        assertFalse(command.arguments.contains("--width"))
    }

    @Test
    fun `classpath 按平台架构过滤且含客户端 JAR`() {
        val command = LaunchCommandBuilder.build(request())
        val entries = command.classpath.split(File.pathSeparatorChar)

        assertTrue(entries.any { it.endsWith("libraries/com/example/lib/1.0/lib-1.0.jar") })
        assertTrue(entries.any { it.endsWith("versions/1.21.8/1.21.8.jar") })
        assertTrue(entries.any { it.endsWith("lwjgl-3.4.3-natives-macos-arm64.jar") })
        assertFalse(entries.any { it.contains("natives-macos.jar") })
        assertFalse(entries.any { it.contains("natives-windows") })
    }

    @Test
    fun `缺少 mainClass 时抛 LaunchException`() {
        val broken = detail(SAMPLE.replace("\"net.minecraft.client.main.Main\"", "\"\""))

        assertFailsWith<LaunchException> { LaunchCommandBuilder.build(request(broken)) }
    }

    @Test
    fun `离线 UUID 是 v3 且稳定`() {
        val uuid = UUID.fromString(offlineUuid("Player"))

        assertEquals(3, uuid.version())
        assertEquals(offlineUuid("Player"), offlineUuid("Player"))
    }

    private companion object {
        const val SAMPLE: String =
            """
            {
              "id": "1.21.8",
              "type": "release",
              "mainClass": "net.minecraft.client.main.Main",
              "assetIndex": { "id": "17", "sha1": "x", "size": 1, "url": "https://example.invalid/17.json" },
              "downloads": { "client": { "url": "https://example.invalid/client.jar", "sha1": "c", "size": 1 } },
              "libraries": [
                {
                  "name": "com.example:lib:1.0",
                  "downloads": { "artifact": { "path": "com/example/lib/1.0/lib-1.0.jar", "url": "u", "sha1": "s", "size": 1 } }
                },
                {
                  "name": "org.lwjgl:lwjgl:3.4.3:natives-macos-arm64",
                  "rules": [{ "action": "allow", "os": { "name": "osx" } }],
                  "downloads": { "artifact": { "path": "org/lwjgl/lwjgl/3.4.3/lwjgl-3.4.3-natives-macos-arm64.jar", "url": "u", "sha1": "s", "size": 1 } }
                },
                {
                  "name": "org.lwjgl:lwjgl:3.4.3:natives-macos",
                  "rules": [{ "action": "allow", "os": { "name": "osx" } }],
                  "downloads": { "artifact": { "path": "org/lwjgl/lwjgl/3.4.3/lwjgl-3.4.3-natives-macos.jar", "url": "u", "sha1": "s", "size": 1 } }
                },
                {
                  "name": "org.lwjgl:lwjgl:3.4.3:natives-windows",
                  "rules": [{ "action": "allow", "os": { "name": "windows" } }],
                  "downloads": { "artifact": { "path": "org/lwjgl/lwjgl/3.4.3/lwjgl-3.4.3-natives-windows.jar", "url": "u", "sha1": "s", "size": 1 } }
                }
              ],
              "logging": {
                "client": {
                  "argument": "-Dlog4j.configurationFile=${'$'}{path}",
                  "file": { "id": "client-1.21.8.xml", "url": "u", "sha1": "s", "size": 1 }
                }
              },
              "arguments": {
                "jvm": ["-Djava.library.path=${'$'}{natives_directory}", "-cp", "${'$'}{classpath}"],
                "game": [
                  "--username", "${'$'}{auth_player_name}",
                  "--uuid", "${'$'}{auth_uuid}",
                  "--accessToken", "${'$'}{auth_access_token}",
                  "--version", "${'$'}{version_name}",
                  "--gameDir", "${'$'}{game_directory}",
                  "--assetsDir", "${'$'}{assets_root}",
                  "--assetIndex", "${'$'}{assets_index_name}",
                  {
                    "rules": [{ "action": "allow", "features": { "has_custom_resolution": true } }],
                    "value": ["--width", "${'$'}{resolution_width}"]
                  }
                ]
              }
            }
            """
    }
}
