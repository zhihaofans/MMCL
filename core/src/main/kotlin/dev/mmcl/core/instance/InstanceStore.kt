package dev.mmcl.core.instance

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.slf4j.Logger

/**
 * 实例清单的持久化：`<dataDir>/instances.json`。
 *
 * - 原子写（临时文件 + move），避免崩溃写坏；
 * - 文件损坏时记日志并返回空列表，不让启动流程崩掉；
 * - `ignoreUnknownKeys` 保证以后加字段能读旧文件。
 */
class InstanceStore(
    private val file: Path,
    private val logger: Logger,
) {
    private val json =
        Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    /** 读取实例列表；文件不存在或损坏时返回空列表。 */
    fun load(): List<Instance> {
        if (!Files.isRegularFile(file)) return emptyList()
        return try {
            json.decodeFromString(FileState.serializer(), Files.readString(file, Charsets.UTF_8)).instances
        } catch (t: SerializationException) {
            logger.warn("实例清单解析失败，按空列表处理：{}", t.message)
            emptyList()
        } catch (t: IOException) {
            logger.warn("读取实例清单失败，按空列表处理：{}", t.message)
            emptyList()
        }
    }

    /** 覆盖写入实例列表。 */
    fun save(instances: List<Instance>) {
        val parent = file.toAbsolutePath().parent ?: return
        Files.createDirectories(parent)
        val payload = json.encodeToString(FileState.serializer(), FileState(instances))
        val temp = Files.createTempFile(parent, "instances", ".tmp")
        Files.writeString(temp, payload, Charsets.UTF_8)
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
    }

    @Serializable
    private data class FileState(
        val instances: List<Instance> = emptyList(),
    )
}
