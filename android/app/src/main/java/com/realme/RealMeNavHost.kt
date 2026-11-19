package com.realme

import android.widget.Toast
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.realme.core.media.ScreenshotAccessibilityService
import com.realme.data.local.DailyCaptureReminder
import com.realme.core.util.HomeShortcut
import com.realme.core.util.QuickSettingsTile
import com.realme.feature.capture.CaptureScreen
import com.realme.feature.capture.CaptureViewModel
import com.realme.feature.editor.ImageEditorScreen
import com.realme.feature.editor.ImageEditorViewModel
import com.realme.feature.posting.PostingScreen
import com.realme.feature.posting.PostingViewModel
import com.realme.feature.profile.ProfileScreen
import com.realme.feature.profile.ProfileViewModel
import com.realme.feature.settings.AccountSecurityScreen
import com.realme.feature.settings.AutoPostSettingsScreen
import com.realme.feature.settings.CaptureSettingsScreen
import com.realme.feature.settings.DeleteAccountScreen
import com.realme.feature.settings.PrivacySettingsScreen
import com.realme.feature.settings.SettingsViewModel
import com.realme.feature.timeline.TimelineScreen
import com.realme.feature.timeline.TimelineViewModel

private object Routes {
    const val TIMELINE = "timeline"
    const val CAPTURES = "captures"
    const val PROFILE = "profile"
    const val POSTING = "posting"
    const val CAPTURE_POST = "capture_post/{captureId}"
    const val IMAGE_EDITOR = "image_editor"
    const val AUTO_POST = "settings/auto_post"
    const val PRIVACY = "settings/privacy"
    const val CAPTURE_SETTINGS = "settings/capture"
    const val ACCOUNT_SECURITY = "settings/security"
    const val DELETE_ACCOUNT = "settings/delete_account"
}

