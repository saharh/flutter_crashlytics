package com.kiwi.fluttercrashlytics

import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import com.crashlytics.android.Crashlytics
import com.crashlytics.android.answers.Answers
import com.crashlytics.android.answers.CustomEvent
import com.crashlytics.android.core.CrashlyticsCore
import io.fabric.sdk.android.Fabric
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry

class FlutterCrashlyticsPlugin(private val context: Context) : MethodChannel.MethodCallHandler {
    companion object {
        @JvmStatic
        fun registerWith(registrar: PluginRegistry.Registrar) {
            val channel = MethodChannel(registrar.messenger(), "flutter_crashlytics")
            channel.setMethodCallHandler(FlutterCrashlyticsPlugin(registrar.context()))
        }
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "initialize" -> {
//                Fabric.with(context, Crashlytics())
                Fabric.with(context, Crashlytics.Builder()
                        .core(CrashlyticsCore.Builder().build())
                        .answers(Answers())
                        .build())

                result.success(null)
            }
            else -> {
                if (Fabric.isInitialized()) {
                    onInitialisedMethodCall(call, result)
                } else {
                    // Should not result in an error. Otherwise Opt Out clients would need to handle errors
                    result.success(null)
                }
            }
        }
    }

    private fun onInitialisedMethodCall(call: MethodCall, result: Result) {
        when {
            call.method == "logEvent" -> {
                val answers = Crashlytics.getInstance().answers
                val info = call.arguments as Map<String, Any>
                val eventName = info["name"] as String
                val eventParams = info["parameters"] as Map<String, Any?>?
                val event = CustomEvent(eventName)
                if (eventParams != null) {
                    for (param in eventParams) {
                        if (param.value is Number) {
                            event.putCustomAttribute(param.key, param.value as Number)
                        } else if (param.value is String) {
                            event.putCustomAttribute(param.key, param.value as String)
                        }
                    }
                }
                answers.logCustom(event)
                result.success(null)
            }
            call.method == "log" -> {
                val core = Crashlytics.getInstance().core
                if (call.arguments is String) {
                    core.log(call.arguments as String)
                } else {
                    val info = call.arguments as List<Any>
                    core.log(info[0].toString() + ": " + info[1] + " " + info[2])
                }
                result.success(null)
            }
            call.method == "setInfo" -> {
                val core = Crashlytics.getInstance().core
                val info = call.arguments as Map<String, Any>
                when (info["value"]) {
                    is String ->
                        core.setString(info["key"] as String, info["value"] as String)
                    is Int ->
                        core.setInt(info["key"] as String, info["value"] as Int)
                    is Double ->
                        core.setDouble(info["key"] as String, info["value"] as Double)
                    is Boolean ->
                        core.setBool(info["key"] as String, info["value"] as Boolean)
                    is Float ->
                        core.setFloat(info["key"] as String, info["value"] as Float)
                    is Long ->
                        core.setLong(info["key"] as String, info["value"] as Long)
                    else -> core.log("ignoring unknown type with key ${info["key"]} and value ${info["value"]}")
                }
                result.success(null)
            }
            call.method == "setUserInfo" -> {
                val core = Crashlytics.getInstance().core
                val info = call.arguments as Map<String, String>
                core.setUserEmail(info["email"])
                core.setUserName(info["name"])
                core.setUserIdentifier(info["id"])
                result.success(null)
            }
            call.method == "reportCrash" -> {
                val core = Crashlytics.getInstance().core
                val exception = (call.arguments as Map<String, Any>)
                val forceCrash = exception["forceCrash"] as? Boolean ?: false

                val throwable = Utils.create(exception)

                if (forceCrash) {
                    //Start a new activity to not crash directly under onMethod call, or it will crash JNI instead of a clean exception
                    val intent = Intent(context, CrashActivity::class.java).apply {
                        putExtra("exception", throwable)
                        flags = FLAG_ACTIVITY_NEW_TASK
                    }

                    context.startActivity(intent)
                } else {
                    core.logException(throwable)
                }
                result.success(null)
            }
            else -> result.notImplemented()
        }
    }
}
