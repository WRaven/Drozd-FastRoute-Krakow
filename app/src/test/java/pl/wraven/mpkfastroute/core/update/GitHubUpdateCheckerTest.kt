package pl.wraven.mpkfastroute.core.update

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubUpdateCheckerTest {
    @Test
    fun stableBuildsSelectStableReleasesAndDebugBuildsSelectPrereleases() {
        assertTrue(GitHubUpdateChecker.releaseMatchesChannel(false, false, isTestBuild = false))
        assertTrue(!GitHubUpdateChecker.releaseMatchesChannel(true, false, isTestBuild = false))
        assertTrue(GitHubUpdateChecker.releaseMatchesChannel(true, false, isTestBuild = true))
        assertTrue(!GitHubUpdateChecker.releaseMatchesChannel(false, false, isTestBuild = true))
        listOf(false, true).forEach { isTestBuild ->
            assertTrue(!GitHubUpdateChecker.releaseMatchesChannel(isTestBuild, true, isTestBuild))
            assertTrue(!GitHubUpdateChecker.releaseMatchesChannel(null, false, isTestBuild))
        }
    }

    @Test
    fun oldPrereleaseUpdatersCanDiscoverTheStableApkThroughABridgeAsset() {
        val bridgeVersion = GitHubUpdateChecker.versionForRelease(
            tagName = "upgrade-v1.0.0",
            assetNames = listOf("Drozd-FastRoute-Krakow-v1.0.0-build66.apk")
        )
        assertEquals("1.0.0-ci.66", bridgeVersion)
        assertTrue(GitHubUpdateChecker.compareVersions(bridgeVersion, "0.1.0-ci.64-test") > 0)
        assertEquals(0, GitHubUpdateChecker.compareVersions("v1.0.0", "1.0.0"))
    }

    @Test
    fun comparesReleaseAndContinuousIntegrationVersions() {
        assertEquals(1, GitHubUpdateChecker.compareVersions("v1.2.0", "1.1.9-test"))
        assertEquals(1, GitHubUpdateChecker.compareVersions("1.2.3", "v1.2.3-ci.12"))
        assertEquals(1, GitHubUpdateChecker.compareVersions("0.1.0-ci.28-test", "0.1.0-ci.27"))
        assertEquals(-1, GitHubUpdateChecker.compareVersions("0.1.0-ci.27", "0.1.0-ci.28"))
        assertEquals(-1, GitHubUpdateChecker.compareVersions("1.2.2", "1.2.3"))
    }

    @Test
    fun selectsNewestReleaseIndependentOfGitHubApiOrdering() {
        assertEquals(
            "0.1.0-ci.47",
            GitHubUpdateChecker.latestVersion(
                listOf("0.1.0-ci.44", "0.1.0-ci.47", "0.1.0-ci.46", "0.1.0-ci.45")
            )
        )
    }

    @Test
    fun extractsVersionFromTestReleaseApkName() {
        assertEquals(
            "0.1.0-ci.42",
            GitHubUpdateChecker.versionForRelease(
                tagName = "test-abc1234",
                assetNames = listOf(
                    "MPK-Krakow-FastRoute-v0.1.0-ci.42-build42-debug.apk"
                )
            )
        )
    }

    @Test
    fun selectsOnlyApkAssetsFromThisRepositoriesReleaseDownloads() {
        val downloadUrl =
            "https://github.com/WRaven/MPK-Krakow-FastRoute/releases/download/v1.2.3/app.apk"

        assertEquals(
            downloadUrl,
            GitHubUpdateChecker.selectApkDownloadUrl(
                listOf(
                    "notes.txt" to "https://github.com/WRaven/MPK-Krakow-FastRoute/releases/download/v1.2.3/notes.txt",
                    "foreign.apk" to "https://example.com/app.apk",
                    "app.apk" to downloadUrl
                )
            )
        )
    }

    @Test
    fun usesLegacyTestApkBuildNumberWhenVersionHasNoCiSuffix() {
        val legacyVersion = GitHubUpdateChecker.versionForRelease(
            tagName = "test-d1a45a3",
            assetNames = listOf(
                "MPK-Krakow-FastRoute-0.1.0-d1a45a3-test-build24-debug.apk"
            )
        )

        assertEquals("0.1.0-ci.24", legacyVersion)
        assertEquals(
            "0.1.0-ci.48",
            GitHubUpdateChecker.latestVersion(listOf(legacyVersion, "0.1.0-ci.48"))
        )
    }

    @Test
    fun acceptsBothRepositoryAddressesDuringTheDrozdMigration() {
        listOf("MPK-Krakow-FastRoute", "Drozd-FastRoute-Krakow").forEach { repository ->
            val url = "https://github.com/WRaven/$repository/releases/download/test-bridge/app.apk"
            assertEquals(
                url,
                GitHubUpdateChecker.selectApkDownloadUrl(listOf("app.apk" to url))
            )
        }
    }

    @Test
    fun rejectsLookalikeAndUnrelatedRepositoriesAfterMigration() {
        listOf(
            "https://github.com/WRaven/Drozd-FastRoute-Krakow-other/releases/download/test/app.apk",
            "https://github.com/other/Drozd-FastRoute-Krakow/releases/download/test/app.apk",
            "https://github.com/WRaven/other/releases/download/test/app.apk",
            "https://github.com.example.com/WRaven/Drozd-FastRoute-Krakow/releases/download/test/app.apk",
            "http://github.com/WRaven/Drozd-FastRoute-Krakow/releases/download/test/app.apk",
            "https://user@github.com/WRaven/Drozd-FastRoute-Krakow/releases/download/test/app.apk",
            "https://github.com:444/WRaven/Drozd-FastRoute-Krakow/releases/download/test/app.apk"
        ).forEach { url ->
            assertTrue(url, !GitHubUpdateChecker.isTrustedDownloadUrl(url))
        }
    }

    @Test
    fun extractsNewBrandedApkVersionAndKeepsOldInstalledVersionsUpdatable() {
        val version = GitHubUpdateChecker.versionForRelease(
            tagName = "test-bridge",
            assetNames = listOf("Drozd-FastRoute-Krakow-v0.1.0-ci.64-build64-debug.apk")
        )
        assertEquals("0.1.0-ci.64", version)
        assertTrue(GitHubUpdateChecker.compareVersions(version, "0.1.0-ci.63-test") > 0)
    }

    @Test
    fun treatsNotFoundAsNoPublishedRelease() {
        val exception = runCatching {
            GitHubUpdateChecker.ensureSuccessfulResponse(404)
        }.exceptionOrNull()

        assertTrue(exception is NoReleaseAvailableException)
    }

    @Test
    fun keepsOtherHttpFailuresAsErrors() {
        val exception = runCatching {
            GitHubUpdateChecker.ensureSuccessfulResponse(503)
        }.exceptionOrNull()

        assertTrue(exception is IOException)
        assertTrue(exception !is NoReleaseAvailableException)
    }
}
