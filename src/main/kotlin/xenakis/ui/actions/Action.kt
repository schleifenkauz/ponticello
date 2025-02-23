package xenakis.ui.actions

import hextant.fx.Shortcut
import hextant.fx.never
import hextant.fx.shortcut
import javafx.event.Event
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.materialdesign2.MaterialDesignP
import reaktive.value.ReactiveBoolean
import reaktive.value.ReactiveString
import reaktive.value.ReactiveValue
import reaktive.value.reactiveValue

class Action<in C> private constructor(
    val name: String,
    val category: Category,
    private val description: (C) -> ReactiveString,
    val shortcut: Shortcut, val icon: (C) -> ReactiveValue<Ikon>,
    private val applicability: (C) -> ReactiveBoolean,
    val ifNotApplicable: IfNotApplicable,
    private val execute: (C, Event?) -> Unit
) {
    fun execute(context: C, event: Event?) {
        execute.invoke(context, event)
    }

    fun getDescription(context: C): ReactiveString = description.invoke(context)

    fun isApplicable(context: C): ReactiveBoolean = applicability(context)

    fun withContext(context: C): ContextualizedAction = Contextualized(this, context)

    enum class Category {
        Unknown, File, Edit, View;
    }

    class Builder<C>(
        val name: String,
        var category: Category = Category.Unknown,
        private var description: (C) -> ReactiveString = { _ -> reactiveValue(name) },
        private var shortcut: Shortcut = NO_SHORTCUT,
        private var icon: (C) -> ReactiveValue<Ikon> = { reactiveValue(NO_ICON) },
        private var applicability: (C) -> ReactiveBoolean = { reactiveValue(true) },
        private var ifNotApplicable: IfNotApplicable = IfNotApplicable.Hide,
        private var execute: (C, ev: Event?) -> Unit = { _, _ -> }
    ) {
        fun description(desc: String) {
            description = { _ -> reactiveValue(desc) }
        }

        fun description(description: (C) -> ReactiveString) {
            this.description = description
        }

        fun shortcut(literal: String) {
            shortcut = literal.shortcut
        }

        fun icon(ikon: Ikon) {
            icon = { reactiveValue(ikon) }
        }

        fun icon(react: (C) -> ReactiveValue<Ikon>) {
            icon = react
        }

        fun execute(body: (C, ev: Event?) -> Unit) {
            execute = body
        }

        fun execute(body: (C) -> Unit) {
            execute = { ctx, _ -> body(ctx) }
        }

        fun applicableIf(predicate: (C) -> ReactiveBoolean) {
            applicability = predicate
        }

        fun ifNotApplicable(consequence: IfNotApplicable) {
            ifNotApplicable = consequence
        }

        fun build(): Action<C> = Action(
            name, category, description,
            shortcut, icon,
            applicability, ifNotApplicable, execute
        )
    }

    class Collector<C> {
        private val actions = mutableListOf<Action<C>>()

        var category: Category = Category.Unknown

        fun add(action: Action<C>) {
            actions.add(action)
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

        override fun isApplicable(): ReactiveBoolean = wrapped.isApplicable(context)

        override fun execute(ev: Event?) {
            wrapped.execute(context, ev)
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