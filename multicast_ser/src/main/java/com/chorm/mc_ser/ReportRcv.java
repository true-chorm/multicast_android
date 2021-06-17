package com.chorm.mc_ser;

import android.os.CountDownTimer;
import android.os.SystemClock;
import android.util.Log;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ReportRcv extends Thread {

    private static final String TAG = "ReportRcv";

    private static final int FUTURE = 300000;
    private static final int INTERVAL = 3000;

    private boolean canRun;

    private DatagramSocket dgSocket;
    private DatagramPacket dgPkg;
    private Map<String, Info> infoMap;
    private OnReportCallback onReportCallback;

    public ReportRcv(OnReportCallback cb) {
        onReportCallback = cb;
        byte[] rbuf = new byte[128];
        infoMap = new HashMap<>();
        try {
            dgSocket = new DatagramSocket(8405);
            dgPkg = new DatagramPacket(rbuf, 128);
        } catch(SocketException e) {
            e.printStackTrace();
        }
        rrcvTimer.start();
        canRun = true;
    }

    @Override
    public void run() {
        Log.d(TAG, "reporterrcv run");
        try {
            String tmp;
            String[] tmps;
            int i;
            Info info;
            while(canRun) {
                dgSocket.receive(dgPkg);

                //parse
                if(dgPkg.getLength() > 20) {
                    tmp = new String(dgPkg.getData(), 0, dgPkg.getLength());
                    tmps = tmp.split(",");

                    if(tmps.length != 5) {
                        Log.w(TAG, "Invalid pkg rcved");
                        continue;
                    }

                    info = infoMap.get(dgPkg.getAddress().getHostAddress());
                    if(info == null) {
                        info = new Info();
                        infoMap.put(dgPkg.getAddress().getHostAddress(), info);
                    }

                    //update information
                    info.cliAddr = dgPkg.getAddress().getHostAddress();
                    info.mcAddr = tmps[0];
                    info.mcPort = tmps[1];
                    info.duration = tmps[2];
                    info.kbTotal = tmps[3];
                    info.rcvRate = tmps[4];
                    info.updateTime = SystemClock.uptimeMillis();
                }
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public void stopRcv() {
        canRun = false;
        rrcvTimer.cancel();
        dgSocket.close();
    }

    private CountDownTimer rrcvTimer = new CountDownTimer(FUTURE, INTERVAL) {

        Map<String, Info> maps = new HashMap<>();
        Info info;
        long now;

        @Override
        public void onTick(long millisUntilFinished) {
            //copy first.
            maps.clear();
            now = SystemClock.uptimeMillis();
            for(String key : infoMap.keySet()) {
                info = infoMap.get(key);
                if(now - info.updateTime > 10000) {
                    Log.w(TAG, "time expired");
                    continue;
                }

                maps.put(key, info);
            }

            //notify
            if(onReportCallback != null) {
                onReportCallback.onReported(maps);
            }
        }

        @Override
        public void onFinish() {
            start();
        }
    };

    public static class Info {
        public String cliAddr;
        public String mcAddr;
        public String mcPort;
        public String duration;
        public String kbTotal;
        public String rcvRate;
        private long updateTime;
    }

    public interface OnReportCallback {
        void onReported(Map<String, Info> onlineDevs);
    }
}
