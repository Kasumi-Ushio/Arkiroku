package org.kasumi321.ushio.phitracker.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AreaChart
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DataThresholding
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import phitracker.composeapp.generated.resources.Res
import phitracker.composeapp.generated.resources.action_go_login
import phitracker.composeapp.generated.resources.tools_accuracy_label
import phitracker.composeapp.generated.resources.tools_chart_constant_label
import phitracker.composeapp.generated.resources.tools_close
import phitracker.composeapp.generated.resources.tools_collapse
import phitracker.composeapp.generated.resources.tools_copied_to_clipboard
import phitracker.composeapp.generated.resources.tools_copy_and_close
import phitracker.composeapp.generated.resources.tools_expand
import phitracker.composeapp.generated.resources.tools_history_empty
import phitracker.composeapp.generated.resources.tools_history_need_two
import phitracker.composeapp.generated.resources.tools_history_range
import phitracker.composeapp.generated.resources.tools_history_sync_count
import phitracker.composeapp.generated.resources.tools_login_required
import phitracker.composeapp.generated.resources.tools_my_rank_subtitle
import phitracker.composeapp.generated.resources.tools_my_rank_title
import phitracker.composeapp.generated.resources.tools_not_queried_yet
import phitracker.composeapp.generated.resources.tools_query_button
import phitracker.composeapp.generated.resources.tools_query_my_rank_button
import phitracker.composeapp.generated.resources.tools_rank_label
import phitracker.composeapp.generated.resources.tools_rank_lookup_subtitle
import phitracker.composeapp.generated.resources.tools_rank_lookup_title
import phitracker.composeapp.generated.resources.tools_rks_calculator_subtitle
import phitracker.composeapp.generated.resources.tools_rks_calculator_title
import phitracker.composeapp.generated.resources.tools_rks_distribution_subtitle
import phitracker.composeapp.generated.resources.tools_rks_distribution_title
import phitracker.composeapp.generated.resources.tools_rks_formula
import phitracker.composeapp.generated.resources.tools_rks_history_subtitle
import phitracker.composeapp.generated.resources.tools_rks_history_title
import phitracker.composeapp.generated.resources.tools_rks_result_label
import phitracker.composeapp.generated.resources.tools_security_notice_message
import phitracker.composeapp.generated.resources.tools_security_notice_title
import phitracker.composeapp.generated.resources.tools_session_token_subtitle
import phitracker.composeapp.generated.resources.tools_session_token_title
import phitracker.composeapp.generated.resources.tools_show_credentials
import phitracker.composeapp.generated.resources.tools_suggest_subtitle
import phitracker.composeapp.generated.resources.tools_suggest_title
import phitracker.composeapp.generated.resources.tools_target_rks_label
import phitracker.composeapp.generated.resources.tools_view_suggestions
import org.kasumi321.ushio.phitracker.data.platform.copyToClipboard
import org.kasumi321.ushio.phitracker.data.platform.showPlatformMessage
import org.kasumi321.ushio.phitracker.ui.components.AnimatedAlertDialog
import org.kasumi321.ushio.phitracker.ui.utils.asString
import org.kasumi321.ushio.phitracker.domain.model.SyncSnapshot
import org.kasumi321.ushio.phitracker.domain.usecase.RksCalculator

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ToolsTab(
        state: ToolsUiState,
        defaultRks: Float,
        onNavigateToSuggest: () -> Unit,
        onFetchRankByUser: () -> Unit,
        onFetchRankByPosition: (Int) -> Unit,
        onFetchRksRank: (Float) -> Unit,
        onNavigateToLogin: () -> Unit = {},
        contentPadding: PaddingValues = PaddingValues(),
        scrollState: ScrollState = rememberScrollState(),
        modifier: Modifier = Modifier
) {
    val syncSnapshots = if (state.apiEnabled && state.useApiData) state.apiHistorySnapshots else state.syncSnapshots
    val sessionToken = state.sessionToken
    val apiEnabled = state.apiEnabled
    val useApiData = state.useApiData
    val apiRankByUser = state.apiRankByUser
    val apiRankByPosition = state.apiRankByPosition
    val apiRksRankResult = state.apiRksRankResult

    // Full-bleed scroll: content scrolls behind the floating glass bars, with
    // spacers keeping the first and last cards clear of them
    Column(
            modifier =
                    modifier.fillMaxSize()
                            .imePadding()
                            .verticalScroll(scrollState)
    ) {
        Column(
                modifier =
                        Modifier.fillMaxWidth()
                                .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(contentPadding.calculateTopPadding()))

                CollapsibleToolCard(
                        title = stringResource(Res.string.tools_rks_calculator_title),
                        subtitle = stringResource(Res.string.tools_rks_calculator_subtitle),
                        icon = Icons.Default.Calculate
                ) { RksCalculatorContent() }

                CollapsibleToolCard(
                        title = stringResource(Res.string.tools_suggest_title),
                        subtitle = stringResource(Res.string.tools_suggest_subtitle),
                        icon = Icons.AutoMirrored.Filled.ShowChart
                ) { SuggestEntryContent(onNavigateToSuggest) }

                CollapsibleToolCard(
                        title = stringResource(Res.string.tools_rks_history_title),
                        subtitle = stringResource(Res.string.tools_rks_history_subtitle),
                        icon = Icons.AutoMirrored.Filled.ShowChart
                ) { RksHistoryChartContent(syncSnapshots) }

                if (apiEnabled && useApiData) {
                    CollapsibleToolCard(
                            title = stringResource(Res.string.tools_my_rank_title),
                            subtitle = stringResource(Res.string.tools_my_rank_subtitle),
                            icon = Icons.Default.AccountCircle
                    ) { ApiRankByUserContent(state = apiRankByUser, onFetch = onFetchRankByUser) }

                    CollapsibleToolCard(
                            title = stringResource(Res.string.tools_rank_lookup_title),
                            subtitle = stringResource(Res.string.tools_rank_lookup_subtitle),
                            icon = Icons.Default.DataThresholding
                    ) {
                        ApiRankByPositionContent(
                                state = apiRankByPosition,
                                onFetch = onFetchRankByPosition
                        )
                    }

                    CollapsibleToolCard(
                            title = stringResource(Res.string.tools_rks_distribution_title),
                            subtitle = stringResource(Res.string.tools_rks_distribution_subtitle),
                            icon = Icons.Default.AreaChart
                    ) {
                        ApiRksRankContent(
                                state = apiRksRankResult,
                                defaultRks = defaultRks,
                                onFetch = onFetchRksRank
                        )
                    }
                }

                CollapsibleToolCard(
                        title = stringResource(Res.string.tools_session_token_title),
                        subtitle = stringResource(Res.string.tools_session_token_subtitle),
                        icon = Icons.Default.ContentCopy
                ) { SessionTokenContent(sessionToken, onNavigateToLogin) }

                Spacer(modifier = Modifier.height(16.dp))
                Spacer(modifier = Modifier.height(contentPadding.calculateBottomPadding()))
            }
    }
}

