package dev.mmcl.core.version

/**
 * 把 Maven 坐标转成仓库内相对路径。
 *
 * `net.fabricmc:fabric-loader:0.20.1` → `net/fabricmc/fabric-loader/0.20.1/fabric-loader-0.20.1.jar`。
 * 格式不合法时返回 `null`。
 */
fun mavenPathOf(coordinates: String): String? {
    val trimmed = coordinates.trim()
    if (trimmed.isEmpty()) return null
    val parts = trimmed.split(':')
    if (parts.size < 3) return null
    val group = parts[0].replace('.', '/')
    val artifact = parts[1]
    val version = parts[2]
    if (group.isBlank() || artifact.isBlank() || version.isBlank()) return null
    val classifier = parts.getOrNull(3)?.takeIf { it.isNotBlank() }?.let { "-$it" }.orEmpty()
    return "$group/$artifact/$version/$artifact-$version$classifier.jar"
}

/**
 * 库条目的下载信息。
 *
 * 优先用 `downloads.artifact`；Fabric profile 这类只有 `name`+`url` 的条目按 Maven 规则拼地址。
 * 返回值里的 `path` 一定非空，`sha1`/`size` 可能为空（Maven 元数据里没有）。
 */
fun Library.resolvedArtifact(): DownloadEntry? {
    downloads.artifact?.let { artifact ->
        if (artifact.path.isNullOrBlank()) {
            mavenPathOf(name)?.let { return artifact.copy(path = it) }
        }
        return artifact.copy(path = artifact.path ?: mavenPathOf(name))
    }
    if (url.isBlank()) return null
    val path = mavenPathOf(name) ?: return null
    return DownloadEntry(url = url.trimEnd('/') + "/" + path, path = path)
}
