package com.hoon.photosorter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

val Bg = Color(0xFF111214)
val Surface = Color(0xFF1C1D21)
val Track = Color(0xFF2C2E33)
val TextMain = Color(0xFFF2F2F0)
val TextSub = Color(0xFFC9CBD0)
val Muted = Color(0xFFA3A6AD)
val Keep = Color(0xFF4C8DFF)
val OnKeep = Color(0xFF0A1630)
val Del = Color(0xFFFF8A3D)
val OnDel = Color(0xFF1A0E05)
val Done = Color(0xFF7FD49B)

@Composable
fun SorterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Keep, onPrimary = OnKeep,
            background = Bg, onBackground = TextMain,
            surface = Surface, onSurface = TextMain,
            secondaryContainer = Color(0xFF2C3A55), onSecondaryContainer = TextMain,
            outline = Color(0xFF34363C),
        ),
    ) {
        CompositionLocalProvider(LocalContentColor provides TextMain, content = content)
    }
}

private val dateFmt = DateTimeFormatter.ofPattern("yyyy년 M월 d일 (E)", Locale.KOREAN)
private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
private val timeSecFmt = DateTimeFormatter.ofPattern("HH:mm:ss")

fun LocalDate.label(): String = format(dateFmt)

fun timeLabel(ms: Long, withSeconds: Boolean = false): String =
    (if (withSeconds) timeSecFmt else timeFmt).format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

fun sizeLabel(bytes: Long): String =
    if (bytes >= 1_000_000_000) "%.1fGB".format(bytes / 1e9) else "%.1fMB".format(bytes / 1e6)

fun durationLabel(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun MediaView(item: MediaItem, modifier: Modifier = Modifier, crop: Boolean = false) {
    Box(modifier.background(Color.Black)) {
        AsyncImage(
            model = item.uri,
            contentDescription = null,
            contentScale = if (crop) ContentScale.Crop else ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        if (item.kind == Kind.VIDEO) {
            Pill(durationLabel(item.durationMs), Modifier.align(Alignment.BottomStart).padding(8.dp))
        }
    }
}

@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    bg: Color = Color(0xC7111214),
    fg: Color = TextMain,
    bold: Boolean = false,
) {
    Text(
        text,
        modifier.background(bg, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
        color = fg,
        fontSize = 13.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
    )
}

@Composable
fun TopBar(title: String, subtitle: String?, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로", tint = TextMain)
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            if (subtitle != null) Text(subtitle, color = Muted, fontSize = 13.sp)
        }
    }
}

@Composable
fun BigButton(
    text: String,
    bg: Color,
    fg: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(60.dp),
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = bg, contentColor = fg,
            disabledContainerColor = Surface, disabledContentColor = Muted,
        ),
    ) {
        Text(text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}
