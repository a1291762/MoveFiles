package net.yasmar.movefiles;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.FileObserver;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.preference.PreferenceManager;

import java.io.File;

import androidx.annotation.Nullable;

public class MainService
        extends Service {

    private static final String TAG = "MainService";

    Context context;
    NotificationManager notificationManager;
    MoveFilesImpl impl;
    FileObserver fileObserver;
    boolean running = false;
    Handler handler;

    public static final String PERSISTENT_CHANNEL = "persistent.1";

    @Override
    public void onCreate() {
        super.onCreate();

        context = getBaseContext();
        Log.init(context);

        Log.i(TAG, "creating service");

        notificationManager = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) {
            throw new NullPointerException("notificationManager");
        }
        impl = MoveFilesImpl.getInstance();

        createNotificationChannels();

        handler = new Handler(Looper.getMainLooper());
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "service is being destroyed");
        running = false;
        if (fileObserver != null) {
            fileObserver.stopWatching();
        }
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if ("start".equals(intent.getAction())) {
            start();
        } else if ("stop".equals(intent.getAction())) {
            stop();
        } else if ("restart".equals(intent.getAction())) {
            if (!running) {
                // the service wasn't actually running?!
                start();
            }
        }
        return START_NOT_STICKY;
    }

    void start() {
        Log.i(TAG, "started foreground service");
        Notification notification = buildNotification();
        startForeground(1, notification);

        if (fileObserver != null) {
            fileObserver.stopWatching();
            fileObserver = null;
        }
        startFileObserver();
        running = true;
    }

    void stop() {
        running = false;
        stopForeground(true);
        stopSelf();
    }

    void createNotificationChannels() {
        String channelId = PERSISTENT_CHANNEL;
        if (notificationManager.getNotificationChannel(channelId) == null) {
            CharSequence name = "Persistent Notification";
            String description = "Used for the foreground service. Can be hidden.";
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel mChannel = new NotificationChannel(channelId, name, importance);
            mChannel.setDescription(description);
            mChannel.setSound(null, null);
            mChannel.enableLights(false);
            notificationManager.createNotificationChannel(mChannel);
        }
    }

    Notification buildNotification() {
        Notification.Builder b = new Notification.Builder(context, PERSISTENT_CHANNEL);
        b.setSmallIcon(R.drawable.ic_launcher_foreground);
        b.setContentTitle("Foreground service");
        b.setContentText("This notification is required to be created while the foreground service runs. Tap to hide.");

        Intent i = new Intent();
        i.setAction("android.settings.APP_NOTIFICATION_SETTINGS");
        i.putExtra("android.provider.extra.APP_PACKAGE", context.getPackageName());
        PendingIntent ci = PendingIntent.getActivity(context, 1, i, PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        b.setContentIntent(ci);

        return b.build();
    }

    void startFileObserver() {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        String sourcePath = sharedPrefs.getString("sourceFolder", null);
        String destPath = sharedPrefs.getString("destFolder", null);
        if (sourcePath == null || destPath == null) {
            Log.w(TAG, "Can't start the foreground service because the folders aren't set!");
            stop();
            return;
        }

        File sourceFolder = new File(sourcePath);
        File destFolder = new File(destPath);
        int mask = FileObserver.CLOSE_WRITE | FileObserver.MOVED_TO | FileObserver.MOVE_SELF | FileObserver.CREATE;
        fileObserver = new FileObserver(sourceFolder, mask) {
            @Override
            public void onEvent(int event, @Nullable String filename) {
                moveLater(sourceFolder, destFolder, filename);
            }
        };
        fileObserver.startWatching();
    }

    void moveLater(File sourceFolder, File destFolder, String filename) {
        File sourceFile = new File(sourceFolder + "/" + filename);
        if (filename == null || filename.startsWith(".") || !sourceFile.isFile()) {
            return;
        }
        Log.i(TAG, "scheduling a move of "+filename+" in 30 seconds");
        handler.postDelayed(() -> moveNow(sourceFolder, destFolder, filename), 30000);
    }

    void moveNow(File sourceFolder, File destFolder, String filename) {
        File sourceFile = new File(sourceFolder + "/" + filename);
        if (!sourceFile.isFile()) {
            // it got removed while we were waiting?
            return;
        }
        long now = System.currentTimeMillis();
        if (sourceFile.lastModified() + 30000 > now) {
            // too new... try later
            moveLater(sourceFolder, destFolder, filename);
            return;
        }
        impl.moveFile(sourceFolder, destFolder, filename);
    }
}
