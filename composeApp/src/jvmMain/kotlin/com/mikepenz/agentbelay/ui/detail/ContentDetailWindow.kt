package com.mikepenz.agentbelay.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.mikepenz.agentbelay.model.ThemeMode
import com.mikepenz.agentbelay.ui.components.PillSegmented
import com.mikepenz.agentbelay.ui.components.PillSegmentedSize
import com.mikepenz.agentbelay.ui.components.SlimAllowButton
import com.mikepenz.agentbelay.ui.components.SlimDenyButton
import com.mikepenz.agentbelay.ui.theme.AgentBelayColors
import com.mikepenz.agentbelay.ui.theme.AgentBelayTheme
import com.mikepenz.agentbelay.ui.theme.PreviewScaffold
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.highlightedCodeBlock
import com.mikepenz.markdown.compose.elements.highlightedCodeFence
import com.mikepenz.markdown.m3.Markdown
import io.github.kdroidfilter.nucleus.window.material.MaterialDecoratedWindow
import io.github.kdroidfilter.nucleus.window.material.MaterialTitleBar

/**
 * Theme override for the popped-out detail window. [APP] mirrors whatever
 * the main shell is using, [LIGHT] / [DARK] force the popped-out window into
 * a specific theme without affecting the rest of the app.
 */
enum class DetailWindowTheme { APP, LIGHT, DARK }

private const val MIN_FONT_SCALE = 0.8f
private const val MAX_FONT_SCALE = 1.8f
private const val FONT_SCALE_STEP = 0.1f

@Composable
fun ContentDetailWindow(
    spec: PopOutSpec,
    appThemeMode: ThemeMode,
    onClose: () -> Unit,
) {
    val windowState = rememberWindowState(
        size = DpSize(760.dp, 640.dp),
        position = WindowPosition(Alignment.Center),
    )

    var themeOverride by remember { mutableStateOf(DetailWindowTheme.APP) }
    val effectiveTheme = when (themeOverride) {
        DetailWindowTheme.APP -> appThemeMode
        DetailWindowTheme.LIGHT -> ThemeMode.LIGHT
        DetailWindowTheme.DARK -> ThemeMode.DARK
    }

    AgentBelayTheme(themeMode = effectiveTheme) {
        MaterialDecoratedWindow(
            onCloseRequest = onClose,
            state = windowState,
            title = spec.title,
            onPreviewKeyEvent = { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    onClose()
                    true
                } else false
            },
        ) {
            MaterialTitleBar {
                Text(
                    spec.title,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            ContentDetailBody(
                content = spec.content,
                themeOverride = themeOverride,
                onThemeOverrideChange = { themeOverride = it },
                approveAction = spec.approveAction,
                denyAction = spec.denyAction,
                refineAction = spec.refineAction,
                onClose = onClose,
            )
        }
    }
}

/**
 * Pure content portion of [ContentDetailWindow] — extracted so it can be
 * rendered in `@Preview` without needing a native window host.
 */
@Composable
fun ContentDetailBody(
    content: String,
    themeOverride: DetailWindowTheme = DetailWindowTheme.APP,
    onThemeOverrideChange: (DetailWindowTheme) -> Unit = {},
    approveAction: (() -> Unit)? = null,
    denyAction: (() -> Unit)? = null,
    refineAction: ((String) -> Unit)? = null,
    onClose: () -> Unit = {},
) {
    var fontScale by remember { mutableFloatStateOf(1.0f) }
    var feedback by remember { mutableStateOf("") }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            DetailToolbar(
                themeOverride = themeOverride,
                onThemeOverrideChange = onThemeOverrideChange,
                fontScale = fontScale,
                onDecreaseFont = {
                    fontScale = (fontScale - FONT_SCALE_STEP).coerceAtLeast(MIN_FONT_SCALE)
                },
                onIncreaseFont = {
                    fontScale = (fontScale + FONT_SCALE_STEP).coerceAtMost(MAX_FONT_SCALE)
                },
                onResetFont = { fontScale = 1.0f },
                onClose = onClose,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                ScaledMarkdown(content = content, fontScale = fontScale)
            }

            if (approveAction != null || denyAction != null || refineAction != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                DetailActionBar(
                    feedback = feedback,
                    onFeedbackChange = { feedback = it },
                    approveAction = approveAction,
                    denyAction = denyAction,
                    refineAction = refineAction,
                )
            }
        }
    }
}

@Composable
private fun ScaledMarkdown(content: String, fontScale: Float) {
    val baseDensity = LocalDensity.current
    val scaledDensity = remember(baseDensity, fontScale) {
        Density(density = baseDensity.density, fontScale = baseDensity.fontScale * fontScale)
    }
    CompositionLocalProvider(LocalDensity provides scaledDensity) {
        Markdown(
            content = content,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            components = markdownComponents(
                codeFence = highlightedCodeFence,
                codeBlock = highlightedCodeBlock,
            ),
        )
    }
}

@Composable
private fun DetailToolbar(
    themeOverride: DetailWindowTheme,
    onThemeOverrideChange: (DetailWindowTheme) -> Unit,
    fontScale: Float,
    onDecreaseFont: () -> Unit,
    onIncreaseFont: () -> Unit,
    onResetFont: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PillSegmented(
            options = listOf(
                DetailWindowTheme.APP to "Auto",
                DetailWindowTheme.LIGHT to "Light",
                DetailWindowTheme.DARK to "Dark",
            ),
            selected = themeOverride,
            onSelect = onThemeOverrideChange,
            size = PillSegmentedSize.SM,
        )

        FontSizeControls(
            fontScale = fontScale,
            onDecrease = onDecreaseFont,
            onIncrease = onIncreaseFont,
            onReset = onResetFont,
        )

        Spacer(Modifier.weight(1f))

        EscHint()
    }
}

