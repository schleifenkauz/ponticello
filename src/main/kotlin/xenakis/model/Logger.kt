package xenakis.model

import hextant.core.editor.ListenerManager
import xenakis.ui.Icon

object Logger {
    private val views = ListenerManager.createWeakListenerManager<View>()
    private val records = mutableListOf<Record>()

    var level: Level = Level.Info

    fun log(level: Level, category: Category? = null, message: String, detailMessage: String? = null) {
        if (level < this.level) return
        val timestamp = System.currentTimeMillis()
        val record = Record(timestamp, level, category, message, detailMessage)
        records.add(record)
        views.notifyListeners { logged(record) }
    }

    fun fine(message: String, category: Category, detailMessage: String? = null) =
        log(Level.Fine, category, message, detailMessage)

    fun info(message: String, category: Category) = log(Level.Info, category, message)
    fun confirm(message: String, category: Category) = log(Level.Confirmation, category, message)
    fun warn(message: String, category: Category) = log(Level.Warning, category, message)
    fun error(message: String, category: Category? = null, detailMessage: String? = null) =
        log(Level.Error, category, message, detailMessage)

    fun severe(message: String, category: Category? = null, detailMessage: String? = null) =
        error(message, category, detailMessage)

    fun clear() {
        records.clear()
    }

    fun addView(view: View) {
        views.addListener(view)
    }

    fun getRecords(filter: Filter? = null): List<Record> =
        if (filter != null) records.filter { r -> filter.accepts(r) } else records

    data class Record(
        val timestamp: Long,
        val level: Level,
        val category: Category?,
        val message: String,
        val detailMessage: String?
    )

    data class Filter(val level: Level, val category: Category, val searchText: String) {
        fun accepts(record: Record): Boolean =
            record.level >= level && category.filter(record.category) && record.message.contains(searchText)
    }

    interface View {
        fun logged(record: Record)
    }

    enum class Level : Comparable<Level> {
        Fine, Info, Confirmation, Warning, Error;

        val icon
            get() = when (this) {
                Fine -> Icon.Debug
                Info -> Icon.Info
                Confirmation -> Icon.Confirmation
                Warning -> Icon.Warning
                Error -> Icon.Error
            }
    }

    sealed class Category(val superCategory: Category? = All) {
        object All : Category(null)
        object Playback : Category()
        object SuperCollider : Category()
        object Score : Category()
        object Registries : Category()
        object Samples : Category(superCategory = Registries)
        object Buses : Category(superCategory = Registries)
        object Buffers : Category(superCategory = Registries)
        object AudioFlow : Category()
        object Instruments : Category(superCategory = Registries)
        object Groups : Category(superCategory = null)
        object GlobalControls : Category(superCategory = Registries)
        object Server : Category()
        object Project : Category()
        object VSTPlugins : Category()

        fun filter(category: Category?): Boolean =
            category == this || (category?.superCategory != null && filter(category.superCategory))

        override fun toString(): String = javaClass.simpleName

        companion object {
            fun values(): List<Category> = Category::class.sealedSubclasses.mapNotNull { t -> t.objectInstance }
        }

    }
}