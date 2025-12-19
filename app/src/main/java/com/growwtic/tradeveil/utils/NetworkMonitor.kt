package com.growwtic.tradeveil.utils

import android.app.Activity
import android.app.Application
import android.app.Dialog
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.Button
import com.growwtic.tradeveil.R
import kotlinx.coroutines.*
import java.lang.ref.WeakReference

object NetworkMonitor {

    private var dialog: Dialog? = null
    private var isInternetAvailable = false
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var currentActivityRef: WeakReference<Activity>? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun init(app: Application) {
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val builder = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .build()

        // Check initial state
        checkInternetConnectivity(app, cm)

        cm.registerNetworkCallback(
            builder,
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    mainHandler.post {
                        validateInternetConnection(app, cm, network)
                    }
                }

                override fun onLost(network: Network) {
                    mainHandler.post {
                        isInternetAvailable = false
                        showDialog(app, true)
                    }
                }

                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    mainHandler.post {
                        val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                        if (hasInternet != isInternetAvailable) {
                            isInternetAvailable = hasInternet
                            showDialog(app, !hasInternet)
                        }
                    }
                }
            })
    }

    // Call this from your activities to set the current activity context
    fun setCurrentActivity(activity: Activity?) {
        currentActivityRef = if (activity != null) WeakReference(activity) else null
    }

    private fun checkInternetConnectivity(ctx: Context, cm: ConnectivityManager) {
        try {
            val activeNetwork = cm.activeNetwork
            val capabilities = cm.getNetworkCapabilities(activeNetwork)

            isInternetAvailable = capabilities?.let {
                it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            } ?: false

            showDialog(ctx, !isInternetAvailable)
        } catch (e: Exception) {
            e.printStackTrace()
            isInternetAvailable = false
            showDialog(ctx, true)
        }
    }

    private fun validateInternetConnection(ctx: Context, cm: ConnectivityManager, network: Network) {
        scope.launch(Dispatchers.IO) {
            try {
                val capabilities = cm.getNetworkCapabilities(network)
                val hasValidatedInternet = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ?: false

                withContext(Dispatchers.Main) {
                    isInternetAvailable = hasValidatedInternet
                    showDialog(ctx, !hasValidatedInternet)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    isInternetAvailable = false
                    showDialog(ctx, true)
                }
            }
        }
    }

    private fun showDialog(ctx: Context, show: Boolean) {
        try {
            if (show && !isInternetAvailable) {
                if (dialog?.isShowing == true) return

                // Get the current activity context, fallback to application context
                val dialogContext = currentActivityRef?.get() ?: ctx

                // Only create dialog if we have a valid activity context
                if (dialogContext is Activity && !dialogContext.isFinishing && !dialogContext.isDestroyed) {
                    dialog = Dialog(dialogContext, android.R.style.Theme_Black_NoTitleBar_Fullscreen).apply {
                        try {
                            val view = LayoutInflater.from(dialogContext).inflate(R.layout.dialog_no_internet, null)
                            setContentView(view)

                            findViewById<Button>(R.id.retryBtn)?.setOnClickListener {
                                val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                                checkInternetConnectivity(ctx, cm)
                            }

                            setCancelable(false)
                            window?.addFlags(
                                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                            )

                            show()
                        } catch (e: Exception) {
                            e.printStackTrace()
                            // If dialog creation fails, at least log it
                        }
                    }
                }
            } else if (!show && isInternetAvailable) {
                dialog?.let {
                    if (it.isShowing) {
                        it.dismiss()
                    }
                }
                dialog = null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Prevent crashes from dialog operations
        }
    }

    fun cleanup() {
        try {
            scope.cancel()
            dialog?.let {
                if (it.isShowing) {
                    it.dismiss()
                }
            }
            dialog = null
            currentActivityRef = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}