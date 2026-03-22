package com.mikepenz.agentapprover.ui.approvals

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mikepenz.agentapprover.ui.theme.ToolWebColor
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

@Composable
fun WebFetchContent(toolInput: Map<String, JsonElement>) {
    val url = toolInput["url"]?.jsonPrimitive?.contentOrNull ?: ""
    val prompt = toolInput["prompt"]?.jsonPrimitive?.contentOrNull

    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = ToolWebColor.copy(alpha = 0.08f),
            shape = MaterialTheme.shapes.small,
        ) {
            Text(
                text = url,
                modifier = Modifier.padding(8.dp),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = ToolWebColor,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (!prompt.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    text = prompt,
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

fun webFetchPopOutContent(toolInput: Map<String, JsonElement>): String {
    val url = toolInput["url"]?.jsonPrimitive?.contentOrNull ?: ""
    val prompt = toolInput["prompt"]?.jsonPrimitive?.contentOrNull
    return buildString {
        appendLine("**URL:** $url")
        if (!prompt.isNullOrBlank()) {
            appendLine()
            appendLine("> $prompt")
        }
    }
}
