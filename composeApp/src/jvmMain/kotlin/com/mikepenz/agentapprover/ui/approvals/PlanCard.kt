package com.mikepenz.agentapprover.ui.approvals

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

@Composable
fun PlanCard(
    request: ApprovalRequest,
    onApprove: (String?) -> Unit,
    onDeny: (String) -> Unit,
) {
    var feedback by remember { mutableStateOf("") }

    val planContent = request.toolInput["plan"]?.jsonPrimitive?.content
        ?: request.toolInput.toString()

    Column(modifier = Modifier.fillMaxWidth()) {
        // No timeout label
        Text("No timeout", fontSize = 10.sp, color = Color.Gray)

        Spacer(Modifier.height(8.dp))

        // Scrollable plan content
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
            color = Color(0xFF1E1E1E),
            shape = MaterialTheme.shapes.small,
        ) {
            Box(
                modifier = Modifier
                    .padding(8.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Markdown(content = planContent)
            }
        }

        Spacer(Modifier.height(8.dp))

        // Feedback input
        OutlinedTextField(
            value = feedback,
            onValueChange = { feedback = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Feedback (optional)", fontSize = 12.sp) },
            minLines = 2,
            maxLines = 4,
            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
        )

        Spacer(Modifier.height(8.dp))

        // Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { onDeny(feedback) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text("Deny")
            }
            Button(
                onClick = { onApprove(feedback.ifBlank { null }) },
                modifier = Modifier.weight(1f),
            ) {
                Text("Approve")
            }
        }
    }
}

@Preview
@Composable
private fun PreviewPlanCard() {
    AgentApproverTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.width(350.dp).padding(12.dp)) {
                PlanCard(
                    request = ApprovalRequest(
                        id = "preview-plan",
                        source = Source.CLAUDE_CODE,
                        toolName = "Plan",
                        toolType = ToolType.PLAN,
                        toolInput = JsonObject(mapOf(
                            "plan" to JsonPrimitive(
                                "## Implementation Plan\n\n" +
                                "1. **Create data models** for user and session\n" +
                                "2. **Set up database** with migrations\n" +
                                "3. **Implement API endpoints**\n" +
                                "   - `GET /users`\n" +
                                "   - `POST /users`\n" +
                                "   - `DELETE /users/:id`\n" +
                                "4. **Add authentication** middleware\n" +
                                "5. **Write tests** for all endpoints"
                            ),
                        )),
                        sessionId = "sess-abc123",
                        cwd = "/home/user/project",
                        timestamp = Clock.System.now(),
                        rawRequestJson = "{}",
                    ),
                    onApprove = {},
                    onDeny = {},
                )
            }
        }
    }
}
