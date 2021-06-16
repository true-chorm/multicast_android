package com.chorm.mc_ser;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/**
 * 1、定时宣告服务端的信息；
 * 2、按设置推流到组播网段；
 * */
public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "Multicast_server";

    private long totalBytesPushed;
    private long bytesPushedSinceLastTime;
    private long beginTick;

    private TextView tvPushedBytes;
    private TextView tvStatusInfo;
    private EditText etMCAddr;
    private EditText etMCPort;
    private Button btnBegin;
    private Button btnStop;

    private StreamPush streamPush;
    private Announcement announcement;
    private ReportRcv reportRcv;
    private String annoInfo;
    SimpleDateFormat sdf;

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

        sdf = new SimpleDateFormat("HH:mm:ss");
        sdf.setTimeZone(TimeZone.getTimeZone("GMT+00:00"));
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(networkStatusReceiver, filter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "Multicast server running");
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(networkStatusReceiver);
        onClick(btnStop);
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
            beginTick = SystemClock.uptimeMillis();

            if(announcement != null) {
                announcement.stopAnno();
                announcement = null;
            }

            announcement = new Announcement(this, mcAddr, mcPort, onAnnouncementCallback);
            announcement.start();

            startReporterRcv();

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

            stopReporterRcv();
            Log.d(TAG, "stop");
        }
    }

    private void startReporterRcv() {
        stopReporterRcv();
        reportRcv = new ReportRcv(onReportCallback);
        reportRcv.start();
    }

    private void stopReporterRcv() {
        if(reportRcv != null) {
            reportRcv.stopRcv();
            reportRcv = null;
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
                    tvPushedBytes.setText(String.format(Locale.US, getResources().getString(R.string.bytes_and_time), Long.toString(totalBytesPushed), sdf.format(SystemClock.uptimeMillis() - beginTick)));
                    break;
                case 2:
                    tvStatusInfo.setText(annoInfo);
                    break;
            }
        }
    };

    private final BroadcastReceiver networkStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive()");
            ConnectivityManager ctm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if(ctm == null) {
                Log.e(TAG, "Not support network");
                return;
            }

            NetworkInfo[] nis = ctm.getAllNetworkInfo();
            for(NetworkInfo ni : nis) {
                if("ethernet".equals(ni.getTypeName())) {
                    if(ni.getState() == NetworkInfo.State.CONNECTED) {
                        Log.d(TAG, "network ok");
                        onClick(btnBegin);
                    } else if(ni.getState() == NetworkInfo.State.DISCONNECTED) {
                        Log.d(TAG, "network broken");
                        onClick(btnStop);
                    }
                    break;
                }
            }
        }
    };

    private final ReportRcv.OnReportCallback onReportCallback = new ReportRcv.OnReportCallback() {
        Set<String> keys;
        ReportRcv.Info info;
        StringBuilder sb = new StringBuilder();
        @Override
        public void onReported(Map<String, ReportRcv.Info> onlineDevs) {
            keys = onlineDevs.keySet();
            sb.delete(0, sb.length());
            for(String key : keys) {
                info = onlineDevs.get(key);
                sb.append("[");
                sb.append(info.cliAddr);
                sb.append("]  ");
                sb.append(info.mcAddr);
                sb.append(":");
                sb.append(info.mcPort);
                sb.append(",  ");
                sb.append(info.duration);
                sb.append(",  ");
                sb.append(info.kbTotal);
                sb.append("(KB),  ");
                sb.append(info.rcvRate);
                sb.append("\n");
            }

            tvStatusInfo.setText(sb.toString());
        }
    };
}