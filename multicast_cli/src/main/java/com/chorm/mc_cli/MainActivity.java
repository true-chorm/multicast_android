package com.chorm.mc_cli;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.TextView;

/**
 * 1、监听服务端的宣告信息；
 * 2、接收组播流；
 * */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "multicast_cli";

    private String annoErrorInfo;
    private String srvIP;
    private String brcIP;
    private String brcPort;
    private AnnouncementRcv announcementRcv;

    private TextView tvRecognize;
    private TextView tvServeIP;
    private TextView tvStatusInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvRecognize = findViewById(R.id.tvRecognize);
        tvServeIP = findViewById(R.id.tvServerIP);
        tvStatusInfo = findViewById(R.id.tvStatusInfo);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (announcementRcv != null) {
            announcementRcv.stopRcv();
            announcementRcv = null;
        }

        announcementRcv = new AnnouncementRcv(onAnnoRcvCallback);
        announcementRcv.start();
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (announcementRcv != null) {
            announcementRcv.stopRcv();
            announcementRcv = null;
        }
    }

    private void online() {
        Log.d(TAG, "online()");
        tvRecognize.setText("在线");
        tvRecognize.setTextColor(Color.GREEN);
    }

    private void offline() {
        Log.d(TAG, "offline()");
        tvRecognize.setText("离线");
        tvRecognize.setTextColor(Color.RED);
    }

    private AnnouncementRcv.OnAnnoRcvCallback onAnnoRcvCallback = new AnnouncementRcv.OnAnnoRcvCallback() {

        @Override
        public void onAnnoRcv(String serverIP, String brcIP, String brcPort) {
            Log.d(TAG, "onAnnoRcv(), srvip:" + serverIP + ", brcip:" + brcIP + ", brcport:" + brcPort);
            srvIP = serverIP;
            MainActivity.this.brcIP = brcIP;
            MainActivity.this.brcPort = brcPort;

            handler.sendEmptyMessage(2);
        }

        @Override
        public void onAnnoRcvError(String info) {
            Log.d(TAG, "onAnnoRcvError(), info:" + info);
            if (info != null && !info.isEmpty()) {
                annoErrorInfo = info;
                handler.sendEmptyMessage(1);
            } else {
                annoErrorInfo = null;
            }
        }
    };

    private final Handler handler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                    tvStatusInfo.setText(annoErrorInfo);
                    break;
                case 2:
                    if (srvIP == null || brcIP == null || brcPort == null) {
                        offline();
                        return;
                    }

                    //TODO IP地址和端口号的有效性检查 。

                    online();
                    break;
            }
        }
    };
}