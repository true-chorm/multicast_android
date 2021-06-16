package com.chorm.mc_cli;

import android.os.CountDownTimer;
import android.os.SystemClock;
import android.util.Log;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

public class Reporter extends Thread {

    private static final String TAG = "Reporter";

    private boolean canRun;
    private byte[] reportBuf;

    private MainActivity mainActivity;
    private DatagramSocket dgSocket;
    private DatagramPacket dgPkg;

    public Reporter(MainActivity ma) {
        mainActivity = ma;
        reportBuf = new byte[128];
        canRun = true;
        try {
            dgSocket = new DatagramSocket();
            dgPkg = new DatagramPacket(reportBuf, 128);
        } catch(SocketException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void run() {
        Log.d(TAG, "Reporter run");
        try {
            byte[] buf;
            String serverAddr;
            String tmp;
            StringBuilder sb = new StringBuilder();
            while(canRun) {
                //TODO 检查合法性。
                serverAddr = mainActivity.getServerAddr();
                if(serverAddr != null) {
                    sb.delete(0, sb.length());

                    //multicast address
                    tmp = mainActivity.getMulticastAddr();
                    if(tmp != null) {
                        sb.append(tmp);
                        sb.append(",");

                        //multicast port
                        tmp = mainActivity.getMulticastPort();
                        if(tmp != null) {
                            sb.append(tmp);
                            sb.append(",");

                            //duration
                            tmp = mainActivity.getDuration();
                            if(tmp != null) {
                                sb.append(tmp);
                                sb.append(",");

                                //kb total
                                sb.append(mainActivity.getKBTotal());
                                sb.append(",");

                                //rate
                                tmp = mainActivity.getRcvRate();
                                if(tmp != null) {
                                    sb.append(tmp);


                                    //send it.
                                    dgPkg.setAddress(InetAddress.getByName(serverAddr));
                                    dgPkg.setPort(8405);
                                    dgPkg.setData(sb.toString().getBytes());
                                    dgSocket.send(dgPkg);
                                    Log.d(TAG, "report pkg sent:");
                                }
                            }
                        }
                    }
                }

                SystemClock.sleep(1111);
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public void stopRun() {
        canRun = false;
    }
}
