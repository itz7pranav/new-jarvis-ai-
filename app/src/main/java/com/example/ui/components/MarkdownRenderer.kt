package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalJarvisColors
import kotlinx.coroutines.delay

sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class CodeBlock(val language: String, val code: String) : MarkdownBlock
    data class MathBlock(val formula: String) : MarkdownBlock
    data class Quote(val text: String) : MarkdownBlock
    data class BulletList(val items: List<String>) : MarkdownBlock
    data class NumberedList(val items: List<Pair<String, String>>) : MarkdownBlock
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MarkdownBlock
    data object Divider : MarkdownBlock
}

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val blocks = remember(markdown) { parseMarkdown(markdown) }
    val jarvisColors = LocalJarvisColors.current

    SelectionContainer(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            blocks.forEach { block ->
                when (block) {
                    is MarkdownBlock.Heading -> {
                        val style = when (block.level) {
                            1 -> MaterialTheme.typography.headlineSmall
                            2 -> MaterialTheme.typography.titleLarge
                            else -> MaterialTheme.typography.titleMedium
                        }
                        Text(
                            text = buildInlineMarkdown(
                                block.text,
                                textColor,
                                jarvisColors.accentCyan,
                                jarvisColors.codeHeaderBackground
                            ),
                            style = style,
                            color = textColor,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    is MarkdownBlock.Paragraph -> {
                        Text(
                            text = buildInlineMarkdown(
                                block.text,
                                textColor,
                                jarvisColors.accentCyan,
                                MaterialTheme.colorScheme.surfaceVariant
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                            color = textColor
                        )
                    }

                    is MarkdownBlock.CodeBlock -> {
                        CodeBlockCard(language = block.language, code = block.code)
                    }

                    is MarkdownBlock.MathBlock -> {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, jarvisColors.subtleBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = block.formula,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 13.5.sp,
                                color = jarvisColors.accentCyan,
                                modifier = Modifier
                                    .horizontalScroll(rememberScrollState())
                                    .padding(12.dp)
                            )
                        }
                    }

                    is MarkdownBlock.Quote -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min)
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(vertical = 8.dp, horizontal = 10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .fillMaxHeight()
                                    .background(
                                        jarvisColors.accentCyan,
                                        RoundedCornerShape(2.dp)
                                    )
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = buildInlineMarkdown(
                                    block.text,
                                    textColor.copy(alpha = 0.9f),
                                    jarvisColors.accentCyan,
                                    MaterialTheme.colorScheme.surfaceVariant
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = textColor.copy(alpha = 0.9f)
                            )
                        }
                    }

                    is MarkdownBlock.BulletList -> {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            block.items.forEach { item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        text = "•",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = jarvisColors.accentCyan,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(end = 8.dp, start = 4.dp)
                                    )
                                    Text(
                                        text = buildInlineMarkdown(
                                            item,
                                            textColor,
                                            jarvisColors.accentCyan,
                                            MaterialTheme.colorScheme.surfaceVariant
                                        ),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = textColor,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    is MarkdownBlock.NumberedList -> {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            block.items.forEach { (num, item) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        text = "$num.",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = jarvisColors.accentAmber,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(end = 8.dp, start = 2.dp)
                                    )
                                    Text(
                                        text = buildInlineMarkdown(
                                            item,
                                            textColor,
                                            jarvisColors.accentCyan,
                                            MaterialTheme.colorScheme.surfaceVariant
                                        ),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = textColor,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    is MarkdownBlock.Table -> {
                        MarkdownTableView(headers = block.headers, rows = block.rows)
                    }

                    MarkdownBlock.Divider -> {
                        HorizontalDivider(
                            color = jarvisColors.subtleBorder,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CodeBlockCard(language: String, code: String) {
    val context = LocalContext.current
    val jarvisColors = LocalJarvisColors.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1800L)
            copied = false
        }
    }

    Surface(
        color = jarvisColors.codeBackground,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, jarvisColors.subtleBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(jarvisColors.codeHeaderBackground)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language.ifBlank { "code" }.lowercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF94A3B8)
                )
                TextButton(
                    onClick = {
                        val clipboard =
                            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("code", code))
                        copied = true
                        Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(
                        imageVector = if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                        contentDescription = "Copy code",
                        tint = if (copied) Color(0xFF34D399) else Color(0xFFCBD5E1),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (copied) "Copied" else "Copy",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (copied) Color(0xFF34D399) else Color(0xFFCBD5E1)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = code,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = Color(0xFFE2E8F0)
                )
            }
        }
    }
}

@Composable
private fun MarkdownTableView(headers: List<String>, rows: List<List<String>>) {
    val jarvisColors = LocalJarvisColors.current
    val columnCount = maxOf(headers.size, rows.maxOfOrNull { it.size } ?: 0)
    if (columnCount == 0) return

    Surface(
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, jarvisColors.subtleBorder),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
    ) {
        Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            // Header row
            Row(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(vertical = 8.dp, horizontal = 10.dp)
            ) {
                for (c in 0 until columnCount) {
                    Text(
                        text = headers.getOrNull(c).orEmpty(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .widthIn(min = 96.dp, max = 220.dp)
                            .padding(end = 12.dp)
                    )
                }
            }
            HorizontalDivider(color = jarvisColors.subtleBorder)
            rows.forEachIndexed { rIdx, row ->
                val bg = if (rIdx % 2 == 1) {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                } else {
                    Color.Transparent
                }
                Row(
                    modifier = Modifier
                        .background(bg)
                        .padding(vertical = 7.dp, horizontal = 10.dp)
                ) {
                    for (c in 0 until columnCount) {
                        Text(
                            text = buildInlineMarkdown(
                                row.getOrNull(c).orEmpty(),
                                MaterialTheme.colorScheme.onSurface,
                                jarvisColors.accentCyan,
                                MaterialTheme.colorScheme.surfaceVariant
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .widthIn(min = 96.dp, max = 220.dp)
                                .padding(end = 12.dp)
                        )
                    }
                }
                if (rIdx < rows.lastIndex) {
                    HorizontalDivider(color = jarvisColors.subtleBorder.copy(alpha = 0.5f))
                }
            }
        }
    }
}

fun parseMarkdown(input: String): List<MarkdownBlock> {
    val lines = input.replace("\r\n", "\n").split("\n")
    val blocks = mutableListOf<MarkdownBlock>()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        if (trimmed.isEmpty()) {
            i++
            continue
        }

        // Fenced code block
        if (trimmed.startsWith("```")) {
            val lang = trimmed.removePrefix("```").trim()
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            if (i < lines.size) i++ // consume closing ```
            blocks.add(MarkdownBlock.CodeBlock(lang, codeLines.joinToString("\n")))
            continue
        }

        // Display math block $$ ... $$
        if (trimmed.startsWith("$$")) {
            val mathLines = mutableListOf<String>()
            if (trimmed.length > 2 && trimmed.endsWith("$$")) {
                blocks.add(MarkdownBlock.MathBlock(trimmed.removePrefix("$$").removeSuffix("$$").trim()))
                i++
                continue
            }
            val first = trimmed.removePrefix("$$").trim()
            if (first.isNotEmpty()) mathLines.add(first)
            i++
            while (i < lines.size && !lines[i].trim().endsWith("$$")) {
                mathLines.add(lines[i])
                i++
            }
            if (i < lines.size) {
                val last = lines[i].trim().removeSuffix("$$").trim()
                if (last.isNotEmpty()) mathLines.add(last)
                i++
            }
            blocks.add(MarkdownBlock.MathBlock(mathLines.joinToString("\n")))
            continue
        }

        // Horizontal rule
        if (trimmed.matches(Regex("^(-{3,}|\\*{3,}|_{3,})$"))) {
            blocks.add(MarkdownBlock.Divider)
            i++
            continue
        }

        // Heading
        val headingMatch = Regex("^(#{1,6})\\s+(.+)$").find(trimmed)
        if (headingMatch != null) {
            val level = headingMatch.groupValues[1].length
            val text = headingMatch.groupValues[2].trim()
            blocks.add(MarkdownBlock.Heading(level, text))
            i++
            continue
        }

        // Table detection (header row + separator row like |---|---|)
        if (trimmed.contains("|") && i + 1 < lines.size &&
            lines[i + 1].trim().matches(Regex("^\\|?\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)+\\|?$"))
        ) {
            val headers = splitTableRow(trimmed)
            i += 2
            val rows = mutableListOf<List<String>>()
            while (i < lines.size && lines[i].trim().contains("|") && lines[i].trim().isNotEmpty()) {
                rows.add(splitTableRow(lines[i].trim()))
                i++
            }
            blocks.add(MarkdownBlock.Table(headers, rows))
            continue
        }

        // Blockquote
        if (trimmed.startsWith(">")) {
            val quoteLines = mutableListOf<String>()
            while (i < lines.size && lines[i].trim().startsWith(">")) {
                quoteLines.add(lines[i].trim().removePrefix(">").trim())
                i++
            }
            blocks.add(MarkdownBlock.Quote(quoteLines.joinToString("\n")))
            continue
        }

        // Bullet list
        if (trimmed.matches(Regex("^[-*+]\\s+.+$"))) {
            val items = mutableListOf<String>()
            while (i < lines.size && lines[i].trim().matches(Regex("^[-*+]\\s+.+$"))) {
                items.add(lines[i].trim().replaceFirst(Regex("^[-*+]\\s+"), ""))
                i++
            }
            blocks.add(MarkdownBlock.BulletList(items))
            continue
        }

        // Numbered list
        val numRegex = Regex("^(\\d+)[.)]\\s+(.+)$")
        if (numRegex.matches(trimmed)) {
            val items = mutableListOf<Pair<String, String>>()
            while (i < lines.size) {
                val m = numRegex.find(lines[i].trim()) ?: break
                items.add(m.groupValues[1] to m.groupValues[2])
                i++
            }
            blocks.add(MarkdownBlock.NumberedList(items))
            continue
        }

        // Paragraph
        val paraLines = mutableListOf<String>()
        while (i < lines.size) {
            val t = lines[i].trim()
            if (t.isEmpty() ||
                t.startsWith("```") ||
                t.startsWith("$$") ||
                t.startsWith("#") ||
                t.startsWith(">") ||
                t.matches(Regex("^[-*+]\\s+.+$")) ||
                numRegex.matches(t) ||
                t.matches(Regex("^(-{3,}|\\*{3,}|_{3,})$"))
            ) {
                break
            }
            paraLines.add(lines[i])
            i++
        }
        if (paraLines.isNotEmpty()) {
            blocks.add(MarkdownBlock.Paragraph(paraLines.joinToString("\n")))
        }
    }

    return blocks
}

private fun splitTableRow(row: String): List<String> {
    return row.trim()
        .removePrefix("|")
        .removeSuffix("|")
        .split("|")
        .map { it.trim() }
}

private fun buildInlineMarkdown(
    text: String,
    defaultColor: Color,
    accentColor: Color,
    inlineCodeBg: Color
): AnnotatedString {
    return buildAnnotatedString {
        var idx = 0
        while (idx < text.length) {
            // Bold **...**
            if (text.startsWith("**", idx)) {
                val end = text.indexOf("**", idx + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = defaultColor)) {
                        append(text.substring(idx + 2, end))
                    }
                    idx = end + 2
                    continue
                }
            }
            // Inline code `...`
            if (text[idx] == '`') {
                val end = text.indexOf('`', idx + 1)
                if (end != -1) {
                    withStyle(
                        SpanStyle(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 13.5.sp,
                            background = inlineCodeBg.copy(alpha = 0.7f),
                            color = accentColor
                        )
                    ) {
                        append(" ${text.substring(idx + 1, end)} ")
                    }
                    idx = end + 1
                    continue
                }
            }
            // Markdown link [label](url)
            if (text[idx] == '[') {
                val closeBracket = text.indexOf(']', idx + 1)
                if (closeBracket != -1 && closeBracket + 1 < text.length && text[closeBracket + 1] == '(') {
                    val closeParen = text.indexOf(')', closeBracket + 2)
                    if (closeParen != -1) {
                        val label = text.substring(idx + 1, closeBracket)
                        val url = text.substring(closeBracket + 2, closeParen).trim()
                        if (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) {
                            withLink(
                                LinkAnnotation.Url(
                                    url = url,
                                    styles = TextLinkStyles(
                                        style = SpanStyle(
                                            color = accentColor,
                                            textDecoration = TextDecoration.Underline,
                                            fontWeight = FontWeight.Medium
                                        )
                                    )
                                )
                            ) {
                                append(label)
                            }
                        } else {
                            withStyle(
                                SpanStyle(
                                    color = accentColor,
                                    textDecoration = TextDecoration.Underline,
                                    fontWeight = FontWeight.Medium
                                )
                            ) {
                                append(label)
                            }
                        }
                        idx = closeParen + 1
                        continue
                    }
                }
            }
            // Auto-link bare https:// or http:// URLs
            if (text.startsWith("https://", idx, ignoreCase = true) ||
                text.startsWith("http://", idx, ignoreCase = true)
            ) {
                var end = idx
                while (end < text.length && !text[end].isWhitespace() && text[end] !in charArrayOf(')', ']', '>', '"', '\'')) {
                    end++
                }
                val rawUrl = text.substring(idx, end).trimEnd('.', ',', ';')
                if (rawUrl.length > 10) {
                    withLink(
                        LinkAnnotation.Url(
                            url = rawUrl,
                            styles = TextLinkStyles(
                                style = SpanStyle(
                                    color = accentColor,
                                    textDecoration = TextDecoration.Underline
                                )
                            )
                        )
                    ) {
                        append(rawUrl)
                    }
                    idx += rawUrl.length
                    continue
                }
            }
            // Italic *...*
            if (text[idx] == '*' && (idx + 1 >= text.length || text[idx + 1] != '*')) {
                val end = text.indexOf('*', idx + 1)
                if (end != -1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = defaultColor)) {
                        append(text.substring(idx + 1, end))
                    }
                    idx = end + 1
                    continue
                }
            }
            append(text[idx])
            idx++
        }
    }
}
