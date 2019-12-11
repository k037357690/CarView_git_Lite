package com.nclab.carview2;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Camera;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Button;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

public class CameraRecorder2 extends AppCompatActivity implements SurfaceHolder.Callback{


    private static final String TAG = "Recorder";
    public static SurfaceView mSurfaceView;
    public static SurfaceHolder mSurfaceHolder;
    public static boolean mPreviewRunning;
    public static Camera mServiceCamera;
    private String self_account = null;
    private String self_username = null;
    private double lat;//緯度
    private double lng;//經度
    private String Status;
    private JSONObject jsonstatus = null;

    //private Button btnStart;


    private static final String PREFS_ACCOUNT_NAME = "prefsFile_account_name";
    private SharedPreferences mPrefs;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_recorder);

        Button btnStart = findViewById(R.id.Start_Stop_Service);

        if(RecorderService.mRecordingStatus){
            btnStart.setBackgroundResource(R.drawable.stop);

        }

        mPrefs = getSharedPreferences("prefsFile_account_name", MODE_PRIVATE);
        getPreferencesData();

        Intent intent = getIntent();
        Status = intent.getStringExtra("Status_extra");

        if(Status != null){
            try {
                jsonstatus = new JSONObject(Status);
                self_username = jsonstatus.getString("Username");
                self_account = jsonstatus.getString("Account");
            } catch (JSONException e) {
                e.printStackTrace();
            }

            SharedPreferences.Editor editor = mPrefs.edit();

            //從輸入欄取得資料寫入偏好設定檔
            //偏好設定檔中，資料的標題為自行設定，如："pref_account"
            editor.putString("pref_account", self_account)
                    .putString("pref_username", self_username)
                    .apply();

        }

        System.out.print("IN_CAMERARECODER onCreate");


        mSurfaceView = findViewById(R.id.surfaceView2);
        mSurfaceHolder = mSurfaceView.getHolder();
        mSurfaceHolder.addCallback(this);
        mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        Button btnSignout = findViewById(R.id.signout);
        btnSignout.setOnClickListener(v -> {
            if(!RecorderService.mRecordingStatus){
                String logout_url = "http://140.115.158.87:3000/android/logout";

                JSONObject logout_json = new JSONObject();

                try {
                    logout_json.put("User_account", self_account);
                    logout_json.put("User_name", self_username);
                    logout_json.put("User_lat", lat);
                    logout_json.put("User_lng", lng);

                } catch (JSONException e) {
                    e.printStackTrace();
                }

                JsonObjectRequest mJsonObjectRequest = new JsonObjectRequest(Request.Method.POST, logout_url, logout_json,
                        new Response.Listener<JSONObject>() {
                            @Override
                            //接收伺服器回傳的其他使用者資料
                            public void onResponse(JSONObject response) {
                                Log.e("online", response.toString());

                            }
                        },
                        new Response.ErrorListener() {
                            @Override
                            public void onErrorResponse(VolleyError error) {
                                Log.e("err", error.toString());
                            }
                        }
                );
                Volley.newRequestQueue(CameraRecorder2.this).add(mJsonObjectRequest);

                Intent Login_intent = new Intent();
                Login_intent.setClass(CameraRecorder2.this,LoginActivity.class);
                startActivity(Login_intent);
            }else{
                Toast.makeText(getApplicationContext(), "請先停止錄影，再按登出", Toast.LENGTH_SHORT).show();
            }



        });

        btnStart.setOnClickListener(v -> {

            if(!RecorderService.mRecordingStatus){
                System.out.print("IN_CAMERARECODER botton onClick");
                btnStart.setBackgroundResource(R.drawable.stop);
                startService();

            }else {
                btnStart.setBackgroundResource(R.drawable.icon_play);
                stopService();
            }

        });

//        Button btnStop = findViewById(R.id.StopService);
//        btnStop.setOnClickListener(v -> {
//            //wakeLock.release();
//
//        });

    }
//禁止系統返回鍵
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return true;
        }
        return false;
    }
    public void startService() {

        Intent serviceIntent = new Intent(this, RecorderService.class);
        serviceIntent.putExtra("inputExtra", "Foreground Service Example in Android");

        ContextCompat.startForegroundService(this, serviceIntent);
    }

    public void stopService() {
        Intent serviceIntent = new Intent(this, RecorderService.class);
        stopService(serviceIntent);
    }
    @Override
    public void surfaceCreated(SurfaceHolder holder) {

    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        // TODO Auto-generated method stub

    }

    private void getPreferencesData() {
        //選擇要取的哪個偏好設定檔，並設定模式
        SharedPreferences sp = getSharedPreferences("prefsFile_account_name",MODE_PRIVATE);
        if(sp.contains("pref_account")){
            self_username = sp.getString("pref_account",null);
            System.out.print("pref account : " + self_account);
        }
        if(sp.contains("pref_username")){
            self_account = sp.getString("pref_username",null);
            System.out.print("pref username : " + self_username);
        }



    }

}

