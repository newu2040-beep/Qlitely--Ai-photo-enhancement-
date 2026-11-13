package com.example

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.core.data.AppearanceSettings
import com.example.core.data.QltyMainViewModel
import com.example.core.data.ThemePreferencesStore
import com.example.core.data.configureAppCheck
import com.example.core.designsystem.QltyBottomNav
import com.example.core.designsystem.QltyColors
import com.example.core.designsystem.QltyNavItem
import com.example.core.designsystem.QltySpace
import com.example.core.designsystem.QltyToast
import com.example.core.domain.EnhancementPreset
import com.example.core.image.NotificationHelper
import com.example.feature.auth.WelcomeAuthScreen
import com.example.feature.auth.authStateFlow
import com.example.feature.auth.signOutUser
import com.example.feature.editor.EditorScreen
import com.example.feature.home.HomeScreen
import com.example.feature.library.LibraryScreen
import com.example.feature.result.ResultScreen
import com.example.feature.settings.SettingsScreen
import com.example.feature.tools.ToolsScreen
import com.example.ui.theme.QLITEYTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

private object QltyRoutes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val TOOLS = "tools"
    const val SETTINGS = "settings"
    const val EDITOR = "editor"
    const val RESULT = "result"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mandatory: Configure App Check with launch intent BEFORE Compose UI or ViewModels
        configureAppCheck(this, intent)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        setContent {
            val context = LocalContext.current
            val themeStore = remember(context) { ThemePreferencesStore(context.applicationContext) }
            val appearance by themeStore.appearanceFlow.collectAsStateWithLifecycle(
                initialValue = AppearanceSettings()
            )

            LaunchedEffect(appearance.isDarkMode) {
                val style = if (appearance.isDarkMode) {
                    SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT
                    )
                }
                enableEdgeToEdge(
                    statusBarStyle = style,
                    navigationBarStyle = style
                )
            }

            QLITEYTheme(
                preset = appearance.themePreset,
                darkTheme = appearance.isDarkMode
            ) {
                AppNavigation(
                    appearance = appearance,
                    themeStore = themeStore
                )
            }
        }
    }
}

@Composable
fun AppNavigation(
    appearance: AppearanceSettings,
    themeStore: ThemePreferencesStore
) {
    val auth = Firebase.auth
    val currentUser by auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = auth.currentUser)

    val user = currentUser
    if (user == null) {
        WelcomeAuthScreen(
            onAuthSuccess = { /* AuthStateListener drives navigation */ }
        )
    } else {
        key(user.uid) {
            QltyAuthenticatedApp(
                user = user,
                appearance = appearance,
                themeStore = themeStore
            )
        }
    }
}

