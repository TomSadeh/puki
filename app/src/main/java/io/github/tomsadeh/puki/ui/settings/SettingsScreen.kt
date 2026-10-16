package io.github.tomsadeh.puki.ui.settings

import kotlin.math.roundToInt
import androidx.compose.material3.Slider
import io.github.tomsadeh.puki.audio.Sfx
import io.github.tomsadeh.puki.ui.common.LocalSfxPlayer
import io.github.tomsadeh.puki.ui.common.LocalVoicePlayer
import io.github.tomsadeh.puki.ui.common.LocalMusicPlayer
import io.github.tomsadeh.puki.ui.common.LocalCompanion
import io.github.tomsadeh.puki.audio.Connection
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.input.KeyboardCapitalization
import io.github.tomsadeh.puki.ui.components.BackArrowIcon
import androidx.compose.ui.zIndex
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tomsadeh.puki.R
import io.github.tomsadeh.puki.BuildConfig
import io.github.tomsadeh.puki.data.models.BilingualText
import io.github.tomsadeh.puki.data.models.Gender
import io.github.tomsadeh.puki.data.models.Language
import io.github.tomsadeh.puki.theme.CloudWhite
import io.github.tomsadeh.puki.theme.EarthBrown
import io.github.tomsadeh.puki.theme.GrassGreen
import io.github.tomsadeh.puki.theme.SkyBlue
import io.github.tomsadeh.puki.theme.SunYellow
import io.github.tomsadeh.puki.ui.common.LocalAppSettings
import io.github.tomsadeh.puki.ui.common.LocalLanguage

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val language = LocalLanguage.current
    val settings = LocalAppSettings.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SkyBlue)
    ) {
        // Back to the hub
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(24.dp)
                .size(64.dp)
                .clip(CircleShape)
                .background(EarthBrown.copy(alpha = 0.85f))
                .clickable { onBack() }
                .zIndex(1f),
            contentAlignment = Alignment.Center
        ) {
            BackArrowIcon()
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = BilingualText("הגדרות", "Settings").resolve(language),
                style = MaterialTheme.typography.headlineLarge,
                color = CloudWhite
            )

            Spacer(modifier = Modifier.height(32.dp))

            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(CloudWhite)
                    .padding(24.dp)
                    .width(400.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SettingsToggleRow(
                    label = BilingualText("שפה", "Language").resolve(language),
                    icon = R.drawable.ic_ui_globe,
                    checked = language == Language.HEBREW,
                    labelOn = "עברית",
                    labelOff = "English",
                    onToggle = { settingsViewModel.toggleLanguage(language) }
                )

                SettingsToggleRow(
                    label = BilingualText("ילד/ה", "Boy/Girl").resolve(language),
                    icon = R.drawable.ic_ui_child,
                    checked = settings.gender == Gender.GIRL,
                    labelOn = BilingualText("ילדה", "Girl").resolve(language),
                    labelOff = BilingualText("ילד", "Boy").resolve(language),
                    labelOnIcon = R.drawable.ic_ui_girl,
                    labelOffIcon = R.drawable.ic_ui_boy,
                    onToggle = { settingsViewModel.toggleGender(settings.gender) }
                )

                // The child's name: the parrot and the personal voice lines use it
                var childName by remember(settings.childName) { mutableStateOf(settings.childName) }
                OutlinedTextField(
                    value = childName,
                    onValueChange = { childName = it.take(30) },
                    label = { Text(BilingualText("שם הילד/ה", "Child's name").resolve(language)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { settingsViewModel.setChildName(childName) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { if (!it.isFocused) settingsViewModel.setChildName(childName) }
                )

                SettingsToggleRow(
                    label = BilingualText("צלילים", "Sounds").resolve(language),
                    icon = R.drawable.ic_ui_speaker,
                    checked = settings.soundEnabled,
                    labelOn = BilingualText("פועל", "On").resolve(language),
                    labelOff = BilingualText("כבוי", "Off").resolve(language),
                    onToggle = { settingsViewModel.toggleSound(settings.soundEnabled) }
                )

                SettingsToggleRow(
                    label = BilingualText("מוזיקה", "Music").resolve(language),
                    icon = R.drawable.ic_ui_music_note,
                    checked = settings.musicEnabled,
                    labelOn = BilingualText("פועל", "On").resolve(language),
                    labelOff = BilingualText("כבוי", "Off").resolve(language),
                    onToggle = { settingsViewModel.toggleMusic(settings.musicEnabled) }
                )

                // Volumes: they apply while dragging, and are saved when the finger lifts
                val musicPlayer = LocalMusicPlayer.current
                val voicePlayer = LocalVoicePlayer.current
                val sfxPlayer = LocalSfxPlayer.current
                var musicLevel by remember(settings.musicVolume) { mutableStateOf(settings.musicVolume) }
                var voiceLevel by remember(settings.voiceVolume) { mutableStateOf(settings.voiceVolume) }
                var sfxLevel by remember(settings.sfxVolume) { mutableStateOf(settings.sfxVolume) }
                VolumeRow(
                    label = BilingualText("עוצמת מוזיקה", "Music volume").resolve(language),
                    icon = R.drawable.ic_ui_music_note,
                    value = musicLevel,
                    onChange = { musicLevel = it; musicPlayer.level = it },
                    onDone = { settingsViewModel.setVolumes(music = musicLevel) }
                )
                VolumeRow(
                    label = BilingualText("עוצמת קולות ופוקי", "Voices and Puki").resolve(language),
                    icon = R.drawable.ic_companion_parrot,
                    value = voiceLevel,
                    onChange = { voiceLevel = it; voicePlayer.level = it },
                    onDone = {
                        settingsViewModel.setVolumes(voice = voiceLevel)
                        voicePlayer.playPraise(language)  // a sample at the new volume
                    }
                )
                VolumeRow(
                    label = BilingualText("עוצמת צלילים", "Sound effects").resolve(language),
                    icon = R.drawable.ic_ui_speaker,
                    value = sfxLevel,
                    onChange = { sfxLevel = it; sfxPlayer.level = it },
                    onDone = {
                        settingsViewModel.setVolumes(sfx = sfxLevel)
                        sfxPlayer.play(Sfx.POP)
                    }
                )

                SettingsToggleRow(
                    label = BilingualText("התוכי פוקי", "Puki the parrot").resolve(language),
                    icon = R.drawable.ic_companion_parrot,
                    checked = settings.companionEnabled,
                    labelOn = BilingualText("פועל", "On").resolve(language),
                    labelOff = BilingualText("כבוי", "Off").resolve(language),
                    onToggle = { settingsViewModel.toggleCompanion(settings.companionEnabled) }
                )
                if (settings.companionEnabled) {
                    // Scan the QR code on the brain's dashboard instead of typing address and code
                    var scanFailed by remember { mutableStateOf(false) }
                    val scan = rememberPairingScanner(
                        BilingualText("כוונו אל קוד ה-QR שבמחשב", "Point at the QR code on the PC").resolve(language)
                    ) { link ->
                        scanFailed = link == null
                        if (link != null) {
                            settingsViewModel.setCompanionPairing(link)
                        }
                    }
                    OutlinedButton(onClick = scan, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            BilingualText("📷 סריקת קוד ה-QR שבמחשב", "📷 Scan the QR code on the PC").resolve(language),
                            style = MaterialTheme.typography.titleMedium,
                            color = EarthBrown
                        )
                    }
                    if (scanFailed) {
                        Text(
                            BilingualText("זה לא קוד ה-QR של פוקי, נסו שוב", "That isn't Puki's QR code, try again").resolve(language),
                            style = MaterialTheme.typography.bodyLarge,
                            color = EarthBrown
                        )
                    }
                    // Address of the PC running brain/doorbell.py (saved when editing finishes)
                    var address by remember(settings.companionAddress) { mutableStateOf(settings.companionAddress) }
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text(BilingualText("כתובת המחשב", "PC address").resolve(language)) },
                        placeholder = { Text("192.168.1.20:8790") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { settingsViewModel.setCompanionAddress(address) }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { if (!it.isFocused) settingsViewModel.setCompanionAddress(address) }
                    )
                    // Pairing code, shown on the PC when the companion server starts
                    var code by remember(settings.companionCode) { mutableStateOf(settings.companionCode) }
                    OutlinedTextField(
                        value = code,
                        onValueChange = {
                            code = it.uppercase()
                            // Saved (and checked) as soon as the whole code is in
                            if (code.count { c -> c.isLetterOrDigit() } == 8) settingsViewModel.setCompanionCode(code)
                        },
                        label = { Text(BilingualText("קוד צימוד (מופיע במחשב)", "Pairing code (shown on the PC)").resolve(language)) },
                        placeholder = { Text("ABCD-EFGH") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            autoCorrect = false,
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { settingsViewModel.setCompanionCode(code) }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { if (!it.isFocused) settingsViewModel.setCompanionCode(code) }
                    )
                    // Whether the PC answers with this address and code (tap to check again)
                    val companion = LocalCompanion.current
                    var connection by remember { mutableStateOf<Connection?>(null) }
                    var recheck by remember { mutableStateOf(0) }
                    LaunchedEffect(settings.companionAddress, settings.companionCode, settings.companionPin, recheck) {
                        connection = null
                        connection = companion?.checkConnection(settings.companionAddress, settings.companionCode, settings.companionPin)
                            ?: Connection.NOT_FOUND
                    }
                    Text(
                        text = when (connection) {
                            null -> BilingualText("בודק את החיבור…", "Checking the connection…")
                            Connection.OK -> BilingualText("✓ פוקי מחובר", "✓ Puki is connected")
                            Connection.WRONG_CODE -> BilingualText("✗ קוד הצימוד שגוי", "✗ Wrong pairing code")
                            Connection.LOCKED_OUT -> BilingualText(
                                "✗ יותר מדי ניסיונות שגויים, נסו שוב בעוד רבע שעה",
                                "✗ Too many wrong codes, try again in 15 minutes"
                            )
                            Connection.WRONG_PC -> BilingualText(
                                "✗ זה לא המחשב שצומד. סרקו שוב את קוד ה-QR שבמחשב",
                                "✗ That isn't the paired PC. Scan the QR code on the PC again"
                            )
                            Connection.NOT_FOUND -> BilingualText(
                                "✗ המחשב לא נמצא (דלוק? באותה רשת?) · לחצו לבדיקה חוזרת",
                                "✗ PC not found (is it on, on the same Wi-Fi?) · tap to check again"
                            )
                        }.resolve(language),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (connection == Connection.OK) GrassGreen else EarthBrown,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { recheck++ }
                            .padding(vertical = 4.dp)
                    )
                }

                // Time limit row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.ic_ui_clock),
                            contentDescription = null,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = BilingualText("הגבלת זמן", "Time Limit").resolve(language),
                            style = MaterialTheme.typography.titleLarge,
                            color = EarthBrown
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(GrassGreen.copy(alpha = 0.2f))
                            .clickable { settingsViewModel.cycleTimeLimit(settings.timeLimitMinutes) }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = if (settings.timeLimitMinutes == 0)
                                BilingualText("ללא", "Off").resolve(language)
                            else
                                "${settings.timeLimitMinutes} ${BilingualText("דקות", "min").resolve(language)}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = EarthBrown
                        )
                    }
                }
            }

            // Puki's brain on this phone (only offered where it can run)
            val context = androidx.compose.ui.platform.LocalContext.current
            if (remember { io.github.tomsadeh.puki.brain.DeviceModels.capable(context) }) {
                Spacer(modifier = Modifier.height(24.dp))
                DeviceBrainCard(settingsViewModel)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // The Play build gets its updates from Play: just the version, for bug reports
            if (BuildConfig.SELF_UPDATE) UpdateCard()
            else Text(
                text = "Puki ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodyLarge,
                color = EarthBrown
            )

            Spacer(modifier = Modifier.height(32.dp))

            Box(
                modifier = Modifier
                    .size(120.dp, 60.dp)
                    .clip(RoundedCornerShape(30.dp))
                    .background(SunYellow)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = BilingualText("חזרה", "Back").resolve(language),
                    style = MaterialTheme.typography.titleLarge,
                    color = EarthBrown,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(
    label: String,
    @DrawableRes icon: Int,
    checked: Boolean,
    labelOn: String,
    labelOff: String,
    onToggle: () -> Unit,
    @DrawableRes labelOnIcon: Int? = null,
    @DrawableRes labelOffIcon: Int? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge,
                color = EarthBrown
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            val labelIcon = if (checked) labelOnIcon else labelOffIcon
            if (labelIcon != null) {
                Image(
                    painter = painterResource(labelIcon),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = if (checked) labelOn else labelOff,
                style = MaterialTheme.typography.bodyLarge,
                color = EarthBrown
            )
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = checked,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = CloudWhite,
                    checkedTrackColor = GrassGreen
                )
            )
        }
    }
}


/** A volume slider with an icon and the level in percent. */
@Composable
private fun VolumeRow(label: String, icon: Int, value: Float, onChange: (Float) -> Unit, onDone: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painter = painterResource(icon), contentDescription = null, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.titleMedium, color = EarthBrown, modifier = Modifier.weight(1f))
            Text("${(value * 100).roundToInt()}%", style = MaterialTheme.typography.titleMedium, color = EarthBrown)
        }
        Slider(value = value, onValueChange = onChange, onValueChangeFinished = onDone)
    }
}
