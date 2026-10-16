package io.github.tomsadeh.puki.data.content

import androidx.annotation.DrawableRes
import io.github.tomsadeh.puki.R
import io.github.tomsadeh.puki.data.models.BilingualText

/**
 * A word for Say it with Puki. Puki says it (voice_say_<id>), the child says it back.
 * [alsoCounts] are related things a toddler might say instead, like the animal's sound;
 * they count as a good try (Puki then says the word itself).
 */
data class SayWord(
    val id: String,
    val word: BilingualText,
    @DrawableRes val drawableRes: Int,
    val alsoCountsHe: List<String> = emptyList(),
    val alsoCountsEn: List<String> = emptyList()
)

/** Early words: things a two-year-old sees every day, most of them short. */
val sayWords = listOf(
    SayWord("dog", BilingualText("כלב", "Dog"), R.drawable.ic_animal_dog, listOf("הב", "כלבלב"), listOf("woof", "doggy", "puppy")),
    SayWord("cat", BilingualText("חתול", "Cat"), R.drawable.ic_animal_cat, listOf("מיאו", "חתולה", "חתלתול"), listOf("meow", "kitty", "kitten")),
    SayWord("cow", BilingualText("פרה", "Cow"), R.drawable.ic_animal_cow, listOf("מו", "פה"), listOf("moo")),
    SayWord("duck", BilingualText("ברווז", "Duck"), R.drawable.ic_animal_duck, listOf("גע", "ברוז"), listOf("quack", "ducky")),
    SayWord("horse", BilingualText("סוס", "Horse"), R.drawable.ic_animal_horse, listOf("סו"), listOf("neigh", "horsey")),
    SayWord("sheep", BilingualText("כבשה", "Sheep"), R.drawable.ic_animal_sheep, listOf("מה"), listOf("baa")),
    SayWord("elephant", BilingualText("פיל", "Elephant"), R.drawable.ic_animal_elephant, listOf("פי"), listOf("ellie")),
    SayWord("monkey", BilingualText("קוף", "Monkey"), R.drawable.ic_animal_monkey, listOf("קו"), listOf("monk")),
    SayWord("lion", BilingualText("אריה", "Lion"), R.drawable.ic_animal_lion, listOf("ראו"), listOf("roar")),
    SayWord("fish", BilingualText("דג", "Fish"), R.drawable.ic_food_fish),
    SayWord("banana", BilingualText("בננה", "Banana"), R.drawable.ic_food_banana, listOf("ננה", "בנה"), listOf("nana")),
    SayWord("bread", BilingualText("לחם", "Bread"), R.drawable.ic_food_bread, listOf("חם"), listOf()),
    SayWord("carrot", BilingualText("גזר", "Carrot"), R.drawable.ic_food_carrot, listOf("גזל"), listOf()),
    SayWord("apple", BilingualText("תפוח", "Apple"), R.drawable.ic_word_apple, listOf("פוח"), listOf()),
    SayWord("ball", BilingualText("כדור", "Ball"), R.drawable.ic_tidy_ball, listOf("כדו"), listOf("bah")),
    SayWord("book", BilingualText("ספר", "Book"), R.drawable.ic_tidy_book),
    SayWord("teddy", BilingualText("דובי", "Teddy"), R.drawable.ic_tidy_teddy, listOf("דוב", "דובון"), listOf("bear", "teddy bear")),
    SayWord("cup", BilingualText("כוס", "Cup"), R.drawable.ic_table_cup),
    SayWord("shoes", BilingualText("נעליים", "Shoes"), R.drawable.ic_clothing_shoes, listOf("נעל"), listOf("shoe")),
    SayWord("moon", BilingualText("ירח", "Moon"), R.drawable.ic_bedtime_moon),
    SayWord("house", BilingualText("בית", "House"), R.drawable.ic_word_house),
    SayWord("drum", BilingualText("תוף", "Drum"), R.drawable.ic_instrument_drum),
)