@Composable
private fun QltyAuthenticatedApp(
    user: FirebaseUser,
    appearance: AppearanceSettings,
    themeStore: ThemePreferencesStore
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: QltyMainViewModel = viewModel(
        key = user.uid,
        factory = QltyMainViewModel.provideFactory(context, user)
    )

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val libraryState by viewModel.libraryState.collectAsStateWithLifecycle()
    val jobs by viewModel.jobsState.collectAsStateWithLifecycle()
    val editorState by viewModel.editorState.collectAsStateWithLifecycle()
    val resultState by viewModel.resultState.collectAsStateWithLifecycle()
    val toast by viewModel.toastMessage.collectAsStateWithLifecycle()

    var notificationGranted by remember {
        mutableStateOf(NotificationHelper.hasNotificationPermission(context))
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationGranted = granted || NotificationHelper.hasNotificationPermission(context)
        if (granted) {
            NotificationHelper.ensureChannel(context)
            viewModel.showToast("Notifications enabled")
        }
    }

    // Request POST_NOTIFICATIONS cleanly once on Android 13+ upon entering Studio
    LaunchedEffect(appearance.hasPromptedPermissions) {
        if (!appearance.hasPromptedPermissions) {
            themeStore.markPermissionsPrompted()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !NotificationHelper.hasNotificationPermission(context)
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.importPickedImage(uri) {
                navController.navigate(QltyRoutes.EDITOR) {
                    launchSingleTop = true
                }
            }
        }
    }

    val fileBrowserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importPickedImage(uri) {
                navController.navigate(QltyRoutes.EDITOR) {
                    launchSingleTop = true
                }
            }
        }
    }

    val saveCopyLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/*")
    ) { uri ->
        if (uri != null) {
            viewModel.saveCopyToUri(uri)
        }
    }

    val bottomNavItems = remember {
        listOf(
            QltyNavItem(
                route = QltyRoutes.HOME,
                label = "Home",
                selectedIcon = Icons.Rounded.Home,
                unselectedIcon = Icons.Outlined.Home
            ),
            QltyNavItem(
                route = QltyRoutes.LIBRARY,
                label = "Library",
                selectedIcon = Icons.Rounded.PhotoLibrary,
                unselectedIcon = Icons.Outlined.PhotoLibrary
            ),
            QltyNavItem(
                route = QltyRoutes.TOOLS,
                label = "Tools",
                selectedIcon = Icons.Rounded.Build,
                unselectedIcon = Icons.Outlined.Build
            ),
            QltyNavItem(
                route = QltyRoutes.SETTINGS,
                label = "Settings",
                selectedIcon = Icons.Rounded.Settings,
                unselectedIcon = Icons.Outlined.Settings
            )
        )
    }

    val showBottomBar = currentRoute in setOf(
        QltyRoutes.HOME,
        QltyRoutes.LIBRARY,
        QltyRoutes.TOOLS,
        QltyRoutes.SETTINGS
    )

    fun launchPicker(preset: EnhancementPreset? = null) {
        viewModel.setPendingPreset(preset)
        photoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    fun launchFileBrowser(preset: EnhancementPreset? = null) {
        viewModel.setPendingPreset(preset)
        fileBrowserLauncher.launch(arrayOf("image/*"))
    }

    fun requestNotificationsOrOpenSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (NotificationHelper.hasNotificationPermission(context)) {
                notificationGranted = true
                viewModel.showToast("Notification permission is already granted")
            } else {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
            context.startActivity(intent)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(QltyColors.Bg)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                if (showBottomBar) {
                    QltyBottomNav(
                        items = bottomNavItems,
                        currentRoute = currentRoute,
                        onSelect = { target ->
                            navController.navigate(target) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = QltyRoutes.HOME,
                enterTransition = { fadeIn(animationSpec = tween(180)) },
                exitTransition = { fadeOut(animationSpec = tween(180)) },
                modifier = Modifier.fillMaxSize()
            ) {
                composable(QltyRoutes.HOME) {
                    HomeScreen(
                        userDisplayName = user.displayName ?: "",
                        userPhotoUrl = user.photoUrl?.toString(),
                        isDarkMode = appearance.isDarkMode,
                        libraryState = libraryState,
                        jobs = jobs,
                        isImporting = editorState.isImporting,
                        onToggleDarkMode = {
                            scope.launch { themeStore.setDarkMode(!appearance.isDarkMode) }
                        },
                        onPickPhoto = { preset -> launchPicker(preset) },
                        onBrowseFiles = { launchFileBrowser(null) },
                        onOpenLibrary = {
                            navController.navigate(QltyRoutes.LIBRARY) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onOpenAsset = { asset ->
                            viewModel.openResultForAsset(asset)
                            navController.navigate(QltyRoutes.RESULT) {
                                launchSingleTop = true
                            }
                        },
                        onOpenProfile = {
                            navController.navigate(QltyRoutes.SETTINGS) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        contentPadding = innerPadding
                    )
                }

                composable(QltyRoutes.LIBRARY) {
                    LibraryScreen(
                        libraryState = libraryState,
                        onImportPhoto = { launchPicker(null) },
                        onOpenAsset = { asset ->
                            viewModel.openResultForAsset(asset)
                            navController.navigate(QltyRoutes.RESULT) {
                                launchSingleTop = true
                            }
                        },
                        onToggleFavorite = { asset -> viewModel.toggleFavorite(asset) },
                        onExportSelected = { list -> viewModel.exportAssetsToGallery(list) },
                        onDeleteSelected = { list -> viewModel.deleteAssets(list) },
                        contentPadding = innerPadding
                    )
                }

                composable(QltyRoutes.TOOLS) {
                    ToolsScreen(
                        capabilities = viewModel.inspectDeviceCapabilities(),
                        isNetworkOnline = viewModel.isNetworkConnected(),
                        onLaunchPreset = { preset -> launchPicker(preset) },
                        contentPadding = innerPadding
                    )
                }

                composable(QltyRoutes.SETTINGS) {
                    SettingsScreen(
                        userDisplayName = user.displayName ?: "",
                        userEmail = user.email ?: "",
                        userPhotoUrl = user.photoUrl?.toString(),
                        preferences = preferences,
                        appearance = appearance,
                        notificationGranted = notificationGranted,
                        onToggleDarkMode = { enabled ->
                            scope.launch { themeStore.setDarkMode(enabled) }
                        },
                        onSelectThemePreset = { preset ->
                            scope.launch { themeStore.setThemePreset(preset) }
                        },
                        onRequestNotificationPermission = { requestNotificationsOrOpenSettings() },
                        onOpenGalleryPicker = { launchPicker(null) },
                        onOpenFileBrowser = { launchFileBrowser(null) },
                        onUpdatePreferences = { transform -> viewModel.updatePreferences(transform) },
                        onDeleteCloudData = { viewModel.deleteAllCloudData() },
                        onSignOut = {
                            signOutUser(
                                credentialManager = credentialManager,
                                scope = scope
                            )
                        },
                        contentPadding = innerPadding
                    )
                }

                composable(QltyRoutes.EDITOR) {
                    EditorScreen(
                        state = editorState,
                        onBack = { navController.popBackStack() },
                        onSelectMode = { mode -> viewModel.selectProcessingMode(mode) },
                        onSelectResolution = { res -> viewModel.selectResolution(res) },
                        onSelectAspectRatio = { ratio -> viewModel.selectAspectRatio(ratio) },
                        onUpdateCustomDimensions = { w, h -> viewModel.updateCustomDimensions(w, h) },
                        onToggleAspectLock = { locked -> viewModel.toggleAspectLock(locked) },
                        onSelectFilter = { filter -> viewModel.selectFilter(filter) },
                        onApplyPreset = { preset -> viewModel.applyPreset(preset) },
                        onUpdateAdjustments = { fn -> viewModel.updateAdjustments(fn) },
                        onAutoTune = { viewModel.autoTuneAdjustments() },
                        onSelectFormat = { fmt -> viewModel.selectExportFormat(fmt) },
                        onUpdateJpegQuality = { q -> viewModel.updateJpegQuality(q) },
                        onReset = { viewModel.resetAdjustments() },
                        onAnalyzeWithCloud = { viewModel.runCloudSmartDiagnosis() },
                        onEnhance = {
                            viewModel.startEnhancement {
                                navController.navigate(QltyRoutes.RESULT) {
                                    launchSingleTop = true
                                }
                            }
                        },
                        onCancelProcessing = { viewModel.cancelActiveProcessing() },
                        onGrantCloudConsent = {
                            viewModel.grantCloudConsentAndEnhance {
                                navController.navigate(QltyRoutes.RESULT) {
                                    launchSingleTop = true
                                }
                            }
                        },
                        onDismissCloudConsent = { viewModel.dismissCloudConsentSheet() },
                        onPickAnotherPhoto = { launchPicker(null) },
                        onBrowseFiles = { launchFileBrowser(null) }
                    )
                }

                composable(QltyRoutes.RESULT) {
                    ResultScreen(
                        state = resultState,
                        autoSaveEnabled = preferences.autoSave,
                        onBack = { navController.popBackStack() },
                        onSaveToGallery = { viewModel.saveResultToGallery() },
                        onSaveCopyToFiles = { suggestedName ->
                            saveCopyLauncher.launch(suggestedName)
                        },
                        onToggleAutoSave = { enabled -> viewModel.toggleAutoSave(enabled) },
                        onReEnhance = { asset ->
                            viewModel.loadAssetIntoEditor(asset) {
                                navController.navigate(QltyRoutes.EDITOR) {
                                    launchSingleTop = true
                                }
                            }
                        },
                        onRenameAsset = { asset, newName -> viewModel.renameAsset(asset, newName) },
                        onDeleteAsset = { asset ->
                            viewModel.deleteAssets(listOf(asset)) {
                                navController.popBackStack()
                            }
                        }
                    )
                }
            }
        }

        // Overlay Toast above bottom navigation
        QltyToast(
            message = toast?.first,
            isError = toast?.second ?: false,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = QltySpace.S32 + QltySpace.S32)
        )
    }
}
