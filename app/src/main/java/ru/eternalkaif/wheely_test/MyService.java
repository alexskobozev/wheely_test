package ru.eternalkaif.wheely_test;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;

import java.util.Timer;
import java.util.TimerTask;

import de.tavendo.autobahn.WebSocket;
import de.tavendo.autobahn.WebSocketConnection;
import de.tavendo.autobahn.WebSocketException;
import de.tavendo.autobahn.WebSocketOptions;

public class MyService extends Service implements GooglePlayServicesClient.ConnectionCallbacks,
        GooglePlayServicesClient.OnConnectionFailedListener, LocationListener {

    public static final String CODE_CONNECT = "connect";
    public static final String CODE_DISCONNECT = "disconnect";
    public static final String CODE_NEW_MESSAGE = "newmessage";
    public static final String CODE_ERROR = "wserror";
    public static final String CONNECTION_RECEIVER = "connection_receiver";

    public static final int NOTIFICATION = 42;


    private String mUsername;
    private String mPassword;
    private Looper mServiceLooper;
    private ConnectToSocket mAuthTask;
    private NotificationManager mNM;
    private final WebSocket mConnection;
    private SharedPreferences mPrefs;
    private LocationClient mLocationClient;
    private Location mCurrentLocation;
    private double mLatitude = 0;
    private double mLongitude = 0;
    private boolean mConnected;
    private LocationRequest mLocationRequest;
    public static final long UPDATE_INTERVAL = 6000;
    public static final long FASTEST_INTERVAL = 5000;
    private boolean mUpdatesRequested;


    public MyService() {
        mConnection = new WebSocketConnection();
    }

    @Override
    public void onCreate() {
        mNM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        mLocationClient = new LocationClient(this, this, this);
        mLocationRequest = LocationRequest.create();
        mLocationRequest.setPriority(
                LocationRequest.PRIORITY_HIGH_ACCURACY);
        mLocationRequest.setInterval(UPDATE_INTERVAL);
        mLocationRequest.setFastestInterval(FASTEST_INTERVAL);
        mUpdatesRequested = true;
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
//            mAuthTask = new ConnectToSocket(mUsername, mPassword);
//            mAuthTask.execute((Void) null);
        }


        mLocationClient.connect();
        mPrefs = getSharedPreferences(Constants.DEFAULT_PREFS, MODE_PRIVATE);
        // Timer to check connection
        Timer checkConnectTimer = new Timer();
        checkConnectTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (mConnected) {
                    if (mLatitude != 0 && mLongitude != 0) {
                        if (!mConnection.isConnected()) {
                            sendMessage(CODE_DISCONNECT);
                            if (mAuthTask != null)
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
                        }

                    }
                }
            }
        }, 0, 2000);


        return START_STICKY_COMPATIBILITY;
    }

    private void makeUseOfNewLocation(Location location) {
        mLongitude = location.getLongitude();
        mLatitude = location.getLatitude();
    }

    @Override
    public void onConnected(Bundle bundle) {
        mConnected = true;
        mLocationClient.requestLocationUpdates(mLocationRequest, this);
    }

    @Override
    public void onDisconnected() {
        mConnected = false;
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }

    @Override
    public void onLocationChanged(Location location) {
        makeUseOfNewLocation(location);
        if (mConnection.isConnected()) {
            mConnection.sendTextMessage("{\"lat\":" + mLatitude
                    + ",\"lon\": " + mLongitude + "}");
        }
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
                                //  mConnection.sendTextMessage("{\"lat\":55.373703,\"lon\": 37.474764}");
                                mConnection.sendTextMessage("{\"lat\":" + mLatitude
                                        + ",\"lon\": " + mLongitude + "}");

                            }

                            @Override
                            public void onClose(int code, Bundle data) {
                                sendMessage(CODE_DISCONNECT);
                            }

                            @Override
                            public void onTextMessage(String payload) {
                                sendMessage(CODE_NEW_MESSAGE, payload);
                            }

                            @Override
                            public void onRawTextMessage(byte[] payload) {
                            }

                            @Override
                            public void onBinaryMessage(byte[] payload) {

                            }

                        }, options
                );
            } catch (WebSocketException e) {
                Log.d("Websocket", "WebSocketException");
                e.printStackTrace();
                sendMessage(CODE_ERROR);
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
        mLocationClient.disconnect();
        // Tell the user we stopped.
        Toast.makeText(this, R.string.local_service_stopped, Toast.LENGTH_SHORT).show();
    }

}
