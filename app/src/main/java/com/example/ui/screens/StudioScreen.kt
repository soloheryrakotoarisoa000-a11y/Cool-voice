package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.audio.AudioEngineState
import com.example.audio.effects.VoiceEffect
import com.example.ui.components.EffectSelectorCard
import com.example.ui.components.WaveformVisualizer
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.NeonCyan
import java.util.Locale

@Composable
fun StudioScreen(
    engineState: AudioEngineState,
    visualizerData: FloatArray,
    onStartEngine: () -> Unit,
    onStopEngine: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onSelectEffect: (VoiceEffect) -> Unit,
    onIntensityChange: (Float) -> Unit,
    onPitchChange: (Float) -> Unit,
    onDepthChange: (Float) -> Unit,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var intensity by remember { mutableFloatStateOf(engineState.activeEffect.defaultIntensity) }
    var pitchFactor by remember { mutableFloatStateOf(engineState.activeEffect.defaultPitchFactor) }
    var depth by remember { mutableFloatStateOf(engineState.activeEffect.defaultDepth) }
    var outputVolume by remember { mutableFloatStateOf(1.0f) }
    var showAdvancedParams by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Status header & device indicator
        item {
            Spacer(modifier = Modifier.height(4.dp))
            StatusAndDeviceBar(engineState = engineState)
        }

        // Real-time Waveform Visualizer
        item {
            WaveformVisualizer(
                visualizerData = visualizerData,
                isActive = engineState.isRunning
            )
        }

        // Live Controls: Start/Stop & Record
        item {
            PrimaryControlBar(
                isRunning = engineState.isRunning,
                isRecording = engineState.isRecording,
                sessionSeconds = engineState.sessionDurationSeconds,
                recordingSeconds = engineState.recordingDurationSeconds,
                onStart = onStartEngine,
                onStop = onStopEngine,
                onStartRecord = onStartRecording,
                onStopRecord = onStopRecording
            )
        }

        // Feedback / Larsen warning banner if on phone speaker
        if (engineState.isFeedbackProtectionActive) {
            item {
                FeedbackWarningBanner()
            }
        }

        // 12 Effects selector Carousel / Grid
        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Effet Vocal Sélectionné",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "${VoiceEffect.values().indexOf(engineState.activeEffect) + 1}/12",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(VoiceEffect.values()) { effect ->
                        EffectMiniTab(
                            effect = effect,
                            isSelected = effect == engineState.activeEffect,
                            onClick = {
                                onSelectEffect(effect)
                                intensity = effect.defaultIntensity
                                pitchFactor = effect.defaultPitchFactor
                                depth = effect.defaultDepth
                            }
                        )
                    }
                }
            }
        }

        // Active Effect Details Card
        item {
            EffectSelectorCard(
                effect = engineState.activeEffect,
                isSelected = true,
                onClick = {}
            )
        }

        // Sliders Section
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
                            text = "Ajustements Sonores",
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

                    Spacer(modifier = Modifier.height(14.dp))

                    // Intensity Slider
                    SliderRow(
                        title = stringResource(R.string.studio_param_intensity),
                        value = intensity,
                        valueDisplay = "${(intensity * 100).toInt()}%",
                        onValueChange = {
                            intensity = it
                            onIntensityChange(it)
                        }
                    )

                    // Output Volume Slider
                    SliderRow(
                        title = stringResource(R.string.studio_param_output_vol),
                        value = outputVolume,
                        valueRange = 0.0f..1.5f,
                        valueDisplay = "${(outputVolume * 100).toInt()}%",
                        onValueChange = {
                            outputVolume = it
                            onVolumeChange(it)
                        }
                    )

                    AnimatedVisibility(visible = showAdvancedParams) {
                        Column {
                            Spacer(modifier = Modifier.height(10.dp))

                            // Pitch Slider
                            SliderRow(
                                title = stringResource(R.string.studio_param_pitch),
                                value = pitchFactor,
                                valueRange = 0.5f..2.0f,
                                valueDisplay = String.format(Locale.getDefault(), "%.2fx", pitchFactor),
                                onValueChange = {
                                    pitchFactor = it
                                    onPitchChange(it)
                                }
                            )

                            // Depth Slider
                            SliderRow(
                                title = stringResource(R.string.studio_param_depth),
                                value = depth,
                                valueDisplay = "${(depth * 100).toInt()}%",
                                onValueChange = {
                                    depth = it
                                    onDepthChange(it)
                                }
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            OutlinedButton(
                                onClick = {
                                    intensity = engineState.activeEffect.defaultIntensity
                                    pitchFactor = engineState.activeEffect.defaultPitchFactor
                                    depth = engineState.activeEffect.defaultDepth
                                    outputVolume = 1.0f
                                    onIntensityChange(intensity)
                                    onPitchChange(pitchFactor)
                                    onDepthChange(depth)
                                    onVolumeChange(outputVolume)
                                },
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .testTag("reset_params_button")
                            ) {
                                Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Réinitialiser")
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StatusAndDeviceBar(engineState: AudioEngineState) {
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = if (engineState.isRunning) EmeraldGreen else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    modifier = Modifier.size(12.dp)
                ) {}

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = if (engineState.isRunning) {
                        stringResource(R.string.studio_status_active)
                    } else {
                        stringResource(R.string.studio_status_ready)
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
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
private fun PrimaryControlBar(
    isRunning: Boolean,
    isRecording: Boolean,
    sessionSeconds: Long,
    recordingSeconds: Long,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onStartRecord: () -> Unit,
    onStopRecord: () -> Unit
) {
    val sessionTimerStr = String.format(Locale.getDefault(), "%02d:%02d", sessionSeconds / 60, sessionSeconds % 60)
    val recTimerStr = String.format(Locale.getDefault(), "%02d:%02d", recordingSeconds / 60, recordingSeconds % 60)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Start / Stop Live Audio Button
        Button(
            onClick = if (isRunning) onStop else onStart,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .weight(1f)
                .height(54.dp)
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
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
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
                .height(54.dp)
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
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
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
private fun FeedbackWarningBanner() {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.studio_feedback_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
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
        border = androidx.compose.foundation.BorderStroke(1.5.dp, borderColor),
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
