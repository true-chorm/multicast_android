package com.chorm.mcapk;

import android.content.Context;
import android.os.CountDownTimer;
import android.os.SystemClock;
import android.util.Log;

import java.lang.reflect.Array;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Arrays;
import java.util.Enumeration;

public class Announcement extends Thread {

    private static final String TAG = "Announcement";

    private static final int ANNO_PORT = 8305;
    private final String MC_ADDRESS;
    private final String MC_PORT;

    private boolean canRun;
    private byte[] abuf;

    private Context context;
    private OnAnnouncementCallback onAnnouncementCallback;
    private DatagramSocket dgSocket;
    private DatagramPacket dgPkg;

    public Announcement(Context ctx, String mcAddr, String mcPort, OnAnnouncementCallback cb) {
        context = ctx;
        MC_ADDRESS = mcAddr;
        MC_PORT = mcPort;
        onAnnouncementCallback = cb;

        InetAddress ia = getLocalIP();
        if(ia == null) {
            if(cb != null) {
                cb.onAnno("查询网卡信息失败，请检查网络连接！");
            }
            return;
        }

        Log.d(TAG, "ip:" + ia.getHostAddress());
        String ip = ia.getHostAddress();
        String brcAddr = ip.substring(0, ip.lastIndexOf('.'));
        String[] ipPrefix = brcAddr.split("\\.");
        if(ipPrefix.length != 3) {
            if(cb != null) {
                cb.onAnno("查询网卡信息失败！");
                return;
            }
        }

        byte[] brcAddr2 = new byte[4];
        brcAddr2[3] = (byte) 0xff;
        try {
            int z = Integer.parseInt(ipPrefix[2]);
            brcAddr2[2] = (byte) z;
            z = Integer.parseInt(ipPrefix[1]);
            brcAddr2[1] = (byte) z;
            z = Integer.parseInt(ipPrefix[0]);
            brcAddr2[0] = (byte) z;
        } catch(Exception e) {
            e.printStackTrace();
            if(cb != null) {
                cb.onAnno("通信失败！");
                return;
            }
        }

        try {
            dgSocket = new DatagramSocket();
            InetAddress ia2 = InetAddress.getByAddress(brcAddr2);
            Log.d(TAG, "fuck you:" + ia2.getHostAddress());
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            sb.append("ANNOCEMENT,");
            sb.append(ia2.getHostAddress());
            sb.append(",");
            sb.append(MC_ADDRESS);
            sb.append(",");
            sb.append(MC_PORT);
            sb.append("]");
            abuf = sb.toString().getBytes();

            dgPkg = new DatagramPacket(abuf, abuf.length, ia2, ANNO_PORT);
        } catch(Exception e) {
            e.printStackTrace();
            if(cb != null) {
                cb.onAnno("未知异常！");
            }
        }
    }

    private InetAddress getLocalIP() {
        //TODO 应判断设备当前上网类型。
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                        return inetAddress;
                    }
                }
            }
        } catch (SocketException ex) {
            ex.printStackTrace();
        }

        return null;
    }

    @Override
    public void run() {
        canRun = true;
        while(canRun) {
            if(dgSocket != null && dgPkg != null) {
                try {
                    dgSocket.send(dgPkg);
                } catch(Exception e) {
                    e.printStackTrace();
                }
                Log.d(TAG, "Announced!");
            } else {
                if(onAnnouncementCallback != null) {
                    onAnnouncementCallback.onAnno("无法启用感知服务");
                }
                canRun = false;
            }
            SystemClock.sleep(1000);
        }
    }

    public void stopAnno() {
        canRun = false;
        onAnnouncementCallback = null;
    }

    public interface OnAnnouncementCallback {
        void onAnno(String info);
    }
}
