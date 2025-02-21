package xenakis.ui

import org.kordamp.ikonli.Ikon

class MaterialIcon(private val code: String, private val description: String): Ikon {
    override fun getDescription(): String = description

    override fun getCode(): Int = Integer.parseInt(code, 16)

    companion object {
        val AUDIO_FILE = MaterialIcon("eb82", "audio_file")
        val LIBRARY_MUSIC = MaterialIcon("e030", "library_music")
        val SAVE = MaterialIcon("e161", "save")
        val OPEN_IN_NEW = MaterialIcon("e89e", "open_in_new")

        val SEARCH = MaterialIcon("e8b6", "search")
        val QUESTION_MARK = MaterialIcon("eb8b", "question_mark")
        val MORE_TIME = MaterialIcon("ea5d", "more_time")
        val OPEN_FILE = MaterialIcon("eaf3", "open_file")
    }
}