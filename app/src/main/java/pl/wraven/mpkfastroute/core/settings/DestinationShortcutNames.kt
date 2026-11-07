package pl.wraven.mpkfastroute.core.settings

data class DestinationShortcutNames(
    val home: String = "Do domu",
    val work: String = "Do pracy"
) {
    init {
        require(home.isNotBlank()) { "The home shortcut name must not be blank." }
        require(work.isNotBlank()) { "The work shortcut name must not be blank." }
    }

    fun normalized(): DestinationShortcutNames = copy(home = home.trim(), work = work.trim())
}
