package com.olamkatan.ui.settings

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
import com.olamkatan.R
import com.olamkatan.data.models.BilingualText
import com.olamkatan.data.models.Gender
import com.olamkatan.data.models.Language
import com.olamkatan.theme.CloudWhite
import com.olamkatan.theme.EarthBrown
import com.olamkatan.theme.GrassGreen
import com.olamkatan.theme.SkyBlue
import com.olamkatan.theme.SunYellow
import com.olamkatan.ui.common.LocalAppSettings
import com.olamkatan.ui.common.LocalLanguage

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
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

                SettingsToggleRow(
                    label = BilingualText("התוכי פוקי", "Puki the parrot").resolve(language),
                    icon = R.drawable.ic_companion_parrot,
                    checked = settings.companionEnabled,
                    labelOn = BilingualText("פועל", "On").resolve(language),
                    labelOff = BilingualText("כבוי", "Off").resolve(language),
                    onToggle = { settingsViewModel.toggleCompanion(settings.companionEnabled) }
                )
                if (settings.companionEnabled) {
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
