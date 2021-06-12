package com.chorm.mc_cli;

import android.os.CountDownTimer;
import android.os.SystemClock;
import android.util.Log;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

public class AnnouncementRcv extends Thread {

    private static final String TAG = "AnnouncementRcv";

    private static final long TICK_MS = 1000; //回调监听结果的时间间隔。

    private boolean canRun;
    private byte[] abuf;

    private OnAnnoRcvCallback onAnnoRcvCallback;
    private DatagramSocket dgSocket;
    private DatagramPacket dgPkg;
    private StreamInfo streamInfo;

    public AnnouncementRcv(OnAnnoRcvCallback cb) {
        onAnnoRcvCallback = cb;

        abuf = new byte[128];
        streamInfo = new StreamInfo();
        try {
            dgSocket = new DatagramSocket(8305);
            dgPkg = new DatagramPacket(abuf, 128);
            canRun = true;
        } catch (SocketException e) {
            e.printStackTrace();
            if (cb != null) {
                cb.onAnnoRcvError("无法创建网络通信！");
            }
        }
    }

    @Override
    public void run() {
        if (!canRun) {
            return;
        }

        //Create an timer for notify result.
        notifyTimer.start();


        try {
            while (canRun) {
                dgSocket.receive(dgPkg);
                try {
                    parseAnnouncement();
                } catch (ArrayIndexOutOfBoundsException e) {
                    e.printStackTrace();
                }
                SystemClock.sleep(1000);
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (onAnnoRcvCallback != null) {
                onAnnoRcvCallback.onAnnoRcvError("通信失败！");
            }
            canRun = false;
            onAnnoRcvCallback = null;
        }

        Log.d(TAG, "announcement receive thread end");
    }

    private void parseAnnouncement() {
        Log.d(TAG, "parseAnnouncement()");
        Log.d(TAG, "pkg len:" + dgPkg.getLength() + ", from:" + dgPkg.getAddress().getHostAddress());

        if(dgPkg.getLength() > 100) {
            notifyAnnounceError("无效数据");
            return;
        }

        if(dgPkg.getData()[0] != '[' || dgPkg.getData()[dgPkg.getLength() - 1] != ']') {
            notifyAnnounceError("格式错误");
            return;
        }

        String data = new String(dgPkg.getData(), 1, dgPkg.getLength() - 2);
        Log.d(TAG, "parse:" + data);
        String[] ps = data.split(",");
        if(ps.length != 4) {
            notifyAnnounceError("格式错误");
            return;
        }

        if(!"ANNOCEMENT".equals(ps[0])) {
            notifyAnnounceError("格式错误");
            return;
        }

        if(streamInfo.isModifying) {
            int i = 0;
            for(; i < 10; i++) {
                if(!streamInfo.isModifying) {
                    streamInfo.isModifying = true; //Not an atomic operation, it's still no guarantee... 2021-06-12 18:30
                    break;
                }

                SystemClock.sleep(10);
            }

            if(i > 9) {
                notifyAnnounceError("未知错误");
                return;
            }
        } else {
            streamInfo.isModifying = true;
        }


        streamInfo.srvIP = ps[1];
        streamInfo.brcIP = ps[2];
        //TODO 检查IP合法性。
        streamInfo.brcPort = ps[3];

        streamInfo.isModifying = false;
    }

    private void notifyAnnounceError(String info) {
        if (onAnnoRcvCallback != null) {
            onAnnoRcvCallback.onAnnoRcvError(info);
        }
    }

    public void  stopRcv() {
        canRun = false;
        onAnnoRcvCallback = null;
        dgSocket.close();
    }

    private CountDownTimer notifyTimer = new CountDownTimer(TICK_MS, TICK_MS) {

        String serverIP;
        String brcIP;
        String brcPort;

        @Override
        public void onTick(long millisUntilFinished) {
            //Do nothing
        }

        @Override
        public void onFinish() {
            if (streamInfo.isModifying) {
                int i = 0;
                for(; i < 10; i++) {
                    if (!streamInfo.isModifying) {
                        streamInfo.isModifying = true; //lock it on
                        break;
                    }

                    SystemClock.sleep(50);
                }

                if(i > 9) {
                    if (onAnnoRcvCallback != null) {
                        onAnnoRcvCallback.onAnnoRcvError("通信超时！");
                    }
                    start();
                    return;
                }
            } else {
                streamInfo.isModifying = true;
            }

            serverIP = streamInfo.srvIP;
            brcIP = streamInfo.brcIP;
            brcPort = streamInfo.brcPort;

            streamInfo.srvIP = null;
            streamInfo.brcIP = null;
            streamInfo.brcPort = null;
            streamInfo.isModifying = false; //release the lock

            if (onAnnoRcvCallback != null) {
                onAnnoRcvCallback.onAnnoRcv(serverIP, brcIP, brcPort);
            }
            start();
        }
    };

    private class StreamInfo {
        boolean isModifying;

        private String srvIP;
        private String brcIP;
        private String brcPort;
    }

    public interface OnAnnoRcvCallback {
        /**
         * 本次检测周期中接收到了服务端的宣告信息。
         * */
        void onAnnoRcv(String serverIP, String brcIP, String brcPort);
        /**
         * 本次检测周期中未接收到服务端的宣告信息。
         * */
        void onAnnoRcvError(String info);
    }
}
