package org.kasumi321.ushio.phitracker.ui.navigation

/**
 * Back-stack rules for the login/home boundary, shared by the navigation
 * gateway and tested against real NavController semantics.
 *
 * A guest Home can open Login on top of itself, so "login succeeded" must be
 * stack-aware: when a Home entry sits below Login, THAT entry is replaced
 * (popUpTo Home inclusive) — the fresh logged-in Home gets a new ViewModel
 * and exactly one Home remains, so back never returns to the stale guest
 * page; when Login is the root (token-holder cold start) it is popped
 * inclusive as before.
 */
internal object LoginBackStackPolicy {

    data class Replacement(val popUpToRoute: String, val inclusive: Boolean)

    fun homeReplacingLogin(
        backStackRoutes: List<String>,
        homeRoute: String,
        loginRoute: String
    ): Replacement =
        if (backStackRoutes.contains(homeRoute)) Replacement(homeRoute, inclusive = true)
        else Replacement(loginRoute, inclusive = true)
}
