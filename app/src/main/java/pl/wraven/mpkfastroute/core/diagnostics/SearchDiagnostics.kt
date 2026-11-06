package pl.wraven.mpkfastroute.core.diagnostics

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Singleton
class SearchDiagnostics @Inject constructor() {
    private val mutableEntries = MutableStateFlow<List<String>>(emptyList())
    val entries = mutableEntries.asStateFlow()

    fun record(message: String) {
        val entry = "${LocalTime.now().format(TIME_FORMAT)}  ${message.take(MAX_MESSAGE_LENGTH)}"
        mutableEntries.update { (it + entry).takeLast(MAX_ENTRIES) }
    }

    fun clear() {
        mutableEntries.value = emptyList()
    }

    private companion object {
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        const val MAX_ENTRIES = 300
        const val MAX_MESSAGE_LENGTH = 300
    }
}
