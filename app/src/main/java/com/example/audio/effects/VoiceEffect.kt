package com.example.audio.effects

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WavingHand
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.R

/**
 * The 12 mandatory vocal transformation effects for COOL VOICE.
 */
enum class VoiceEffect(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val descRes: Int,
    val defaultPitchFactor: Float,
    val defaultIntensity: Float,
    val defaultDepth: Float
) {
    ROBOT(
        id = "robot",
        titleRes = R.string.fx_robot_title,
        descRes = R.string.fx_robot_desc,
        defaultPitchFactor = 1.0f,
        defaultIntensity = 0.85f,
        defaultDepth = 0.70f
    ),
    FEMME(
        id = "femme",
        titleRes = R.string.fx_woman_title,
        descRes = R.string.fx_woman_desc,
        defaultPitchFactor = 1.35f,
        defaultIntensity = 0.80f,
        defaultDepth = 0.60f
    ),
    HOMME(
        id = "homme",
        titleRes = R.string.fx_man_title,
        descRes = R.string.fx_man_desc,
        defaultPitchFactor = 0.75f,
        defaultIntensity = 0.80f,
        defaultDepth = 0.65f
    ),
    ENFANT(
        id = "enfant",
        titleRes = R.string.fx_child_title,
        descRes = R.string.fx_child_desc,
        defaultPitchFactor = 1.45f,
        defaultIntensity = 0.85f,
        defaultDepth = 0.50f
    ),
    MONSTRE(
        id = "monstre",
        titleRes = R.string.fx_monster_title,
        descRes = R.string.fx_monster_desc,
        defaultPitchFactor = 0.60f,
        defaultIntensity = 0.90f,
        defaultDepth = 0.85f
    ),
    GRAVE(
        id = "grave",
        titleRes = R.string.fx_deep_title,
        descRes = R.string.fx_deep_desc,
        defaultPitchFactor = 0.70f,
        defaultIntensity = 0.80f,
        defaultDepth = 0.75f
    ),
    AIGUE(
        id = "aigue",
        titleRes = R.string.fx_high_title,
        descRes = R.string.fx_high_desc,
        defaultPitchFactor = 1.60f,
        defaultIntensity = 0.85f,
        defaultDepth = 0.50f
    ),
    ECHO(
        id = "echo",
        titleRes = R.string.fx_echo_title,
        descRes = R.string.fx_echo_desc,
        defaultPitchFactor = 1.0f,
        defaultIntensity = 0.75f,
        defaultDepth = 0.80f
    ),
    EXTRATERRESTRE(
        id = "extraterrestre",
        titleRes = R.string.fx_alien_title,
        descRes = R.string.fx_alien_desc,
        defaultPitchFactor = 1.15f,
        defaultIntensity = 0.90f,
        defaultDepth = 0.75f
    ),
    DESSIN_ANIME(
        id = "dessin_anime",
        titleRes = R.string.fx_cartoon_title,
        descRes = R.string.fx_cartoon_desc,
        defaultPitchFactor = 1.50f,
        defaultIntensity = 0.85f,
        defaultDepth = 0.65f
    ),
    PERSONNE_AGEE(
        id = "personne_agee",
        titleRes = R.string.fx_elderly_title,
        descRes = R.string.fx_elderly_desc,
        defaultPitchFactor = 0.92f,
        defaultIntensity = 0.75f,
        defaultDepth = 0.70f
    ),
    BEBE(
        id = "bebe",
        titleRes = R.string.fx_baby_title,
        descRes = R.string.fx_baby_desc,
        defaultPitchFactor = 1.75f,
        defaultIntensity = 0.85f,
        defaultDepth = 0.60f
    );

    fun getIcon(): ImageVector {
        return when (this) {
            ROBOT -> Icons.Default.SmartToy
            FEMME -> Icons.Default.Face
            HOMME -> Icons.Default.Accessibility
            ENFANT -> Icons.Default.ChildCare
            MONSTRE -> Icons.Default.Warning
            GRAVE -> Icons.Default.VolumeUp
            AIGUE -> Icons.Default.MusicNote
            ECHO -> Icons.Default.Hearing
            EXTRATERRESTRE -> Icons.Default.Public
            DESSIN_ANIME -> Icons.Default.EmojiEmotions
            PERSONNE_AGEE -> Icons.Default.WavingHand
            BEBE -> Icons.Default.RecordVoiceOver
        }
    }
}
