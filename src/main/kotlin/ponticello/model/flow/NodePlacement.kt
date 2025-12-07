package ponticello.model.flow

data class NodePlacement(
    val addAction: AddAction,
    val target: String
) {
    val code get() = "addAction: $addAction, target: $target"

    val moveMethod get() = when (addAction) {
        AddAction.AddAfter -> "moveAfter"
        AddAction.AddBefore -> "moveBefore"
        AddAction.AddToTail -> "moveToTail"
        AddAction.AddToHead -> "moveToHead"
        AddAction.AddReplace -> throw AssertionError("AddReplace is not a valid move method")
    }

    override fun toString(): String = "($code)"

    enum class AddAction {
        AddAfter, AddBefore, AddToTail, AddToHead, AddReplace;

        override fun toString() = "'${name.first().lowercase()}${name.drop(1)}'"
    }

    companion object {
        fun replace(target: String) = NodePlacement(AddAction.AddReplace, target)

        fun after(target: String) = NodePlacement(AddAction.AddAfter, target)

        fun tail(target: String) = NodePlacement(AddAction.AddToTail, target)

        fun head(target: String) = NodePlacement(AddAction.AddToHead, target)

        fun before(target: String) = NodePlacement(AddAction.AddBefore, target)
    }
}