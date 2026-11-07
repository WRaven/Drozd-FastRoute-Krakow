package pl.wraven.mpkfastroute.core.live

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import java.io.IOException
import java.io.StringReader
import java.net.URI
import java.net.URISyntaxException
import javax.inject.Inject
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import org.xml.sax.SAXException

data class ZtpServiceNewsItem(
    val title: String,
    val url: String
)

class ZtpServiceNewsClient @Inject constructor(
    private val httpClient: HttpClient
) {
    suspend fun getLatestItems(): List<ZtpServiceNewsItem> {
        val response = httpClient.get(FEED_URL) {
            header(HttpHeaders.Accept, "application/rss+xml, application/xml, text/xml")
            header(HttpHeaders.UserAgent, USER_AGENT)
        }
        if (!response.status.isSuccess()) {
            throw IOException("ZTP RSS returned HTTP ${response.status.value}.")
        }
        val xml = response.bodyAsText()
        return withContext(Dispatchers.IO) {
            ZtpServiceNewsParser.parse(xml)
        }
    }

    companion object {
        const val FEED_URL = "https://ztp.krakow.pl/feed?post_type=komunikat"
        private const val USER_AGENT = "Drozd-FastRoute/0.1.0 (Android)"
    }
}

internal object ZtpServiceNewsParser {
    fun parse(xml: String): List<ZtpServiceNewsItem> {
        if (xml.contains("<!DOCTYPE", ignoreCase = true)) {
            throw IOException("ZTP RSS document types are not supported.")
        }
        val builder = try {
            DocumentBuilderFactory.newInstance()
                .apply { isExpandEntityReferences = false }
                .newDocumentBuilder()
        } catch (exception: ParserConfigurationException) {
            throw IOException("Unable to configure the ZTP RSS parser.", exception)
        }
        builder.setEntityResolver { _, _ ->
            throw SAXException("External RSS entities are disabled.")
        }
        val document = try {
            builder.parse(InputSource(StringReader(xml)))
        } catch (exception: SAXException) {
            throw IOException("ZTP returned invalid RSS data.", exception)
        }

        if (document.documentElement?.nodeName != "rss") {
            throw IOException("ZTP returned an unexpected RSS document.")
        }

        val items = document.getElementsByTagName("item")
        return (0 until items.length)
            .mapNotNull { index ->
                val item = items.item(index) as? Element ?: return@mapNotNull null
                val title = item.directChildText("title") ?: return@mapNotNull null
                val url = item.directChildText("link")
                    ?.takeIf(::isOfficialZtpUrl)
                    ?: return@mapNotNull null
                ZtpServiceNewsItem(title = title, url = url)
            }
            .take(MAX_ITEMS)
    }

    private fun Element.directChildText(name: String): String? =
        (0 until childNodes.length)
            .asSequence()
            .map(childNodes::item)
            .firstOrNull { node ->
                node.nodeType == Node.ELEMENT_NODE &&
                    (node.localName ?: node.nodeName.substringAfter(':')) == name
            }
            ?.textContent
            ?.trim()
            ?.takeIf(String::isNotBlank)

    private fun isOfficialZtpUrl(value: String): Boolean {
        val uri = try {
            URI(value)
        } catch (_: URISyntaxException) {
            return false
        }
        return uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals(ZTP_HOST, ignoreCase = true) &&
            uri.userInfo == null
    }

    private const val MAX_ITEMS = 10
    private const val ZTP_HOST = "ztp.krakow.pl"
}
