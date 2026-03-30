package ponticello.model.ctx

import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.materialdesign2.MaterialDesignA
import reaktive.value.ReactiveString

abstract class BoundVariable {
    abstract val origin: Any

    abstract val name: ReactiveString

    abstract val info: ReactiveString

    open val priority: Int get() = 0

    open val icon: Ikon get() = MaterialDesignA.ALPHA_V_CIRCLE

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BoundVariable

        return origin == other.origin
    }

    override fun hashCode(): Int {
        return origin.hashCode()
    }
}