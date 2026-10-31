package dev.mmcl.core.download

import dev.mmcl.core.version.AssetIndex

/**
 * 把资源索引转成下载清单。
 *
 * 地址由来源插件提供（[objectUrl]）；返回 `null` 的对象计入 [Plan.missingUrls]，不下载。
 * 资源对象按 SHA-1 寻址：`assets/objects/<前两位>/<hash>`。
 */
object AssetPlanner {
    /**
     * @property files 可下载的资源对象
     * @property missingUrls 没有下载地址的对象数量（通常意味着当前来源插件不支持资源下载）
     */
    data class Plan(
        val files: List<PlannedFile>,
        val missingUrls: Int,
    )

    fun plan(index: AssetIndex, objectUrl: (String) -> String?): Plan {
        val files = mutableListOf<PlannedFile>()
        var missing = 0
        for (asset in index.objects.values) {
            val hash = asset.hash
            if (hash.length < 2) {
                missing++
                continue
            }
            val url = objectUrl(hash)
            if (url.isNullOrBlank()) {
                missing++
                continue
            }
            files +=
                PlannedFile(
                    relativePath = "assets/objects/${hash.substring(0, 2)}/$hash",
                    url = url,
                    sha1 = hash,
                    size = asset.size,
                )
        }
        return Plan(files = files.sortedBy { it.relativePath }, missingUrls = missing)
    }
}
