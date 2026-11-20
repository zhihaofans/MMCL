package dev.mmcl.core.plugin

import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PluginIntegrityTest {
    private fun writeJar(dir: Path, name: String = "plugin.jar", content: String = "hello"): Path {
        val jar = dir.resolve(name)
        JarOutputStream(Files.newOutputStream(jar)).use { out ->
            out.putNextEntry(JarEntry("plugin.txt"))
            out.write(content.toByteArray(Charsets.UTF_8))
            out.closeEntry()
        }
        return jar
    }

    @Test
    fun `未签名 JAR 得到 SHA-256 与未签名状态`() {
        val jar = writeJar(Files.createTempDirectory("mmcl-integrity"))

        val integrity = PluginIntegrityVerifier.inspect(jar)

        assertEquals(64, integrity.sha256.length)
        assertTrue(integrity.sha256.all { it in "0123456789abcdef" })
        assertEquals(12, integrity.shortSha256.length)
        assertIs<PluginSignature.Unsigned>(integrity.signature)
    }

    @Test
    fun `内容不同则摘要不同`() {
        val dir = Files.createTempDirectory("mmcl-integrity")
        val first = PluginIntegrityVerifier.sha256Of(writeJar(dir, "a.jar", "one"))
        val second = PluginIntegrityVerifier.sha256Of(writeJar(dir, "b.jar", "two"))

        assertTrue(first != second)
    }

    @Test
    fun `签名后被篡改的 JAR 判定为签名无效`() {
        val keytool = jdkTool("keytool") ?: return
        val jarsigner = jdkTool("jarsigner") ?: return
        val dir = Files.createTempDirectory("mmcl-signed")
        val jar = writeJar(dir)
        val keystore = dir.resolve("test.p12")

        run(keytool, "-genkeypair", "-alias", "test", "-dname", "CN=MMCL Test", "-keyalg", "RSA",
            "-validity", "1", "-keystore", keystore.toString(), "-storepass", "changeit",
            "-keypass", "changeit", "-storetype", "PKCS12", "-noprompt")
        run(jarsigner, "-keystore", keystore.toString(), "-storepass", "changeit",
            "-keypass", "changeit", jar.toString(), "test")

        assertIs<PluginSignature.Signed>(PluginIntegrityVerifier.signatureOf(jar))

        tamper(jar)

        assertIs<PluginSignature.Invalid>(PluginIntegrityVerifier.signatureOf(jar))
    }

    /** 改写条目内容但不动签名文件，制造「签名与内容不符」。 */
    private fun tamper(jar: Path) {
        val original = Files.readAllBytes(jar)
        ZipInputStream(original.inputStream().buffered()).use { input ->
            ZipOutputStream(Files.newOutputStream(jar)).use { output ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    val bytes = input.readAllBytes()
                    output.putNextEntry(ZipEntry(entry.name))
                    if (entry.name == "plugin.txt") {
                        output.write(ByteArray(1) { (bytes.first().toInt() xor 0x01).toByte() } + bytes.drop(1))
                    } else {
                        output.write(bytes)
                    }
                    output.closeEntry()
                }
            }
        }
    }

    private fun jdkTool(name: String): Path? {
        val home = Path.of(System.getProperty("java.home"))
        return listOf(home.resolve("bin/$name"), home.resolve("bin/$name.exe"))
            .firstOrNull { Files.isExecutable(it) }
    }

    private fun run(vararg command: Any) {
        val process = ProcessBuilder(command.map { it.toString() }).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        val exit = process.waitFor()
        check(exit == 0) { "${command.first()} 失败（$exit）：$output" }
    }
}
