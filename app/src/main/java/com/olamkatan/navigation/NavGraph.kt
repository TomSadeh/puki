package com.olamkatan.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.olamkatan.ui.hub.HubScreen
import com.olamkatan.ui.animals.AnimalGardenScreen
import com.olamkatan.ui.colors.ColorSplashScreen
import com.olamkatan.ui.shapes.ShapePlaygroundScreen
import com.olamkatan.ui.counting.CountingGardenScreen
import com.olamkatan.ui.music.MusicMeadowScreen
import com.olamkatan.ui.words.WordGardenScreen
import com.olamkatan.ui.say.SayItScreen
import com.olamkatan.ui.myday.MyDayScreen
import com.olamkatan.ui.myday.bath.BathTimeScreen
import com.olamkatan.ui.myday.brush.BrushyBrushyScreen
import com.olamkatan.ui.myday.tidy.TidyUpScreen
import com.olamkatan.ui.myday.dress.GettingDressedScreen
import com.olamkatan.ui.myday.table.TableTimeScreen
import com.olamkatan.ui.myday.bedtime.BedtimeRoutineScreen
import com.olamkatan.ui.canvas.FreeCanvasScreen
import com.olamkatan.ui.stickers.StickerAlbumScreen
import com.olamkatan.ui.settings.SettingsScreen

object Routes {
    const val HUB = "hub"
    const val ANIMALS = "animals"
    const val COLORS = "colors"
    const val SHAPES = "shapes"
    const val COUNTING = "counting"
    const val MUSIC = "music"
    const val WORDS = "words"
    const val SAY = "say"
    const val MY_DAY = "myday"
    const val MY_DAY_BATH = "myday/bath"
    const val MY_DAY_BRUSH = "myday/brush"
    const val MY_DAY_TIDY = "myday/tidy"
    const val MY_DAY_DRESS = "myday/dress"
    const val MY_DAY_TABLE = "myday/table"
    const val MY_DAY_BEDTIME = "myday/bedtime"
    const val CANVAS = "canvas"
    const val STICKERS = "stickers"
    const val SETTINGS = "settings"
}

@Composable
fun OlamKatanNavGraph(navController: NavHostController) {
    // Safe back navigation — prevents double-tap from popping past the hub
    val safeBack: () -> Unit = {
        if (navController.currentBackStackEntry?.destination?.route != Routes.HUB) {
            navController.popBackStack()
        }
    }

    // Safe navigate — prevents duplicate destinations from rapid tapping
    val safeNavigate: (String) -> Unit = { route ->
        if (navController.currentBackStackEntry?.destination?.route != route) {
            navController.navigate(route) {
                launchSingleTop = true
            }
        }
    }

    // "Continue" on a game's end card: the same game again, as a fresh screen (so a fresh game)
    val playAgain: (String) -> () -> Unit = { route ->
        {
            if (navController.currentBackStackEntry?.destination?.route == route) {
                navController.navigate(route) { popUpTo(route) { inclusive = true } }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.HUB
    ) {
        composable(Routes.HUB) {
            HubScreen(
                onIslandClick = safeNavigate,
                onSettingsClick = { safeNavigate(Routes.SETTINGS) },
                onStickersClick = { safeNavigate(Routes.STICKERS) }
            )
        }
        composable(Routes.ANIMALS) {
            AnimalGardenScreen(onBack = safeBack, onPlayAgain = playAgain(Routes.ANIMALS))
        }
        composable(Routes.COLORS) {
            ColorSplashScreen(onBack = safeBack, onPlayAgain = playAgain(Routes.COLORS))
        }
        composable(Routes.SHAPES) {
            ShapePlaygroundScreen(onBack = safeBack, onPlayAgain = playAgain(Routes.SHAPES))
        }
        composable(Routes.COUNTING) {
            CountingGardenScreen(onBack = safeBack, onPlayAgain = playAgain(Routes.COUNTING))
        }
        composable(Routes.MUSIC) {
            MusicMeadowScreen(onBack = safeBack, onPlayAgain = playAgain(Routes.MUSIC))
        }
        composable(Routes.WORDS) {
            WordGardenScreen(onBack = safeBack, onPlayAgain = playAgain(Routes.WORDS))
        }
        composable(Routes.SAY) {
            SayItScreen(onBack = safeBack, onPlayAgain = playAgain(Routes.SAY))
        }
        composable(Routes.MY_DAY) {
            MyDayScreen(
                onRoomClick = safeNavigate,
                onBack = safeBack
            )
        }
        composable(Routes.MY_DAY_BATH) {
            BathTimeScreen(onBack = safeBack, onPlayAgain = playAgain(Routes.MY_DAY_BATH))
        }
        composable(Routes.MY_DAY_BRUSH) {
            BrushyBrushyScreen(onBack = safeBack, onPlayAgain = playAgain(Routes.MY_DAY_BRUSH))
        }
        composable(Routes.MY_DAY_TIDY) {
            TidyUpScreen(onBack = safeBack, onPlayAgain = playAgain(Routes.MY_DAY_TIDY))
        }
        composable(Routes.MY_DAY_DRESS) {
            GettingDressedScreen(onBack = safeBack, onPlayAgain = playAgain(Routes.MY_DAY_DRESS))
        }
        composable(Routes.MY_DAY_TABLE) {
            TableTimeScreen(onBack = safeBack, onPlayAgain = playAgain(Routes.MY_DAY_TABLE))
        }
        composable(Routes.MY_DAY_BEDTIME) {
            BedtimeRoutineScreen(onBack = safeBack)
        }
        composable(Routes.CANVAS) {
            FreeCanvasScreen(onBack = safeBack)
        }
        composable(Routes.STICKERS) {
            StickerAlbumScreen(onBack = safeBack)
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = safeBack)
        }
    }
}
