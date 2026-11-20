package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.audio.AudioEngineState
import com.example.audio.PlaybackState
import com.example.audio.effects.VoiceEffect
import com.example.database.RecordingEntity
import com.example.ui.components.EffectSelectorCard
import com.example.ui.components.WaveformVisualizer
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.NeonCyan
import java.util.Locale

@Composable
fun StudioScreen(
    engineState: AudioEngineState,
    visualizerData: FloatArray,
    lastSavedRecording: RecordingEntity?,
    playbackState: PlaybackState,
    onStartEngine: () -> Unit,
    onStopEngine: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onToggleMonitorDuringRecording: (Boolean) -> Unit,
    onSelectEffect: (VoiceEffect) -> Unit,
    onIntensityChange: (Float) -> Unit,
    onPitchChange: (Float) -> Unit,
    onDepthChange: (Float) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onPlayPreview: (RecordingEntity) -> Unit,
    onPausePreview: () -> Unit,
    onDismissPreview: () -> Unit,
    onOpenLibrary: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAdvancedParams by remember { mutableStateOf(false) }
    var showAllEffectsList by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Status header & device/telemetry indicator
        item {
            Spacer(modifier = Modifier.height(2.dp))
            StatusAndDeviceBar(engineState = engineState)
        }

        // Actionable Error Banner (if any audio initialization or mic error occurred)
        if (!engineState.errorMessage.isNullOrBlank()) {
            item {
                ErrorAlertCard(
                    message = engineState.errorMessage,
                    onDismiss = onDismissError
                )
            }
        }

        // Real-time Waveform Visualizer + Live Mic VU Meter
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WaveformVisualizer(
                    visualizerData = visualizerData,
                    isActive = engineState.isRunning
                )
                MicInputMeter(
                    level = engineState.micInputLevel,
                    isActive = engineState.isRunning
                )
            }
        }

        // Live Controls: Direct Listening & Recording
        item {
            PrimaryControlBar(
                isListening = engineState.isRunning && engineState.isMonitoringOutput && !engineState.isRecording,
                isRunning = engineState.isRunning,
                isRecording = engineState.isRecording,
                sessionSeconds = engineState.sessionDurationSeconds,
                recordingSeconds = engineState.recordingDurationSeconds,
                onStartListening = onStartEngine,
                onStopListening = onStopEngine,
                onStartRecord = onStartRecording,
                onStopRecord = onStopRecording
            )
        }

        // Instant Studio Preview Player after stopping a recording
        if (lastSavedRecording != null && !engineState.isRecording) {
            item {
                val isPlayingThis = playbackState.currentRecordingId == lastSavedRecording.id && playbackState.isPlaying
                val progress = if (playbackState.currentRecordingId == lastSavedRecording.id && playbackState.durationMs > 0) {
                    (playbackState.currentPositionMs.toFloat() / playbackState.durationMs).coerceIn(0f, 1f)
                } else {
                    0f
                }

                InstantRecordingPreviewCard(
                    recording = lastSavedRecording,
                    isPlaying = isPlayingThis,
                    progress = progress,
                    onPlayPause = {
                        if (isPlayingThis) onPausePreview() else onPlayPreview(lastSavedRecording)
                    },
                    onOpenLibrary = onOpenLibrary,
                    onDismiss = onDismissPreview
                )
            }
        }

        // Speaker Mode Pro Options (when no headphones plugged in)
        if (!engineState.isHeadphonesConnected) {
            item {
                SpeakerStudioOptionsCard(
                    monitorDuringRecording = engineState.monitorDuringRecording,
                    antiFeedbackActive = engineState.isFeedbackProtectionActive,
                    onToggleMonitorDuringRecording = onToggleMonitorDuringRecording
                )
            }
        }

        // 12 Effects selector Carousel / All Effects toggle
        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Effet Vocal (${VoiceEffect.values().indexOf(engineState.activeEffect) + 1}/12)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = if (showAllEffectsList) "Vue compacte" else "Voir les 12 effets",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        ),
                        modifier = Modifier
                            .clickable { showAllEffectsList = !showAllEffectsList }
                            .padding(vertical = 4.dp, horizontal = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(VoiceEffect.values()) { effect ->
                        EffectMiniTab(
                            effect = effect,
                            isSelected = effect == engineState.activeEffect,
                            onClick = { onSelectEffect(effect) }
                        )
                    }
                }
            }
        }

        // Expanded list of all 12 effects or active effect detail card
        if (showAllEffectsList) {
            items(VoiceEffect.values()) { effect ->
                EffectSelectorCard(
                    effect = effect,
                    isSelected = effect == engineState.activeEffect,
                    onClick = {
                        onSelectEffect(effect)
                        showAllEffectsList = false
                    }
                )
            }
        } else {
            item {
                EffectSelectorCard(
                    effect = engineState.activeEffect,
                    isSelected = true,
                    onClick = { showAllEffectsList = true }
                )
            }
        }

        // Audio DSP Sliders Section
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Ajustements Sonores DSP",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Text(
                            text = if (showAdvancedParams) "Masquer détails" else "Paramètres avancés",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier
                                .clickable { showAdvancedParams = !showAdvancedParams }
                                .padding(4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    SliderRow(
                        title = stringResource(R.string.studio_param_intensity),
                        value = engineState.intensity,
                        valueDisplay = "${(engineState.intensity * 100).toInt()}%",
                        onValueChange = onIntensityChange
                    )

                    SliderRow(
                        title = stringResource(R.string.studio_param_output_vol),
                        value = engineState.masterVolume,
                        valueRange = 0.0f..1.5f,
                        valueDisplay = "${(engineState.masterVolume * 100).toInt()}%",
                        onValueChange = onVolumeChange
                    )

                    AnimatedVisibility(visible = showAdvancedParams) {
                        Column {
                            Spacer(modifier = Modifier.height(8.dp))

                            SliderRow(
                                title = stringResource(R.string.studio_param_pitch),
                                value = engineState.pitchFactor,
                                valueRange = 0.5f..2.0f,
                                valueDisplay = String.format(Locale.getDefault(), "%.2fx", engineState.pitchFactor),
                                onValueChange = onPitchChange
                            )

                            SliderRow(
                                title = stringResource(R.string.studio_param_depth),
                                value = engineState.depth,
                                valueDisplay = "${(engineState.depth * 100).toInt()}%",
                                onValueChange = onDepthChange
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            OutlinedButton(
                                onClick = {
                                    onIntensityChange(engineState.activeEffect.defaultIntensity)
                                    onPitchChange(engineState.activeEffect.defaultPitchFactor)
                                    onDepthChange(engineState.activeEffect.defaultDepth)
                                    onVolumeChange(1.0f)
                                },
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .testTag("reset_params_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Réinitialiser")
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(18.dp))
        }
    }
}

@Composable
private fun StatusAndDeviceBar(engineState: AudioEngineState) {
    val rateKhz = String.format(Locale.getDefault(), "%.1f kHz", engineState.sampleRate / 1000f)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                val dotColor = when {
                    engineState.isRecording -> Color.Red
                    engineState.isRunning -> EmeraldGreen
                    else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                }
                Surface(
                    shape = CircleShape,
                    color = dotColor,
                    modifier = Modifier.size(11.dp)
                ) {}

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = when {
                            engineState.isRecording -> stringResource(R.string.studio_status_recording)
                            engineState.isRunning -> stringResource(R.string.studio_status_active)
                            else -> stringResource(R.string.studio_status_ready)
                        },
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$rateKhz • ~${engineState.latencyMs} ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.padding(start = 6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (engineState.isHeadphonesConnected) Icons.Default.Headphones else Icons.Default.VolumeUp,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (engineState.isHeadphonesConnected) "Casque" else "Haut-parleur",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun MicInputMeter(level: Float, isActive: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = null,
            tint = if (isActive && level > 0.02f) EmeraldGreen else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Micro",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(10.dp))
        LinearProgressIndicator(
            progress = { if (isActive) level.coerceIn(0f, 1f) else 0f },
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = when {
                level > 0.85f -> MaterialTheme.colorScheme.error
                level > 0.1f -> EmeraldGreen
                else -> MaterialTheme.colorScheme.primary
            },
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
private fun ErrorAlertCard(message: String, onDismiss: () -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Fermer",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
private fun PrimaryControlBar(
    isListening: Boolean,
    isRunning: Boolean,
    isRecording: Boolean,
    sessionSeconds: Long,
    recordingSeconds: Long,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onStartRecord: () -> Unit,
    onStopRecord: () -> Unit
) {
    val sessionTimerStr = String.format(Locale.getDefault(), "%02d:%02d", sessionSeconds / 60, sessionSeconds % 60)
    val recTimerStr = String.format(Locale.getDefault(), "%02d:%02d", recordingSeconds / 60, recordingSeconds % 60)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Live Listening Button
        Button(
            onClick = if (isRunning) onStopListening else onStartListening,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRunning && !isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .weight(1f)
                .height(58.dp)
                .testTag("toggle_studio_audio_button")
        ) {
            Icon(
                imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = if (isRunning) stringResource(R.string.studio_btn_stop) else stringResource(R.string.studio_btn_start),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1
                )
                if (isRunning) {
                    Text(
                        text = sessionTimerStr,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)
                    )
                }
            }
        }

        // Record Button
        Button(
            onClick = if (isRecording) onStopRecord else onStartRecord,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRecording) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                contentColor = if (isRecording) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .weight(1f)
                .height(58.dp)
                .testTag("toggle_studio_record_button")
        ) {
            Icon(
                imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                contentDescription = null,
                tint = if (isRecording) MaterialTheme.colorScheme.error else Color.Red
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = if (isRecording) stringResource(R.string.studio_btn_stop_record) else stringResource(R.string.studio_btn_record),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1
                )
                if (isRecording) {
                    Text(
                        text = recTimerStr,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)
                    )
                }
            }
        }
    }
}

