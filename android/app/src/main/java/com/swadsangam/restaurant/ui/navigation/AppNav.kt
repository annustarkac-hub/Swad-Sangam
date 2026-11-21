package com.swadsangam.restaurant.ui.navigation

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.swadsangam.restaurant.R
import com.swadsangam.restaurant.data.prefs.UserPrefs
import com.swadsangam.restaurant.notifications.Notifier
import com.swadsangam.restaurant.ui.AppViewModel
import com.swadsangam.restaurant.ui.cart.CartScreen
import com.swadsangam.restaurant.ui.checkout.CheckoutScreen
import com.swadsangam.restaurant.ui.feedback.FeedbackScreen
import com.swadsangam.restaurant.ui.home.HomeScreen
import com.swadsangam.restaurant.ui.item.ItemScreen
import com.swadsangam.restaurant.ui.menu.MenuScreen
import com.swadsangam.restaurant.ui.more.MoreScreen
import com.swadsangam.restaurant.ui.more.RestaurantInfoScreen
import com.swadsangam.restaurant.ui.order.OrderScreen
import com.swadsangam.restaurant.ui.orders.OrdersScreen
import kotlinx.coroutines.launch

object Routes {
    const val HOME = "home"
    const val MENU = "menu?category={category}&search={search}"
    const val ITEM = "item/{id}"
    const val CART = "cart"
    const val CHECKOUT = "checkout"
    const val ORDER = "order/{token}"
    const val ORDERS = "orders"
    const val MORE = "more"
    const val INFO = "info"
    const val FEEDBACK = "feedback?token={token}"

    fun menu(category: String? = null, search: Boolean = false) = "menu?category=${category.orEmpty()}&search=$search"
    fun item(id: String) = "item/$id"
    fun order(token: String) = "order/$token"
    fun feedback(token: String? = null) = "feedback?token=${token.orEmpty()}"
}

private data class Tab(val route: String, val nav: String, val label: Int, val icon: ImageVector)

private val TABS = listOf(
    Tab(Routes.HOME, Routes.HOME, R.string.tab_home, Icons.Default.Home),
    Tab(Routes.MENU, Routes.menu(), R.string.tab_menu, Icons.Default.RestaurantMenu),
    Tab(Routes.CART, Routes.CART, R.string.tab_cart, Icons.Default.ShoppingCart),
    Tab(Routes.ORDERS, Routes.ORDERS, R.string.tab_orders, Icons.AutoMirrored.Filled.ReceiptLong),
    Tab(Routes.MORE, Routes.MORE, R.string.tab_more, Icons.Default.MoreHoriz),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNav(appVm: AppViewModel, prefs: UserPrefs, deepLink: String?, onDeepLinkHandled: () -> Unit) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val cartCount by appVm.cartCount.collectAsStateWithLifecycle()
    val table by appVm.tableNumber.collectAsStateWithLifecycle()
    val hindi = LocalConfiguration.current.locales[0].language == "hi"
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(deepLink) {
        val link = deepLink ?: return@LaunchedEffect
        val uri = android.net.Uri.parse(link)
        when (uri.host) {
            "menu" -> {
                uri.getQueryParameter("table")?.take(10)?.let { appVm.tableNumber.value = it }
                nav.navigate(Routes.menu(uri.getQueryParameter("category")))
            }
            "order" -> uri.lastPathSegment?.let { nav.navigate(Routes.order(it)) }
        }
        onDeepLinkHandled()
    }

    val isTab = TABS.any { it.route == route }
    val title = when (route) {
        Routes.ITEM -> ""
        Routes.CHECKOUT -> stringResource(R.string.checkout)
        Routes.ORDER -> stringResource(R.string.order_status)
        Routes.INFO -> stringResource(R.string.restaurant_info)
        Routes.FEEDBACK -> stringResource(R.string.give_feedback)
        Routes.MENU -> stringResource(R.string.tab_menu)
        Routes.CART -> stringResource(R.string.tab_cart)
        Routes.ORDERS -> stringResource(R.string.my_orders)
        Routes.MORE -> stringResource(R.string.tab_more)
        else -> null
    }

    Scaffold(
        topBar = {
            if (route != Routes.HOME && title != null) {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        if (!isTab) IconButton({ nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary, titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                    ),
                )
            }
        },
        bottomBar = {
            if (isTab || route == null) {
                NavigationBar {
                    TABS.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = {
                                nav.navigate(tab.nav) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                if (tab.route == Routes.CART && cartCount > 0) {
                                    BadgedBox(badge = { Badge { Text("$cartCount") } }) { Icon(tab.icon, null) }
                                } else Icon(tab.icon, null)
                            },
                            label = { Text(stringResource(tab.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Routes.HOME, modifier = Modifier.padding(padding)) {
            composable(Routes.HOME) {
                HomeScreen(
                    hindi = hindi,
                    onOpenMenu = { c, s -> nav.navigate(Routes.menu(c, s)) },
                    onOpenItem = { nav.navigate(Routes.item(it)) },
                    onOpenInfo = { nav.navigate(Routes.INFO) },
                    onOpenOrders = { nav.navigate(Routes.ORDERS) },
                )
            }
            composable(
                Routes.MENU,
                arguments = listOf(
                    navArgument("category") { type = NavType.StringType; defaultValue = "" },
                    navArgument("search") { type = NavType.BoolType; defaultValue = false },
                ),
            ) { e ->
                MenuScreen(hindi, e.arguments?.getBoolean("search") ?: false, table, onOpenItem = { nav.navigate(Routes.item(it)) })
            }
            composable(Routes.ITEM, arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                ItemScreen(hindi, onAdded = { nav.popBackStack() })
            }
            composable(Routes.CART) {
                CartScreen(hindi, onBrowse = { nav.navigateTab(Routes.menu()) }, onCheckout = { nav.navigate(Routes.CHECKOUT) })
            }
            composable(Routes.CHECKOUT) {
                CheckoutScreen(
                    hindi = hindi, tableNumber = table,
                    onPlaced = { token ->
                        Notifier.show(context, context.getString(R.string.notif_submitted),
                            context.getString(R.string.notif_submitted_body), token, token.hashCode())
                        nav.navigate(Routes.order(token)) { popUpTo(Routes.HOME) }
                        scope.launch {
                            if (Build.VERSION.SDK_INT >= 33 && !prefs.askedNotificationPermission()) {
                                prefs.setAskedNotificationPermission()
                                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    },
                    onBackToCart = { nav.popBackStack() },
                )
            }
            composable(Routes.ORDER, arguments = listOf(navArgument("token") { type = NavType.StringType })) {
                OrderScreen(onFeedback = { nav.navigate(Routes.feedback(it)) }, onHome = { nav.navigateTab(Routes.HOME) })
            }
            composable(Routes.ORDERS) {
                OrdersScreen(onOpen = { nav.navigate(Routes.order(it)) }, onCart = { nav.navigateTab(Routes.CART) },
                    onBrowse = { nav.navigateTab(Routes.menu()) })
            }
            composable(Routes.MORE) {
                MoreScreen(onInfo = { nav.navigate(Routes.INFO) }, onMenu = { nav.navigateTab(Routes.menu()) },
                    onFeedback = { nav.navigate(Routes.feedback()) })
            }
            composable(Routes.INFO) { RestaurantInfoScreen(onMenu = { nav.navigateTab(Routes.menu()) }) }
            composable(Routes.FEEDBACK, arguments = listOf(navArgument("token") { type = NavType.StringType; defaultValue = "" })) {
                FeedbackScreen(onDone = { nav.popBackStack() })
            }
        }
    }
}

private fun NavHostController.navigateTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
}