private data class BottomItem(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
fun RealMeNavHost(
    container: AppContainer,
    factory: RealMeViewModelFactory,
    sessionUserId: String,
    captureRequest: Int = 0,
    onCaptureRequestHandled: () -> Unit = {},
    captureToReview: String? = null,
    onCaptureReviewed: () -> Unit = {},
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val context = LocalContext.current
    val timelineViewModel: TimelineViewModel = viewModel(factory = factory)
    val captureViewModel: CaptureViewModel = viewModel(factory = factory)
    val profileViewModel: ProfileViewModel = viewModel(factory = factory)
    val postingViewModel: PostingViewModel = viewModel(factory = factory)
    val editorViewModel: ImageEditorViewModel = viewModel(factory = factory)
    val settingsViewModel: SettingsViewModel = viewModel(factory = factory)
    val bottomItems = listOf(
        BottomItem(Routes.TIMELINE, "タイムライン", Icons.Default.Home),
        BottomItem(Routes.CAPTURES, "ライブラリ", Icons.Default.PhotoLibrary),
        BottomItem(Routes.PROFILE, "プロフィール", Icons.Default.Person),
    )
    val bottomRoutes = bottomItems.map { it.route }
    LaunchedEffect(captureRequest) {
        if (captureRequest != 0) {
            navController.navigate(Routes.CAPTURES) { launchSingleTop = true }
        }
    }
    LaunchedEffect(captureToReview) {
        captureToReview?.let { id ->
            postingViewModel.loadCapture(id)
            navController.navigate("capture_post/$id") { launchSingleTop = true }
            onCaptureReviewed()
        }
    }

    Scaffold(
        bottomBar = {
            if (currentRoute in bottomRoutes) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    bottomItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(item.label) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
    ) { outerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.TIMELINE,
            modifier = Modifier.padding(outerPadding),
        ) {
            composable(Routes.TIMELINE) {
                val state by timelineViewModel.uiState.collectAsStateWithLifecycle()
                TimelineScreen(
                    state = state,
                    isDebug = BuildConfig.DEBUG,
                    onCreatePost = {
                        postingViewModel.prepareNew()
                        navController.navigate(Routes.POSTING)
                    },
                    onStartDemo = timelineViewModel::startDemo,
                    onDelete = timelineViewModel::delete,
                    onReplaceWithPrivacy = timelineViewModel::replaceWithPrivacy,
                    onClearMessage = timelineViewModel::clearMessage,
                    onRefreshClock = timelineViewModel::refreshClock,
                )
            }
            composable(Routes.CAPTURES) {
                val state by captureViewModel.uiState.collectAsStateWithLifecycle()
                CaptureScreen(
                    state = state,
                    startFromReminder = captureRequest != 0,
                    onReminderHandled = onCaptureRequestHandled,
                    onOpenSettings = { navController.navigate(Routes.CAPTURE_SETTINGS) },
                    onOpenCapture = {
                        postingViewModel.loadCapture(it)
                        navController.navigate("capture_post/$it")
                    },
                    onDelete = captureViewModel::delete,
                    onPublishNow = captureViewModel::publishNow,
                    onCancelDraft = captureViewModel::cancelDraft,
                    onAddHomeShortcut = { HomeShortcut.requestPin(context) },
                    onAddQuickSettingsTile = {
                        if (!QuickSettingsTile.requestAdd(context)) {
                            Toast.makeText(
                                context,
                                "Android 13以降で追加できます。手動でクイック設定に追加してください。",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    },
                    onClearMessage = captureViewModel::clearMessage,
                )
            }
            composable(Routes.PROFILE) {
                val state by profileViewModel.uiState.collectAsStateWithLifecycle()
                ProfileScreen(
                    state = state,
                    onSave = profileViewModel::save,
                    onOpenAutoPost = { navController.navigate(Routes.AUTO_POST) },
                    onOpenPrivacy = { navController.navigate(Routes.PRIVACY) },
                    onOpenCaptureSettings = { navController.navigate(Routes.CAPTURE_SETTINGS) },
                    onOpenSecurity = { navController.navigate(Routes.ACCOUNT_SECURITY) },
                    onOpenDeleteAccount = { navController.navigate(Routes.DELETE_ACCOUNT) },
                    onSignOut = {
                        ScreenshotAccessibilityService.hideOverlay(context)
                        profileViewModel.signOut()
                    },
                    onClearMessage = profileViewModel::clearMessage,
                )
            }
            composable(Routes.POSTING) {
                val state by postingViewModel.uiState.collectAsStateWithLifecycle()
                PostingScreen(
                    state = state,
                    onDiscard = { navController.popBackStack() },
                    onSelectImage = postingViewModel::selectImage,
                    onCaptionChange = postingViewModel::updateCaption,
                    onPublish = postingViewModel::publish,
                    onPublished = { navigateToTimeline(navController) },
                    onEditImage = {
                        val selected = state.selectedImagePath
                        if (selected != null) {
                            editorViewModel.load(selected, state.captureId)
                            navController.navigate(Routes.IMAGE_EDITOR)
                        }
                    },
                    allowTestImagePicker = BuildConfig.DEBUG,
                )
            }
            composable(
                route = Routes.CAPTURE_POST,
                arguments = listOf(navArgument("captureId") { type = NavType.StringType }),
            ) {
                val state by postingViewModel.uiState.collectAsStateWithLifecycle()
                PostingScreen(
                    state = state,
                    onDiscard = {
                        postingViewModel.discardAutoPost()
                        navController.popBackStack()
                    },
                    onSelectImage = postingViewModel::selectImage,
                    onCaptionChange = postingViewModel::updateCaption,
                    onPublish = postingViewModel::publish,
                    onPublished = { navigateToTimeline(navController) },
                    onEditImage = {
                        val selected = state.selectedImagePath
                        if (selected != null) {
                            editorViewModel.load(selected, state.captureId)
                            navController.navigate(Routes.IMAGE_EDITOR)
                        }
                    },
                    allowTestImagePicker = BuildConfig.DEBUG,
                )
            }
            composable(Routes.IMAGE_EDITOR) {
                val state by editorViewModel.uiState.collectAsStateWithLifecycle()
                ImageEditorScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onFilter = editorViewModel::setFilter,
                    onCrop = editorViewModel::setCrop,
                    onFreeCrop = editorViewModel::setFreeCrop,
                    onEffect = editorViewModel::setEffect,
                    onEffectRect = editorViewModel::setEffectRect,
                    onUndo = editorViewModel::undo,
                    onAddText = editorViewModel::addText,
                    onCycleTextColor = editorViewModel::cycleTextColor,
                    onMoveText = editorViewModel::moveText,
                    onAddStamp = editorViewModel::addStamp,
                    onMoveStamp = editorViewModel::moveStamp,
                    onScaleStamp = editorViewModel::scaleLastStamp,
                    onRotateStamp = editorViewModel::rotateLastStamp,
                    onSave = editorViewModel::save,
                    onSaved = { path ->
                        postingViewModel.applyEditedImage(path)
                        navController.popBackStack()
                    },
                    onClearError = editorViewModel::clearError,
                )
            }
            composable(Routes.DELETE_ACCOUNT) {
                val state by settingsViewModel.uiState.collectAsStateWithLifecycle()
                DeleteAccountScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onDelete = settingsViewModel::deleteAccount,
                    onClearMessage = settingsViewModel::clearMessage,
                )
            }
            composable(Routes.AUTO_POST) {
                val state by settingsViewModel.uiState.collectAsStateWithLifecycle()
                AutoPostSettingsScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onSelect = settingsViewModel::setAutoPostMinutes,
                    onClearMessage = settingsViewModel::clearMessage,
                )
            }
            composable(Routes.PRIVACY) {
                val state by settingsViewModel.uiState.collectAsStateWithLifecycle()
                PrivacySettingsScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onSelectImage = settingsViewModel::selectPrivacyImage,
                    onPublish = settingsViewModel::publishPrivacy,
                    onPublished = {
                        settingsViewModel.consumePublished()
                        navigateToTimeline(navController)
                    },
                    onClearMessage = settingsViewModel::clearMessage,
                )
            }
            composable(Routes.CAPTURE_SETTINGS) {
                val state by settingsViewModel.uiState.collectAsStateWithLifecycle()
                CaptureSettingsScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onSave = settingsViewModel::setOverlayAppearance,
                    onClearMessage = settingsViewModel::clearMessage,
                )
            }
            composable(Routes.ACCOUNT_SECURITY) {
                val state by settingsViewModel.uiState.collectAsStateWithLifecycle()
                AccountSecurityScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onSavePin = settingsViewModel::saveAccountPin,
                    onRemovePin = settingsViewModel::removeAccountPin,
                    onClearMessage = settingsViewModel::clearMessage,
                )
            }
        }
    }
}

private fun navigateToTimeline(navController: androidx.navigation.NavHostController) {
    navController.navigate(Routes.TIMELINE) {
        popUpTo(Routes.TIMELINE) { inclusive = false }
        launchSingleTop = true
    }
}


