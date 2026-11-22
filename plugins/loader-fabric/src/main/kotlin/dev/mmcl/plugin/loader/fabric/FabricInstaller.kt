package dev.mmcl.plugin.loader.fabric

import dev.mmcl.api.capability.LoaderInstallRequest
import dev.mmcl.api.capability.LoaderInstallResult
import dev.mmcl.api.capability.LoaderInstaller
import dev.mmcl.api.capability.LoaderVersion
import java.io.IOException
import org.slf4j.Logger

/**
 * Fabric 的 [LoaderInstaller] 实现。
 *
 * 只负责选版本、拉 profile、校验并返回 overlay JSON；
 * 写盘、下载、`inheritsFrom` 合并都由启动器负责。
 */
internal class FabricInstaller(
    private val client: FabricMetaClient,
    private val http: FabricHttp,
    private val mirrors: () -> List<dev.mmcl.api.capability.UrlMirror>,
    private val logger: Logger,
) : LoaderInstaller {
    override val loaderId: String = "fabric"

    override suspend fun listVersions(gameVersionId: String): List<LoaderVersion> {
        val response = client.loaders(gameVersionId)
        if (response.source == FabricSource.MIRROR) {
            logger.warn("Fabric loader 列表来自 BMCLAPI 镜像：{}", gameVersionId)
        }
        val versions = FabricPayloads.parseLoaders(response.body)
        if (versions.isEmpty()) {
            throw IOException("Fabric 没有适用于 $gameVersionId 的 loader 版本")
        }
        return versions
    }

    override suspend fun install(request: LoaderInstallRequest): LoaderInstallResult {
        val versions = listVersions(request.gameVersionId)
        val requested = request.loaderVersion?.trim()?.takeIf { it.isNotEmpty() }
        val chosen =
            if (requested != null) {
                versions.firstOrNull { it.version == requested }
                    ?: throw IOException("Fabric 没有 $requested（${request.gameVersionId}）")
            } else {
                versions.firstOrNull { it.stable } ?: versions.first()
            }

        val profile = client.profile(request.gameVersionId, chosen.version)
        val check = FabricPayloads.checkProfile(profile.body, request.gameVersionId)
        val normalized = FabricPayloads.normalizeLibraries(profile.body, mirrors(), http, logger)
        val warnings = (check.warnings + normalized.warnings).toMutableList()
        if (profile.source == FabricSource.MIRROR) {
            warnings += "Fabric profile 来自 BMCLAPI 镜像，可能与官方存在差异"
        }
        logger.info("Fabric loader {} 已生成版本 {}", chosen.version, check.id)
        return LoaderInstallResult(
            versionId = check.id,
            detailJson = normalized.detailJson,
            warnings = warnings,
        )
    }
}
