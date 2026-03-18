package com.mikepenz.agentapprover.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import co.touchlab.kermit.Logger
import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import com.mikepenz.agentapprover.model.ToolType
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// Risk colors
val RiskSafe = Color(0xFF4CAF50)
val RiskLow = Color(0xFF8BC34A)
val RiskMedium = Color(0xFFFF9800)
val RiskHigh = Color(0xFFFF5722)
val RiskCritical = Color(0xFFF44336)

// Tool badge colors
val ToolBashColor = Color(0xFFFF9800)
val ToolAskColor = Color(0xFF2196F3)
val ToolPlanColor = Color(0xFF9C27B0)
val ToolDefaultColor = Color(0xFF607D8B)

fun riskColor(risk: Int): Color = when (risk) {
    1 -> RiskSafe
    2 -> RiskLow
    3 -> RiskMedium
    4 -> RiskHigh
    5 -> RiskCritical
    else -> RiskMedium
}

fun riskLabel(risk: Int): String = when (risk) {
    1 -> "Safe"
    2 -> "Low"
    3 -> "Medium"
    4 -> "High"
    5 -> "Critical"
    else -> "Medium"
}

fun toolColor(toolType: ToolType): Color = when (toolType) {
    ToolType.ASK_USER_QUESTION -> ToolAskColor
    ToolType.PLAN -> ToolPlanColor
    ToolType.DEFAULT -> ToolDefaultColor
}

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF8B5CF6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3B2667),
    onPrimaryContainer = Color(0xFFE0D0FF),
    secondary = Color(0xFFA78BFA),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF2D2150),
    onSecondaryContainer = Color(0xFFE8DEFF),
    tertiary = Color(0xFF7DD3FC),
    onTertiary = Color(0xFF003548),
    background = Color(0xFF0F1419),
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF1A2332),
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF243044),
    onSurfaceVariant = Color(0xFFB0BEC5),
    outline = Color(0xFF475569),
    outlineVariant = Color(0xFF334155),
    inverseSurface = Color(0xFFE2E8F0),
    inverseOnSurface = Color(0xFF1A2332),
    error = Color(0xFFFF6B6B),
    onError = Color.White,
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF7C3AED),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE5FF),
    onPrimaryContainer = Color(0xFF22005D),
    secondary = Color(0xFF8B5CF6),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3EDFF),
    onSecondaryContainer = Color(0xFF1D0A42),
    tertiary = Color(0xFF0284C7),
    onTertiary = Color.White,
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF1A1A2E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1A2E),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
    inverseSurface = Color(0xFF1A2332),
    inverseOnSurface = Color(0xFFE2E8F0),
    error = Color(0xFFDC2626),
    onError = Color.White,
)

@Composable
fun AgentApproverTheme(content: @Composable () -> Unit) {
    val colorScheme = if (isSystemInDarkTheme()) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}

/**
 * Simple file log writer that appends log entries to a file in the app data directory.
 */
class FileLogWriter(
    private val logDir: File = File(System.getProperty("user.home"), ".agent-approver/logs"),
) : LogWriter() {
    private val logFile: File by lazy {
        logDir.mkdirs()
        File(logDir, "agent-approver.log")
    }

    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        try {
            val timestamp = LocalDateTime.now().format(formatter)
            val line = buildString {
                append("$timestamp [$severity] $tag: $message")
                if (throwable != null) {
                    append("\n${throwable.stackTraceToString()}")
                }
                append("\n")
            }
            logFile.appendText(line)
        } catch (_: Exception) {
            // Silently ignore file write failures to avoid recursive logging
        }
    }
}

/**
 * Configures Kermit with the file log writer.
 */
fun configureLogging() {
    Logger.addLogWriter(FileLogWriter())
}
