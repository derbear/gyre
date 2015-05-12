package edu.berkeley.eecs.fastacc;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataItemBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.Buffer;
import java.util.Iterator;


public class PhoneActivity extends ActionBarActivity implements SensorEventListener,
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener,
        DataApi.DataListener {

    public static final String BACKING_FILENAME = "sensor_data" + System.currentTimeMillis();

    private DataOutputStream phoneRecordWriter;
    private DataOutputStream watchRecordWriter;

    private boolean recording = false;

    private int readingNum = 0;

    private GoogleApiClient googleApiClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.v("PhoneActivity", System.currentTimeMillis() + "");

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_phone);

        try {
            phoneRecordWriter = initializeWriter("phone_" + BACKING_FILENAME);
            watchRecordWriter = initializeWriter("watch_" + BACKING_FILENAME);
        } catch (IOException e) {
            Log.e("PhoneActivity", "failed to initialize backing store", e);
        }

        // register API client
        googleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();

        setRecording(false);
        Button b = (Button) findViewById(R.id.recordingToggleButton);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setRecording(!recording);
            }
        });
    }

    private DataOutputStream initializeWriter(String filename) throws IOException {
        File external = new File(getExternalFilesDir(null), filename);
        return new DataOutputStream(new BufferedOutputStream(new FileOutputStream(external)));
    }

    private void setRecording(boolean recording) {
        if (this.recording == recording) {
            return;
        }
        this.recording = recording;

        if (recording) {
            TextView tv = (TextView) findViewById(R.id.recordingFlagTextView);
            tv.setText("" + recording);

            startRecording();

        } else {
            stopRecording();

            TextView tv = (TextView) findViewById(R.id.recordingFlagTextView);
            tv.setText("" + recording);
        }

    }

    private void startRecording() {
        SensorManager sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        Sensor s = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorManager.registerListener(this, s, SensorManager.SENSOR_DELAY_FASTEST);
        s = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        sensorManager.registerListener(this, s, SensorManager.SENSOR_DELAY_FASTEST);

        googleApiClient.connect();
    }

    private void stopRecording() {
        googleApiClient.disconnect();

        SensorManager sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        sensorManager.unregisterListener(this);

        try {
            phoneRecordWriter.flush();
            watchRecordWriter.flush();
        } catch (IOException e) {
            Log.e("PhoneActivity", "failed to flush", e);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        setRecording(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        try {
            synchronized (phoneRecordWriter) {
                // 1: device either accelerometer or gyroscope
                byte accel = 0;
                if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
                    accel = 1;
                }
                phoneRecordWriter.writeByte(accel);

                // 2: reading number
                phoneRecordWriter.writeInt(readingNum);
                readingNum++;

                // 3: system timestamp
                // 4: event timestamp
                phoneRecordWriter.writeLong(System.currentTimeMillis());
                phoneRecordWriter.writeLong(event.timestamp);

                // 5: x, y, z
                phoneRecordWriter.writeFloat(event.values[0]);
                phoneRecordWriter.writeFloat(event.values[1]);
                phoneRecordWriter.writeFloat(event.values[2]);
            }
        } catch (IOException e) {
            Log.e("PhoneActivity", "failed to write sensor data!", e);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        Log.v("WatchActivity", "accuracy changed: " + sensor + ": " + accuracy);
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.e("WatchActivity", "connection failed: " + connectionResult);
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.e("WatchActivity", "connection suspended: " + i);
    }

    @Override
    public void onConnected(Bundle bundle) {
        Log.v("WatchActivity", "connected");
        Wearable.DataApi.addListener(googleApiClient, this);
    }

    final class DataReceivedCallback implements ResultCallback<DataItemBuffer> {
        @Override
        public void onResult(DataItemBuffer dataItems) {
            try {
                synchronized (watchRecordWriter) {
                    for (DataItem item : dataItems) {
                        DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();
                        for (String key : dataMap.keySet()) {
                            byte[] data = dataMap.getByteArray(key);

                            // 1: packet number
                            watchRecordWriter.writeInt(Integer.parseInt(key));

                            // 2: receive timestamp
                            watchRecordWriter.writeLong(System.currentTimeMillis());

                            // 3: packet size
                            watchRecordWriter.writeInt(data.length);

                            // 4: packet
                            watchRecordWriter.write(data);
                        }
                    }
                    dataItems.release();
                }
            } catch (IOException e) {
                Log.e("PhoneActivity", "failed to write watch data", e);
            }
        }
    }
    private final DataReceivedCallback drcbInstance = new DataReceivedCallback();

    @Override
    public void onDataChanged(DataEventBuffer dataEventBuffer) {
        for (DataEvent e: dataEventBuffer) {
            PendingResult<DataItemBuffer> results = Wearable.DataApi.getDataItems(googleApiClient,
                    e.getDataItem().getUri());
            results.setResultCallback(drcbInstance);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_phone, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }



}
