package com.chorm.mc_ser;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

/**
 * 1、定时宣告服务端的信息；
 * 2、按设置推流到组播网段；
 * */
public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "Multicast_server";

    private long totalBytesPushed;
    private long bytesPushedSinceLastTime;

    private TextView tvPushedBytes;
    private TextView tvStatusInfo;
    private EditText etMCAddr;
    private EditText etMCPort;
    private Button btnBegin;
    private Button btnStop;

    private StreamPush streamPush;
    private Announcement announcement;
    private String annoInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvPushedBytes = findViewById(R.id.tvPushedBytes);
        tvStatusInfo = findViewById(R.id.tvStatusInfo);
        etMCAddr = findViewById(R.id.etMCAddr);
        etMCPort = findViewById(R.id.etMCPort);
        btnBegin = findViewById(R.id.btnBeginMC);
        btnStop = findViewById(R.id.btnStopMC);

        btnBegin.setOnClickListener(this);
        btnStop.setOnClickListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "Multicast server running");
    }

    @Override
    public void onClick(View v) {
        if(v == btnBegin) {
            if(streamPush != null) {
                streamPush.stopPush();
                streamPush = null; //It's necessary
            }

            String mcAddr = getMulticastAddress();
            String mcPort = getMulticastPort();

            streamPush = new StreamPush(mcAddr, mcPort, onPushedCallback);
            streamPush.start();

            if(announcement != null) {
                announcement.stopAnno();
                announcement = null;
            }

            announcement = new Announcement(this, mcAddr, mcPort, onAnnouncementCallback);
            announcement.start();
            Log.d(TAG, "begin");
        } else if(v == btnStop) {
            if(streamPush != null) {
                streamPush.stopPush();
                streamPush = null;
            }

            if(announcement != null) {
                announcement.stopAnno();
                announcement = null;
            }
            Log.d(TAG, "stop");
        }
    }

    private String getMulticastAddress() {
        //TODO 检查IP输入的合法性。
        return etMCAddr.getText().toString();
    }

    private String getMulticastPort() {
        //TODO 检查端口号输入的合法性。
        return etMCPort.getText().toString();
    }

    private StreamPush.OnPushedCallback onPushedCallback = new StreamPush.OnPushedCallback() {
        @Override
        public void onStreamPushedStatistic(long totalPushed, long sinceLast) {
            totalBytesPushed = totalPushed;
            bytesPushedSinceLastTime = sinceLast;
            handler.sendEmptyMessage(1);
        }
    };

    private Announcement.OnAnnouncementCallback onAnnouncementCallback = new Announcement.OnAnnouncementCallback() {
        @Override
        public void onAnno(String info) {
            annoInfo = info;
            handler.sendEmptyMessage(2);
        }
    };

    private final Handler handler = new Handler(){

        @Override
        public void handleMessage(@NonNull Message msg) {
            switch(msg.what) {
                case 1:
                    tvPushedBytes.setText(String.valueOf(totalBytesPushed));
                    break;
                case 2:
                    tvStatusInfo.setText(annoInfo);
                    break;
            }
        }
    };

}