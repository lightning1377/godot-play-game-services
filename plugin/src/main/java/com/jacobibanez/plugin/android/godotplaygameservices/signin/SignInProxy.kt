package com.jacobibanez.plugin.android.godotplaygameservices.signin

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.games.AuthenticationResult
import com.google.android.gms.games.GamesClientStatusCodes
import com.google.android.gms.games.GamesSignInClient
import com.google.android.gms.games.PlayGames
import com.google.android.gms.tasks.Task
import com.jacobibanez.plugin.android.godotplaygameservices.BuildConfig
import com.jacobibanez.plugin.android.godotplaygameservices.signals.SignInSignals.serverSideAccessRequested
import com.jacobibanez.plugin.android.godotplaygameservices.signals.SignInSignals.userAuthenticated
import com.jacobibanez.plugin.android.godotplaygameservices.signals.SignInSignals.userAuthenticationFailed
import org.godotengine.godot.Dictionary
import org.godotengine.godot.Godot
import org.godotengine.godot.plugin.GodotPlugin.emitSignal

class SignInProxy(
    private val godot: Godot,
    private val gamesSignInClient: GamesSignInClient = PlayGames.getGamesSignInClient(godot.getActivity()!!)
) {

    private companion object {
        const val PLAY_GAMES_PACKAGE = "com.google.android.play.games"
        const val SOURCE_IS_AUTHENTICATED = "isAuthenticated"
        const val SOURCE_SIGN_IN = "signIn"
        const val REASON_API_EXCEPTION = "api_exception"
        const val REASON_TASK_EXCEPTION = "task_exception"
        const val REASON_TASK_CANCELED = "task_canceled"
        const val REASON_NOT_AUTHENTICATED = "not_authenticated"
    }

    private val tag: String = SignInProxy::class.java.simpleName

    fun isAuthenticated() {
        Log.d(tag, "Checking if user is authenticated")
        gamesSignInClient.isAuthenticated.addOnCompleteListener { task ->
            handleAuthTask(task, SOURCE_IS_AUTHENTICATED)
        }
    }

    fun signIn() {
        Log.d(tag, "Signing in")
        gamesSignInClient.signIn().addOnCompleteListener { task ->
            handleAuthTask(task, SOURCE_SIGN_IN)
        }
    }

    private fun handleAuthTask(task: Task<AuthenticationResult>, source: String) {
        if (task.isSuccessful) {
            val isAuthenticated = task.result?.isAuthenticated == true
            Log.d(tag, "Auth [$source] completed. isAuthenticated=$isAuthenticated")
            if (!isAuthenticated) {
                emitSignal(
                    godot,
                    BuildConfig.GODOT_PLUGIN_NAME,
                    userAuthenticationFailed,
                    0,
                    REASON_NOT_AUTHENTICATED,
                    "AuthenticationResult.isAuthenticated returned false (user cancelled, no Play Games profile, or Play Games app unavailable/outdated)",
                    source
                )
            }
            emitSignal(
                godot,
                BuildConfig.GODOT_PLUGIN_NAME,
                userAuthenticated,
                isAuthenticated
            )
        } else {
            val exception = task.exception
            val statusCode: Int
            val reason: String
            val message: String

            when {
                task.isCanceled && exception == null -> {
                    statusCode = -1
                    reason = REASON_TASK_CANCELED
                    message = "Authentication task was canceled"
                }
                exception is ApiException -> {
                    statusCode = exception.statusCode
                    reason = REASON_API_EXCEPTION
                    val statusName = GamesClientStatusCodes.getStatusCodeString(statusCode)
                    val detail = exception.localizedMessage?.takeIf { it.isNotBlank() }
                        ?: exception.message?.takeIf { it.isNotBlank() }
                        ?: "No details"
                    message = "${exception::class.java.simpleName} ($statusName): $detail"
                }
                else -> {
                    statusCode = -1
                    reason = REASON_TASK_EXCEPTION
                    message = if (exception != null) {
                        val detail = exception.localizedMessage?.takeIf { it.isNotBlank() }
                            ?: exception.message?.takeIf { it.isNotBlank() }
                            ?: "No details"
                        "${exception::class.java.simpleName}: $detail"
                    } else {
                        "Task failed with no exception"
                    }
                }
            }

            Log.e(tag, "Auth [$source] failed (code=$statusCode, reason=$reason): $message", exception)
            emitSignal(
                godot,
                BuildConfig.GODOT_PLUGIN_NAME,
                userAuthenticationFailed,
                statusCode,
                reason,
                message,
                source
            )
            emitSignal(
                godot,
                BuildConfig.GODOT_PLUGIN_NAME,
                userAuthenticated,
                false
            )
        }
    }

    fun getPlayServicesDiagnostics(): Dictionary {
        val result = Dictionary()
        val activity = godot.getActivity()
        if (activity == null) {
            result["play_services_status_code"] = -1
            result["play_services_status_name"] = "NO_ACTIVITY"
            result["play_services_available"] = false
            result["play_services_resolvable"] = false
            result["play_games_app_installed"] = false
            result["play_games_app_enabled"] = false
            result["play_games_app_version_name"] = ""
            result["play_games_app_version_code"] = -1L
            return result
        }

        val apiAvailability = GoogleApiAvailability.getInstance()
        val gmsStatus = apiAvailability.isGooglePlayServicesAvailable(activity)
        result["play_services_status_code"] = gmsStatus
        result["play_services_status_name"] = apiAvailability.getErrorString(gmsStatus)
        result["play_services_available"] = (gmsStatus == ConnectionResult.SUCCESS)
        result["play_services_resolvable"] = apiAvailability.isUserResolvableError(gmsStatus)

        try {
            val pkgInfo = getPackageInfoCompat(activity.packageManager, PLAY_GAMES_PACKAGE)
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkgInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pkgInfo.versionCode.toLong()
            }
            result["play_games_app_installed"] = true
            result["play_games_app_enabled"] = pkgInfo.applicationInfo?.enabled ?: false
            result["play_games_app_version_name"] = pkgInfo.versionName ?: ""
            result["play_games_app_version_code"] = versionCode
        } catch (_: PackageManager.NameNotFoundException) {
            result["play_games_app_installed"] = false
            result["play_games_app_enabled"] = false
            result["play_games_app_version_name"] = ""
            result["play_games_app_version_code"] = -1L
        } catch (e: Exception) {
            Log.w(tag, "Failed to query package info for $PLAY_GAMES_PACKAGE: ${e.message}", e)
            result["play_games_app_installed"] = false
            result["play_games_app_enabled"] = false
            result["play_games_app_version_name"] = ""
            result["play_games_app_version_code"] = -1L
        }

        return result
    }

    private fun getPackageInfoCompat(packageManager: PackageManager, packageName: String): PackageInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0)
        }
    }

    fun signInRequestServerSideAccess(serverClientId: String, forceRefreshToken: Boolean) {
        Log.d(
            tag,
            "Requesting server side access for client id $serverClientId with refresh token $forceRefreshToken"
        )
        gamesSignInClient.requestServerSideAccess(serverClientId, forceRefreshToken)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(tag, "Access granted to server side for user: $serverClientId")
                    emitSignal(
                        godot,
                        BuildConfig.GODOT_PLUGIN_NAME,
                        serverSideAccessRequested,
                        task.result
                    )
                } else {
                    Log.e(
                        tag,
                        "Failed to request server side access. Cause: ${task.exception}",
                        task.exception
                    )
                }
            }
    }
}