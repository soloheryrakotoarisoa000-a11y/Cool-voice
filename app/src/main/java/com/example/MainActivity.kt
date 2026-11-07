package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.audio.effects.VoiceEffect
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StudioScreen
import com.example.ui.theme.CoolVoiceTheme
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.NeonCyan

enum class AppScreen {
    HOME, STUDIO, LIBRARY, SETTINGS
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val userSettings by viewModel.userSettings.collectAsState()

            CoolVoiceTheme(themeMode = userSettings.themeMode) {
                MainAppScaffold(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainAppScaffold(viewModel: MainViewModel) {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf(AppScreen.HOME) }
    var showPermissionRationale by remember { mutableStateOf(false) }
    var pendingActionAfterPermission by remember { mutableStateOf<(() -> Unit)?>(null) }

    val engineState by viewModel.engineState.collectAsState()
    val visualizerData by viewModel.visualizerData.collectAsState()
    val recordings by viewModel.recordings.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val userSettings by viewModel.userSettings.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            pendingActionAfterPermission?.invoke()
            pendingActionAfterPermission = null
        }
    }

    val requestMicPermission = { onGranted: () -> Unit ->
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            onGranted()
        } else {
            pendingActionAfterPermission = onGranted
            showPermissionRationale = true
        }
    }

    // Back button handling
    BackHandler(enabled = currentScreen != AppScreen.HOME) {
        currentScreen = AppScreen.HOME
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .testTag("bottom_nav_bar"),
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = currentScreen == AppScreen.HOME,
                    onClick = { currentScreen = AppScreen.HOME },
                    icon = {
                        Icon(
                            imageVector = if (currentScreen == AppScreen.HOME) Icons.Filled.Home else Icons.Outlined.Home,
                            contentDescription = stringResource(R.string.nav_home)
                        )
                    },
                    label = { Text(stringResource(R.string.nav_home)) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.testTag("nav_item_home")
                )

                NavigationBarItem(
                    selected = currentScreen == AppScreen.STUDIO,
                    onClick = { currentScreen = AppScreen.STUDIO },
                    icon = {
                        Box {
                            Icon(
                                imageVector = if (currentScreen == AppScreen.STUDIO) Icons.Filled.Mic else Icons.Outlined.Mic,
                                contentDescription = stringResource(R.string.nav_studio)
                            )
                            if (engineState.isRunning) {
                                Surface(
                                    shape = CircleShape,
                                    color = EmeraldGreen,
                                    modifier = Modifier
                                        .size(8.dp)
                                        .align(Alignment.TopEnd)
                                ) {}
                            }
                        }
                    },
                    label = { Text(stringResource(R.string.nav_studio)) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.testTag("nav_item_studio")
                )

                NavigationBarItem(
                    selected = currentScreen == AppScreen.LIBRARY,
                    onClick = { currentScreen = AppScreen.LIBRARY },
                    icon = {
                        Icon(
                            imageVector = if (currentScreen == AppScreen.LIBRARY) Icons.Filled.Folder else Icons.Outlined.Folder,
                            contentDescription = stringResource(R.string.nav_library)
                        )
                    },
                    label = { Text(stringResource(R.string.nav_library)) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.testTag("nav_item_library")
                )

                NavigationBarItem(
                    selected = currentScreen == AppScreen.SETTINGS,
                    onClick = { currentScreen = AppScreen.SETTINGS },
                    icon = {
                        Icon(
                            imageVector = if (currentScreen == AppScreen.SETTINGS) Icons.Filled.Settings else Icons.Outlined.Settings,
                            contentDescription = stringResource(R.string.nav_settings)
                        )
                    },
                    label = { Text(stringResource(R.string.nav_settings)) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.testTag("nav_item_settings")
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            // App Top Header
            AppTopHeader(
                currentScreen = currentScreen,
                isAudioRunning = engineState.isRunning,
                isRecording = engineState.isRecording
            )

            // Screens
            when (currentScreen) {
                AppScreen.HOME -> HomeScreen(
                    onNavigateToStudio = { initialEffect ->
                        if (initialEffect != null) {
                            viewModel.selectEffect(initialEffect)
                        }
                        currentScreen = AppScreen.STUDIO
                    },
                    onNavigateToLibrary = { currentScreen = AppScreen.LIBRARY },
                    recentRecordings = recordings,
                    onPlayRecording = { rec ->
                        viewModel.playRecording(rec)
                        currentScreen = AppScreen.LIBRARY
                    }
                )

                AppScreen.STUDIO -> StudioScreen(
                    engineState = engineState,
                    visualizerData = visualizerData,
                    onStartEngine = {
                        requestMicPermission {
                            viewModel.startAudio()
                        }
                    },
                    onStopEngine = { viewModel.stopAudio() },
                    onStartRecording = {
                        requestMicPermission {
                            viewModel.startRecording()
                        }
                    },
                    onStopRecording = { viewModel.stopRecording() },
                    onSelectEffect = { effect -> viewModel.selectEffect(effect) },
                    onIntensityChange = { viewModel.setIntensity(it) },
                    onPitchChange = { viewModel.setPitchFactor(it) },
                    onDepthChange = { viewModel.setDepth(it) },
                    onVolumeChange = { viewModel.setVolume(it) }
                )

                AppScreen.LIBRARY -> LibraryScreen(
                    recordings = recordings,
                    playbackState = playbackState,
                    onPlay = { rec -> viewModel.playRecording(rec) },
                    onPause = { viewModel.pausePlayback() },
                    onSeek = { pos -> viewModel.seekPlayback(pos) },
                    onRename = { rec, title -> viewModel.renameRecording(rec, title) },
                    onDelete = { rec -> viewModel.deleteRecording(rec) },
                    onOpenStudio = { currentScreen = AppScreen.STUDIO }
                )

                AppScreen.SETTINGS -> SettingsScreen(
                    userSettings = userSettings,
                    onThemeSelected = { viewModel.setThemeMode(it) },
                    onSampleRateSelected = { viewModel.setSampleRate(it) },
                    onAntiFeedbackToggled = { viewModel.setAntiFeedback(it) }
                )
            }
        }
    }

    // Permission Rationale Dialog
    if (showPermissionRationale) {
        AlertDialog(
            onDismissRequest = { showPermissionRationale = false },
            title = { Text(text = stringResource(R.string.permission_mic_title)) },
            text = { Text(text = stringResource(R.string.permission_mic_desc)) },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionRationale = false
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    modifier = Modifier.testTag("grant_permission_confirm_button")
                ) {
                    Text(stringResource(R.string.permission_mic_btn))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showPermissionRationale = false },
                    modifier = Modifier.testTag("grant_permission_cancel_button")
                ) {
                    Text(stringResource(R.string.library_action_cancel))
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
private fun AppTopHeader(
    currentScreen: AppScreen,
    isAudioRunning: Boolean,
    isRecording: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(34.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_cool_voice_logo),
                    contentDescription = null,
                    modifier = Modifier.padding(4.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = when (currentScreen) {
                        AppScreen.HOME -> "Tableau de Bord"
                        AppScreen.STUDIO -> "Studio DSP en direct"
                        AppScreen.LIBRARY -> "Mes Fichiers WAV"
                        AppScreen.SETTINGS -> "Configuration & À propos"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Live badge
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isRecording) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.padding(end = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Red,
                            modifier = Modifier.size(8.dp)
                        ) {}
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "REC",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            } else if (isAudioRunning) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = EmeraldGreen,
                            modifier = Modifier.size(8.dp)
                        ) {}
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "DIRECT",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "HORS-LIGNE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}
