package com.example.juzzics

import android.Manifest.permission.READ_MEDIA_AUDIO
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.juzzics.common.enums.BottomNavItems
import com.example.juzzics.navigation.BottomNavigation
import com.example.juzzics.ui.theme.JuzzicsTheme


class MainActivity : ComponentActivity() {
    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JuzzicsTheme {
                val hasAudioPermission = remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(this, READ_MEDIA_AUDIO) ==
                                PackageManager.PERMISSION_GRANTED
                    )
                }
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                    onResult = { isGranted ->
                        hasAudioPermission.value = isGranted
                    }
                )
                LaunchedEffect(hasAudioPermission) {
                    if (!hasAudioPermission.value) {
                        permissionLauncher.launch(READ_MEDIA_AUDIO)
                    }
                }
                if (hasAudioPermission.value) {
                    val rootNavController = rememberNavController()
                    Scaffold(bottomBar = { BottomBar(rootNavController = rootNavController) }) { paddingValues ->
                        Box(modifier = Modifier.padding(bottom = paddingValues.calculateBottomPadding())) {
                            BottomNavigation(rootNavController)
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun BottomBar(rootNavController: NavHostController) {
    val rootNavBackStackEntry by rootNavController.currentBackStackEntryAsState()
    NavigationBar() { ShowNavBar(rootNavController = rootNavController, rootNavBackStackEntry) }
}

@Composable
fun RowScope.ShowNavBar(
    rootNavController: NavHostController,
    navBackStackEntry: NavBackStackEntry?
) {
    BottomNavItems.entries.forEach { item ->
        val isSelected = item.title.lowercase() == navBackStackEntry?.destination?.route
        NavigationBarItem(selected = isSelected,
            onClick = {
                rootNavController.navigate(item.route) {
                    popUpTo(rootNavController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            label = { Text(text = item.title) },
            icon = {
                Icon(
                    imageVector = if (isSelected) item.selectedIcon else item.unSelectedIcon,
                    contentDescription = item.title
                )
            })
    }
}
