package dev.gsprasada.nexoracloud

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DecimalFormat

// ------------------------------------------------------------------ format & ikon

fun nxSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024.0 && index < units.lastIndex) { value /= 1024.0; index++ }
    return (if (index == 0) value.toLong().toString() else DecimalFormat("#.##").format(value)) + " " + units[index]
}

/** Jenis berkas ringkas untuk ikon dan filter: photo, video, audio, text, zip, apk, pdf, doc, other. */
fun nxKind(mime: String, name: String): String {
    val ext = name.substringAfterLast('.', "").lowercase()
    return when {
        mime.startsWith("image/") -> "photo"
        mime.startsWith("video/") -> "video"
        mime.startsWith("audio/") -> "audio"
        ext == "apk" -> "apk"
        ext in setOf("zip", "rar", "7z", "tar", "gz") || mime.contains("zip") -> "zip"
        ext == "pdf" || mime == "application/pdf" -> "pdf"
        mime.startsWith("text/") || ext in setOf("txt", "md", "json", "xml", "csv", "log", "kt", "js", "html", "css") -> "text"
        ext in setOf("doc", "docx", "xls", "xlsx", "ppt", "pptx") -> "doc"
        else -> "other"
    }
}

fun nxEmoji(kind: String): String = when (kind) {
    "photo" -> "🖼️"; "video" -> "🎬"; "audio" -> "🎵"; "text" -> "📝"; "zip" -> "📦"
    "apk" -> "🤖"; "pdf" -> "📕"; "doc" -> "📄"; "folder" -> "📁"; else -> "📎"
}

fun nxSourceLabel(source: String): String = when (source) {
    "tdlib" -> "✈️ TDLib"; "gdrive" -> "🇬 Google Drive"; "github" -> "🐙 GitHub"; "lokal" -> "📱 Lokal"; else -> source
}

// ------------------------------------------------------------------ kartu, tombol, field

@Composable
fun NxCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalNexora.current
    Column(
        modifier.fillMaxWidth().padding(bottom = 12.dp)
            .shadow(2.dp, RoundedCornerShape(16.dp))
            .background(c.surface, RoundedCornerShape(16.dp)).padding(14.dp),
        content = content
    )
}

enum class NxKind { Primary, Outline, Warn, Danger }

@Composable
fun NxButton(text: String, modifier: Modifier = Modifier, kind: NxKind = NxKind.Primary, enabled: Boolean = true, onClick: () -> Unit) {
    val c = LocalNexora.current
    val shape = RoundedCornerShape(10.dp)
    val bg: Color
    val fg: Color
    val stroke: BorderStroke?
    when (kind) {
        NxKind.Primary -> { bg = c.primary; fg = Color.White; stroke = null }
        NxKind.Outline -> { bg = Color.Transparent; fg = c.primary; stroke = BorderStroke(1.dp, c.primary) }
        NxKind.Warn -> { bg = c.warningBg; fg = c.warning; stroke = null }
        NxKind.Danger -> { bg = c.dangerBg; fg = c.danger; stroke = null }
    }
    var base = modifier.fillMaxWidth().padding(top = 8.dp).alpha(if (enabled) 1f else 0.45f).clip(shape).background(bg)
    if (stroke != null) base = base.border(stroke, shape)
    Box(base.clickable(enabled = enabled, onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
        Text(text, color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

@Composable
fun NxMiniButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = LocalNexora.current
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier.clip(shape).background(c.surface).border(1.dp, c.border, shape).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 5.dp)
    ) { Text(text, color = c.text, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
}

@Composable
fun NxField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    secret: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    minLines: Int = 1,
    enabled: Boolean = true
) {
    val c = LocalNexora.current
    var reveal by remember { mutableStateOf(false) }
    val trailing: (@Composable () -> Unit)? = if (secret) {
        { Text(if (reveal) "🙈" else "👁️", Modifier.clickable { reveal = !reveal }.padding(8.dp)) }
    } else null
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth().padding(top = 6.dp),
        enabled = enabled,
        singleLine = singleLine,
        minLines = minLines,
        placeholder = { Text(placeholder, color = c.muted, fontSize = 13.sp) },
        textStyle = TextStyle(fontSize = 13.sp, color = c.text),
        shape = RoundedCornerShape(10.dp),
        visualTransformation = if (secret && !reveal) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        trailingIcon = trailing,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = c.text, unfocusedTextColor = c.text, disabledTextColor = c.muted,
            focusedBorderColor = c.primary, unfocusedBorderColor = c.border, cursorColor = c.primary,
            focusedContainerColor = c.surface, unfocusedContainerColor = c.surface, disabledContainerColor = c.surfaceVariant
        )
    )
}

@Composable
fun NxDropdown(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    disabled: Set<String> = emptySet()
) {
    val c = LocalNexora.current
    var open by remember { mutableStateOf(false) }
    val label = options.firstOrNull { it.first == selected }?.second ?: selected
    val shape = RoundedCornerShape(10.dp)
    Box(modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(shape).background(c.surface).border(1.dp, c.border, shape)
                .clickable { open = true }.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, Modifier.weight(1f), color = c.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("▾", color = c.muted)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text, fontSize = 13.sp) },
                    enabled = value !in disabled,
                    onClick = { open = false; onSelect(value) }
                )
            }
        }
    }
}

