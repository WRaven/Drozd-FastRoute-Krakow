package pl.wraven.mpkfastroute.core.live

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Test

class ZtpServiceNewsParserTest {
    @Test
    fun parsesTitlesAndOfficialArticleLinks() {
        val items = ZtpServiceNewsParser.parse(
            """
                <?xml version="1.0"?>
                <rss version="2.0">
                  <channel>
                    <item>
                      <title>Zmiana &amp; utrudnienia</title>
                      <link>https://ztp.krakow.pl/kmk/komunikaty/1</link>
                    </item>
                    <item>
                      <title><![CDATA[Objazd linii 5]]></title>
                      <link>https://ztp.krakow.pl/kmk/komunikaty/2</link>
                    </item>
                  </channel>
                </rss>
            """.trimIndent()
        )

        assertEquals(
            listOf(
                ZtpServiceNewsItem(
                    title = "Zmiana & utrudnienia",
                    url = "https://ztp.krakow.pl/kmk/komunikaty/1"
                ),
                ZtpServiceNewsItem(
                    title = "Objazd linii 5",
                    url = "https://ztp.krakow.pl/kmk/komunikaty/2"
                )
            ),
            items
        )
    }

    @Test
    fun ignoresMissingTitlesAndNonZtpLinks() {
        val items = ZtpServiceNewsParser.parse(
            """
                <rss><channel>
                  <item>
                    <title>Nieoficjalny link</title>
                    <link>https://example.com/alert</link>
                  </item>
                  <item>
                    <link>https://ztp.krakow.pl/kmk/komunikaty/1</link>
                  </item>
                </channel></rss>
            """.trimIndent()
        )

        assertEquals(emptyList<ZtpServiceNewsItem>(), items)
    }

    @Test(expected = IOException::class)
    fun rejectsDoctypeDeclarations() {
        ZtpServiceNewsParser.parse(
            """
                <!DOCTYPE rss [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <rss><channel><item><title>&xxe;</title></item></channel></rss>
            """.trimIndent()
        )
    }
}
