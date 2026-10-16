package io.github.tomsadeh.puki.data.models

/**
 * Text in both languages. [he] is the default Hebrew form; lines that address
 * the child directly can add [heGirl] for the feminine form.
 */
data class BilingualText(
    val he: String,
    val en: String,
    val heGirl: String? = null
) {
    fun resolve(language: Language, gender: Gender = Gender.BOY): String = when (language) {
        Language.HEBREW -> if (gender == Gender.GIRL && heGirl != null) heGirl else he
        Language.ENGLISH -> en
    }
}
