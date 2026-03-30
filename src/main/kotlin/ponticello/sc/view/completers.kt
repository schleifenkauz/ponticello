package ponticello.sc.view

import bundles.PublicProperty
import hextant.completion.Completion
import hextant.completion.CompletionStrategy
import hextant.completion.ConfiguredCompleter
import hextant.core.editor.Expander
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.materialdesign2.MaterialDesignC
import org.kordamp.ikonli.materialdesign2.MaterialDesignL
import ponticello.model.code.GlobalPatternObject
import ponticello.model.code.GlobalPatternRegistry
import ponticello.model.ctx.Scope
import ponticello.model.instr.BusObject
import ponticello.model.obj.NamedObject
import ponticello.model.registry.NamedObjectList
import ponticello.model.registry.ScoreObjectRegistry
import ponticello.model.score.ScoreObject
import ponticello.model.server.BufferObject
import ponticello.model.server.BufferRegistry
import ponticello.model.server.BusRegistry
import reaktive.value.now

object BoundVariableCompleter : ConfiguredCompleter<Expander<*, *>, Scope.BoundVariable>(CompletionStrategy.simple) {
    override fun completionPool(context: Expander<*, *>): Collection<Scope.BoundVariable> =
        context.context[Scope].boundVariables()

    override fun extractText(context: Expander<*, *>, item: Scope.BoundVariable): String = item.name.now

    override fun Completion.Builder<Scope.BoundVariable>.configure(context: Expander<*, *>) {
        infoText = completion.info.now
        icon = completion.icon
    }
}

abstract class RegistryCompleter<O : NamedObject>(
    private val registryProperty: PublicProperty<out NamedObjectList<O>>
) : ConfiguredCompleter<Expander<*, *>, O>(CompletionStrategy.simple) {
    override fun completionPool(context: Expander<*, *>): Collection<O> = context.context[registryProperty]

    override fun extractText(context: Expander<*, *>, item: O): String = item.name.now

    protected abstract fun infoText(item: O): String

    protected open fun icon(item: O): Ikon? = null

    override fun Completion.Builder<O>.configure(context: Expander<*, *>) {
        infoText = infoText(completion)
        icon = icon(completion)
    }
}

object BusReferenceCompleter : RegistryCompleter<BusObject>(BusRegistry) {
    override fun infoText(item: BusObject): String = item.rate.toString()

    override fun icon(item: BusObject): Ikon = Material2AL.GRAPHIC_EQ
}

object BufferReferenceCompleter : RegistryCompleter<BufferObject>(BufferRegistry) {
    override fun infoText(item: BufferObject): String = "buf [${item.channels()}]"

    override fun icon(item: BufferObject): Ikon = Material2AL.LIBRARY_MUSIC
}

object ScoreObjectReferenceCompleter : RegistryCompleter<ScoreObject>(ScoreObjectRegistry) {
    override fun infoText(item: ScoreObject): String = item.type

    override fun icon(item: ScoreObject): Ikon = MaterialDesignC.CHART_TIMELINE
}

object GlobalPatternReferenceCompleter : RegistryCompleter<GlobalPatternObject>(GlobalPatternRegistry) {
    override fun infoText(item: GlobalPatternObject): String = "pattern"

    override fun icon(item: GlobalPatternObject): Ikon = MaterialDesignL.LARAVEL
}

