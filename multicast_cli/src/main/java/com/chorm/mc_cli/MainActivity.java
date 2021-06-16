package com.chorm.mc_cli;

import androidx.appcompat.app.AppCompatActivity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.widget.TextView;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.text.SimpleDateFormat;
import java.util.Enumeration;
import java.util.Locale;
import java.util.TimeZone;

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
    private int timerId;
    private long timerBeginTick;
    private boolean isOnline;

    private TextView tvRecognize;
    private TextView tvServeIP;
    private TextView tvMulticastAddr;
    private TextView tvStatusInfo;
    private TextView tvTotalRcv;
    private TextView tvTotalRcvRate;
    private TextView tvTimer;

    //以下是组播接收相关的。
    private String mcingIP;
    private String mcingPort;
    private MulticastRcv multicastRcv;
    private SimpleDateFormat sdf;

    //以下是报告器相关。
    private long kbTotal;
    private Reporter reporter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvRecognize = findViewById(R.id.tvRecognize);
        tvServeIP = findViewById(R.id.tvServerIP);
        tvMulticastAddr = findViewById(R.id.tvBrcAddr);
        tvStatusInfo = findViewById(R.id.tvStatusInfo);
        tvTotalRcv = findViewById(R.id.tvTotalRcv);
        tvTotalRcvRate = findViewById(R.id.tvTotalRcvRate);
        tvTimer = findViewById(R.id.tvTimer);
        sdf = new SimpleDateFormat("HH:mm:ss");
        sdf.setTimeZone(TimeZone.getTimeZone("GMT+00:00"));
        isOnline = true; //For the next line.
        offline();
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (announcementRcv != null) {
            announcementRcv.stopRcv();
            announcementRcv = null;
        }

        announcementRcv = new AnnouncementRcv(onAnnoRcvCallback);
        announcementRcv.start();

        startReporter();

        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(networkStatusReceiver, filter);
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (announcementRcv != null) {
            announcementRcv.stopRcv();
            announcementRcv = null;
        }

        stopReporter();

        unregisterReceiver(networkStatusReceiver);
    }

    private void startReporter() {
        stopReporter();
        reporter = new Reporter(this);
        reporter.start();
    }

    private void stopReporter() {
        if(reporter != null) {
            reporter.stopRun();
            reporter = null;
        }
    }

    private void online() {
        if(!isOnline) {
            tvRecognize.setText("在线");
            tvRecognize.setTextColor(Color.GREEN);

            tvStatusInfo.setText("");
            tvServeIP.setText(srvIP);

            tvMulticastAddr.setText(String.format(Locale.US, getResources().getString(R.string.ip_address2), brcIP, brcPort));
            timerId = R.string.timer_online;
            timerBeginTick = SystemClock.uptimeMillis();
            isOnline = true;
        }
    }

    private void offline() {
        if(isOnline) {
            tvRecognize.setText("离线");
            tvRecognize.setTextColor(Color.RED);
            tvStatusInfo.setText("");
            tvServeIP.setText("");
            tvMulticastAddr.setText("");
            timerId = R.string.timer_offline;
            timerBeginTick = SystemClock.uptimeMillis();
            isOnline = false;
        }
    }

    private void multicast_rcv_proc() {
        if(brcIP != null && brcPort != null) {
            if(!brcIP.equals(mcingIP) || !brcPort.equals(mcingPort)) {
                stopMulticastPlaying();
                mcingIP = brcIP;
                mcingPort = brcPort;
                startMulticastPlay();
            }
        }
    }

    private void stopMulticastPlaying() {
        if(multicastRcv != null) {
            multicastRcv.stopRcv();
            multicastRcv = null;
        }
    }

    private void startMulticastPlay() {
        multicastRcv = new MulticastRcv(mcingIP, mcingPort, onMulticastStatisticCallback);
        multicastRcv.start();
    }

    public String getServerAddr() {
        return srvIP;
    }

    public String getMulticastAddr() {
        return mcingIP;
    }

    public String getMulticastPort() {
        return mcingPort;
    }

    public String getDuration() {
        String[] tmp = tvTimer.getText().toString().split("\t");
        if(tmp.length == 3) {
            return tmp[2];
        } else {
            return "00:00:00";
        }
    }

    public long getKBTotal() {
        return kbTotal;
    }

    public String getRcvRate() {
        return tvTotalRcvRate.getText().toString();
    }

    private final MulticastRcv.OnMulticastStatisticCallback onMulticastStatisticCallback = new MulticastRcv.OnMulticastStatisticCallback() {

        @Override
        public void onMulticastStatistic(long kbTotal, int kbThisTime, long seqNoThisTime) {
            MainActivity.this.kbTotal = kbTotal;
            tvTotalRcv.setText(String.format(Locale.US, MainActivity.this.getResources().getString(R.string.multicast_rcv), kbTotal));
            tvTotalRcvRate.setText(String.format(Locale.US, MainActivity.this.getResources().getString(R.string.multicast_rcv_rate), ((double)kbTotal / (double)(seqNoThisTime)) * 100));
        }
    };

    private final AnnouncementRcv.OnAnnoRcvCallback onAnnoRcvCallback = new AnnouncementRcv.OnAnnoRcvCallback() {

        long tickTmp;

        @Override
        public void onAnnoRcv(String serverIP, String brcIP, String brcPort) {
            srvIP = serverIP;
            MainActivity.this.brcIP = brcIP;
            MainActivity.this.brcPort = brcPort;

            if (srvIP == null || brcIP == null || brcPort == null) {
                offline();
                tickTmp = SystemClock.uptimeMillis() - timerBeginTick;
                tvTimer.setText(String.format(Locale.US, getResources().getString(timerId), sdf.format(tickTmp)));
                if(multicastRcv != null) {
                    multicastRcv.resetIndicator();
                }
                return;
            }

            //TODO IP地址和端口号的有效性检查 。

            online();
            multicast_rcv_proc();

            tickTmp = SystemClock.uptimeMillis() - timerBeginTick;
            tvTimer.setText(String.format(Locale.US, getResources().getString(timerId), sdf.format(tickTmp)));
        }

        @Override
        public void onAnnoRcvError(String info) {
            if (info != null && !info.isEmpty()) {
                annoErrorInfo = info;
                tvStatusInfo.setText(annoErrorInfo);
            } else {
                annoErrorInfo = null;
            }
        }
    };

    private final BroadcastReceiver networkStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            ConnectivityManager ctm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if(ctm == null) {
                Log.e(TAG, "Not support network");
                return;
            }

            NetworkInfo[] nis = ctm.getAllNetworkInfo();
            for(NetworkInfo ni : nis) {
                if("ethernet".equals(ni.getTypeName())) {
                    if(ni.getState() == NetworkInfo.State.CONNECTED) {
                        startReporter();
                    } else if(ni.getState() == NetworkInfo.State.DISCONNECTED) {
                        stopReporter();
                    }
                    break;
                }
            }
        }
    };


}