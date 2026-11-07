package pl.wraven.mpkfastroute.core.update

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import javax.inject.Inject
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pl.wraven.mpkfastroute.BuildConfig

data class UpdateCheckResult(
    val currentVersion: String,
    val latestVersion: String,
    val releaseUrl: String,
    val downloadUrl: String,
    val updateAvailable: Boolean
)

private data class ReleaseCandidate(
    val version: String,
    val releaseUrl: String,
    val downloadUrl: String
)

class NoReleaseAvailableException : IOException()

class GitHubUpdateChecker @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val httpClient: HttpClient,
    private val json: Json
) {
    suspend fun checkForUpdates(): UpdateCheckResult {
        val response = httpClient.get(RELEASES_API) {
            header("Accept", "application/vnd.github+json")
            header("User-Agent", USER_AGENT)
        }
        ensureSuccessfulResponse(response.status.value)

        val releases = try {
            json.parseToJsonElement(response.bodyAsText()).jsonArray
        } catch (exception: SerializationException) {
            throw IOException("GitHub returned an invalid releases response.", exception)
        }
        val releaseCandidates = releases.mapNotNull { element ->
            val candidate = element.jsonObject
            if (
                !releaseMatchesChannel(
                    prerelease = candidate["prerelease"]?.jsonPrimitive?.boolean,
                    draft = candidate["draft"]?.jsonPrimitive?.boolean,
                    isTestBuild = BuildConfig.DEBUG
                )
            ) {
                return@mapNotNull null
            }
            val tagName = candidate["tag_name"]?.jsonPrimitive?.content
                ?: throw IOException("GitHub release response has no tag name.")
            val releaseUrl = candidate["html_url"]?.jsonPrimitive?.content
                ?: throw IOException("GitHub release response has no release URL.")
            val assets = candidate["assets"]?.jsonArray
                ?.mapNotNull { asset ->
                    val assetObject = asset.jsonObject
                    val assetName = assetObject["name"]?.jsonPrimitive?.content
                        ?: return@mapNotNull null
                    assetName to assetObject["browser_download_url"]?.jsonPrimitive?.content
                }
                .orEmpty()
            val downloadUrl = selectApkDownloadUrl(assets) ?: return@mapNotNull null
            ReleaseCandidate(
                version = versionForRelease(tagName, assets.map { it.first }),
                releaseUrl = releaseUrl,
                downloadUrl = downloadUrl
            )
        }
        val latestVersion = latestVersion(releaseCandidates.map(ReleaseCandidate::version))
            ?: throw NoReleaseAvailableException()
        val latestRelease = releaseCandidates.first { it.version == latestVersion }
        val currentVersion = context.packageManager
            .getPackageInfo(context.packageName, 0)
            .versionName
            ?: throw IllegalStateException("The installed app version is unavailable.")

        return UpdateCheckResult(
            currentVersion = currentVersion,
            latestVersion = latestVersion,
            releaseUrl = latestRelease.releaseUrl,
            downloadUrl = latestRelease.downloadUrl,
            updateAvailable = compareVersions(latestVersion, currentVersion) > 0
        )
    }

    companion object {
        const val RELEASES_PAGE =
            "https://github.com/WRaven/MPK-Krakow-FastRoute/releases"
        const val RELEASES_API =
            "https://api.github.com/repos/WRaven/MPK-Krakow-FastRoute/releases?per_page=100"
        private const val USER_AGENT = "Drozd-FastRoute"
        private val TRUSTED_RELEASE_PATHS = listOf(
            "/WRaven/MPK-Krakow-FastRoute/releases/download/",
            "/WRaven/Drozd-FastRoute-Krakow/releases/download/"
        )
        private val VERSION_PATTERN =
            Regex("""^[vV]?(\d+)\.(\d+)\.(\d+)(?:-ci\.(\d+))?(?:[-+].*)?$""")
        private val APK_VERSION_PATTERN =
            Regex("""(?i)(?:^|[-_])v?(\d+\.\d+\.\d+(?:-ci\.\d+)?)\b""")
        private val APK_BUILD_NUMBER_PATTERN =
            Regex("""(?i)(?:^|[-_])build(\d+)(?:[-_.]|$)""")

        internal fun releaseMatchesChannel(
            prerelease: Boolean?,
            draft: Boolean?,
            isTestBuild: Boolean
        ): Boolean = draft != true && prerelease == isTestBuild

        internal fun selectApkDownloadUrl(assets: List<Pair<String, String?>>): String? =
            assets.firstNotNullOfOrNull { (assetName, downloadUrl) ->
                downloadUrl?.takeIf {
                    assetName.endsWith(".apk", ignoreCase = true) &&
                        isTrustedDownloadUrl(it)
                }
            }

        internal fun isTrustedDownloadUrl(downloadUrl: String): Boolean =
            try {
                val uri = URI(downloadUrl)
                uri.scheme.equals("https", ignoreCase = true) &&
                    uri.host?.equals("github.com", ignoreCase = true) == true &&
                    (uri.port == -1 || uri.port == 443) &&
                    uri.userInfo == null &&
                    TRUSTED_RELEASE_PATHS.any { uri.path?.startsWith(it) == true }
            } catch (_: URISyntaxException) {
                false
            }

        internal fun ensureSuccessfulResponse(statusCode: Int) {
            when {
                statusCode == 404 -> throw NoReleaseAvailableException()
                statusCode !in 200..299 ->
                    throw IOException("GitHub returned HTTP $statusCode.")
            }
        }

        internal fun compareVersions(left: String, right: String): Int {
            val leftParts = versionParts(left)
            val rightParts = versionParts(right)
            for (index in 0..2) {
                val comparison = leftParts[index].compareTo(rightParts[index])
                if (comparison != 0) {
                    return comparison
                }
            }
            val leftCiBuild = leftParts[3]
            val rightCiBuild = rightParts[3]
            return when {
                leftCiBuild == rightCiBuild -> 0
                leftCiBuild < 0 -> 1
                rightCiBuild < 0 -> -1
                else -> leftCiBuild.compareTo(rightCiBuild)
            }
        }

        internal fun latestVersion(versions: List<String>): String? =
            versions.maxWithOrNull { left, right -> compareVersions(left, right) }

        internal fun versionForRelease(tagName: String, assetNames: List<String>): String {
            if (VERSION_PATTERN.matches(tagName)) {
                return tagName.removePrefix("v").removePrefix("V")
            }
            return assetNames.firstNotNullOfOrNull { assetName ->
                val version = APK_VERSION_PATTERN.find(assetName)?.groupValues?.get(1)
                    ?: return@firstNotNullOfOrNull null
                if (version.contains("-ci.")) {
                    version
                } else {
                    val buildNumber =
                        APK_BUILD_NUMBER_PATTERN.find(assetName)?.groupValues?.get(1)
                    buildNumber?.let { "$version-ci.$it" } ?: version
                }
            } ?: throw IllegalArgumentException(
                "GitHub release has no APK asset with a supported version."
            )
        }

        private fun versionParts(version: String): List<Int> {
            val match = VERSION_PATTERN.matchEntire(version)
                ?: throw IllegalArgumentException("Unsupported app version: $version.")
            val baseVersion = (1..3).map { index ->
                match.groupValues[index].toIntOrNull()
                    ?: throw IllegalArgumentException("Unsupported app version: $version.")
            }
            val ciBuild = match.groupValues[4].toIntOrNull() ?: -1
            return baseVersion + ciBuild
        }
    }
}
