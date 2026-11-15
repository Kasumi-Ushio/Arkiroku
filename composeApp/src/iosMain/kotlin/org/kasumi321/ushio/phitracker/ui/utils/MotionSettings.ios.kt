package org.kasumi321.ushio.phitracker.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled
import platform.UIKit.UIAccessibilityReduceMotionStatusDidChangeNotification

@Composable
actual fun rememberReducedMotionEnabled(): Boolean {
    var reducedMotionEnabled by remember {
        mutableStateOf(UIAccessibilityIsReduceMotionEnabled())
    }
    val notificationCenter = NSNotificationCenter.defaultCenter

    DisposableEffect(notificationCenter) {
        val observer = notificationCenter.addObserverForName(
            UIAccessibilityReduceMotionStatusDidChangeNotification,
            null,
            NSOperationQueue.mainQueue
        ) { _ ->
            reducedMotionEnabled = UIAccessibilityIsReduceMotionEnabled()
        }
        onDispose { notificationCenter.removeObserver(observer) }
    }

    return reducedMotionEnabled
}
