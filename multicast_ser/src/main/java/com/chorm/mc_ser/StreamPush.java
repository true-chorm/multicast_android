package com.chorm.mc_ser;

import android.os.CountDownTimer;
import android.os.SystemClock;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

public class StreamPush extends Thread {

    private static final String TAG = "StreamPush";

    private static final int INET_PKG_LEN = 1024; //每个网络包1024字节
    private static final int INET_PKG_AMOUNT = 10000; //总共n个网络包。
    private static final int PUSH_FUTURE = 100000; //100s
    private static final int PUSH_INTERVAL = 1000; //1s
    private final String ADDRESS;
    private final int PORT;

    private int canSendCounter;
    private boolean canRun;
    private OnPushedCallback onPushedCallback;
    private MulticastSocket ms;
    private DatagramPacket dpkg;

    private byte[][] inetpkg;
    private long totalKB;

    public StreamPush(String ip, String port, OnPushedCallback cb) {
        ADDRESS = ip;
        PORT = Integer.parseInt(port);
        onPushedCallback = cb;

        inetpkg = new byte[INET_PKG_AMOUNT][INET_PKG_LEN]; //8MB
        try {
            ms = new MulticastSocket(); //Let TTL as 1
            dpkg = new DatagramPacket(inetpkg[0], 0);
            dpkg.setAddress(InetAddress.getByName(ADDRESS));
            dpkg.setPort(PORT);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        canRun = true;
        canSendCounter = 0;
        totalKB = 0;
        callbackTimer.start();

        try {
            while(canRun) {
                if(canSendCounter > 0) {
                    canSendCounter--;

                    //load 8mb pkg and send it.
                    loadAndSend();
                    continue; //No need to wait, boy!
                }

                SystemClock.sleep(203);
            }
        } catch(Exception e) {
            e.printStackTrace();
        }

        callbackTimer.cancel();
    }

    /**
     * 准备好8MB的网络包。
     * 一个包1kb，准备8000个。
     * */
    private void loadAndSend() throws IOException {
        for(int i = 0; i < INET_PKG_AMOUNT; i++) {
            loadData(inetpkg[i]);
            dpkg.setData(inetpkg[i], 0, INET_PKG_LEN);
            ms.send(dpkg);
        }
    }

    private void loadData(byte[] dbuf) {
        totalKB++;
//        if(totalKB == Long.MAX_VALUE) {
//            totalKB = 0;
//        }
        dbuf[0] = (byte) (totalKB >> 56);
        dbuf[1] = (byte) (totalKB >> 48);
        dbuf[2] = (byte) (totalKB >> 40);
        dbuf[3] = (byte) (totalKB >> 32);
        dbuf[4] = (byte) (totalKB >> 24);
        dbuf[5] = (byte) (totalKB >> 16);
        dbuf[6] = (byte) (totalKB >> 8);
        dbuf[7] = (byte) totalKB;
    }

    public void stopPush() {
        canRun = false;
        onPushedCallback = null;
    }

    private final CountDownTimer callbackTimer = new CountDownTimer(PUSH_FUTURE, PUSH_INTERVAL) {

        private long tmp;
        private long kbCountLastTime; //上一次回调时传过去的KB数量。

        @Override
        public void onTick(long millisUntilFinished) {
            if(onPushedCallback != null) {
                tmp = totalKB;
                onPushedCallback.onStreamPushedStatistic(tmp, tmp - kbCountLastTime);
                kbCountLastTime = tmp;
            }

            canSendCounter++;
        }

        @Override
        public void onFinish() {
            start();
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
