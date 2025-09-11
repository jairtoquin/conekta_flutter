package com.apeirotechnologies.conekta_flutter

import android.app.Activity
import android.content.Context
import androidx.annotation.NonNull
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result

private const val API_KEY_ARGUMENT_NAME = "apiKey"
private const val CARD_NAME_ARGUMENT_NAME = "cardName"
private const val CARD_NUMBER_ARGUMENT_NAME = "cardNumber"
private const val CVV_ARGUMENT_NAME = "cvv"
private const val EXPIRATION_MONTH_ARGUMENT_NAME = "expirationMonth"
private const val EXPIRATION_YEAR_ARGUMENT_NAME = "expirationYear"
private const val SET_API_KEY_METHOD_NAME = "setApiKey"
private const val ON_CREATE_CARD_TOKEN_METHOD_NAME = "onCreateCardToken"
private const val METHOD_CHANNEL_NAME = "conekta_flutter"

/** ConektaFlutterPlugin (Android embedding v2) */
class ConektaFlutterPlugin : FlutterPlugin, MethodCallHandler, ActivityAware {

    private lateinit var channel: MethodChannel
    private lateinit var conektaProvider: ConektaProvider

    private var applicationContext: Context? = null
    private var activity: Activity? = null

    // ---- FlutterPlugin ----
    override fun onAttachedToEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        applicationContext = binding.applicationContext
        channel = MethodChannel(binding.binaryMessenger, METHOD_CHANNEL_NAME)
        channel.setMethodCallHandler(this)
        conektaProvider = ConektaProvider()
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
        applicationContext = null
    }

    // ---- ActivityAware ----
    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
        // Inicializa el provider que requiere Activity
        conektaProvider.init(binding.activity)
    }

    override fun onDetachedFromActivityForConfigChanges() {
        // Se separa por rotación/cambio de config; limpiar referencias a Activity
        activity = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        // Reasigna Activity tras cambio de config
        activity = binding.activity
        conektaProvider.init(binding.activity)
    }

    override fun onDetachedFromActivity() {
        activity = null
    }

    // ---- MethodChannel ----
    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
        when (call.method) {
            SET_API_KEY_METHOD_NAME -> {
                val apiKey = call.argument<String>(API_KEY_ARGUMENT_NAME)
                if (apiKey.isNullOrBlank()) {
                    result.error(
                        ConektaError.ApiKeyNotProvided.code,
                        ConektaError.ApiKeyNotProvided.message,
                        null
                    )
                    return
                }
                conektaProvider.setApiKey(apiKey)
                result.success(true)
            }

            ON_CREATE_CARD_TOKEN_METHOD_NAME -> {
                val card = getCardArgument(call.arguments)
                val apiKey = conektaProvider.getApiKey()

                if (apiKey.isNullOrBlank()) {
                    result.error(
                        ConektaError.ApiKeyNotProvided.code,
                        ConektaError.ApiKeyNotProvided.message,
                        null
                    )
                    return
                }

                if (card == null) {
                    result.error(
                        ConektaError.InvalidCardArguments.code,
                        ConektaError.InvalidCardArguments.message,
                        null
                    )
                    return
                }

                conektaProvider.onCreateCardToken(card) { token, error ->
                    if (error == null) {
                        result.success(token)
                    } else {
                        result.error(error.code, error.message, null)
                    }
                }
            }

            else -> result.notImplemented()
        }
    }

    // ---- Util ----
    private fun getCardArgument(arguments: Any?): ConektaCard? {
        val map = arguments as? Map<*, *> ?: return null

        val name = map[CARD_NAME_ARGUMENT_NAME] as? String ?: return null
        val number = map[CARD_NUMBER_ARGUMENT_NAME] as? String ?: return null
        val cvv = map[CVV_ARGUMENT_NAME] as? String ?: return null
        val month = map[EXPIRATION_MONTH_ARGUMENT_NAME] as? String ?: return null
        val year = map[EXPIRATION_YEAR_ARGUMENT_NAME] as? String ?: return null

        return ConektaCard(
            cardName = name,
            cardNumber = number,
            cvv = cvv,
            expirationMonth = month,
            expirationYear = year
        )
    }
}
