package com.justan.mobiline

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.justan.mobiline.sensors.CalibrationOffset
import com.justan.mobiline.sensors.OrientationRepository
import com.justan.mobiline.data.SessionStore
import com.justan.mobiline.ui.CalibrationScreen
import com.justan.mobiline.ui.CamberScreen
import com.justan.mobiline.ui.HomeScreen
import com.justan.mobiline.ui.MobiLineTheme
import com.justan.mobiline.ui.PreflightScreen
import com.justan.mobiline.ui.SavedSessionsScreen
import com.justan.mobiline.ui.ToeScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repo = OrientationRepository(applicationContext)
        val sessionStore = SessionStore(applicationContext)

        setContent {
            MobiLineTheme {
                Surface {
                    val navController = rememberNavController()
                    // Calibration offset is shared app-wide for this simple version.
                    // Persist it (DataStore/Room) later if you want it to survive restarts.
                    val calibrationOffset = remember { mutableStateOf(CalibrationOffset()) }

                    NavHost(navController = navController, startDestination = "home") {
                        // Dashboard is the entry point. Choosing a mode sends
                        // the user through calibration (the level reading)
                        // first, so the offset is ready before measuring.
                        composable("home") {
                            HomeScreen(
                                onSelectCamber = { navController.navigate("calib_camber") },
                                onSelectToe = { navController.navigate("calib_toe") },
                                onRecalibrate = { navController.navigate("calib_recal") },
                                onSavedSessions = { navController.navigate("sessions") }
                            )
                        }

                        // Calibration once per chosen mode; on completion run the
                        // offset and route on to that mode's preflight.
                        fun calibrateThen(dest: String, pop: String) =
                            {
                                offset: CalibrationOffset ->
                                calibrationOffset.value = offset
                                navController.navigate(dest) {
                                    popUpTo(pop) { inclusive = true }
                                }
                            }

                        composable("calib_camber") {
                            CalibrationScreen(
                                repository = repo,
                                onBack = { navController.popBackStack() },
                                onSavedSessions = { navController.navigate("sessions") },
                                onCalibrated = calibrateThen("preflight_camber", "calib_camber")
                            )
                        }
                        composable("calib_toe") {
                            CalibrationScreen(
                                repository = repo,
                                onBack = { navController.popBackStack() },
                                onSavedSessions = { navController.navigate("sessions") },
                                onCalibrated = calibrateThen("preflight_toe", "calib_toe")
                            )
                        }
                        composable("calib_recal") {
                            CalibrationScreen(
                                repository = repo,
                                onBack = { navController.popBackStack() },
                                onSavedSessions = { navController.navigate("sessions") },
                                onCalibrated = calibrateThen("home", "calib_recal")
                            )
                        }

                        composable("preflight_camber") {
                            PreflightScreen(
                                modeName = "Camber",
                                onContinue = { navController.navigate("camber") },
                                onBack = { navController.popBackStack() },
                                onSavedSessions = { navController.navigate("sessions") }
                            )
                        }
                        composable("preflight_toe") {
                            PreflightScreen(
                                modeName = "Toe",
                                onContinue = { navController.navigate("toe") },
                                onBack = { navController.popBackStack() },
                                onSavedSessions = { navController.navigate("sessions") }
                            )
                        }
                        composable("camber") {
                            CamberScreen(
                                repository = repo,
                                calibrationOffset = calibrationOffset.value,
                                store = sessionStore,
                                onBack = { navController.popBackStack() },
                                onSavedSessions = { navController.navigate("sessions") }
                            )
                        }
                        composable("toe") {
                            ToeScreen(
                                repository = repo,
                                calibrationOffset = calibrationOffset.value,
                                store = sessionStore,
                                onBack = { navController.popBackStack() },
                                onSavedSessions = { navController.navigate("sessions") }
                            )
                        }
                        composable("sessions") {
                            SavedSessionsScreen(
                                store = sessionStore,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
