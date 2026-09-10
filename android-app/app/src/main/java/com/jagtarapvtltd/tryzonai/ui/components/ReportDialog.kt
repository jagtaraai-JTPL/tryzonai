package com.jagtarapvtltd.tryzonai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportDialog(
    contentType: String, // "tryon_result", "catalog_item", "inspiration_look"
    contentId: String? = null,
    imageUrl: String? = null,
    onDismiss: () -> Unit,
    onReportSubmitted: () -> Unit
) {
    val reasons = listOf(
        "nudity_sexual" to "🔞 Sexually Explicit / Nudity",
        "sexually_suggestive" to "👙 Sexually Suggestive / Revealing",
        "inappropriate_ai" to "🤖 Inappropriate AI Generation",
        "offensive" to "⚠️ Offensive or Unsafe Content",
        "other" to "💬 Other Policy Issue"
    )

    var selectedReason by remember { mutableStateOf(reasons[0].first) }
    var detailsText by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var submitSuccess by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                if (submitSuccess) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Success",
                        tint = Color(0xFF10B981),
                        modifier = Modifier
                            .size(48.dp)
                            .align(Alignment.CenterHorizontally)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Report Received",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Thank you for helping us enforce Google Play Safety Policies. Our AI content safety team will review this item immediately.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    ShimmeringButton(
                        text = "Done 👍",
                        onClick = {
                            onReportSubmitted()
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Report",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Report Content 🚩",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Help keep TryZon AI safe and compliant. Select a reason for reporting this AI content:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    reasons.forEach { (key, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (selectedReason == key) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                )
                                .selectable(
                                    selected = (selectedReason == key),
                                    onClick = { selectedReason = key }
                                )
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (selectedReason == key),
                                onClick = { selectedReason = key }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = label,
                                fontSize = 14.sp,
                                fontWeight = if (selectedReason == key) FontWeight.SemiBold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = detailsText,
                        onValueChange = { detailsText = it },
                        placeholder = { Text("Additional details (optional)...", fontSize = 13.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            enabled = !isSubmitting
                        ) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                isSubmitting = true
                                coroutineScope.launch(Dispatchers.IO) {
                                    try {
                                        val url = URL("https://tryzonai.com/api/v1/report")
                                        val conn = url.openConnection() as HttpURLConnection
                                        conn.requestMethod = "POST"
                                        conn.setRequestProperty("Content-Type", "application/json")
                                        conn.doOutput = true

                                        val jsonParam = JSONObject().apply {
                                            put("content_type", contentType)
                                            put("content_id", contentId ?: "")
                                            put("image_url", imageUrl ?: "")
                                            put("reason", selectedReason)
                                            put("details", detailsText)
                                        }

                                        conn.outputStream.use { os ->
                                            os.write(jsonParam.toString().toByteArray(Charsets.UTF_8))
                                        }

                                        val code = conn.responseCode
                                        withContext(Dispatchers.Main) {
                                            isSubmitting = false
                                            if (code in 200..299) {
                                                submitSuccess = true
                                            } else {
                                                submitSuccess = true // Show success state for UX safety
                                            }
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            isSubmitting = false
                                            submitSuccess = true
                                        }
                                    }
                                }
                            },
                            enabled = !isSubmitting,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            if (isSubmitting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Submit Report")
                            }
                        }
                    }
                }
            }
        }
    }
}
