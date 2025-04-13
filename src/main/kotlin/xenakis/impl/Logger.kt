package xenakis.impl

import hextant.core.editor.ListenerManager
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.materialdesign2.MaterialDesignA
import org.kordamp.ikonli.materialdesign2.MaterialDesignB
import org.kordamp.ikonli.materialdesign2.MaterialDesignC
import org.kordamp.ikonli.materialdesign2.MaterialDesignI
import xenakis.ui.impl.stackTraceString

object Logger {
    private val views = ListenerManager.Companion.createWeakListenerManager<View>()
    private val records = mutableListOf<Record>()

    var level: Level = Level.Info

    fun log(
        level: Level, category: Category? = null, message: String,
        detailMessage: String? = null, exception: Throwable? = null,
    ) {
        if (level < this.level) return
        val timestamp = System.currentTimeMillis()
        val record = Record(timestamp, level, category, message, detailMessage ?: exception?.message)
        records.add(record)
        if (level == Level.Error) {
            System.err.println("Error: $message")
            if (exception != null) {
                exception.printStackTrace()
            } else {
                Thread.dumpStack()
            }
        }
        views.notifyListeners { logged(record) }
    }

    fun fine(message: String, category: Category, detailMessage: String? = null) =
        log(Level.Fine, category, message, detailMessage)

    fun info(message: String, category: Category) = log(Level.Info, category, message)
    fun confirm(message: String, category: Category) = log(Level.Confirmation, category, message)
    fun warn(message: String, category: Category) = log(Level.Warning, category, message)
    fun error(message: String, category: Category? = null, detailMessage: String? = null) =
        log(Level.Error, category, message, detailMessage)

    fun error(message: String, exception: Throwable, category: Category? = null) {
        log(Level.Error, category, message, exception.stackTraceString(), exception)
    }

    fun severe(message: String, category: Category? = null, detailMessage: String? = null) =
        error(message, category, detailMessage)

    fun clear() {
        records.clear()
        views.notifyListeners { clearedLog() }
    }

    fun addView(view: View) {
        views.addListener(view)
    }

    fun getRecords(filter: Filter? = null): List<Record> =
        if (filter != null) records.filter { r -> filter.accepts(r) } else records.toList()

    data class Record(
        val timestamp: Long,
        val level: Level,
        val category: Category?,
        val message: String,
        val detailMessage: String?,
    )

    data class Filter(val level: Level, val category: Category, val searchText: String) {
        fun accepts(record: Record): Boolean =
            record.level >= level && category.filter(record.category) && record.message.contains(searchText)
    }

    interface View {
        fun logged(record: Record)

        fun clearedLog()
    }

    enum class Level : Comparable<Level> {
        Fine, Info, Confirmation, Warning, Error;

        val icon
            get(): Ikon = when (this) {
                Fine -> MaterialDesignB.BUG
                Info -> MaterialDesignI.INFORMATION
                Confirmation -> MaterialDesignC.CHECK_CIRCLE
                Warning -> MaterialDesignA.ALERT
                Error -> MaterialDesignA.ALERT_OCTAGON
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

        fun filter(category: Category?): Boolean = when {
            this == All -> true
            category == this -> true
            category?.superCategory != null -> filter(category.superCategory)
            else -> false
        }

        override fun toString(): String = javaClass.simpleName

        companion object {
            fun values(): List<Category> = Category::class.sealedSubclasses.mapNotNull { t -> t.objectInstance }
        }

    }
}