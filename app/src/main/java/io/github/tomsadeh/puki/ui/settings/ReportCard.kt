package io.github.tomsadeh.puki.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tomsadeh.puki.audio.Companion
import io.github.tomsadeh.puki.data.models.BilingualText
import io.github.tomsadeh.puki.theme.CloudWhite
import io.github.tomsadeh.puki.theme.EarthBrown
import io.github.tomsadeh.puki.ui.common.LocalLanguage

/**
 * מלשין לרנינג: what Puki said this session, newest first, each with a flag for a parent who
 * heard something that wasn't right. A report stays at home (the tablet, and the PC if there is
 * one) and becomes a case to fix Puki by.
 */
@Composable
fun ReportCard(companion: Companion) {
    val language = LocalLanguage.current
    val lines by companion.recentLines.collectAsState()
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(CloudWhite)
            .padding(24.dp)
            .width(400.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(BilingualText("מלשין לרנינג", "What Puki said").resolve(language), style = MaterialTheme.typography.titleMedium, color = EarthBrown)
        Text(
            BilingualText(
                "מה פוקי אמר. משהו לא היה בסדר? 🚩 שומר את זה בבית, כדי לתקן את פוקי.",
                "Something wasn't right? 🚩 keeps it at home, to fix Puki by."
            ).resolve(language),
            style = MaterialTheme.typography.bodyMedium,
            color = EarthBrown.copy(alpha = 0.8f)
        )
        if (lines.isEmpty()) {
            Text(
                BilingualText("פוקי עוד לא אמר כלום הפעם.", "Puki hasn't said anything yet this time.").resolve(language),
                style = MaterialTheme.typography.bodyMedium,
                color = EarthBrown.copy(alpha = 0.6f)
            )
        }
        for (line in lines.asReversed().take(10)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    val before = line.heard.ifBlank { line.situation }
                    if (before.isNotBlank()) {
                        Text("“$before”", fontSize = 13.sp, color = EarthBrown.copy(alpha = 0.65f), maxLines = 2)
                    }
                    Text(line.said, style = MaterialTheme.typography.bodyLarge, color = EarthBrown)
                }
                Box(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (line.reported) Color(0xFFE8F5E9) else Color(0xFFFFEBEE))
                        .clickable(enabled = !line.reported) { companion.report(line) },
                    contentAlignment = Alignment.Center
                ) { Text(if (line.reported) "✓" else "🚩", fontSize = 20.sp) }
            }
        }
    }
}
