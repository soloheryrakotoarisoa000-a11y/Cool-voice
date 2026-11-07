package com.example.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.R
import com.example.audio.PlaybackState
import com.example.database.RecordingEntity
import com.example.ui.components.DeleteConfirmDialog
import com.example.ui.components.RenameDialog
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LibraryScreen(
    recordings: List<RecordingEntity>,
    playbackState: PlaybackState,
    onPlay: (RecordingEntity) -> Unit,
    onPause: () -> Unit,
    onSeek: (Int) -> Unit,
    onRename: (RecordingEntity, String) -> Unit,
    onDelete: (RecordingEntity) -> Unit,
    onOpenStudio: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var recordingToRename by remember { mutableStateOf<RecordingEntity?>(null) }
    var recordingToDelete by remember { mutableStateOf<RecordingEntity?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp)
    ) {
        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = stringResource(R.string.library_title),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "${recordings.size} enregistrement(s) sauvegardé(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (recordings.isEmpty()) {
            EmptyLibraryState(onOpenStudio = onOpenStudio)
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(recordings, key = { it.id }) { item ->
                    val isCurrentTrack = playbackState.currentRecordingId == item.id
                    val isPlayingThis = isCurrentTrack && playbackState.isPlaying

                    RecordingCardItem(
                        recording = item,
                        isPlaying = isPlayingThis,
                        isCurrentTrack = isCurrentTrack,
                        currentPositionMs = if (isCurrentTrack) playbackState.currentPositionMs else 0,
                        durationMs = if (isCurrentTrack && playbackState.durationMs > 0) playbackState.durationMs else item.durationMs.toInt(),
                        onPlayClick = {
                            if (isPlayingThis) {
                                onPause()
                            } else {
                                onPlay(item)
                            }
                        },
                        onSeek = onSeek,
                        onRenameClick = { recordingToRename = item },
                        onDeleteClick = { recordingToDelete = item },
                        onShareClick = { shareAudioFile(context, item) }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    // Rename dialog
    recordingToRename?.let { rec ->
        RenameDialog(
            initialTitle = rec.title,
            onDismiss = { recordingToRename = null },
            onConfirm = { newTitle ->
                onRename(rec, newTitle)
                recordingToRename = null
            }
        )
    }

    // Delete confirmation dialog
    recordingToDelete?.let { rec ->
        DeleteConfirmDialog(
            recordingTitle = rec.title,
            onDismiss = { recordingToDelete = null },
            onConfirm = {
                onDelete(rec)
                recordingToDelete = null
            }
        )
    }
}

@Composable
private fun RecordingCardItem(
    recording: RecordingEntity,
    isPlaying: Boolean,
    isCurrentTrack: Boolean,
    currentPositionMs: Int,
    durationMs: Int,
    onPlayClick: () -> Unit,
    onSeek: (Int) -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onShareClick: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    val dateStr = dateFormat.format(Date(recording.createdAt))
    val sizeKb = recording.fileSizeBytes / 1024
    val sizeStr = if (sizeKb > 1024) String.format(Locale.getDefault(), "%.1f Mo", sizeKb / 1024f) else "$sizeKb Ko"

    val durationSec = durationMs / 1000
    val durationText = String.format(Locale.getDefault(), "%02d:%02d", durationSec / 60, durationSec % 60)

    val currentSec = currentPositionMs / 1000
    val currentText = String.format(Locale.getDefault(), "%02d:%02d", currentSec / 60, currentSec % 60)

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        ),
        border = androidx.compose.foundation.BorderStroke(
            if (isPlaying) 1.5.dp else 1.dp,
            if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("recording_item_${recording.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .size(46.dp)
                        .clickable(onClick = onPlayClick)
                        .testTag("play_recording_${recording.id}")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Lire",
                        tint = if (isPlaying) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = recording.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$dateStr • $sizeStr",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = recording.effectName,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Player progress bar if current track
            AnimatedVisibility(visible = isCurrentTrack) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Slider(
                        value = currentPositionMs.toFloat().coerceIn(0f, durationMs.toFloat().coerceAtLeast(1f)),
                        onValueChange = { onSeek(it.toInt()) },
                        valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = currentText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = durationText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action buttons row: Rename, Share, Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onRenameClick,
                    modifier = Modifier.testTag("rename_button_${recording.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.library_rename),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onShareClick,
                    modifier = Modifier.testTag("share_button_${recording.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = stringResource(R.string.library_share),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.testTag("delete_button_${recording.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = stringResource(R.string.library_delete),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyLibraryState(onOpenStudio: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(80.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(18.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = stringResource(R.string.library_empty_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.library_empty_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onOpenStudio,
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(imageVector = Icons.Default.Mic, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Enregistrer ma voix")
            }
        }
    }
}

private fun shareAudioFile(context: Context, recording: RecordingEntity) {
    val file = File(recording.filePath)
    if (!file.exists()) return

    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/wav"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, recording.title)
            putExtra(Intent.EXTRA_TEXT, "Écoute ma voix transformée avec COOL VOICE (${recording.effectName}) !")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(
            Intent.createChooser(shareIntent, context.getString(R.string.library_share_title))
        )
    } catch (e: Exception) {
        android.util.Log.e("LibraryScreen", "Failed to share audio file", e)
    }
}
