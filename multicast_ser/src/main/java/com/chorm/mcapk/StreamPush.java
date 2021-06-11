package com.chorm.mcapk;

import android.os.CountDownTimer;
import android.os.SystemClock;
import android.util.Log;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

public class StreamPush extends Thread {

    private static final String TAG = "StreamPush";

    private static final int BUF_LEN = 1024;
    private static final int PUSHED_BYTES_NOTIFY_INTERVAL = 1000;
    private final String ADDRESS;
    private final int PORT;

    private boolean canRun;
    private long totalBytes;
    private long bytesAmountLastCallback; //上一次回调时记录下的总数据量。
    private OnPushedCallback onPushedCallback;
    private MulticastSocket ms;
    private DatagramPacket dpkg;
    private byte[] dbuf;
    private long counter;

    public StreamPush(String ip, String port, OnPushedCallback cb) {
        ADDRESS = ip;
        PORT = Integer.parseInt(port);
        onPushedCallback = cb;

        counter = 0;
        dbuf = new byte[BUF_LEN];
        try {
            ms = new MulticastSocket();
            dpkg = new DatagramPacket(dbuf, 0);
            dpkg.setAddress(InetAddress.getByName(ADDRESS));
            dpkg.setPort(PORT);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        canRun = true;
        callbackTimer.start();

        try {
            while(canRun) {
                loadData();
                dpkg.setData(dbuf, 0, BUF_LEN);
                ms.send(dpkg);
                totalBytes += 1;
                SystemClock.sleep(100);
            }
        } catch(Exception e) {
            e.printStackTrace();
        }

        callbackTimer.cancel();
    }

    private void loadData() {
        counter++;
        Log.d(TAG, "counter:" + counter);
        dbuf[0] = (byte) (counter >> 56);
        dbuf[1] = (byte) (counter >> 48);
        dbuf[2] = (byte) (counter >> 40);
        dbuf[3] = (byte) (counter >> 32);
        dbuf[4] = (byte) (counter >> 24);
        dbuf[5] = (byte) (counter >> 16);
        dbuf[6] = (byte) (counter >> 8);
        dbuf[7] = (byte) counter;
    }

    public void stopPush() {
        canRun = false;
        onPushedCallback = null;
    }

    private final CountDownTimer callbackTimer = new CountDownTimer(PUSHED_BYTES_NOTIFY_INTERVAL, PUSHED_BYTES_NOTIFY_INTERVAL) {

        private long tmp;

        @Override
        public void onTick(long millisUntilFinished) {
            //Let it empty.
        }

        @Override
        public void onFinish() {
            if(onPushedCallback != null) {
                tmp = totalBytes;
                onPushedCallback.onStreamPushedStatistic(tmp, tmp - bytesAmountLastCallback);
                bytesAmountLastCallback = tmp;

                start();
            }
        }
    };

    public interface OnPushedCallback {
        /**
         * @param totalPushed 自启动以来总共推出去的流数量(KB)
         * @param sinceLast 自上一次回调以来共推出去的数据量(KB)
         * */
        void onStreamPushedStatistic(long totalPushed, long sinceLast);
    }
}
