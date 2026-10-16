package io.github.tomsadeh.puki.data.content

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import io.github.tomsadeh.puki.R
import io.github.tomsadeh.puki.data.models.BilingualText

data class AnimalDef(
    val id: String,
    val name: BilingualText,
    val sound: BilingualText,
    val emoji: String,
    val color: Color,
    val foodId: String,
    @DrawableRes val drawableRes: Int = 0
)

data class FoodDef(
    val id: String,
    val name: BilingualText,
    val emoji: String,
    val color: Color,
    @DrawableRes val drawableRes: Int = 0
)

val level1Animals = listOf(
    AnimalDef(
        id = "cow",
        name = BilingualText("פרה", "Cow"),
        sound = BilingualText("מוּ!", "Moo!"),
        emoji = "\uD83D\uDC04",
        color = Color(0xFFF5F5DC),
        foodId = "grass",
        drawableRes = R.drawable.ic_animal_cow
    ),
    AnimalDef(
        id = "dog",
        name = BilingualText("כלב", "Dog"),
        sound = BilingualText("הב הב!", "Woof!"),
        emoji = "\uD83D\uDC36",
        color = Color(0xFFD2B48C),
        foodId = "bone",
        drawableRes = R.drawable.ic_animal_dog
    ),
    AnimalDef(
        id = "cat",
        name = BilingualText("חתול", "Cat"),
        sound = BilingualText("מיאו!", "Meow!"),
        emoji = "\uD83D\uDC31",
        color = Color(0xFFFFB347),
        foodId = "fish",
        drawableRes = R.drawable.ic_animal_cat
    ),
    AnimalDef(
        id = "chicken",
        name = BilingualText("תרנגולת", "Chicken"),
        sound = BilingualText("קו קו קו!", "Cluck cluck!"),
        emoji = "\uD83D\uDC14",
        color = Color(0xFFFFF8DC),
        foodId = "seeds",
        drawableRes = R.drawable.ic_animal_chicken
    )
)

val level2Animals = listOf(
    AnimalDef("horse", BilingualText("סוס", "Horse"), BilingualText("איהההה!", "Neigh!"), "\uD83D\uDC34", Color(0xFF8D6E63), "hay", R.drawable.ic_animal_horse),
    AnimalDef("pig", BilingualText("חזיר", "Pig"), BilingualText("חרר!", "Oink!"), "\uD83D\uDC37", Color(0xFFFFCDD2), "corn", R.drawable.ic_animal_pig),
    AnimalDef("sheep", BilingualText("כבשה", "Sheep"), BilingualText("מעע!", "Baa!"), "\uD83D\uDC11", Color(0xFFF5F5F5), "clover", R.drawable.ic_animal_sheep),
    AnimalDef("duck", BilingualText("ברווז", "Duck"), BilingualText("גע גע!", "Quack!"), "\uD83E\uDD86", Color(0xFFFFF9C4), "bread", R.drawable.ic_animal_duck),
    AnimalDef("rabbit", BilingualText("ארנב", "Rabbit"), BilingualText("...", "..."), "\uD83D\uDC30", Color(0xFFE0E0E0), "carrot", R.drawable.ic_animal_rabbit),
    AnimalDef("goat", BilingualText("עז", "Goat"), BilingualText("מעע!", "Maa!"), "\uD83D\uDC10", Color(0xFFBCAAA4), "leaves", R.drawable.ic_animal_goat)
)

val level3Animals = listOf(
    AnimalDef("lion", BilingualText("אריה", "Lion"), BilingualText("שאגה!", "Roar!"), "\uD83E\uDD81", Color(0xFFFFB74D), "meat", R.drawable.ic_animal_lion),
    AnimalDef("elephant", BilingualText("פיל", "Elephant"), BilingualText("טרררר!", "Trumpet!"), "\uD83D\uDC18", Color(0xFFBDBDBD), "peanuts", R.drawable.ic_animal_elephant),
    AnimalDef("monkey", BilingualText("קוף", "Monkey"), BilingualText("או או!", "Ooh ooh!"), "\uD83D\uDC35", Color(0xFF8D6E63), "banana", R.drawable.ic_animal_monkey),
    AnimalDef("giraffe", BilingualText("ג׳ירפה", "Giraffe"), BilingualText("...", "..."), "\uD83E\uDD92", Color(0xFFFFE082), "leaves", R.drawable.ic_animal_giraffe),
    AnimalDef("penguin", BilingualText("פינגווין", "Penguin"), BilingualText("...", "..."), "\uD83D\uDC27", Color(0xFF263238), "fish", R.drawable.ic_animal_penguin)
)

val level1Foods = listOf(
    FoodDef(
        id = "grass",
        name = BilingualText("עשב", "Grass"),
        emoji = "\uD83C\uDF3F",
        color = Color(0xFF4CAF50),
        drawableRes = R.drawable.ic_food_grass
    ),
    FoodDef(
        id = "bone",
        name = BilingualText("עצם", "Bone"),
        emoji = "\uD83E\uDDB4",
        color = Color(0xFFF5F5DC),
        drawableRes = R.drawable.ic_food_bone
    ),
    FoodDef(
        id = "fish",
        name = BilingualText("דג", "Fish"),
        emoji = "\uD83D\uDC1F",
        color = Color(0xFF64B5F6),
        drawableRes = R.drawable.ic_food_fish
    ),
    FoodDef(
        id = "seeds",
        name = BilingualText("גרעינים", "Seeds"),
        emoji = "\uD83C\uDF3E",
        color = Color(0xFFFFD54F),
        drawableRes = R.drawable.ic_food_seeds
    )
)

val level2Foods = level1Foods + listOf(
    FoodDef("hay", BilingualText("חציר", "Hay"), "\uD83C\uDF3E", Color(0xFFFFE082), R.drawable.ic_food_hay),
    FoodDef("corn", BilingualText("תירס", "Corn"), "\uD83C\uDF3D", Color(0xFFFFF176), R.drawable.ic_food_corn),
    FoodDef("clover", BilingualText("תלתן", "Clover"), "☘\uFE0F", Color(0xFF66BB6A), R.drawable.ic_food_clover),
    FoodDef("bread", BilingualText("לחם", "Bread"), "\uD83C\uDF5E", Color(0xFFD7CCC8), R.drawable.ic_food_bread),
    FoodDef("carrot", BilingualText("גזר", "Carrot"), "\uD83E\uDD55", Color(0xFFFF7043), R.drawable.ic_food_carrot),
    FoodDef("leaves", BilingualText("עלים", "Leaves"), "\uD83C\uDF43", Color(0xFF81C784), R.drawable.ic_food_leaves)
)

val level3Foods = level2Foods + listOf(
    FoodDef("meat", BilingualText("בשר", "Meat"), "\uD83E\uDD69", Color(0xFFEF5350), R.drawable.ic_food_meat),
    FoodDef("peanuts", BilingualText("בוטנים", "Peanuts"), "\uD83E\uDD5C", Color(0xFFBCAAA4), R.drawable.ic_food_peanuts),
    FoodDef("banana", BilingualText("בננה", "Banana"), "\uD83C\uDF4C", Color(0xFFFFEE58), R.drawable.ic_food_banana)
)


fun foodsForLevel(level: Int): List<FoodDef> = when {
    level >= 3 -> level3Foods
    level >= 2 -> level2Foods
    else -> level1Foods
}
