package xenakis.model

import bundles.PublicProperty
import bundles.publicProperty
import reaktive.value.now

class NamingManager {
    private val objectsByName = mutableMapOf<String, ScoreObject>()

    fun isNameAvailable(name: String) = name !in objectsByName

    fun getObject(name: String): ScoreObject = objectsByName[name] ?: error("no object '$name'")

    fun addedObject(obj: ScoreObject) {
        objectsByName[obj.name.now] = obj
    }

    fun removedObject(obj: ScoreObject) {
        objectsByName.remove(obj.name.now) ?: error("No object with name '${obj.name}' exists")
    }

    fun renamedObject(obj: ScoreObject, oldName: String, newName: String) {
        objectsByName.remove(oldName)
        objectsByName[newName] = obj
    }

    fun availableName(prefix: String): String {
        for (n in 1..Int.MAX_VALUE) {
            val name = "${prefix}_$n"
            if (isNameAvailable(name)) return name
        }
        throw AssertionError()
    }

    fun nameForCopy(obj: ScoreObject): String = availableName("${obj.name}_copy")

    fun nameForClone(obj: ScoreObject): String = availableName("${obj.name}_clone")

    fun isNameTaken(name: String): Boolean = name in objectsByName

    companion object : PublicProperty<NamingManager> by publicProperty("NamingManager")
}