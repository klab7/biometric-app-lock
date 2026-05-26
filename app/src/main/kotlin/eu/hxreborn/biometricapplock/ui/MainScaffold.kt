@file:Suppress("AssignedValueIsNeverRead")

package eu.hxreborn.biometricapplock.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.rememberNavBackStack
import eu.hxreborn.biometricapplock.App
import eu.hxreborn.biometricapplock.prefs.AppPrefs
import eu.hxreborn.biometricapplock.prefs.Prefs
import eu.hxreborn.biometricapplock.ui.component.ChangelogSheet
import eu.hxreborn.biometricapplock.ui.navigation.BottomNav
import eu.hxreborn.biometricapplock.ui.navigation.ClassicBottomNav
import eu.hxreborn.biometricapplock.ui.navigation.MainNavDisplay
import eu.hxreborn.biometricapplock.ui.navigation.Screen
import eu.hxreborn.biometricapplock.ui.navigation.bottomNavItems
import eu.hxreborn.biometricapplock.ui.viewmodel.ScopeViewModel
import eu.hxreborn.biometricapplock.updates.UpdateState
import kotlinx.coroutines.delay

@Composable
fun MainScaffold(viewModel: ScopeViewModel) {
    val backStack = rememberNavBackStack(Screen.Dashboard)
    val currentKey = backStack.lastOrNull() as? Screen
    val isTopLevel = bottomNavItems.any { it.key == currentKey }

    val updateState by App.updateRepository.currentState.collectAsStateWithLifecycle()
    val cachedAvailable by App.updateRepository.cachedAvailable.collectAsStateWithLifecycle()
    val prefs by App.prefsRepository.state.collectAsStateWithLifecycle(initialValue = AppPrefs.Defaults)

    var showUpdateSheet by remember { mutableStateOf(false) }
    var shownForVersion by remember { mutableStateOf<String?>(null) }

    val hasUnseenUpdate =
        cachedAvailable?.latestVersion?.let { it != prefs.lastDismissedAvailableVersion } == true

    LaunchedEffect(updateState) {
        val state = updateState
        val dismissed = App.prefsRepository.read(Prefs.LAST_DISMISSED_AVAILABLE_VERSION)
        if (state is UpdateState.Available &&
            state.latestVersion != dismissed &&
            shownForVersion != state.latestVersion
        ) {
            delay(800L)
            shownForVersion = state.latestVersion
            showUpdateSheet = true
        }
    }

    if (showUpdateSheet) {
        ChangelogSheet(
            onDismiss = { showUpdateSheet = false },
            showCheckingState = false,
        )
    }

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = isTopLevel,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                if (prefs.floatingNavBar) {
                    BottomNav(
                        backStack = backStack,
                        currentKey = currentKey,
                        showUpdateBadge = hasUnseenUpdate,
                    )
                } else {
                    ClassicBottomNav(
                        backStack = backStack,
                        currentKey = currentKey,
                        showUpdateBadge = hasUnseenUpdate,
                    )
                }
            }
        },
    ) { contentPadding ->
        MainNavDisplay(
            backStack = backStack,
            viewModel = viewModel,
            contentPadding = contentPadding,
            onShowUpdateSheet = { showUpdateSheet = true },
        )
    }
}
