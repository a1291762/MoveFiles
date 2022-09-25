package net.yasmar.movefiles;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Button;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;
import java.util.concurrent.TimeUnit;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import static android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION;

public class MainActivity extends Activity {

    private static final String TAG = "MainActivity";

    private static final int SOURCE_CODE = 1;
    private static final int DESTINATION_CODE = 2;
    private static final int GRANT_CODE = 3;

    Button source;
    Button destination;
    Button enable;

    private SharedPreferences sharedPrefs;
    private boolean mEnabled;

    Context context;
    ContentResolver contentResolver;
    WorkManager workManager;
    MoveFilesImpl impl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        context = this;
        contentResolver = context.getContentResolver();
        workManager = WorkManager.getInstance(context);
        impl = new MoveFilesImpl(context);

        boolean isManager = Environment.isExternalStorageManager();
        if (isManager) {
            setContentView(R.layout.activity_main);
        } else {
            setContentView(R.layout.activity_grant);
            Button grant = findViewById(R.id.grant);
            grant.setOnClickListener((view) -> {
                Intent intent = new Intent();
                intent.setAction(ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivityForResult(intent, GRANT_CODE);
            });
            return;
        }

        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        mEnabled = sharedPrefs.getBoolean("enabled", false);
        String sourceFolder = sharedPrefs.getString("sourceFolder", null);
        String destFolder = sharedPrefs.getString("destFolder", null);

        boolean workScheduled = isWorkScheduled("moveFiles");
        if (mEnabled && !workScheduled) {
            // we have become disabled :(
            Log.w(TAG, "we have become disabled because the manager permission was lost ("+isManager+") or the job got disabled ("+workScheduled+")");
            toggleEnabled();
        }

        source = findViewById(R.id.source);
        if (sourceFolder != null) {
            source.setText(sourceFolder);
        }
        source.setOnClickListener((view) -> selectSource());

        destination = findViewById(R.id.dest);
        if (destFolder != null) {
            destination.setText(destFolder);
        }
        destination.setOnClickListener((view) -> selectDestination());

        enable = findViewById(R.id.enable);
        enable.setText(mEnabled ? R.string.disable : R.string.enable);
        enable.setOnClickListener((view) -> toggleEnabled());
    }

    boolean isWorkScheduled(String tag) {
        ListenableFuture<List<WorkInfo>> statuses = workManager.getWorkInfosByTag(tag);
        try {
            List<WorkInfo> workInfoList = statuses.get();
            for (WorkInfo workInfo : workInfoList) {
                WorkInfo.State state = workInfo.getState();
                if (state == WorkInfo.State.RUNNING | state == WorkInfo.State.ENQUEUED) {
                    return true;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public void onResume() {
        super.onResume();

        boolean isManager = Environment.isExternalStorageManager();
        if (isManager) {
            setContentView(R.layout.activity_main);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    void selectSource() {
        Log.i(TAG, "Select Source");
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        startActivityForResult(intent, SOURCE_CODE);
    }

    void selectDestination() {
        Log.i(TAG, "Select Destination");
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        startActivityForResult(intent, DESTINATION_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK) {
            return;
        }
        if (requestCode != SOURCE_CODE && requestCode != DESTINATION_CODE) {
            Log.w(TAG, "unhandled request code "+requestCode);
            return;
        }
        Uri folderUri = data.getData();
        Log.i(TAG, "folder "+folderUri);
        String path = folderUri.getLastPathSegment().replace(':', '/')
                .replace("primary/", Environment.getExternalStorageDirectory().getAbsolutePath()+"/");
        Log.i(TAG, "path "+path);
        String property = requestCode == SOURCE_CODE ? "sourceFolder" : "destFolder";
        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putString(property, path);
        editor.apply();
        if (requestCode == SOURCE_CODE) {
            source.setText(path);
        } else {
            destination.setText(path);
        }
    }

    void toggleEnabled() {
        if (mEnabled) {
            disableService();
        } else {
            enableService();
        }
        mEnabled = !mEnabled;
        if (enable != null) {
            enable.setText(mEnabled ? R.string.disable : R.string.enable);
        }
        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putBoolean("enabled", mEnabled);
        editor.apply();
    }

    void enableService() {
        Log.i(TAG, "Start Work");

        PeriodicWorkRequest r = new PeriodicWorkRequest.Builder(
                MoveFilesWorker.class,
                15,
                TimeUnit.MINUTES)
                .addTag("moveFiles")
                .build();
        workManager.enqueueUniquePeriodicWork(
                "moveFiles",
                ExistingPeriodicWorkPolicy.KEEP,
                r);
    }

    void disableService() {
        Log.i(TAG, "Disable Work");
        workManager.cancelAllWork();
    }

}
