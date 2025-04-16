package xenakis.model.obj

import javafx.scene.input.DataFormat
import xenakis.impl.Decimal

sealed class BufferObject : AbstractSuperColliderObject() {
    abstract fun channels(): Int

    abstract fun frames(): Int

    abstract fun duration(): Decimal

    companion object {
        val DATA_FORMAT = DataFormat("buffer")
    }
}