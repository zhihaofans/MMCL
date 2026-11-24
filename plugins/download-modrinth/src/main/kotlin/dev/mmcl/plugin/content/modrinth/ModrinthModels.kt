package dev.mmcl.plugin.content.modrinth

import dev.mmcl.api.capability.ContentDependency
import dev.mmcl.api.capability.ContentFile
import dev.mmcl.api.capability.ContentProject
import dev.mmcl.api.capability.ContentVersion
import kotlinx.serialization.Serializable

@Serializable
internal data class SearchResponse(
    val hits: List<SearchHit> = emptyList(),
)

@Serializable
internal data class SearchHit(
    val project_id: String = "",
    val slug: String = "",
    val title: String = "",
    val description: String = "",
    val downloads: Long = 0,
)

@Serializable
internal data class VersionDto(
    val id: String = "",
    val project_id: String? = null,
    val name: String = "",
    val version_number: String = "",
    val game_versions: List<String> = emptyList(),
    val loaders: List<String> = emptyList(),
    val files: List<FileDto> = emptyList(),
    val dependencies: List<DependencyDto> = emptyList(),
)

@Serializable
internal data class FileDto(
    val url: String = "",
    val filename: String = "",
    val size: Long = 0,
    val primary: Boolean = false,
    val hashes: HashesDto? = null,
)

@Serializable
internal data class HashesDto(
    val sha1: String? = null,
    val sha512: String? = null,
)

@Serializable
internal data class DependencyDto(
    val version_id: String? = null,
    val project_id: String? = null,
    val file_name: String? = null,
    val dependency_type: String = "required",
)

internal fun SearchHit.toContentProject() =
    ContentProject(
        id = project_id,
        slug = slug,
        title = title,
        description = description,
        downloads = downloads,
    )

internal fun VersionDto.toContentVersion() =
    ContentVersion(
        id = id,
        projectId = project_id,
        name = name,
        versionNumber = version_number,
        gameVersions = game_versions,
        loaders = loaders,
        files =
            files.map { file ->
                ContentFile(
                    fileName = file.filename,
                    url = file.url,
                    size = file.size,
                    sha1 = file.hashes?.sha1,
                    sha512 = file.hashes?.sha512,
                    primary = file.primary,
                )
            },
        dependencies =
            dependencies.map { dependency ->
                ContentDependency(
                    projectId = dependency.project_id,
                    versionId = dependency.version_id,
                    fileName = dependency.file_name,
                    required = dependency.dependency_type == "required",
                )
            },
    )
