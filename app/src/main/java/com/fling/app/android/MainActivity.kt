package com.fling.app.android

import android.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.iid.FirebaseInstanceId
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.kakao.sdk.auth.LoginClient
import com.kakao.sdk.common.KakaoSdk


private var flingWebView: WebView? = null
private var mainIntent: Intent? = null

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            this.supportActionBar!!.hide()
        } catch (e: NullPointerException) {
        }

        mainIntent = intent
        // Kakao SDK 초기화
        KakaoSdk.init(this, "e7a6bc6bfab031c7fde8755d08149079")

        flingWebView = WebView(this)
        flingWebView!!.settings.javaScriptEnabled = true
        flingWebView!!.addJavascriptInterface(WebAppInterface(this), "FlingWeb")
        setContentView(flingWebView)

        flingWebView!!.loadUrl("https://fling-web.azurewebsites.net/app?platform=android")
        //flingWebView!!.loadUrl("http://192.168.0.21:8080/app?platform=android")

    }

    override fun onResume() {
        super.onResume()
        // registering BroadcastReceiver
        val intentFilter = IntentFilter("showComponentPostDetailPopup")
        registerReceiver(showComponentPostDetailPopupReceiver, intentFilter)
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(
            showComponentPostDetailPopupReceiver
        )
    }

    private val showComponentPostDetailPopupReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {

            // reload your webview here
            flingWebView?.evaluateJavascript(
                "window.FlingWebView.showComponentPostDetailPopup('${
                    intent.getBundleExtra("msg").getString(
                        "selectedPostId"
                    )
                }');",
                null
            )
        }
    }
}

class WebAppInterface(private val mContext: Context) {

    /** Show a toast from the web page  */
    @JavascriptInterface
    fun showToast(toast: String) {
        Toast.makeText(mContext, toast, Toast.LENGTH_SHORT).show()
    }

    @JavascriptInterface
    fun clickKakaoLogin() {

        var KAKAO_TAG = "[kakao]";

        if (LoginClient.instance.isKakaoTalkLoginAvailable(mContext)) {
            LoginClient.instance.loginWithKakaoTalk(mContext) { token, error ->
                if (error != null) {
                    Log.e(KAKAO_TAG, "loginWithKakaoTalk - 로그인 실패", error)
                }
                else if (token != null) {
                    Log.i(KAKAO_TAG, "loginWithKakaoTalk - 로그인 성공 ${token.accessToken}")
                }
            }
        }
        else {
            LoginClient.instance.loginWithKakaoAccount(mContext) { token, error ->
                if (error != null) {
                    Log.e(KAKAO_TAG, "loginWithKakaoAccount - 로그인 실패", error)
                }
                else if (token != null) {
                    Log.i(KAKAO_TAG, "loginWithKakaoAccount - 로그인 성공 ${token.accessToken}")
                    //Toast.makeText(mContext, token.accessToken, Toast.LENGTH_SHORT).show()

                    flingWebView?.evaluateJavascript(
                        "window.FlingWebView.verifyKakaoAuth('${token.accessToken}');",
                        null
                    )
                }
            }
        }
    }

    @JavascriptInterface
    fun onSuccessLogin() {

        var FIREBASE_TAG = "[firebase]";

        FirebaseInstanceId.getInstance().instanceId
            .addOnCompleteListener(OnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w(FIREBASE_TAG, "getInstanceId failed", task.exception)
                    return@OnCompleteListener
                }

                // Get new Instance ID token
                val deviceToken = task.result?.token as String

                // Log and toast
                Log.d(FIREBASE_TAG, deviceToken)

                flingWebView?.evaluateJavascript(
                    "window.FlingWebView.setDeviceToken('${deviceToken}');",
                    null
                )
            })
    }
}


class MessagingService : FirebaseMessagingService() {
    // [START receive_message]
    override fun onMessageReceived(remoteMessage: RemoteMessage) {

        var TAG = "[firebase-messaging]";

        // [START_EXCLUDE]
        // There are two types of messages data messages and notification messages. Data messages are handled
        // here in onMessageReceived whether the app is in the foreground or background. Data messages are the type
        // traditionally used with GCM. Notification messages are only received here in onMessageReceived when the app
        // is in the foreground. When the app is in the background an automatically generated notification is displayed.
        // When the user taps on the notification they are returned to the app. Messages containing both notification
        // and data payloads are treated as notification messages. The Firebase console always sends notification
        // messages. For more see: https://firebase.google.com/docs/cloud-messaging/concept-options
        // [END_EXCLUDE]

        // TODO(developer): Handle FCM messages here.
        // Not getting messages here? See why this may be: https://goo.gl/39bRNJ
        Log.d(TAG, "From: ${remoteMessage.from}")

        // Check if message contains a data payload.
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")

            if (remoteMessage.data["actionType"] == "showComponent") {
                if (remoteMessage.data["componentName"] == "postDetailDialog") {
                    Log.d(
                        TAG,
                        "window.FlingWebView.showComponentPostDetailPopup('${remoteMessage.data["selectedPostId"]}');"
                    )


                    /*
                    Push Notification Show
                     */

                    val showComponentPostDetailPopup = Intent()
                    val bundle = Bundle() // use bundle if you want to pass data
                    bundle.putString("selectedPostId", remoteMessage.data["selectedPostId"].toString())
                    showComponentPostDetailPopup.putExtra("msg", bundle)
                    showComponentPostDetailPopup.action = "showComponentPostDetailPopup"
                    //sendBroadcast(showComponentPostDetailPopup)

//                    val pendingIntent =
//                        PendingIntent.getActivity(this, 0, mainIntent, PendingIntent.FLAG_UPDATE_CURRENT)

                    val pendingIntent = PendingIntent.getBroadcast(this, 0, showComponentPostDetailPopup, PendingIntent.FLAG_UPDATE_CURRENT)

                    val channelId = "Default"
                    val builder = NotificationCompat.Builder(this, channelId)
                        .setSmallIcon(R.drawable.ic_notification_overlay)
                        .setContentTitle(remoteMessage.notification!!.title)
                        .setContentText(remoteMessage.notification!!.body).setAutoCancel(true)
                        .setContentIntent(pendingIntent)
                    val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val channel = NotificationChannel(
                            channelId,
                            "Default channel",
                            NotificationManager.IMPORTANCE_DEFAULT
                        )
                        manager.createNotificationChannel(channel)
                    }
                    manager.notify(0, builder.build())


                    /*
                    Push Notification Click Event Handling
                     */
//                    val showComponentPostDetailPopup = Intent()
//                    val bundle = Bundle() // use bundle if you want to pass data
//
//                    bundle.putString("selectedPostId", remoteMessage.data["selectedPostId"].toString())
//                    showComponentPostDetailPopup.putExtra("msg", bundle)
//
//                    showComponentPostDetailPopup.action = "showComponentPostDetailPopup"
//                    sendBroadcast(showComponentPostDetailPopup)
                }
            }
        }

        // Check if message contains a notification payload.
        remoteMessage.notification?.let {
            Log.d(TAG, "Message Notification Body: ${it.body}")
        }

        // Also if you intend on generating your own notifications as a result of a received FCM
        // message, here is where that should be initiated. See sendNotification method below.
    }
    // [END receive_message]

}