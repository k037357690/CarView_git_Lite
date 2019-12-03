package com.nclab.carview2;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.ammarptn.debug.gdrive.lib.ui.gdrivedebugview.GdriveDebugViewFragment;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.jetbrains.annotations.Nullable;
import org.json.JSONException;
import org.json.JSONObject;

import com.ammarptn.debug.gdrive.lib.*;

import java.util.ArrayList;

//目前0306版本================================================//
//====登入頁面========================//
//================================================//
public class LoginActivity extends AppCompatActivity {

    FirebaseAuth auth = FirebaseAuth.getInstance();
    String url ="http://140.115.158.87:3000/android/loginpage";
    private String loginAccount;
    private String loginPwd;
    private EditText loginAccount_ET;
    private EditText loginPwd_ET;
    private Button registerBtn;
    private Button loginBtn;
    private Button forgetBtn;
    private SharedPreferences mPrefs;

    //偏好設定檔是整個app共用的，可在其他.java中取用資料
    private static final String PREFS_CARVIEW = "prefsFile";//新增偏好設定檔，自定名稱

    private FirebaseUser user;

    GdriveDebugViewFragment gdViewer = new GdriveDebugViewFragment();
    private GoogleSignInClient mGoogleSignInClient = null;
    private GDriveDebugViewActivity gDriveDebugViewActivity;

    //連接layout的元件
    private void connect_LoginLayout() {
        registerBtn = findViewById(R.id.welcomeRegister);
        loginBtn = findViewById(R.id.welcomelogin);
       // forgetBtn = findViewById(R.id.forgetpassword);
        loginAccount_ET = findViewById(R.id.loginAccount);
        loginPwd_ET = findViewById(R.id.loginPwd);

    }
//
//    private uploadService.myBinder myBinder;
//    private ServiceConnection serviceConnection = new ServiceConnection() {
//        @Override
//        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
//            myBinder = (uploadService.myBinder) serviceConnection;
//            myBinder.startUpload();
//        }
//
//        @Override
//        public void onServiceDisconnected(ComponentName componentName) {
//
//        }
//    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if(RecorderService.isActive){
            Intent intent = new Intent(LoginActivity.this, CameraRecorder2.class);
            startActivity(intent);
        }


        //================================================//
        //=======判斷權限是否打開===========================//
        //=======若沒有則跳出視窗詢問========================//
        //================================================//
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted
            ActivityCompat.requestPermissions(LoginActivity.this,
                    new String[]{Manifest.permission.CAMERA,Manifest.permission.RECORD_AUDIO,Manifest.permission.WRITE_EXTERNAL_STORAGE,Manifest.permission.ACCESS_FINE_LOCATION},
                    1);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        //Intent intent = new Intent(this, uploadService.class);
        //this.startService(intent);


        //連接layout的元件
        connect_LoginLayout();

        mPrefs = getSharedPreferences(PREFS_CARVIEW, MODE_PRIVATE);
        getPreferencesData();    //載入偏好設定檔資料

        //按下註冊按鈕，導至註冊畫面//


        //按下忘記密碼按鈕，導至忘記密碼畫面//
    /*    forgetBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setClass(LoginActivity.this, Forgetpassword.class);
                startActivity(intent);
            }
        });*/


        //按下登入按鈕，寫入資料到偏好設定檔，跳至create_JSON_and_connect_server()//
        loginBtn.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                //記住帳號密碼//
                //若帳號密碼不是空白且有勾記住，寫入偏好設定檔//
                if((!loginAccount_ET.getText().toString().equals("")) &&
                        (!loginPwd_ET.getText().toString().equals(""))){

                    //打開Editor才可以編輯偏好設定檔
                    SharedPreferences.Editor editor = mPrefs.edit();

                    //從輸入欄取得資料寫入偏好設定檔
                    //偏好設定檔中，資料的標題為自行設定，如："pref_account"
                    editor.putString("pref_account", loginAccount_ET.getText().toString())
                            .putString("pref_pass", loginPwd_ET.getText().toString())
                            .apply();
                }

                create_JSON_and_connect_server();

            }
        });

    }

    //================================================//
    //========將使用者輸入之登入帳號密碼包成Json==========//
    //================================================//
    private void create_JSON_and_connect_server() {
        loginAccount = loginAccount_ET.getText().toString();
        loginPwd = loginPwd_ET.getText().toString();
        JSONObject json = new JSONObject();
        try {
            json.put("Account",loginAccount);
            json.put("Password",loginPwd);

        } catch (JSONException e) {
            e.printStackTrace();
        }
        connect_Server_login(json);

        Log.e("fghfhg", String.valueOf(json));
    }

    //================================================//
    //========將上面包成的json資料送至伺服器=============//
    //========判讀伺服器回穿登入狀態(1代表正確可登入)======//
    //========收到1則跳轉至MainActivity=================//
    //================================================//
    public void connect_Server_login(JSONObject logindata){
        JsonObjectRequest mJsonObjectRequest = new JsonObjectRequest(Request.Method.POST, url, logindata,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        Log.e("logindata",response.toString());
                        try {
                            if(response.getString("status").equals("1")){
                                Intent Login_intent = new Intent();
                                Login_intent.setClass(LoginActivity.this,CameraRecorder2.class);
                                Login_intent.putExtra("Status_extra",response.toString());
                                startActivity(Login_intent);
                                overridePendingTransition(android.R.anim.fade_in,android.R.anim.fade_out);
                                //loginAccount_ET.getText().clear();
                                //loginPwd_ET.getText().clear();
                            }else{
                                Toast toast = Toast.makeText(LoginActivity.this,"Incorrect information", Toast.LENGTH_LONG);
                                toast.show();
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.e("err", error.toString());
                    }
                }
        );

        //初始化Volley並將上面的json放入queue裡面
        Volley.newRequestQueue(this).add(mJsonObjectRequest);

    }

    //================================================//
    //=========從偏好設定檔取回資料======================//
    //=========並放到帳號密碼輸入欄中=====================//
    //=========判斷是否要將選項打勾======================//
    //================================================//
    private void getPreferencesData() {
        //選擇要取的哪個偏好設定檔，並設定模式
        SharedPreferences sp = getSharedPreferences(PREFS_CARVIEW,MODE_PRIVATE);
        if(sp.contains("pref_account")){
            String a = sp.getString("pref_account",null);
            loginAccount_ET.setText(a.toString());
        }
        if(sp.contains("pref_pass")){
            String p = sp.getString("pref_pass",null);
            loginPwd_ET.setText(p.toString());
        }


    }

    private void google_signIn(){
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, 1);

    }
    //禁止系統返回鍵
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return true;
        }
        return false;
    }

}
