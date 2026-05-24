package eu.hxreborn.biometricapplock.ui

import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.rememberNavBackStack
import eu.hxreborn.biometricapplock.ui.navigation.BottomNav
import eu.hxreborn.biometricapplock.ui.navigation.MainNavDisplay
import eu.hxreborn.biometricapplock.ui.navigation.Screen
import eu.hxreborn.biometricapplock.ui.navigation.bottomNavItems
import eu.hxreborn.biometricapplock.ui.viewmodel.ScopeViewModel

@Composable
fun MainScaffold(viewModel: ScopeViewModel) {
    val backStack = rememberNavBackStack(Screen.Dashboard)
    val currentKey = backStack.lastOrNull() as? Screen
    val isTopLevel = bottomNavItems.any { it.key == currentKey }

    Scaffold(
        bottomBar = {
            if (isTopLevel) {
                BottomNav(
                    backStack = backStack,
                    currentKey = currentKey,
                )
            }
        },
    ) { contentPadding ->
        MainNavDisplay(
            backStack = backStack,
            viewModel = viewModel,
            contentPadding = contentPadding,
        )
    }
}
