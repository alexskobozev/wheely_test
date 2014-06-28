package ru.eternalkaif.wheely_test;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;

import de.tavendo.autobahn.WebSocket;
import de.tavendo.autobahn.WebSocketConnection;
import de.tavendo.autobahn.WebSocketException;
import de.tavendo.autobahn.WebSocketOptions;

public class MyService extends Service {

    public static final String CODE_CONNECT = "connect";
    public static final String CODE_DISCONNECT = "disconnect";
    public static final String CODE_NEW_MESSAGE = "newmessage";
    public static final String CONNECTION_RECEIVER = "connection_receiver";

    private int NOTIFICATION = 42;


    private String mUsername;
    private String mPassword;
    private Looper mServiceLooper;
    private ConnectToSocket mAuthTask;
    private NotificationManager mNM;
    private final WebSocket mConnection;
    private SharedPreferences mPrefs;

    public MyService() {
        mConnection = new WebSocketConnection();
    }

    @Override
    public void onCreate() {
        mNM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        showNotification();
    }


    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent.getExtras() != null) {
            mUsername = intent.getStringExtra(LoginActivity.SPREF_USERNAME);
            mPassword = intent.getStringExtra(LoginActivity.SPREF_PASSWORD);
            mAuthTask = new ConnectToSocket(mUsername, mPassword);
            mAuthTask.execute((Void) null);
        }
        mPrefs = getSharedPreferences(Constants.DEFAULT_PREFS, MODE_PRIVATE);
        // Timer to check connection
        Timer checkConnectTimer = new Timer();
        checkConnectTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                Log.d("Websocket", "checking timer");
                if (!mConnection.isConnected()) {
                    Log.d("Websocket", "not connected check");
                    sendMessage(CODE_DISCONNECT);
                    mAuthTask.cancel(true);
                    if (mUsername != null && mPassword != null) {
                        mAuthTask = new ConnectToSocket(mUsername, mPassword);
                        mAuthTask.execute((Void) null);
                    } else {
                        mAuthTask = new ConnectToSocket(mPrefs.getString(
                                LoginActivity.SPREF_USERNAME, ""), mPrefs.getString(
                                LoginActivity.SPREF_PASSWORD, ""));
                        mAuthTask.execute((Void) null);
                    }
                } else {
                    Log.d("Websocket", "connected check");
                }
            }
        }, 2000, 2000);


        return START_STICKY_COMPATIBILITY;
    }

    public class ConnectToSocket extends AsyncTask<Void, Void, Boolean> {

        private final String mUsername;
        private final String mPassword;

        final WebSocketOptions options;

        ConnectToSocket(String username, String password) {
            mUsername = username;
            mPassword = password;

            options = new WebSocketOptions();
            options.setActivityTimeout(10000);
            options.setPongTimeout(10000);
            options.setSocketReceiveTimeout(10000);

        }

        @Override
        protected Boolean doInBackground(final Void... params) {


            Log.d("Websocket", "trying to connect");
            try {
                mConnection.connect(Constants.SERVER_URL
                                + "?username=" + mUsername
                                + "&password=" + mPassword,
                        new WebSocket.ConnectionHandler() {

                            @Override
                            public void onOpen() {
                                sendMessage(CODE_CONNECT);
                                Log.d("Websocket", "onOpen");
                                Intent intent = new Intent();
                                intent.setAction("services.wheelyService");
                                getApplicationContext().startService(intent);
                                mConnection.sendTextMessage("{\"lat\":55.373703,\"lon\": 37.474764}");

                            }

                            @Override
                            public void onClose(int code, Bundle data) {
                                sendMessage(CODE_DISCONNECT);
                                Log.d("Websocket", "onClose, code " + code + " data = " + data.toString());
                            }

                            @Override
                            public void onTextMessage(String payload) {
                                Log.d("Websocket", "onTextMessage " + payload);
                                sendMessage(CODE_NEW_MESSAGE, payload);

                            }

                            @Override
                            public void onRawTextMessage(byte[] payload) {
                                Log.d("Websocket", "onRawTextMessage " + Arrays.toString(payload));
                            }

                            @Override
                            public void onBinaryMessage(byte[] payload) {
                                Log.d("Websocket", "onBinaryMessage " + Arrays.toString(payload));

                            }

                        }, options
                );
            } catch (WebSocketException e) {
                Log.d("Websocket", "WebSocketException");
                e.printStackTrace();
                sendMessage(CODE_DISCONNECT);
                return false;
            }


            return true;
        }
    }


    private void sendMessage(String code) {
        Intent intent = new Intent(CONNECTION_RECEIVER);
        intent.putExtra("message", code);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void sendMessage(String codeNewMessage, String payload) {
        Intent intent = new Intent(CONNECTION_RECEIVER);
        intent.putExtra("message", codeNewMessage);
        intent.putExtra("body", payload);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void showNotification() {
        // In this sample, we'll use the same text for the ticker and the expanded notification
        CharSequence text = getText(R.string.local_service_started);

        // Set the icon, scrolling text and timestamp
        Notification notification = new Notification(R.drawable.ic_launcher, text,
                System.currentTimeMillis());

        // The PendingIntent to launch our activity if the user selects this notification
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, MapsActivity.class), 0);

        // Set the info for the views that show in the notification panel.
        notification.setLatestEventInfo(this, getText(R.string.local_service_label),
                text, contentIntent);

        startForeground(424, notification);
    }

    @Override
    public void onDestroy() {
        // Cancel the persistent notification.
        mNM.cancel(NOTIFICATION);

        // Tell the user we stopped.
        Toast.makeText(this, R.string.local_service_stopped, Toast.LENGTH_SHORT).show();
    }

}
