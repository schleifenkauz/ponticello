package xenakis.model

import bundles.PublicProperty
import bundles.publicProperty
import reaktive.value.now

class ScoreObjectRegistry : ObjectRegistry<ScoreObject>() {
    override val objects: MutableList<ScoreObject> = mutableListOf()

    override val objectType: String
        get() = "Score object"

    override fun getDefault(): ScoreObject = error("No default score object")

    override fun onAdded(obj: ScoreObject, idx: Int) {}

    fun availableName(prefix: String): String {
        for (n in 1..Int.MAX_VALUE) {
            val name = "${prefix}$n"
            if (!has(name)) return name
        }
        throw AssertionError()
    }

    fun nameForCopy(obj: ScoreObject): String {
        return appendSuffix(obj, "_copy")
    }

    fun nameForClone(obj: ScoreObject): String = appendSuffix(obj, "_clone")

    private fun appendSuffix(obj: ScoreObject, suffix: String): String {
        val name = obj.name.now
        val withoutDigits = name.dropLastWhile { it.isDigit() }
        val prefix = if (withoutDigits.endsWith(suffix)) withoutDigits else "$name$suffix"
        return availableName(prefix)
    }

    companion object : PublicProperty<ScoreObjectRegistry> by publicProperty("ScoreObjectRegistry")
}