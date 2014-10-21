package com.puuga.locationtracking;

import android.app.Activity;
import android.location.Location;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;


public class MainActivity extends Activity implements
        GooglePlayServicesClient.ConnectionCallbacks,
        GooglePlayServicesClient.OnConnectionFailedListener,
        LocationListener {

    LocationClient mLocationClient;
    LocationRequest mLocationRequest;
    Location currentLocation;
    Location lastLocation;

    TextView tvStatus;
    TextView tvAccurate;

    ArrayList<Location> results;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = (TextView) findViewById(R.id.tv_status);
        tvAccurate = (TextView) findViewById(R.id.tv_accurate);

        mLocationClient = new LocationClient(this, this, this);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();

        mLocationClient.connect();
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mLocationClient.isConnected()) {
            mLocationClient.removeLocationUpdates(this);
        }
        mLocationClient.disconnect();
    }

    @Override
    public void onConnected(Bundle bundle) {
        Log.d("PS", "connected");

        initLocationManager();
    }

    @Override
    public void onDisconnected() {
        Log.d("PS", "disconnected");
    }

    @Override
    public void onLocationChanged(Location location) {
        lastLocation = currentLocation;
        currentLocation = location;
        if (lastLocation == null) {
            return;
        }

        tvAccurate.setText(String.valueOf(currentLocation.getAccuracy()));
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.d("PS", "connect failed");
    }

    public void toggleService(View view) {
        // Is the toggle on?
        boolean on = ((ToggleButton) view).isChecked();

        if (on) {
            mLocationClient.connect();
            tvStatus.setText("Working");

            if (results == null) {
                results = new ArrayList<Location>();
            } else {
                results.clear();
            }
        } else {
            tvStatus.setText("Stop");
            if (mLocationClient.isConnected()) {
                mLocationClient.removeLocationUpdates(this);
            }
            mLocationClient.disconnect();

            makeCSV();
        }
    }

    private void makeCSV() {
        new Thread() {
            public void run() {
                try {

                    FileWriter fw = new FileWriter(getOutputResultFile());

                    fw.append("time,");
                    fw.append("latitude,");
                    fw.append("longitude,");
                    fw.append("accuracy,");
                    fw.append("speed");
                    fw.append('\n');

                    for (int i=0; i<results.size(); i++) {
                        Location location = results.get(i);
                        fw.append(location.getTime()+",");
                        fw.append(location.getLatitude()+",");
                        fw.append(location.getLongitude()+",");
                        if (location.hasAccuracy()) {
                            fw.append(location.getAccuracy()+",");
                        } else {
                            fw.append("-1,");
                        }
                        if (location.hasSpeed()) {
                            fw.append(location.getSpeed()+",");
                        } else {
                            fw.append("0,");
                        }
                        fw.append('\n');
                    }


                    // fw.flush();
                    fw.close();

                } catch (Exception e) {
                }
            }
        }.start();
    }

    private void initLocationManager() {
        if (mLocationClient == null){
            mLocationClient = new LocationClient(this, this, this);
        }

        if (mLocationRequest == null) {
            mLocationRequest = LocationRequest.create();
            mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
            mLocationRequest.setInterval(0);
            mLocationRequest.setFastestInterval(0);
        }

        mLocationClient.requestLocationUpdates(mLocationRequest, this);
    }

    private static File getOutputResultFile() {
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.

        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOCUMENTS), "LocationData");
        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                Log.d("LocationData", "failed to create directory");
                return null;
            }
        }

        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                "csv_" + timeStamp + ".csv");


        return mediaFile;
    }
}
