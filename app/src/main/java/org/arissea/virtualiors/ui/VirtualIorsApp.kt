package org.arissea.virtualiors.ui

import android.os.Build
import android.view.Window
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.arissea.virtualiors.camera.CameraCaptureController
import org.arissea.virtualiors.model.AppConfig
import org.arissea.virtualiors.model.SstvSourceType
import org.arissea.virtualiors.persistence.PresetRepository
import org.arissea.virtualiors.transmission.TransmissionEngine
import org.arissea.virtualiors.transmission.TransmissionUiState

private enum class AppScreen { ONBOARDING, OPTIONS, SETTINGS, TRANSMISSION }

@Composable
fun VirtualIorsApp(window: Window, cameraController: CameraCaptureController) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val scheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) darkColorScheme() else lightColorScheme()
    val preferences = remember { context.getSharedPreferences("virtual_iors_prefs", 0) }
    val repository = remember { PresetRepository(context) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val cameraState by cameraController.state.collectAsState()
    var config by remember { mutableStateOf(repository.loadCurrentConfig()) }
    var presets by remember { mutableStateOf(repository.load()) }
    var transmissionState by remember { mutableStateOf<TransmissionUiState?>(null) }
    var transmissionJob by remember { mutableStateOf<Job?>(null) }
    var returnAfterTransmission by remember { mutableStateOf(AppScreen.OPTIONS) }
    var screen by remember {
        mutableStateOf(
            if (preferences.getBoolean("onboarding_v2_complete", false)) AppScreen.OPTIONS else AppScreen.ONBOARDING,
        )
    }
    fun updateConfig(updated: AppConfig) {
        config = updated
        repository.saveCurrentConfig(updated)
    }

    BackHandler(enabled = screen == AppScreen.SETTINGS) {
        screen = AppScreen.OPTIONS
    }
    val engine = remember {
        TransmissionEngine(
            context = context,
            cameraController = cameraController,
            onState = { transmissionState = it },
            onMessage = { message -> scope.launch { snackbar.showSnackbar(message) } },
        )
    }

    fun startEngineTask(returnTo: AppScreen, task: suspend () -> Unit) {
        transmissionJob?.cancel()
        returnAfterTransmission = returnTo
        transmissionState = null
        screen = AppScreen.TRANSMISSION
        transmissionJob = scope.launch {
            try {
                task()
            } catch (_: CancellationException) {
                // Stop is a normal user action.
            } catch (error: Throwable) {
                snackbar.showSnackbar(error.message ?: "Transmission stopped")
            } finally {
                engine.stop()
                transmissionState = null
                if (screen == AppScreen.TRANSMISSION) screen = returnTo
            }
        }
    }

    LaunchedEffect(config.sourceType) {
        if (config.sourceType != SstvSourceType.AUTO_CAMERA) cameraController.unbind()
    }
    DisposableEffect(Unit) {
        onDispose {
            transmissionJob?.cancel()
            engine.shutdown()
            cameraController.release()
        }
    }
    val transmitting = screen == AppScreen.TRANSMISSION
    DisposableEffect(transmitting) {
        if (transmitting) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    MaterialTheme(colorScheme = scheme) {
        val barColor = MaterialTheme.colorScheme.background.toArgb()
        SideEffect {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
            window.statusBarColor = barColor
            window.navigationBarColor = barColor
        }
        Box(Modifier.fillMaxSize()) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxSize().systemBarsPadding()) {
                    VirtualIorsLogoCard()
                    AnimatedContent(
                        targetState = screen,
                        transitionSpec = { fadeIn(tween(500)) togetherWith fadeOut(tween(200)) },
                        modifier = Modifier.weight(1f),
                        label = "VirtualIORS content",
                    ) { target ->
                        when (target) {
                            AppScreen.ONBOARDING -> OnboardingScreen(
                                onContinue = {
                                    preferences.edit().putBoolean("onboarding_v2_complete", true).apply()
                                    screen = AppScreen.OPTIONS
                                },
                            )
                            AppScreen.OPTIONS -> OptionsScreen(
                                config = config,
                                presets = presets,
                                cameraReady = cameraState.ready,
                                cameraController = cameraController,
                                onConfigChange = ::updateConfig,
                                onOpenSettings = { screen = AppScreen.SETTINGS },
                                onLoadPreset = { updateConfig(it.config) },
                                onSavePreset = { name ->
                                    runCatching { repository.upsert(name, config) }
                                        .onSuccess { presets = repository.load() }
                                        .onFailure { scope.launch { snackbar.showSnackbar(it.message ?: "Preset could not be saved") } }
                                },
                                onDeletePreset = { preset ->
                                    repository.delete(preset)
                                    presets = repository.load()
                                },
                                onMessage = { scope.launch { snackbar.showSnackbar(it) } },
                                onStart = { startEngineTask(AppScreen.OPTIONS) { engine.run(config) } },
                            )
                            AppScreen.SETTINGS -> SettingsScreen(
                                config = config,
                                onConfigChange = ::updateConfig,
                                onTestRobot36 = {
                                    startEngineTask(AppScreen.SETTINGS) { engine.transmitRobot36Test(config) }
                                },
                                onTestAprs = {
                                    startEngineTask(AppScreen.SETTINGS) { engine.transmitAprsTest(config) }
                                },
                                onMessage = { scope.launch { snackbar.showSnackbar(it) } },
                                onBack = { screen = AppScreen.OPTIONS },
                            )
                            AppScreen.TRANSMISSION -> TransmissionScreen(
                                state = transmissionState,
                                onStop = {
                                    engine.stop()
                                    transmissionJob?.cancel()
                                    transmissionJob = null
                                    transmissionState = null
                                    screen = returnAfterTransmission
                                },
                            )
                        }
                    }
                }
            }
            SnackbarHost(
                snackbar,
                modifier = Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = if (screen == AppScreen.OPTIONS) 88.dp else 0.dp),
            )
        }
    }
}
