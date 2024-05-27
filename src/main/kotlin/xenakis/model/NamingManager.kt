package xenakis.model

import bundles.PublicProperty
import bundles.publicProperty

class NamingManager {
    private val objectsByName = mutableMapOf<String, ScoreObject>()

    fun isNameAvailable(name: String) = name !in objectsByName

    fun getObject(name: String): ScoreObject = objectsByName[name] ?: error("no object '$name'")

    fun addedObject(obj: ScoreObject) {
        objectsByName[obj.name] = obj
    }

    fun removedObject(obj: ScoreObject) {
        objectsByName.remove(obj.name) ?: error("No object with name '${obj.name}' exists")
    }

    fun renamedObject(obj: ScoreObject, oldName: String, newName: String) {
        objectsByName.remove(oldName)
        objectsByName[newName] = obj
    }

    fun nameForCopy(obj: ScoreObject): String {
        for (n in 1..Int.MAX_VALUE) {
            val name = "${obj.name}_copy$n"
            if (isNameAvailable(name)) return name
        }
        throw AssertionError()
    }

    fun nameForClone(obj: ScoreObject): String {
        for (n in 1..Int.MAX_VALUE) {
            val name = "${obj.name}_clone$n"
            if (isNameAvailable(name)) return name
        }
        throw AssertionError()
    }

    fun isNameTaken(name: String): Boolean = name in objectsByName

    companion object : PublicProperty<NamingManager> by publicProperty("NamingManager")
}