@Composable
private fun InstantRecordingPreviewCard(
    recording: RecordingEntity,
    isPlaying: Boolean,
    progress: Float,
    onPlayPause: () -> Unit,
    onOpenLibrary: () -> Unit,
    onDismiss: () -> Unit
) {
    val durationSec = recording.durationMs / 1000
    val durationStr = String.format(Locale.getDefault(), "%02d:%02d", durationSec / 60, durationSec % 60)

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        ),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("instant_recording_preview_card")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = EmeraldGreen,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.studio_last_recording_title),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Fermer",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(46.dp)
                        .clickable(onClick = onPlayPause)
                        .testTag("preview_play_pause_button")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Écouter",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = recording.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$durationStr • ${recording.effectName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isPlaying || progress > 0f) {
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                        )
                    }
                }

                TextButton(onClick = onOpenLibrary) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Gérer")
                }
            }
        }
    }
}

@Composable
private fun SpeakerStudioOptionsCard(
    monitorDuringRecording: Boolean,
    antiFeedbackActive: Boolean,
    onToggleMonitorDuringRecording: (Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (antiFeedbackActive) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.studio_speaker_mode_tip),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.studio_monitor_toggle_title),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.studio_monitor_toggle_desc),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Switch(
                    checked = monitorDuringRecording,
                    onCheckedChange = onToggleMonitorDuringRecording,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("toggle_monitor_during_recording")
                )
            }
        }
    }
}

@Composable
private fun EffectMiniTab(
    effect: VoiceEffect,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) NeonCyan else Color.Transparent,
        label = "mini_tab_border"
    )

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.5.dp, borderColor),
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag("tab_effect_${effect.id}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = effect.getIcon(),
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(effect.titleRes),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                ),
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SliderRow(
    title: String,
    value: Float,
    valueDisplay: String,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0.0f..1.0f
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = valueDisplay,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
