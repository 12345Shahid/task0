package com.rcl.androidstreamcontrol.service;

import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.rcl.androidstreamcontrol.webrtc.AgoraManager;

public class ServiceRepository {
    private static final String TAG = "ServiceRepository";
    public PeerConnectionListener peerConnectionListener;
    Context context;
    public AgoraManager agoraManager;

    private Intent cachedProjectionIntent;
    private Notification fgServiceNotification;

    public boolean controlEnabled = true;
    public boolean isExpectingMediaProjection = false;

    public ServiceRepository(Context context) {
        this.context = context;
        agoraManager = new AgoraManager(context);
    }

    public void onUnbind() {
        Log.d(TAG, "onUnbind: Disconnecting Agora!");
        if (agoraManager != null) {
            agoraManager.disconnect();
        }
    }

    public void setMProjectionIntent(Intent intent) {
        this.cachedProjectionIntent = intent;
    }

    public void setFgServiceNotification(Notification notification) {
        this.fgServiceNotification = notification;
    }

    public void start() {
        Log.d(TAG, "start: Fetching token and connecting to Agora...");
        if (agoraManager != null) {
            agoraManager.fetchTokenAndConnect(null, null);
        }
    }

    public interface PeerConnectionListener {
        public void postPeerConnected();
        public void postPeerDisconnected();
    }
}
