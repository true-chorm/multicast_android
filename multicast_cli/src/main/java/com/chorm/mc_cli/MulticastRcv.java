package com.chorm.mc_cli;

public class MulticastRcv extends Thread {

    private static final String TAG = "MulticastRcv";

    private boolean canRun;

    public MulticastRcv() {

    }

    @Override
    public void run() {

    }

    public void stopRcv() {
        canRun = false;
    }
}
