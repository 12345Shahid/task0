package com.rcl.androidstreamcontrol.service;

import static com.rcl.androidstreamcontrol.utils.MyConstants.NOTIF_CHANNEL_ID;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.lifecycle.LifecycleService;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.rcl.androidstreamcontrol.R;

public class FollowerService extends LifecycleService implements ServiceRepository.PeerConnectionListener {

    private static final String TAG = "FollowerService";
    private final MutableLiveData<String> notifyEndService = new MutableLiveData<String>();
    private ServiceRepository serviceRepo = new ServiceRepository(this);
    private final IBinder mBinder = new FollowerBinder();

    public class FollowerBinder extends Binder {
        public FollowerService getService() {
            return FollowerService.this;
        }
        public LiveData<String> getNotifyEndService() { return notifyEndService; }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        super.onBind(intent);
        return mBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        Log.d(TAG, "onStartCommand");

        createNotificationChannel();

        Intent notificationIntent = getPackageManager()
                .getLaunchIntentForPackage(getPackageName())
                .setPackage(null)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
                .setContentTitle("Portal Remote Service")
                .setContentText("Listening for connection requests...")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(1, notification);
        }

        serviceRepo.peerConnectionListener = this;
        serviceRepo.start();

        return START_STICKY;
    }

    private void createNotificationChannel() {
        NotificationChannel serviceChannel = new NotificationChannel(
                NOTIF_CHANNEL_ID,
                "Foreground Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
        );

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(serviceChannel);
        }
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        serviceRepo.onUnbind();
        super.onDestroy();
    }

    @Override
    public void postPeerConnected() {
        Log.d(TAG, "Peer connected");
    }

    @Override
    public void postPeerDisconnected() {
        Log.d(TAG, "Peer disconnected");
    }
}