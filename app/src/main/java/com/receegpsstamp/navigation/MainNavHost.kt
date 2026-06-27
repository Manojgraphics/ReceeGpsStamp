package com.receegpsstamp.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.receegpsstamp.feature.annotate.AnnotateScreen
import com.receegpsstamp.feature.app.AppViewModel
import com.receegpsstamp.feature.dashboard.DashboardScreen
import com.receegpsstamp.feature.gallery.GalleryScreen
import com.receegpsstamp.feature.login.LoginScreen
import com.receegpsstamp.feature.recce.StartRecceScreen
import com.receegpsstamp.feature.settings.SettingsScreen
import com.receegpsstamp.feature.splash.SplashScreen

@Composable
fun MainNavHost() {
    val nav = rememberNavController()

    NavHost(
        navController = nav,
        startDestination = Routes.SPLASH,
        enterTransition = { slideInHorizontally(tween(240)) { it / 6 } + fadeIn(tween(240)) },
        exitTransition = { fadeOut(tween(160)) },
        popEnterTransition = { fadeIn(tween(180)) },
        popExitTransition = { slideOutHorizontally(tween(220)) { it / 6 } + fadeOut(tween(180)) },
    ) {

        composable(Routes.SPLASH) {
            SplashScreen(onFinished = {
                nav.navigate(Routes.LOGIN) { popUpTo(Routes.SPLASH) { inclusive = true } }
            })
        }

        composable(Routes.LOGIN) {
            LoginScreen(
                onSignedIn = { nav.navigate(Routes.MAIN) { popUpTo(Routes.LOGIN) { inclusive = true } } },
            )
        }

        composable(Routes.MAIN) {
            MainScaffold(
                onNavigate = { route ->
                    // Guard against the double-tap crash: ignore a tap if we're already navigating
                    // to (or sitting on) that destination. launchSingleTop alone isn't enough when
                    // two taps fire in the same frame from the drawer.
                    if (nav.currentDestination?.route != route) {
                        nav.navigate(route) {
                            popUpTo(Routes.MAIN) { saveState = false }
                            launchSingleTop = true
                        }
                    }
                },
            )
        }

        composable(Routes.DASHBOARD) {
            val appVm: AppViewModel = hiltViewModel()
            val appState by appVm.state.collectAsStateWithLifecycle()
            val ctx = androidx.compose.ui.platform.LocalContext.current
            androidx.compose.runtime.LaunchedEffect(Unit) {
                appVm.shareFile.collect { (uri, mime) ->
                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = mime
                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    ctx.startActivity(android.content.Intent.createChooser(intent, "Share report"))
                }
            }
            // Share one/many stores' details — text (+ photos for a single store).
            androidx.compose.runtime.LaunchedEffect(Unit) {
                appVm.shareReq.collect { req ->
                    val uris = req.photoPaths.mapNotNull { path ->
                        val f = java.io.File(path)
                        if (f.exists()) androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f) else null
                    }
                    val intent = when {
                        uris.size > 1 -> android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE).apply {
                            type = "image/*"
                            putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, ArrayList(uris))
                            putExtra(android.content.Intent.EXTRA_TEXT, req.text)
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        uris.size == 1 -> android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "image/*"
                            putExtra(android.content.Intent.EXTRA_STREAM, uris.first())
                            putExtra(android.content.Intent.EXTRA_TEXT, req.text)
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        else -> android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, req.text)
                        }
                    }
                    // WhatsApp drops the caption on multi-image shares — copy text so it can be pasted.
                    val clip = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clip.setPrimaryClip(android.content.ClipData.newPlainText("Store", req.text))
                    ctx.startActivity(android.content.Intent.createChooser(intent, "Share store"))
                }
            }
            DashboardScreen(
                onBack = { nav.popBackStack() },
                distributorCount = appState.distributors.size,
                distributorName = appState.selectedDistributor?.name ?: "",
                selectedDistributorId = appState.selectedDistributor?.id ?: "",
                distributors = appState.distributors,
                companies = appState.companies,
                allShops = appState.allShops,
                allRecces = appState.allRecces,
                expenses = appState.expenses,
                onOpenExpenses = { nav.navigate(Routes.EXPENSES) },
                onSetProjectStage = { id, stg -> appVm.setProjectStage(id, stg) },
                onProjectExportPdf = { appVm.exportProjectPdf(it) },
                onProjectExportExcel = { appVm.exportProjectExcel(it) },
                onRemoveProjectPhotos = { appVm.removeProjectPhotos(it) },
                onDeleteProject = { appVm.deleteProject(it) },
                onImportProject = { appVm.importProjectFile(it) },
                onEditStore = { appVm.startEditRecce(it); nav.popBackStack() },
                onShareStore = { appVm.shareStore(it.id) },
                onShareStores = { appVm.shareStores(it) },
                onExportStoresPdf = { appVm.exportStoresPdf(it) },
                onExportStoresExcel = { appVm.exportStoresExcel(it) },
            )
        }
        composable(Routes.EXPENSES) {
            val expVm: com.receegpsstamp.feature.expense.ExpenseViewModel = hiltViewModel()
            val expState by expVm.uiState.collectAsStateWithLifecycle()
            val expCtx = androidx.compose.ui.platform.LocalContext.current
            androidx.compose.runtime.LaunchedEffect(Unit) {
                expVm.messages.collect { msg -> android.widget.Toast.makeText(expCtx, msg, android.widget.Toast.LENGTH_SHORT).show() }
            }
            androidx.compose.runtime.LaunchedEffect(Unit) {
                expVm.shareFile.collect { (uri, mime) ->
                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = mime
                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    expCtx.startActivity(android.content.Intent.createChooser(intent, "Share report"))
                }
            }
            com.receegpsstamp.feature.expense.ExpenseScreen(
                expenses = expState.expenses,
                approvals = expState.approvals,
                vehicleNumber = expState.vehicleNumber,
                vehicleType = expState.vehicleType,
                vehicles = expState.vehicles,
                projects = expState.projects,
                compressBill = { expVm.compressBillPhoto(it) },
                onAdd = { expVm.addExpense(it) },
                onDelete = { expVm.deleteExpense(it) },
                onExportPdf = { list, scope -> expVm.exportExpensesPdf(list, scope) },
                onExportExcel = { list, scope -> expVm.exportExpensesCsv(list, scope) },
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.FLEET) {
            val flVm: com.receegpsstamp.feature.fleet.FleetViewModel = hiltViewModel()
            val flState by flVm.uiState.collectAsStateWithLifecycle()
            com.receegpsstamp.feature.fleet.FleetScreen(
                vehicles = flState.vehicles,
                fuelLogs = flState.fuelLogs,
                serviceLogs = flState.serviceLogs,
                onAddFuel = { flVm.addFuelLog(it) },
                onDeleteFuel = { flVm.deleteFuelLog(it) },
                onAddService = { flVm.addServiceLog(it) },
                onDeleteService = { flVm.deleteServiceLog(it) },
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.GALLERY) {
            val appVm6: AppViewModel = hiltViewModel()
            val s6 by appVm6.state.collectAsStateWithLifecycle()
            GalleryScreen(
                onBack = { nav.popBackStack() },
                shops = s6.allShops,
                recceEntries = s6.allRecces,
                distributorName = s6.selectedDistributor?.name ?: "",
                distributors = s6.distributors,
                selectedDistributorId = s6.selectedDistributor?.id ?: "",
                onAnnotate = { path -> appVm6.startAnnotate(path); nav.navigate(Routes.ANNOTATE) },
            )
        }
        composable(
            "${Routes.SETTINGS}?page={page}",
            arguments = listOf(androidx.navigation.navArgument("page") { defaultValue = "menu" }),
        ) { backStackEntry ->
            val appVm7: AppViewModel = hiltViewModel()
            val ctx7 = androidx.compose.ui.platform.LocalContext.current
            androidx.compose.runtime.LaunchedEffect(Unit) {
                appVm7.messages.collect { msg ->
                    android.widget.Toast.makeText(ctx7, msg, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            SettingsScreen(
                onBack = { nav.popBackStack() },
                onSignedOut = { nav.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } } },
                initialPage = backStackEntry.arguments?.getString("page") ?: "menu",
            )
        }
        composable(Routes.ANNOTATE) {
            val appVm: AppViewModel = hiltViewModel()
            val path by appVm.annotatePhoto.collectAsStateWithLifecycle()
            val annotateSettings by appVm.settings.collectAsStateWithLifecycle()
            AnnotateScreen(
                photoPath = path,
                saveCleanCopy = annotateSettings.saveNoMarkupCopy,
                onDone = { appVm.consumeAnnotate(); nav.popBackStack() },
            )
        }
    }
}
