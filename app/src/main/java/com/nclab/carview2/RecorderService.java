package com.nclab.carview2;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import org.json.JSONException;
import org.json.JSONObject;

public class RecorderService extends Service implements com.google.android.gms.location.LocationListener{

    public static Boolean isActive = false;

    private static final String TAG = "RecorderService";
    private SurfaceView mSurfaceView;
    private SurfaceHolder mSurfaceHolder;
    private static Camera mServiceCamera;
    public static boolean mRecordingStatus;
    private MediaRecorder mMediaRecorder;
    private PowerManager.WakeLock wakeLock = null;
    public static final String CHANNEL_ID = "ForegroundServiceChannel";
    public File folder = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/CarView_Lite");

    private String current_Time;
    private Double current_Lat;
    private Double current_Lng;
    private LocationManager locationManager;
    private Location current_Location;
    private float current_Direc;
    private float current_Speed; //in km/hr

    private Location previous_Location;
    private int time_Interval = 3; //in second
    private float time_Interval_hr = (float) time_Interval/3600; //in hour

    private SensorManager sm;
    private Sensor accSensor;
    private Sensor magSensor;
    float[] magneticFieldValues = new float[3];
    float[] acclerometerValues = new float[3];
    private SimpleDateFormat sd = new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault());

    private Handler handler;
    private Runnable runnable;

    private long freespace;
    private long freespacebyte ;

    @Override
    public void onCreate() {
        super.onCreate();
        System.out.print("IN_SERVICE Service Restart");

        freespacebyte = folder.getUsableSpace();
        freespace = freespacebyte/1073741824;
        if (freespace < 1)
        {
            stopRecording();
        }

        if (!folder.exists()) {
            System.out.println("LOG_DATA : No directory!! ");
            folder.mkdir();
        }

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, RecorderService.class.getName());
        wakeLock.acquire();

        mRecordingStatus = false;

        if (mRecordingStatus == false) {
            startRecording();
        }

    }
    @Override
    public IBinder onBind(Intent intent) {
        // TODO Auto-generated method stub
        return null;

    }

    @Override
    public void onDestroy() {

        System.out.print("IN_SERVICE Service Destroy");

        if (wakeLock != null) {
            wakeLock.release();
            wakeLock = null;
        }
        stopRecording();
        mRecordingStatus = false;

        super.onDestroy();

    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {

        isActive = true;

        System.out.print("IN_SERVICE Service onStartCommand");
        String input = intent.getStringExtra("inputExtra");
        createNotificationChannel();
        Intent notificationIntent = new Intent(this, CameraRecorder2.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, 0);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("背景錄影")
                .setContentText(input)
                .setSmallIcon(R.drawable.icon_camera)
                .setContentIntent(pendingIntent)
                .build();
        startForeground(1, notification);

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    Activity#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for Activity#requestPermissions for more details.
        }
        current_Location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        current_Lat = current_Location.getLatitude();
        current_Lng = current_Location.getLongitude();


        sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accSensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magSensor = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        sm.registerListener(myListener, accSensor, SensorManager.SENSOR_DELAY_NORMAL, 5000000);
        sm.registerListener(myListener, magSensor, SensorManager.SENSOR_DELAY_NORMAL, 5000000);

        LocationListener locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                System.out.println("LOG_DATA : In locationListener locationChanged ");

                current_Location = location;
                current_Lat = location.getLatitude();
                current_Lng = location.getLongitude();

            }

            @Override
            public void onStatusChanged(String s, int i, Bundle bundle) {

            }

            @Override
            public void onProviderEnabled(String s) {

            }

            @Override
            public void onProviderDisabled(String s) {

            }

        };
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 1, locationListener);


        handler = new Handler();
        runnable = new Runnable() {
            @Override
            public void run() {

                current_Time = sd.format(new Date());
                current_Lat = current_Location.getLatitude();
                current_Lng = current_Location.getLongitude();
                current_Direc = calculate_Orientation();

                boolean hasPrevious = true;
                float distance;
                if (previous_Location == null) {
                    System.out.println("LOG_DATA previous location null");

                    hasPrevious = false;
                    current_Speed = 0;
                }
                if (hasPrevious) {
                    System.out.println("LOG_DATA previous location not null");
                    distance = current_Location.distanceTo(previous_Location); //in meter
                    System.out.println("LOG_DATA : distance from previous  " + distance);
                    current_Speed = distance / (1000 * time_Interval_hr); //in km/hr
                    System.out.println("LOG_DATA : current_Speed  "+ current_Speed);
                }

                storeToPrevious(current_Location);

                logData_to_json(
                        current_Time,
                        String.format("%.6f", current_Lat),
                        String.format("%.6f", current_Lng),
                        String.format("%.3f", current_Direc),
                        String.format("%.3f", current_Speed)
                );

                handler.postDelayed(this, time_Interval * 1000);
            }

        };
        runnable.run();

        //do heavy work on a background thread


        //stopSelf();

        return START_NOT_STICKY;
    }
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    "ForegroundServiceChannel",
                    "Foreground Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );

            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }
    public boolean startRecording(){

        if (mServiceCamera == null)
        {
            mServiceCamera = Camera.open(0);
            mSurfaceView = CameraRecorder2.mSurfaceView;
            mSurfaceHolder = CameraRecorder2.mSurfaceHolder;
            mServiceCamera.setDisplayOrientation(90);
        }
        System.out.print("IN_SERVICE Service startRecording");

        try {
            Toast.makeText(getBaseContext(), "Recording Started", Toast.LENGTH_SHORT).show();

            //mServiceCamera.setDisplayOrientation(90);

            //mServiceCamera = Camera.open();
            Camera.Parameters p = mServiceCamera.getParameters();
            if (p.getSupportedFocusModes().contains(
                    Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                p.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
            }

            final List<Size> listSize = p.getSupportedPreviewSizes();
            Size mPreviewSize = listSize.get(2);
            Log.v(TAG, "use: width = " + mPreviewSize.width
                    + " height = " + mPreviewSize.height);
            p.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
            p.setPreviewFormat(PixelFormat.YCbCr_420_SP);
            mServiceCamera.setParameters(p);

            try {
                mServiceCamera.setPreviewDisplay(mSurfaceHolder);
                mServiceCamera.startPreview();
            }
            catch (IOException e) {
                Log.e(TAG, e.getMessage());
                e.printStackTrace();
            }

            mServiceCamera.unlock();
            mMediaRecorder = new MediaRecorder();
            mMediaRecorder.setCamera(mServiceCamera);
            mMediaRecorder.setOrientationHint(90);
            mMediaRecorder.setVideoEncodingBitRate(1500000);
            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
            mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
            mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.DEFAULT);
            if (!folder.exists()) {
                folder.mkdir();
            }
            mMediaRecorder.setOutputFile(folder.getAbsolutePath()+"/"+
                    DateFormat.format("yyyy-MM-dd_kk-mm-ss", new Date().getTime())+ ".mp4");
            mMediaRecorder.setVideoFrameRate(30);
            mMediaRecorder.setVideoSize(1280,720);
            mMediaRecorder.setPreviewDisplay(mSurfaceHolder.getSurface());
            mMediaRecorder.prepare();
            mMediaRecorder.start();
            mRecordingStatus = true;

            return true;
        } catch (IllegalStateException e) {
            Log.d(TAG, e.getMessage());
            e.printStackTrace();
            return false;
        } catch (IOException e) {
            Log.d(TAG, e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public void stopRecording() {

        isActive = false;

        System.out.print("IN_SERVICE Service stopRecording");

        Toast.makeText(getBaseContext(), "Recording Stopped", Toast.LENGTH_SHORT).show();
        try {

            mServiceCamera.reconnect();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        mMediaRecorder.stop();
        mMediaRecorder.reset();

        mServiceCamera.stopPreview();
        mMediaRecorder.release();

        mServiceCamera.release();
        mServiceCamera = null;

        handler.removeCallbacks(runnable);

    }

    final SensorEventListener myListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent sensorEvent) {
            if (sensorEvent.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                magneticFieldValues = sensorEvent.values.clone();
            }
            if (sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                acclerometerValues = sensorEvent.values.clone();
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int i) {

        }
    };

    public float calculate_Orientation() {
        float[] values = new float[3];
        float[] R = new float[9];

        SensorManager.getRotationMatrix(R, null, acclerometerValues, magneticFieldValues);
        SensorManager.getOrientation(R, values);

        values[0] = (float) Math.toDegrees(values[0]);

        return values[0];
    }
    public void storeToPrevious(Location loc) {
        previous_Location = new Location(loc);
    }

    public void logData_to_json(String time, String lat, String lng, String direc, String speed) {

        JSONObject logData = new JSONObject();
        try {
            logData.put("Time", time);
            logData.put("Lat", lat);
            logData.put("Lng", lng);
            logData.put("Direc", direc);
            logData.put("Speed", speed);

        } catch (JSONException e) {
            e.printStackTrace();
        }

        System.out.println("LOG_DATA : " + logData);

//      For debuging. Don't show in practice version
//        Toast.makeText(getApplicationContext(), lat + ", " + lng + ", "+speed, Toast.LENGTH_LONG).show();


        dataWriter(logData.toString());

    }

    public void dataWriter(String data) {

        final File file = new File(folder, "LogFile.txt");

        try {
            //file.createNewFile();
            FileOutputStream fOut = new FileOutputStream(file, true);
            OutputStreamWriter outWriter = new OutputStreamWriter(fOut);
            outWriter.append(data);

            outWriter.flush();
            outWriter.close();

        } catch (IOException e) {
            Log.e("Exception", "File write failed: " + e.toString());
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        current_Location = location;
        current_Lat = location.getLatitude();
        current_Lng = location.getLongitude();
    }
}
