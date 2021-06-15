package com.chorm.mc_cli;

import android.os.CountDownTimer;
import android.os.SystemClock;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.Random;
import java.util.logging.Logger;

public class MulticastRcv extends Thread {

    private static final String TAG = "MulticastRcv";

    private boolean canRun;
    private String ip;
    private int port;
    private MulticastSocket ms;

    private long longtmp;
    private long kbRcv; //接收到多少kb的数据。
    private long[][] pkgSeq;
    private int[] seqIdx;
    private int seqUsing;
    /**客户端可能是从中途启动的，此时要记下启动时接收到的第一个序号。*/
    private long beginSeqNo;

    private OnMulticastStatisticCallback onMulticastStatisticCallback;

    public MulticastRcv(String ip, String port, OnMulticastStatisticCallback cb) {
        this.ip = ip;
        this.port = Integer.parseInt(port);
        onMulticastStatisticCallback = cb;
        canRun = true;
        kbRcv = 0;
        pkgSeq = new long[2][10000];
        seqIdx = new int[2];
        seqUsing = 0;
        beginSeqNo = -1;
        clearSeq(pkgSeq[seqUsing]);

        statisticCalback.start();
    }

    @Override
    public void run() {
        SystemClock.sleep(100); //Yes, we need it.

        try {
            ms = new MulticastSocket(port);
            ms.joinGroup(InetAddress.getByName(ip));
        } catch(IOException e) {
            e.printStackTrace();
        }

        byte[] rbuf = new byte[1600];
        DatagramPacket dgPkg = new DatagramPacket(rbuf, 1060);
        int usingtmp;
        try {
            Random rd = new Random();
            while(canRun) {
                ms.receive(dgPkg);

                //parse the pkg which rcv
                if(dgPkg.getLength() == 1024) {
                    kbRcv++;
                    usingtmp = seqUsing;
                    pkgSeq[usingtmp][seqIdx[usingtmp]] = longFrom8Bytes(dgPkg.getData(), 0, false);
                    if(beginSeqNo == -1) {
                        beginSeqNo = pkgSeq[usingtmp][seqIdx[usingtmp]] - 1;
                    }
                    seqIdx[usingtmp]++;
                }
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public static long longFrom8Bytes(byte[] input, int offset, boolean littleEndian){
        long value=0;
        // 循环读取每个字节通过移位运算完成long的8个字节拼装
        for(int  count=0;count<8;++count){
            int shift=(littleEndian?count:(7-count))<<3;
            value |=((long)0xff<< shift) & ((long)input[offset+count] << shift);
        }
        return value;
    }

    public void stopRcv() {
        canRun = false;
        beginSeqNo = -1;
    }

    private void clearSeq(long[] seq) {
        for(int i = 0; i < 10000; i++) {
            seq[i] = 0;
        }
        seqIdx[seqUsing] = 0;
    }

    private final CountDownTimer statisticCalback = new CountDownTimer(1000, 1000) {

        int zeroCount;
        @Override
        public void onTick(long millisUntilFinished) {
            //Do nothing.
        }

        @Override
        public void onFinish() {
            int usingtmp = seqUsing;
            int idxtmp = seqIdx[usingtmp];
            long kbTotalTmp = kbRcv;

            if(idxtmp > 0) {
                zeroCount = 0;
                //switch it
                if(seqUsing == 0) {
                    clearSeq(pkgSeq[1]);
                    seqUsing = 1;
                } else {
                    clearSeq(pkgSeq[0]);
                    seqUsing = 0;
                }

                //statistic
                sort(pkgSeq[usingtmp], 0, idxtmp - 1);

                //notify
                if(onMulticastStatisticCallback != null) {
                    onMulticastStatisticCallback.onMulticastStatistic(kbTotalTmp, idxtmp, pkgSeq[usingtmp][idxtmp - 1] - beginSeqNo);
                }
            } else {
                if(zeroCount++ > 5) {
                    Log.d(TAG, "reset statistic");
                    kbRcv = 0;
                    beginSeqNo = -1;
                    zeroCount = 0;
                }
            }

            start();
        }

        private void sort(long[] array, int left, int right) {
            if(left > right) {
                return;
            }
            // base中存放基准数
            long base = array[left];
            int i = left, j = right;
            while(i != j) {
                // 顺序很重要，先从右边开始往左找，直到找到比base值小的数
                while(array[j] >= base && i < j) {
                    j--;
                }

                // 再从左往右边找，直到找到比base值大的数
                while(array[i] <= base && i < j) {
                    i++;
                }

                // 上面的循环结束表示找到了位置或者(i>=j)了，交换两个数在数组中的位置
                if(i < j) {
                    long tmp = array[i];
                    array[i] = array[j];
                    array[j] = tmp;
                }
            }

            // 将基准数放到中间的位置（基准数归位）
            array[left] = array[i];
            array[i] = base;

            // 递归，继续向基准的左右两边执行和上面同样的操作
            // i的索引处为上面已确定好的基准值的位置，无需再处理
            sort(array, left, i - 1);
            sort(array, i + 1, right);
        }
    };

    public interface OnMulticastStatisticCallback {
        void onMulticastStatistic(long kbTotal, int kbThisTime, long seqNoThisTime);
    }
}
