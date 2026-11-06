package pl.wraven.mpkfastroute.core.update

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import javax.inject.Inject
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException

data class UpdateCheckResult(
    val currentVersion: String,
    val latestVersion: String,
    val releaseUrl: String,
    val updateAvailable: Boolean
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
        val isTestBuild = context.packageName.endsWith(".debug")
        val release = releases.firstOrNull { element ->
            val candidate = element.jsonObject
            candidate["draft"]?.jsonPrimitive?.boolean != true &&
                candidate["prerelease"]?.jsonPrimitive?.boolean == isTestBuild
        }?.jsonObject ?: throw NoReleaseAvailableException()
        val tagName = release["tag_name"]?.jsonPrimitive?.content
            ?: throw IOException("GitHub release response has no tag name.")
        val releaseUrl = release["html_url"]?.jsonPrimitive?.content
            ?: throw IOException("GitHub release response has no release URL.")
        val assetNames = release["assets"]?.jsonArray
            ?.mapNotNull { asset -> asset.jsonObject["name"]?.jsonPrimitive?.content }
            .orEmpty()
        val latestVersion = versionForRelease(tagName, assetNames)
        val currentVersion = context.packageManager
            .getPackageInfo(context.packageName, 0)
            .versionName
            ?: throw IllegalStateException("The installed app version is unavailable.")

        return UpdateCheckResult(
            currentVersion = currentVersion,
            latestVersion = latestVersion,
            releaseUrl = releaseUrl,
            updateAvailable = compareVersions(latestVersion, currentVersion) > 0
        )
    }

    companion object {
        const val RELEASES_PAGE =
            "https://github.com/WRaven/MPK-Krakow-FastRoute/releases"
        const val RELEASES_API =
            "https://api.github.com/repos/WRaven/MPK-Krakow-FastRoute/releases?per_page=100"
        private const val USER_AGENT = "MPK-Krakow-FastRoute"
        private val VERSION_PATTERN =
            Regex("""^[vV]?(\d+)\.(\d+)\.(\d+)(?:-ci\.(\d+))?(?:[-+].*)?$""")
        private val APK_VERSION_PATTERN =
            Regex("""(?i)(?:^|[-_])v?(\d+\.\d+\.\d+(?:-ci\.\d+)?)\b""")

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

        internal fun versionForRelease(tagName: String, assetNames: List<String>): String {
            if (VERSION_PATTERN.matches(tagName)) {
                return tagName.removePrefix("v").removePrefix("V")
            }
            return assetNames.firstNotNullOfOrNull { assetName ->
                APK_VERSION_PATTERN.find(assetName)?.groupValues?.get(1)
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
