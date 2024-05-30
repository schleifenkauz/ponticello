package xenakis.model

import bundles.PublicProperty
import bundles.publicProperty
import reaktive.value.now

interface SuffixGenerator {
    fun generateSuffix(obj: ScoreObject): String

    fun getSuffix(obj: ScoreObject): String

    object None : SuffixGenerator {
        override fun generateSuffix(obj: ScoreObject): String = ""
        override fun getSuffix(obj: ScoreObject): String = ""
    }

    class CountingSuffixGenerator : SuffixGenerator {
        private var id = 0
        private val map = mutableMapOf<ScoreObject, Int>()

        override fun generateSuffix(obj: ScoreObject): String {
            map[obj] = id
            val suffix = "_${id++}"
            return suffix
        }

        override fun getSuffix(obj: ScoreObject): String {
            val id = map[obj] ?: error("no suffix found for ${obj.name.now}")
            return "_$id"
        }
    }

    companion object : PublicProperty<SuffixGenerator> by publicProperty("SuffixGenerator")
}