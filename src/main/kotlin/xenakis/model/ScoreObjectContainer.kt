package xenakis.model

abstract class ScoreObjectContainer {
    abstract fun removeObject(obj: ScoreObject)
    abstract fun renamedObject(obj: ScoreObject, oldName: String, newName: String)
    abstract fun getObject(name: String): ScoreObject
}