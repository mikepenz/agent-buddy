package com.mikepenz.agentapprover.ui.approvals

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.agentapprover.model.*
import com.mikepenz.agentapprover.ui.theme.AgentApproverTheme
import com.mikepenz.markdown.m3.Markdown
import kotlinx.datetime.Clock
import kotlinx.serialization.json.*

@Composable
fun AskUserQuestionCard(
    request: ApprovalRequest,
    onSendResponse: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var response by remember { mutableStateOf("") }

    val question = request.toolInput["question"]?.jsonPrimitive?.contentOrNull
        ?: request.toolInput.toString()
    val options = request.toolInput["options"]?.jsonArray?.mapNotNull {
        it.jsonPrimitive.contentOrNull
    } ?: emptyList()

    Column(modifier = Modifier.fillMaxWidth()) {
        // No timeout label
        Text("No timeout", fontSize = 10.sp, color = Color.Gray)

        Spacer(Modifier.height(8.dp))

        // Question
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF1E1E1E),
            shape = MaterialTheme.shapes.small,
        ) {
            Box(modifier = Modifier.padding(8.dp)) {
                Markdown(content = question)
            }
        }

        // Options
        if (options.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                options.forEach { option ->
                    AssistChip(
                        onClick = { response = option },
                        label = { Text(option, fontSize = 11.sp) },
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Response input
        OutlinedTextField(
            value = response,
            onValueChange = { response = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Type your response...", fontSize = 12.sp) },
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
        )

        Spacer(Modifier.height(8.dp))

        // Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            ) {
                Text("Dismiss")
            }
            Button(
                onClick = { onSendResponse(response) },
                modifier = Modifier.weight(1f),
                enabled = response.isNotBlank(),
            ) {
                Text("Send Response")
            }
        }
    }
}

@Preview
@Composable
private fun PreviewAskUserQuestion() {
    AgentApproverTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.width(350.dp).padding(12.dp)) {
                AskUserQuestionCard(
                    request = ApprovalRequest(
                        id = "preview-ask",
                        source = Source.CLAUDE_CODE,
                        toolName = "AskUserQuestion",
                        toolType = ToolType.ASK_USER_QUESTION,
                        toolInput = JsonObject(mapOf(
                            "question" to JsonPrimitive("Which database backend should I use for this project?"),
                            "options" to JsonArray(listOf(
                                JsonPrimitive("PostgreSQL"),
                                JsonPrimitive("SQLite"),
                                JsonPrimitive("MySQL"),
                            )),
                        )),
                        sessionId = "sess-abc123",
                        cwd = "/home/user/project",
                        timestamp = Clock.System.now(),
                        rawRequestJson = "{}",
                    ),
                    onSendResponse = {},
                    onDismiss = {},
                )
            }
        }
    }
}