@Composable
fun NxChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = LocalNexora.current
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier.clip(shape).background(if (selected) c.primary else c.surface)
            .border(1.dp, if (selected) c.primary else c.border, shape).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) { Text(text, color = if (selected) Color.White else c.text, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1) }
}

@Composable
fun NxPill(text: String, modifier: Modifier = Modifier, bg: Color? = null, fg: Color? = null) {
    val c = LocalNexora.current
    Text(
        text, modifier.background(bg ?: c.surfaceVariant, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
        color = fg ?: c.muted, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1
    )
}

@Composable
fun NxSectionTitle(text: String) {
    val c = LocalNexora.current
    Text(text.uppercase(), color = c.muted, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp, top = 12.dp, bottom = 4.dp))
}

@Composable
fun NxMenuRow(icon: String, title: String, sub: String? = null, badge: String? = null, danger: Boolean = false, onClick: () -> Unit) {
    val c = LocalNexora.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 15.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(icon, fontSize = 18.sp, modifier = Modifier.width(28.dp))
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(title, color = if (danger) c.danger else c.text, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
            if (sub != null) Text(sub, color = c.muted, fontSize = 11.sp)
        }
        if (badge != null) NxPill(badge)
        Text("›", color = c.muted, fontSize = 18.sp, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
fun NxSwitchRow(title: String, sub: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val c = LocalNexora.current
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = c.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(sub, color = c.muted, fontSize = 11.sp)
        }
        Switch(
            checked = checked, onCheckedChange = onChange, enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = c.primary, checkedThumbColor = Color.White)
        )
    }
}

@Composable
fun NxStat(value: String, label: String, modifier: Modifier = Modifier, tint: Color? = null) {
    val c = LocalNexora.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = tint ?: c.text, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
        Text(label, color = c.muted, fontSize = 10.5.sp)
    }
}

@Composable
fun NxEmpty(text: String) {
    val c = LocalNexora.current
    Text(text, color = c.muted, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(28.dp))
}

@Composable
fun NxCircleButton(text: String, modifier: Modifier = Modifier, bg: Color? = null, fg: Color? = null, size: Int = 34, onClick: () -> Unit) {
    val c = LocalNexora.current
    Box(
        modifier.size(size.dp).shadow(2.dp, CircleShape).clip(CircleShape).background(bg ?: c.surface).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(text, color = fg ?: c.text, fontSize = 15.sp) }
}

// ------------------------------------------------------------------ lapisan: sheet, dialog, toast

/** Bottom sheet dengan scrim dan animasi geser dari bawah. */
@Composable
fun NxSheet(visible: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalNexora.current
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.82f).dp
    AnimatedVisibility(visible, enter = fadeIn(tween(160)), exit = fadeOut(tween(140))) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                Modifier.animateEnterExit(
                    enter = slideInVertically(tween(200)) { it },
                    exit = slideOutVertically(tween(160)) { it }
                ).fillMaxWidth().heightIn(max = maxHeight)
                    .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)).background(c.surface)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                    .navigationBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
                content = {
                    Box(Modifier.align(Alignment.CenterHorizontally).width(38.dp).size(width = 38.dp, height = 4.dp).background(c.border, RoundedCornerShape(2.dp)))
                    Spacer(Modifier.size(10.dp))
                    content()
                }
            )
        }
    }
}

/** Dialog tengah layar dengan animasi skala/pudar. */
@Composable
fun NxDialog(visible: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalNexora.current
    AnimatedVisibility(visible, enter = fadeIn(tween(150)), exit = fadeOut(tween(120))) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss).imePadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier.animateEnterExit(
                    enter = scaleIn(tween(170), initialScale = 0.92f) + fadeIn(tween(170)),
                    exit = scaleOut(tween(120), targetScale = 0.95f) + fadeOut(tween(120))
                ).padding(horizontal = 24.dp).fillMaxWidth().heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.85f).dp)
                    .shadow(12.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(c.surface)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                    .verticalScroll(rememberScrollState()).padding(18.dp),
                content = content
            )
        }
    }
}

@Composable
fun NxSheetAction(icon: String, text: String, danger: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val c = LocalNexora.current
    Row(
        Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.4f).clip(RoundedCornerShape(10.dp)).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(icon, fontSize = 16.sp, modifier = Modifier.width(24.dp))
        Text(text, color = if (danger) c.danger else c.text, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun NxToastHost(message: String?, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf("") }
    LaunchedEffect(message) { if (message != null) shown = message }
    AnimatedVisibility(message != null, modifier = modifier, enter = fadeIn(tween(150)), exit = fadeOut(tween(220))) {
        Text(
            shown, color = Color.White, fontSize = 12.sp, maxLines = 3, textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp).shadow(6.dp, RoundedCornerShape(20.dp))
                .background(Color(0xFF111827), RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

/** Kerangka halaman penuh (subpage) dengan header berisi tombol kembali. */
@Composable
fun NxSubpage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    scroll: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    val c = LocalNexora.current
    Column(modifier.fillMaxSize().background(c.bg)) {
        Row(
            Modifier.fillMaxWidth().background(c.surface).padding(horizontal = 15.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            NxCircleButton("←", size = 32, bg = c.surfaceVariant, onClick = onBack)
            Text(title, Modifier.weight(1f), color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            actions()
        }
        if (scroll) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), content = content)
        } else {
            Column(Modifier.fillMaxSize(), content = content)
        }
    }
}