/**
 * Pill-styled font-size stepper. Shape and density mirror [PillSegmented] so
 * the two controls read as siblings: same 8dp container radius, same surface
 * + line-1 border, same 3dp inner padding, same SM-row height.
 */
@Composable
private fun FontSizeControls(
    fontScale: Float,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onReset: () -> Unit,
) {
    val canDecrease = fontScale > MIN_FONT_SCALE + 0.001f
    val canIncrease = fontScale < MAX_FONT_SCALE - 0.001f
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(AgentBelayColors.surface)
            .border(1.dp, AgentBelayColors.line1, RoundedCornerShape(8.dp))
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        StepperButton(label = "A−", enabled = canDecrease, onClick = onDecrease)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(5.dp))
                .clickable(role = androidx.compose.ui.semantics.Role.Button) { onReset() }
                .padding(horizontal = 10.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "${(fontScale * 100).toInt()}%",
                color = AgentBelayColors.inkSecondary,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                letterSpacing = (-0.05).sp,
            )
        }
        StepperButton(label = "A+", enabled = canIncrease, onClick = onIncrease)
    }
}

@Composable
private fun StepperButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val fg = if (enabled) AgentBelayColors.inkPrimary else AgentBelayColors.inkSubtle
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .then(
                if (enabled) Modifier.clickable(role = androidx.compose.ui.semantics.Role.Button) { onClick() }
                else Modifier,
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.05).sp,
        )
    }
}

@Composable
private fun EscHint() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(AgentBelayColors.surface2)
                .border(1.dp, AgentBelayColors.line1, RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 1.dp),
        ) {
            Text(
                text = "ESC",
                color = AgentBelayColors.inkSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
            )
        }
        Text(
            text = "close",
            color = AgentBelayColors.inkTertiary,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun DetailActionBar(
    feedback: String,
    onFeedbackChange: (String) -> Unit,
    approveAction: (() -> Unit)?,
    denyAction: (() -> Unit)?,
    refineAction: ((String) -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (refineAction != null) {
            OutlinedTextField(
                value = feedback,
                onValueChange = onFeedbackChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Refine plan with instructions…", fontSize = 13.sp) },
                minLines = 2,
                maxLines = 5,
                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
            )
        }

        val hasFeedback = feedback.isNotBlank()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (hasFeedback && refineAction != null) {
                Spacer(Modifier.weight(1f))
                SlimDenyButton(
                    label = "Refine Plan",
                    icon = null,
                    onClick = { refineAction(feedback) },
                )
            } else {
                if (denyAction != null) {
                    SlimDenyButton(
                        modifier = Modifier.weight(1f),
                        label = "Reject",
                        onClick = denyAction,
                    )
                }
                if (approveAction != null) {
                    SlimAllowButton(
                        modifier = Modifier.weight(1f),
                        label = "Approve Plan",
                        onClick = approveAction,
                    )
                }
            }
        }
    }
}

// ── Previews ───────────────────────────────────────────────────────────────

private const val SAMPLE_MARKDOWN = """# Implementation Plan

Agent Belay reviews the following steps before proceeding:

1. Add data models
2. Wire the hook server
3. Persist approvals to disk

```kotlin
fun hello() = "world"
```

> Note: all side effects gated on user approval.
"""

private const val LONG_MARKDOWN = """# Detailed migration notes

This is a dense multi-paragraph document used to verify scroll, line-height and
typography rendering inside the popped-out detail window.

## Background

The migration replaces the legacy JSON persistence layer with a small SQLite
database. Historical entries are preserved by a one-time import.

## Steps

1. Open the database on first launch.
2. Import existing `history.json` rows.
3. Verify counts match.

```kotlin
class HistoryStorage(private val db: Database) {
    suspend fun insert(entry: HistoryEntry) { /* … */ }
}
```

### Caveats

- Import is idempotent but slow on first run.
- Rollback requires deleting the database file.
- Future versions will migrate in-place.

## Acceptance

- [ ] Existing history visible
- [ ] New approvals persist
- [ ] Statistics unchanged
"""

@Preview(widthDp = 760, heightDp = 640)
@Composable
private fun PreviewContentDetailBody() {
    PreviewScaffold {
        ContentDetailBody(content = SAMPLE_MARKDOWN)
    }
}

@Preview(widthDp = 760, heightDp = 640)
@Composable
private fun PreviewContentDetailBodyWithActions() {
    PreviewScaffold {
        ContentDetailBody(
            content = SAMPLE_MARKDOWN,
            approveAction = {},
            denyAction = {},
            refineAction = {},
        )
    }
}

@Preview(widthDp = 760, heightDp = 640)
@Composable
private fun PreviewContentDetailBodyLong() {
    PreviewScaffold {
        ContentDetailBody(content = LONG_MARKDOWN)
    }
}

@Preview(widthDp = 760, heightDp = 640)
@Composable
private fun PreviewContentDetailBodyLight() {
    PreviewScaffold(themeMode = ThemeMode.LIGHT) {
        ContentDetailBody(
            content = SAMPLE_MARKDOWN,
            approveAction = {},
            denyAction = {},
            refineAction = {},
        )
    }
}
