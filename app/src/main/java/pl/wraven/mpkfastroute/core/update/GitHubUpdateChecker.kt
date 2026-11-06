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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException

data class UpdateCheckResult(
    val currentVersion: String,
    val latestVersion: String,
    val releaseUrl: String,
    val updateAvailable: Boolean
)

class GitHubUpdateChecker @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val httpClient: HttpClient,
    private val json: Json
) {
    suspend fun checkForUpdates(): UpdateCheckResult {
        val response = httpClient.get(LATEST_RELEASE_API) {
            header("Accept", "application/vnd.github+json")
            header("User-Agent", USER_AGENT)
        }
        if (response.status.value !in 200..299) {
            throw IOException("GitHub returned HTTP ${response.status.value}.")
        }

        val release = try {
            json.parseToJsonElement(response.bodyAsText()).jsonObject
        } catch (exception: SerializationException) {
            throw IOException("GitHub returned an invalid release response.", exception)
        }
        val latestVersion = release["tag_name"]?.jsonPrimitive?.content
            ?: throw IOException("GitHub release response has no tag name.")
        val releaseUrl = release["html_url"]?.jsonPrimitive?.content
            ?: throw IOException("GitHub release response has no release URL.")
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
        const val LATEST_RELEASE_API =
            "https://api.github.com/repos/WRaven/MPK-Krakow-FastRoute/releases/latest"
        private const val USER_AGENT = "MPK-Krakow-FastRoute"
        private val VERSION_PATTERN = Regex("""^[vV]?(\d+)\.(\d+)\.(\d+)(?:[-+].*)?$""")

        internal fun compareVersions(left: String, right: String): Int {
            val leftParts = versionParts(left)
            val rightParts = versionParts(right)
            for (index in leftParts.indices) {
                val comparison = leftParts[index].compareTo(rightParts[index])
                if (comparison != 0) {
                    return comparison
                }
            }
            return 0
        }

        private fun versionParts(version: String): List<Int> {
            val match = VERSION_PATTERN.matchEntire(version)
                ?: throw IllegalArgumentException("Unsupported app version: $version.")
            return (1..3).map { index ->
                match.groupValues[index].toIntOrNull()
                    ?: throw IllegalArgumentException("Unsupported app version: $version.")
            }
        }
    }
}
