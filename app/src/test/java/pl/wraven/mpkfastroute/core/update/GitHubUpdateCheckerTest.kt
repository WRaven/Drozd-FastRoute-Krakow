package pl.wraven.mpkfastroute.core.update

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubUpdateCheckerTest {
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
