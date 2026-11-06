package pl.wraven.mpkfastroute.core.update

import org.junit.Assert.assertEquals
import org.junit.Test

class GitHubUpdateCheckerTest {
    @Test
    fun comparesVersionNumbersAndIgnoresBuildSuffixes() {
        assertEquals(1, GitHubUpdateChecker.compareVersions("v1.2.0", "1.1.9-test"))
        assertEquals(0, GitHubUpdateChecker.compareVersions("1.2.3", "v1.2.3-ci.12"))
        assertEquals(-1, GitHubUpdateChecker.compareVersions("1.2.2", "1.2.3"))
    }
}
