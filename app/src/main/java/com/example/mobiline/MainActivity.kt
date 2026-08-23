package com.example.mobiline

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.mobiline.sensors.CalibrationOffset
import com.example.mobiline.sensors.OrientationRepository
import com.example.mobiline.ui.CalibrationScreen
import com.example.mobiline.ui.CamberScreen
import com.example.mobiline.ui.HomeScreen
import com.example.mobiline.ui.PreflightScreen
import com.example.mobiline.ui.ToeScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repo = OrientationRepository(applicationContext)

        setContent {
            MaterialTheme {
                Surface {
                    val navController = rememberNavController()
                    // Calibration offset is shared app-wide for this simple version.
                    // Persist it (DataStore/Room) later if you want it to survive restarts.
                    val calibrationOffset = remember { mutableStateOf(CalibrationOffset()) }

                    NavHost(navController = navController, startDestination = "calibration") {
                        composable("calibration") {
                            CalibrationScreen(
                                repository = repo,
                                onCalibrated = { offset ->
                                    calibrationOffset.value = offset
                                    navController.navigate("home") {
                                        popUpTo("calibration") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("home") {
                            HomeScreen(
                                onSelectCamber = { navController.navigate("preflight_camber") },
                                onSelectToe = { navController.navigate("preflight_toe") },
                                onRecalibrate = {
                                    navController.navigate("calibration") {
                                        popUpTo("home") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("preflight_camber") {
                            PreflightScreen(
                                modeName = "Camber",
                                onContinue = { navController.navigate("camber") }
                            )
                        }
                        composable("preflight_toe") {
                            PreflightScreen(
                                modeName = "Toe",
                                onContinue = { navController.navigate("toe") }
                            )
                        }
                        composable("camber") {
                            CamberScreen(
                                repository = repo,
                                calibrationOffset = calibrationOffset.value
                            )
                        }
                        composable("toe") {
                            ToeScreen(
                                repository = repo,
                                calibrationOffset = calibrationOffset.value
                            )
                        }
                    }
                }
            }
        }
    }
}