@Composable
private fun CollapsibleToolCard(
        title: String,
        subtitle: String,
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        content: @Composable ColumnScope.() -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
                modifier =
                        Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                )
                Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                    imageVector =
                            if (expanded) Icons.Default.KeyboardArrowUp
                            else Icons.Default.KeyboardArrowDown,
                    contentDescription =
                            if (expanded) stringResource(Res.string.tools_collapse)
                            else stringResource(Res.string.tools_expand),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
        ) {
            Column(
                    modifier =
                            Modifier.fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content
            )
        }
    }
}

@Composable
private fun RksCalculatorContent() {
    var chartConstantInput by rememberSaveable { mutableStateOf("") }
    var accuracyInput by rememberSaveable { mutableStateOf("") }

    val chartConstant = chartConstantInput.toFloatOrNull()
    val accuracy = accuracyInput.toFloatOrNull()

    val resultRks =
            if (chartConstant != null &&
                            accuracy != null &&
                            accuracy in 0f..100f &&
                            chartConstant >= 0f
            ) {
                RksCalculator.calculateSingleRks(accuracy, chartConstant)
            } else null

    Text(
            text = stringResource(Res.string.tools_rks_formula),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
                value = chartConstantInput,
                onValueChange = { chartConstantInput = it },
                label = { Text(stringResource(Res.string.tools_chart_constant_label)) },
                placeholder = { Text("15.3") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
        )

        OutlinedTextField(
                value = accuracyInput,
                onValueChange = { accuracyInput = it },
                label = { Text(stringResource(Res.string.tools_accuracy_label)) },
                placeholder = { Text("97.50") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
        )
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
                text = stringResource(Res.string.tools_rks_result_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
                text = if (resultRks != null) resultRks.formatFour() else "\u2014",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color =
                        if (resultRks != null) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RksHistoryChartContent(snapshots: List<SyncSnapshot>) {
    if (snapshots.size < 2) {
        Box(
                modifier = Modifier.fillMaxWidth().height(120.dp),
                contentAlignment = Alignment.Center
        ) {
            Text(
                    text =
                            if (snapshots.isEmpty())
                                    stringResource(Res.string.tools_history_empty)
                            else stringResource(Res.string.tools_history_need_two),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
            )
        }
        return
    }

    val sorted = remember(snapshots) { snapshots.sortedBy { it.timestamp } }
    val minRks = remember(sorted) { sorted.minOf { it.rks } }
    val maxRks = remember(sorted) { sorted.maxOf { it.rks } }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                    text =
                            stringResource(
                                    Res.string.tools_history_range,
                                    minRks.formatFour(),
                                    maxRks.formatFour()
                            ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                    text = stringResource(Res.string.tools_history_sync_count, snapshots.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // RKS History Line Chart (180dp, CMP-safe)
        val rksValues = sorted.map { it.rks }
        val chartMin = rksValues.min()
        val chartMax = rksValues.max()
        val range = chartMax - chartMin
        val padding = if (range > 0f) range * 0.1f else 0.5f
        val yMin = chartMin - padding
        val yMax = chartMax + padding
        val yRange = yMax - yMin
        val gridLines = 5
        val labelColumnWidth = 40.dp
        val chartPadding = 8.dp
        val circleRadius = 4.dp
        val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
        val lineColor = MaterialTheme.colorScheme.primary
        val pointColor = MaterialTheme.colorScheme.primary

        Row(modifier = Modifier.fillMaxWidth().height(180.dp)) {
            // Y-axis labels
            Column(
                    modifier =
                            Modifier.width(labelColumnWidth)
                                    .fillMaxHeight()
                                    .padding(top = chartPadding, bottom = chartPadding),
                    verticalArrangement = Arrangement.SpaceBetween
            ) {
                repeat(gridLines) { i ->
                    val value = yMax - (yRange * i / (gridLines - 1))
                    Text(
                            text = value.formatTwo(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.End,
                            modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Canvas chart area
            Canvas(modifier = Modifier.weight(1f).fillMaxHeight()) {
                val width = size.width
                val height = size.height
                val chartHeight = height - chartPadding.toPx() * 2

                // Grid lines
                repeat(gridLines) { i ->
                    val y = chartPadding.toPx() + (chartHeight * i / (gridLines - 1))
                    drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(width, y),
                            strokeWidth = 1f
                    )
                }

                // Map data points to canvas coordinates
                val points =
                        rksValues.mapIndexed { index, rks ->
                            val x =
                                    if (rksValues.size == 1) width / 2f
                                    else {
                                        chartPadding.toPx() +
                                                (index.toFloat() / (rksValues.size - 1)) *
                                                        (width - chartPadding.toPx() * 2)
                                    }
                            val y = chartPadding.toPx() + ((yMax - rks) / yRange) * chartHeight
                            Offset(x, y)
                        }

                // Line path
                if (points.size >= 2) {
                    val path =
                            Path().apply {
                                moveTo(points[0].x, points[0].y)
                                for (i in 1 until points.size) {
                                    lineTo(points[i].x, points[i].y)
                                }
                            }
                    drawPath(
                            path = path,
                            color = lineColor,
                            style = Stroke(width = 2.5f, cap = StrokeCap.Round)
                    )
                }

                // Data points
                points.forEach { offset ->
                    drawCircle(color = pointColor, radius = circleRadius.toPx(), center = offset)
                }
            }
        }

        HorizontalDivider()

        val recent = sorted.sortedByDescending { it.timestamp }.take(10)
        recent.forEachIndexed { index, snapshot ->
            val prevRks = if (index + 1 < recent.size) recent[index + 1].rks else null
            val delta = if (prevRks != null) snapshot.rks - prevRks else null

            Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                        text = epochMillisToShortDateString(snapshot.timestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (delta != null && delta != 0f) {
                        Text(
                                text =
                                        if (delta > 0) "+${delta.formatFour()}"
                                        else delta.formatFour(),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (delta > 0) Color(0xFF4CAF50) else Color(0xFFF44336),
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.End,
                                modifier = Modifier.width(72.dp)
                        )
                    } else {
                        Spacer(modifier = Modifier.width(72.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                            text = snapshot.rks.formatFour(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(76.dp)
                    )
                }
            }
            if (index < recent.lastIndex) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
            }
        }
    }
}

@Composable
private fun ApiRankByUserContent(state: ApiToolResult, onFetch: () -> Unit) {
    OutlinedButton(
            onClick = onFetch,
            enabled = !state.isLoading,
            modifier = Modifier.fillMaxWidth()
    ) {
        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(stringResource(Res.string.tools_query_my_rank_button))
    }
    ApiToolResultPanel(state = state)
}

@Composable
private fun ApiRankByPositionContent(state: ApiToolResult, onFetch: (Int) -> Unit) {
    var rankInput by rememberSaveable { mutableStateOf("") }
    Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
                value = rankInput,
                onValueChange = { rankInput = it },
                label = { Text(stringResource(Res.string.tools_rank_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f)
        )
        Button(onClick = { onFetch(rankInput.toIntOrNull() ?: -1) }, enabled = !state.isLoading) {
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Text(stringResource(Res.string.tools_query_button))
            }
        }
    }
    ApiToolResultPanel(state = state)
}

@Composable
private fun ApiRksRankContent(state: ApiToolResult, defaultRks: Float, onFetch: (Float) -> Unit) {
    var rksInput by
            rememberSaveable(defaultRks) {
                mutableStateOf(if (defaultRks > 0f) defaultRks.formatFour() else "")
            }
    Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
                value = rksInput,
                onValueChange = { rksInput = it },
                label = { Text(stringResource(Res.string.tools_target_rks_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f)
        )
        Button(onClick = { onFetch(rksInput.toFloatOrNull() ?: -1f) }, enabled = !state.isLoading) {
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Text(stringResource(Res.string.tools_query_button))
            }
        }
    }
    ApiToolResultPanel(state = state)
}

@Composable
private fun ApiToolResultPanel(state: ApiToolResult) {
    if (state.rows.isNotEmpty()) {
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
            ) { state.rows.forEach { row -> RankInfoRow(label = row.label.asString(), value = row.value.asString()) } }
        }
        return
    }

    Text(
            text = state.message?.asString() ?: stringResource(Res.string.tools_not_queried_yet),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun RankInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(0.38f)
        )
        Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(0.62f)
        )
    }
}

@Composable
private fun SessionTokenContent(sessionToken: String?, onNavigateToLogin: () -> Unit = {}) {
    var showTokenDialog by remember { mutableStateOf(false) }
    val copiedToClipboardMessage = stringResource(Res.string.tools_copied_to_clipboard)

    if (sessionToken == null) {
        Text(
                text = stringResource(Res.string.tools_login_required),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onNavigateToLogin, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.action_go_login))
        }
    } else {
        OutlinedButton(onClick = { showTokenDialog = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Key, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(Res.string.tools_show_credentials))
        }
    }

    if (showTokenDialog && sessionToken != null) {
        AnimatedAlertDialog(
                onDismissRequest = { showTokenDialog = false },
                icon = {
                    Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                    )
                },
                title = { Text(stringResource(Res.string.tools_security_notice_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                                text = stringResource(Res.string.tools_security_notice_message),
                                style = MaterialTheme.typography.bodyMedium
                        )
                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                    text = sessionToken,
                                    style =
                                            MaterialTheme.typography.bodySmall.copy(
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 11.sp
                                            ),
                                    modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                            onClick = {
                                copyToClipboard("sessionToken", sessionToken)
                                showPlatformMessage(copiedToClipboardMessage)
                                showTokenDialog = false
                            }
                    ) {
                        Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(Res.string.tools_copy_and_close))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showTokenDialog = false }) {
                        Text(stringResource(Res.string.tools_close))
                    }
                }
        )
    }
}

// ══════════════════════════════════════════════════════════════
// 推分建议
// ══════════════════════════════════════════════════════════════

@Composable
private fun SuggestEntryContent(onNavigateToSuggest: () -> Unit) {
    OutlinedButton(onClick = onNavigateToSuggest, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(stringResource(Res.string.tools_view_suggestions))
    }
}

