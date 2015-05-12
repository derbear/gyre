package edu.berkeley.eecs.fastacc;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.nio.ByteBuffer;

public class WatchActivity extends Activity implements SensorEventListener,
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener  {

    public static final String SENSOR_DATA_PATH = "/sensors";

    private TextView mTextView;

    private GoogleApiClient googleApiClient;

    // buffered writing
    public static final int BUFFER_READINGS_SIZE_LIMIT = 600;
    public static final int BUFFER_READINGS_TIME_LIMIT = 250;

    private int readingNumber = 0;
    private int packetNumber = 0;

    private long lastFlushTime = 0;
    private int readingsWritten = 0;

    // 33 hardcoded, check that this actually works
    private final ByteBuffer buffer = ByteBuffer.allocate(BUFFER_READINGS_SIZE_LIMIT * 33);


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_watch);
        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                mTextView = (TextView) stub.findViewById(R.id.text);
            }
        });

        // register API client
        googleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // register sensors
        SensorManager sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        Sensor s = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorManager.registerListener(this, s, SensorManager.SENSOR_DELAY_FASTEST);
        s = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        sensorManager.registerListener(this, s, SensorManager.SENSOR_DELAY_FASTEST);

        googleApiClient.connect();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // unregister sensors
        SensorManager sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        sensorManager.unregisterListener(this);

        googleApiClient.disconnect();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        synchronized (this) {
            // 1: device either accelerometer or gyroscope
            byte accel = 0;
            if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
                accel = 1;
            }
            buffer.put(accel);

            // 2: put reading number in
            buffer.putInt(readingNumber);
            readingNumber++;

            // 3: put system timestamp
            // 4: put event timestamp
            buffer.putLong(System.currentTimeMillis());
            buffer.putLong(event.timestamp);

            // 5: put x, y, z
            buffer.putFloat(event.values[0]);
            buffer.putFloat(event.values[1]);
            buffer.putFloat(event.values[2]);

            readingsWritten++;
            checkFlush();
        }
    }

    private void checkFlush() {
        if (readingsWritten >= BUFFER_READINGS_SIZE_LIMIT-1 ||
                System.currentTimeMillis() - lastFlushTime >= BUFFER_READINGS_TIME_LIMIT) {
            // Log.v("WatchActivity", "firing flush");

            // special EOF character
            buffer.put((byte) -1);

            PutDataMapRequest fetchRequest = PutDataMapRequest.create(SENSOR_DATA_PATH);
            fetchRequest.getDataMap().putByteArray(Integer.toString(packetNumber), buffer.array());
            PutDataRequest request = fetchRequest.asPutDataRequest();
            PendingResult<DataApi.DataItemResult> result = Wearable.DataApi.putDataItem(googleApiClient, request);

            buffer.clear();
            packetNumber++;
            readingsWritten = 0;
            lastFlushTime = System.currentTimeMillis();
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
    }
}
