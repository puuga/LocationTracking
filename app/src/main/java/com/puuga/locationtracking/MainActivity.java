package com.puuga.locationtracking;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
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
        LocationListener,
        SensorEventListener {

    LocationClient mLocationClient;
    LocationRequest mLocationRequest;
    Location currentLocation;
    Location lastLocation;

    TextView tvStatus;
    TextView tvAccurate;
    TextView tvFrequency;

    ArrayList<Location> results;

    private SensorManager mSensorManager;
    private Sensor mSensor;
    private Sensor mLinearSensor;
    private double[] gravity = new double[3];
    private double[] linear_acceleration = new double[3];
    final float alpha = 0.8f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = (TextView) findViewById(R.id.tv_status);
        tvAccurate = (TextView) findViewById(R.id.tv_accurate);
        tvFrequency = (TextView) findViewById(R.id.tv_frequency);

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

        initAndStartMotion();
        //mLocationClient.connect();
    }

    @Override
    protected void onPause() {
        super.onPause();

        stopMotion();

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
        tvFrequency.setText(String.valueOf(((double)(currentLocation.getTime()-lastLocation.getTime()))/1000));

        // save
        results.add(location);
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

                    for (Location location : results) {
                        fw.append(String.valueOf(location.getTime())).append(",");
                        fw.append(String.valueOf(location.getLatitude())).append(",");
                        fw.append(String.valueOf(location.getLongitude())).append(",");
                        if (location.hasAccuracy()) {
                            fw.append(String.valueOf(location.getAccuracy())).append(",");
                        } else {
                            fw.append("-1,");
                        }
                        if (location.hasSpeed()) {
                            fw.append(String.valueOf(location.getSpeed()));
                        } else {
                            fw.append("0");
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

    private void initAndStartMotion() {
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        if (mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION) != null){
            // Success! There's a TYPE_LINEAR_ACCELERATION.
            Log.d("sensor", "use TYPE_LINEAR_ACCELERATION");
            mLinearSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        }
        else {
            // Failure! No TYPE_LINEAR_ACCELERATION.
            Log.d("sensor", "use TYPE_ACCELEROMETER");
            mLinearSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }

        mSensorManager.registerListener(this, mLinearSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    private void stopMotion() {
        try {
            mSensorManager.unregisterListener(this);
        } catch (NullPointerException e) {
            //e.printStackTrace();
        }
        mSensorManager = null;
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        //Log.d("onSensorChanged",sensorEvent.toString());

        //Log.d("senser type", String.valueOf(sensorEvent.sensor.getType()));
        float gX = sensorEvent.values[0];
        float gY = sensorEvent.values[0];
        float gZ = sensorEvent.values[0];
        Log.d("xyz","x:"+gX+", y:"+gY+", z:"+gZ);
        if (sensorEvent.sensor.getType() == 10) {
            double currentCalAcc = Math.sqrt(Math.pow(gX,2) + Math.pow(gY,2) + Math.pow(gZ,2));
            Log.d("CalAcc","CalAcc:"+currentCalAcc);
        } else {
            gravity[0] = alpha * gravity[0] + (1 - alpha) * gX;
            gravity[1] = alpha * gravity[1] + (1 - alpha) * gY;
            gravity[2] = alpha * gravity[2] + (1 - alpha) * gZ;

            linear_acceleration[0] = gX - gravity[0];
            linear_acceleration[1] = gY - gravity[1];
            linear_acceleration[2] = gZ - gravity[2];

            double currentCalAcc = Math.sqrt(Math.pow(linear_acceleration[0],2) + Math.pow(linear_acceleration[1],2) + Math.pow(linear_acceleration[2],2));
            Log.d("CalAccMod","CalAccMod:"+currentCalAcc);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
        Log.d("onAccuracyChanged",sensor.toString());
    }
}
