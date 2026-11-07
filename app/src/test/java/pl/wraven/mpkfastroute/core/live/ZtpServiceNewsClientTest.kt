package pl.wraven.mpkfastroute.core.live

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ZtpServiceNewsClientTest {
    @Test
    fun fetchesAndParsesTheOfficialFeed() = runBlocking {
        val client = HttpClient(
            MockEngine {
                respond(
                    content = RSS,
                    headers = headersOf(HttpHeaders.ContentType, "application/rss+xml")
                )
            }
        )

        try {
            assertEquals(
                listOf(
                    ZtpServiceNewsItem(
                        title = "Utrudnienia w ruchu",
                        url = "https://ztp.krakow.pl/kmk/komunikaty/1"
                    )
                ),
                ZtpServiceNewsClient(client).getLatestItems()
            )
        } finally {
            client.close()
        }
    }

    @Test
    fun reportsUnsuccessfulHttpResponses() {
        runBlocking {
            val client = HttpClient(
                MockEngine {
                    respond(content = "", status = HttpStatusCode.ServiceUnavailable)
                }
            )

            try {
                assertThrows(IOException::class.java) {
                    runBlocking { ZtpServiceNewsClient(client).getLatestItems() }
                }
            } finally {
                client.close()
            }
        }
    }

    private companion object {
        const val RSS = """
            <rss><channel><item>
              <title>Utrudnienia w ruchu</title>
              <link>https://ztp.krakow.pl/kmk/komunikaty/1</link>
            </item></channel></rss>
        """
    }
}
