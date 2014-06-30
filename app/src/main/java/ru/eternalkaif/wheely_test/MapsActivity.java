package ru.eternalkaif.wheely_test;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.LocalBroadcastManager;

import com.github.johnpersano.supertoasts.SuperActivityToast;
import com.github.johnpersano.supertoasts.SuperToast;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class MapsActivity extends FragmentActivity {

    private static final String LAT_STRING = "lat";
    private static final String LON_STRING = "lon";
    private GoogleMap mMap; // Might be null if Google Play services APK is not available.
    ArrayList<JSONObject> coordsList;
    private SuperActivityToast superActivityToast;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        coordsList = new ArrayList<JSONObject>();
        superActivityToast = new SuperActivityToast(this,
                SuperToast.Type.PROGRESS);
        setUpMapIfNeeded();
    }

    @Override
    protected void onResume() {
        super.onResume();
        setUpMapIfNeeded();
        LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver,
                new IntentFilter(MyService.CONNECTION_RECEIVER));
        superActivityToast.setText(getString(R.string.connection));
        superActivityToast.setIndeterminate(true);
        superActivityToast.show();
    }

    @Override
    protected void onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageReceiver);
        SuperActivityToast.cancelAllSuperActivityToasts();
        super.onPause();
    }


    /**
     * Sets up the map if it is possible to do so (i.e., the Google Play services APK is correctly
     * installed) and the map has not already been instantiated.. This will ensure that we only ever
     * call {@link #setUpMap()} once when {@link #mMap} is not null.
     * <p/>
     * If it isn't installed {@link SupportMapFragment} (and
     * {@link com.google.android.gms.maps.MapView MapView}) will show a prompt for the user to
     * install/update the Google Play services APK on their device.
     * <p/>
     * A user can return to this FragmentActivity after following the prompt and correctly
     * installing/updating/enabling the Google Play services. Since the FragmentActivity may not
     * have been completely destroyed during this process (it is likely that it would only be
     * stopped or paused), {@link #onCreate(android.os.Bundle)} may not be called again so we should call this
     * method in {@link #onResume()} to guarantee that it will be called.
     */
    private void setUpMapIfNeeded() {
        // Do a null check to confirm that we have not already instantiated the map.
        if (mMap == null) {
            // Try to obtain the map from the SupportMapFragment.
            mMap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map))
                    .getMap();
            // Check if we were successful in obtaining the map.
            if (mMap != null) {
                setUpMap();
            }
        }
    }

    /**
     * This is where we can add markers or lines, add listeners or move the camera. In this case, we
     * just add a marker near Africa.
     * <p/>
     * This should only be called once and when we are sure that {@link #mMap} is not null.
     */
    private void setUpMap() {
           mMap.addMarker(new MarkerOptions().position(new LatLng(0, 0)).title("Marker"));
    }

    private boolean mConnected;
    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {


        @Override
        public void onReceive(Context context, Intent intent) {
            String message = intent.getStringExtra("message");
            if (message.equals(MyService.CODE_CONNECT)) {
                mConnected = true;
                SuperActivityToast.cancelAllSuperActivityToasts();


            } else if (message.equals(MyService.CODE_DISCONNECT)) {
                mConnected = false;
                superActivityToast.setText(getString(R.string.disconnected));
                superActivityToast.setIndeterminate(true);
                superActivityToast.show();
                //TODO:
            } else if (message.equals(MyService.CODE_NEW_MESSAGE)) {
                SuperActivityToast.cancelAllSuperActivityToasts();
                mConnected = true;
                if (intent.hasExtra("body")) {
                    try {
                        JSONArray jsonArray = new JSONArray(intent.getStringExtra("body"));
                        coordsList = new ArrayList<JSONObject>();
                        coordsList.clear(); // flush and renew quicker than update in that case
                        int length = jsonArray.length();
                        for (int i = 0; i < length; i++) {
                            coordsList.add(jsonArray.getJSONObject(i));
                        }
                        pointsToMap(coordsList);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    };

    private void pointsToMap(ArrayList<JSONObject> coordsList) {
        if (mMap == null) return;

        mMap.clear();
        for (JSONObject obj : coordsList) {
            try {
                mMap.addMarker(new MarkerOptions().position(
                                new LatLng(Double.parseDouble(obj.getString(LAT_STRING)),
                                        Double.parseDouble(obj.getString(LON_STRING)))
                        ).title("Point")
                );
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }
}
