package com.pasoseguro.app.navigation

sealed class Screen(val route: String) {
    object Home     : Screen("home")
    object Navigate : Screen(Feature.NAVIGATE.route)
    object Scan     : Screen(Feature.SCAN.route)
    object Route    : Screen(Feature.ROUTE.route)
    object Contacts : Screen(Feature.CONTACTS.route)
    object Alerts   : Screen(Feature.ALERTS.route)
    object Config   : Screen(Feature.CONFIG.route)
}
