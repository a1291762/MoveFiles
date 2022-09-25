package net.yasmar.movefiles;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;

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
        impl = new MoveFilesImpl(context);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.i(TAG, "Moving files...");
        if (!Environment.isExternalStorageManager()) {
            return Result.failure();
        }

        // work is run even when the phone is rebooted or the app is upgraded
        // if a service was supposed to be running, restart it now
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean useService = sharedPrefs.getBoolean("service", false);
        if (useService) {
            Intent intent = new Intent(context, MainService.class);
            intent.setAction("start");
            context.startService(intent);
        }

        // the service only watches for changes, so we still need to actually move files now
        String sourceFolder = sharedPrefs.getString("sourceFolder", null);
        String destFolder = sharedPrefs.getString("destFolder", null);
        impl.moveFiles(sourceFolder, destFolder);

        return Result.success();
    }

}
