package xenakis.ui.actions

import hextant.fx.Shortcut
import hextant.fx.never
import hextant.fx.shortcut
import javafx.event.Event
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.materialdesign2.MaterialDesignP
import reaktive.value.*
import reaktive.value.binding.equalTo

class Action<in C> private constructor(
    val name: String,
    val category: Category,
    private val description: (C) -> ReactiveString,
    val shortcuts: List<Shortcut>, val icon: (C) -> ReactiveValue<Ikon>,
    private val applicability: (C) -> ReactiveBoolean,
    val ifNotApplicable: IfNotApplicable,
    val toggleState: (C) -> ReactiveValue<Boolean>?,
    private val execute: (C, Event?) -> Unit
) {
    fun withContext(context: C): ContextualizedAction = Contextualized(this, context)

    enum class Category {
        Unknown, File, Edit, View;
    }

    class Builder<C>(
        val name: String,
        var category: Category = Category.Unknown,
        private var description: (C) -> ReactiveString = { _ -> reactiveValue(name) },
        private val shortcuts: MutableList<Shortcut> = mutableListOf(),
        private var icon: (C) -> ReactiveValue<Ikon> = { reactiveValue(NO_ICON) },
        private var applicability: (C) -> ReactiveBoolean = { reactiveValue(true) },
        private var ifNotApplicable: IfNotApplicable = IfNotApplicable.Hide,
        private var toggleState: (C) -> ReactiveValue<Boolean>? = { null },
        private var execute: (C, ev: Event?) -> Unit = { _, _ -> }
    ) {
        fun description(desc: String) {
            description = { _ -> reactiveValue(desc) }
        }

        fun description(description: (C) -> ReactiveString) {
            this.description = description
        }

        fun shortcut(literal: String) {
            shortcuts.add(literal.shortcut)
        }

        fun shortcuts(vararg literals: String) {
            for (literal in literals) {
                shortcut(literal)
            }
        }

        fun icon(ikon: Ikon) {
            icon = { reactiveValue(ikon) }
        }

        fun icon(react: (C) -> ReactiveValue<Ikon>) {
            icon = react
        }

        fun executes(body: (C, ev: Event?) -> Unit) {
            execute = body
        }

        fun executes(body: (C) -> Unit) {
            execute = { ctx, _ -> body(ctx) }
        }

        fun toggles(variable: (C) -> ReactiveVariable<Boolean>) {
            toggleState = variable
            executes { ctx ->
                val v = variable(ctx)
                v.now = !v.now
            }
        }

        fun <T> selects(value: T, variable: (C) -> ReactiveVariable<T>) {
            toggleState = { ctx -> variable(ctx).equalTo(value) }
            executes { ctx ->
                val v = variable(ctx)
                v.set(value)
            }
        }

        fun applicableIf(predicate: (C) -> ReactiveBoolean) {
            applicability = predicate
        }

        fun ifNotApplicable(consequence: IfNotApplicable) {
            ifNotApplicable = consequence
        }

        fun build(): Action<C> = Action(
            name, category, description,
            shortcuts, icon,
            applicability, ifNotApplicable, toggleState, execute
        )
    }

    open class Collector<C>() {
        private val actions = mutableListOf<Action<C>>()

        var category: Category = Category.Unknown

        constructor(collect: Collector<C>.() -> Unit): this() {
            collect()
        }

        fun add(action: Action<C>) {
            actions.add(action)
        }

        fun addAll(collector: Collector<C>) {
            actions.addAll(collector.actions)
        }

        inline fun addAction(name: String, configure: Builder<C>.() -> Unit) {
            val builder = Builder<C>(name, category = category)
            builder.configure()
            add(builder.build())
        }

        fun getAction(name: String): Action<C> =
            actions.find { a -> a.name == name } ?: error("No action with name $name")

        fun withContext(context: C): List<ContextualizedAction> =
            actions.map { action -> Contextualized(action, context) }
    }

    private class Contextualized<C>(
        override val wrapped: Action<C>,
        private val context: C
    ) : ContextualizedAction {
        override val icon: ReactiveValue<Ikon>
            get() = wrapped.icon(context)

        override fun getDescription(): ReactiveString = wrapped.description(context)

        override fun isApplicable(): ReactiveBoolean = wrapped.applicability(context)

        override fun toggleState(): ReactiveBoolean? = wrapped.toggleState(context)

        override fun execute(ev: Event?) {
            wrapped.execute.invoke(context, ev)
        }
    }

    enum class IfNotApplicable {
        Hide, Disable;
    }

    companion object {
        val NO_SHORTCUT = never()
        val NO_ICON = MaterialDesignP.PROGRESS_QUESTION
    }
}