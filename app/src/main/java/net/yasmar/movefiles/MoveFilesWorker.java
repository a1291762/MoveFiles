package net.yasmar.movefiles;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Environment;
import android.preference.PreferenceManager;

import java.io.File;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class MoveFilesWorker
        extends Worker {

    private static final String TAG = "MoveFilesWorker";

    Context context;
    MoveFilesImpl impl;

    public MoveFilesWorker(
            @NonNull Context context,
            @NonNull WorkerParameters workerParameters) {
        super(context, workerParameters);
        this.context = context;

        // Log to the app's files folder on /sdcard
        File externalFiles = context.getExternalFilesDir(null);
        if (externalFiles != null) {
            Log.dataDir = externalFiles.toString();
            Log.LOG_TO_FILE = true;
        }

        //Log.i(TAG, "MoveFilesWorker is getting created");

        impl = MoveFilesImpl.getInstance();
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.i(TAG, "MoveFilesWorker was told to do work...");
        if (!Environment.isExternalStorageManager()) {
            Log.w(TAG, "The app isn't an external storage manager anymore?!");
            return Result.failure();
        }

        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);

        // the service only watches for changes, so we still need to actually move files now
        String sourcePath = sharedPrefs.getString("sourceFolder", null);
        String destPath = sharedPrefs.getString("destFolder", null);
        if (sourcePath != null && destPath != null) {
            File sourceFolder = new File(sourcePath);
            File destFolder = new File(destPath);
            impl.moveFiles(sourceFolder, destFolder);
        }

        // work is run even when the phone is rebooted or the app is upgraded
        // if a service was supposed to be running, restart it now
        boolean serviceEnabled = sharedPrefs.getBoolean("service", false);
        if (serviceEnabled) {
            Intent intent = new Intent(context, MainService.class);
            intent.setAction("restart");
            context.startForegroundService(intent);
        }

        return Result.success();
    }

}
