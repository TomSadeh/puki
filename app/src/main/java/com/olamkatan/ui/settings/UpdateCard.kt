package com.olamkatan.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.olamkatan.data.models.BilingualText
import com.olamkatan.theme.CloudWhite
import com.olamkatan.theme.EarthBrown
import com.olamkatan.theme.GrassGreen
import com.olamkatan.ui.common.LocalLanguage
import com.olamkatan.update.Updater
import kotlinx.coroutines.launch

/** "Version x · up to date", or "Update to y" with a download bar, in the parent settings. */
@Composable
fun UpdateCard() {
    val ctx = LocalContext.current
    val language = LocalLanguage.current
    val scope = rememberCoroutineScope()
    val current = remember { Updater.currentVersion(ctx) }
    val available by Updater.available.collectAsState()
    val installMessage by Updater.installMessage.collectAsState()
    var checking by remember { mutableStateOf(true) }
    var offline by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        offline = runCatching { Updater.check(ctx) }.isFailure
        checking = false
    }

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(CloudWhite)
            .padding(24.dp)
            .width(400.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val update = available
        val line = when {
            checking -> BilingualText("גרסה $current · בודק אם יש עדכון…", "Version $current · checking for updates…")
            update != null -> BilingualText("יש גרסה חדשה: ${update.version} (עכשיו $current)", "New version: ${update.version} (now $current)")
            offline -> BilingualText("גרסה $current · אין חיבור לבדוק עדכונים", "Version $current · can't check for updates (offline)")
            else -> BilingualText("גרסה $current · הכי חדשה", "Version $current · up to date")
        }
        Text(line.resolve(language), style = MaterialTheme.typography.titleMedium, color = EarthBrown)

        if (update != null) {
            val p = progress
            if (p != null) {
                LinearProgressIndicator(progress = p, modifier = Modifier.fillMaxWidth(), color = GrassGreen)
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(GrassGreen)
                        .clickable {
                            problem = null
                            if (!Updater.mayInstall(ctx)) {
                                // Once: Android asks the parent to let Puki install its own updates
                                Updater.askToAllowInstalls(ctx)
                                return@clickable
                            }
                            scope.launch {
                                progress = 0f
                                try {
                                    val apk = Updater.download(ctx, update) { progress = it }
                                    Updater.install(ctx, apk)
                                } catch (e: Exception) {
                                    problem = e.message
                                }
                                progress = null
                            }
                        }
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        BilingualText("עדכון ל-${update.version}", "Update to ${update.version}").resolve(language),
                        style = MaterialTheme.typography.titleMedium,
                        color = CloudWhite
                    )
                }
                if (!Updater.mayInstall(ctx)) {
                    Text(
                        BilingualText(
                            "בפעם הראשונה אנדרואיד ישאל אם לאפשר לפוקי להתקין עדכונים. אחרי שמאשרים, חוזרים ולוחצים שוב.",
                            "The first time, Android asks whether Puki may install updates. Allow it, come back and tap again."
                        ).resolve(language),
                        style = MaterialTheme.typography.bodyMedium,
                        color = EarthBrown
                    )
                }
            }
        }
        val failed = problem ?: installMessage?.takeIf { it != "ok" }
        if (failed != null) {
            Text(
                BilingualText("העדכון לא הצליח: $failed", "The update didn't work: $failed").resolve(language),
                style = MaterialTheme.typography.bodyMedium,
                color = EarthBrown
            )
        }
    }
}
