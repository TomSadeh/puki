package com.olamkatan.ui.onboarding

import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.olamkatan.R
import com.olamkatan.data.models.AppSettings
import com.olamkatan.data.models.BilingualText
import com.olamkatan.data.models.Gender
import com.olamkatan.data.models.Language
import com.olamkatan.ui.settings.SettingsViewModel
import com.olamkatan.ui.settings.rememberPairingScanner

private val Ink = Color(0xFF5D4037)
private val Cream = Color(0xFFFFF8E1)
private val Wood = Color(0xFFBC8F5F)

/**
 * Shown once, on first launch, for a parent: language, the child's name and gender,
 * and whether to use the parrot. Everything can be changed later in parent settings.
 */
@Composable
fun OnboardingScreen(settings: AppSettings, viewModel: SettingsViewModel = viewModel()) {
    val focus = LocalFocusManager.current
    val lang = settings.language
    fun t(he: String, en: String) = BilingualText(he, en).resolve(lang)
    var name by remember { mutableStateOf(settings.childName) }
    var address by remember { mutableStateOf(settings.companionAddress) }
    var code by remember { mutableStateOf(settings.companionCode) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF81D4FA), Color(0xFFE1F5FE)))),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .padding(12.dp)
                .shadow(8.dp, RoundedCornerShape(28.dp))
                .clip(RoundedCornerShape(28.dp))
                .background(Cream)
                .border(BorderStroke(3.dp, Wood), RoundedCornerShape(28.dp))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(painterResource(R.drawable.ic_companion_parrot), contentDescription = null, modifier = Modifier.size(80.dp))
            Text(
                t("ברוכים הבאים לפוקי!", "Welcome to Puki!"),
                style = MaterialTheme.typography.headlineMedium.copy(shadow = null),
                color = Ink
            )
            Text(
                t("כמה פרטים קטנים, ומתחילים לשחק.", "A few quick details, and then it's playtime."),
                style = MaterialTheme.typography.bodyLarge.copy(shadow = null),
                color = Ink
            )

            // Language, and girl/boy (for Hebrew grammar: "תלחצי" / "תלחץ"), on one row
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Choice("עברית", selected = lang == Language.HEBREW) { viewModel.setLanguage(Language.HEBREW) }
                Choice("English", selected = lang == Language.ENGLISH) { viewModel.setLanguage(Language.ENGLISH) }
                Spacer(Modifier.width(16.dp))
                Choice(t("בת", "Girl"), R.drawable.ic_ui_girl, selected = settings.gender == Gender.GIRL) {
                    viewModel.setGender(Gender.GIRL)
                }
                Choice(t("בן", "Boy"), R.drawable.ic_ui_boy, selected = settings.gender == Gender.BOY) {
                    viewModel.setGender(Gender.BOY)
                }
            }

            // Child's name
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(30) },
                label = { Text(t("איך קוראים לילד/ה?", "What's your child's name?")) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Ink, unfocusedTextColor = Ink),
                modifier = Modifier.widthIn(min = 320.dp)
            )

            // The parrot companion
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    t("התוכי פוקי (מחשב בבית)", "Puki the parrot (a PC at home)"),
                    style = MaterialTheme.typography.titleLarge.copy(shadow = null),
                    color = Ink
                )
                Spacer(Modifier.width(12.dp))
                Switch(checked = settings.companionEnabled, onCheckedChange = { viewModel.setCompanionEnabled(it) })
            }
            if (settings.companionEnabled) {
                // Scan the QR code on the brain's dashboard instead of typing address and code
                var scanned by remember { mutableStateOf<Boolean?>(null) }  // null until a scan
                val scan = rememberPairingScanner(t("כוונו אל קוד ה-QR שבמחשב", "Point at the QR code on the PC")) { link ->
                    scanned = link != null
                    if (link != null) {
                        address = link.host
                        code = link.code
                        viewModel.setCompanionAddress(link.host)
                        viewModel.setCompanionCode(link.code)
                    }
                }
                OutlinedButton(onClick = scan, modifier = Modifier.widthIn(min = 320.dp)) {
                    Text(t("📷 סריקת קוד ה-QR שבמחשב", "📷 Scan the QR code on the PC"), style = MaterialTheme.typography.titleMedium, color = Ink)
                }
                if (scanned == false) {
                    Text(t("זה לא קוד ה-QR של פוקי, נסו שוב", "That isn't Puki's QR code, try again"), color = Ink)
                } else if (scanned == true) {
                    Text(t("✓ המחשב נקלט", "✓ Got the PC"), color = Color(0xFF2E7D32))
                }
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text(t("כתובת המחשב (אפשר גם אחר כך)", "PC address (can be set later)")) },
                    placeholder = { Text("192.168.1.20:8790") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Ink, unfocusedTextColor = Ink),
                    modifier = Modifier.widthIn(min = 320.dp)
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase() },
                    label = { Text(t("קוד צימוד (מופיע במחשב)", "Pairing code (shown on the PC)")) },
                    placeholder = { Text("ABCD-EFGH") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        autoCorrect = false,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Ink, unfocusedTextColor = Ink),
                    modifier = Modifier.widthIn(min = 320.dp)
                )
            }

            Box(
                modifier = Modifier
                    .shadow(6.dp, RoundedCornerShape(32.dp))
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color(0xFF66BB6A))
                    .clickable {
                        viewModel.setChildName(name)
                        viewModel.setCompanionAddress(address)
                        viewModel.setCompanionCode(code)
                        viewModel.finishOnboarding()
                    }
                    .padding(horizontal = 40.dp, vertical = 14.dp)
            ) {
                Text(t("יאללה, משחקים!", "Let's play!"), style = MaterialTheme.typography.headlineMedium, color = Color.White)
            }
        }
    }
}

@Composable
private fun Choice(label: String, @DrawableRes icon: Int? = null, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) Color(0xFFFFE082) else Color.White)
            .border(BorderStroke(if (selected) 3.dp else 1.dp, if (selected) Color(0xFFFFA000) else Wood), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(36.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(label, style = MaterialTheme.typography.titleLarge.copy(shadow = null), fontWeight = FontWeight.Bold, color = Ink)
    }
}
