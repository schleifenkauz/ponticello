package xenakis.model.flow

data class NodePlacement(
    val addAction: AddAction,
    val target: String
) {
    enum class AddAction {
        AddAfter, AddBefore, AddToTail, AddToHead;

        override fun toString() = "'${name.first().lowercase()}${name.drop(1)}'"
    }

}