package net.yasmar.movefiles;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.FileObserver;
import android.preference.PreferenceManager;
import android.text.Layout;
import android.text.method.ScrollingMovementMethod;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import androidx.annotation.Nullable;
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

    boolean showingGrantScreen = false;

    Button source;
    Button destination;
    Button enable;
    Button service;
    TextView logView;

    private SharedPreferences sharedPrefs;
    private boolean mEnabled;

    Context context;
    ContentResolver contentResolver;
    WorkManager workManager;
    MoveFilesImpl impl;
    FileObserver fileObserver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        context = this;
        // Log to the app's files folder on /sdcard
        File externalFiles = context.getExternalFilesDir(null);
        if (externalFiles != null) {
            Log.dataDir = externalFiles.toString();
            Log.LOG_TO_FILE = true;
        }

        contentResolver = context.getContentResolver();
        workManager = WorkManager.getInstance(context);
        impl = new MoveFilesImpl(context);

        boolean isManager = Environment.isExternalStorageManager();
        if (isManager) {
            Log.i(TAG, "Showing config screen");
            setContentView(R.layout.activity_main);
        } else {
            Log.i(TAG, "Showing grant screen");
            setContentView(R.layout.activity_grant);
            showingGrantScreen = true;
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
        boolean useService = sharedPrefs.getBoolean("service", false);

        boolean workScheduled = isWorkScheduled("moveFiles");
        if (mEnabled && !workScheduled) {
            // we have become disabled :(
            Log.w(TAG, "we have become disabled?!");
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

        service = findViewById(R.id.service);
        service.setText(useService ? R.string.service_disable : R.string.service_enable);
        service.setOnClickListener((view) -> launchService());

        logView = findViewById(R.id.log);
        logView.setMovementMethod(new ScrollingMovementMethod());

        // Read the log when the layout is done (so we can reliably scroll to the end)
        LinearLayout layout = findViewById(R.id.layout);
        layout.getViewTreeObserver().addOnGlobalLayoutListener(this::readLog);

        // Read the log when it changes (so we can observe events as they happen)
        fileObserver = new FileObserver(Log.logFile, FileObserver.MODIFY) {
            @Override
            public void onEvent(int i, @Nullable String s) {
                readLog();
            }
        };
        fileObserver.startWatching();
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "activity is being destroyed");
        fileObserver.stopWatching();
        super.onDestroy();
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

        boolean shouldBeShowingGrantScreen = !Environment.isExternalStorageManager();
        if (shouldBeShowingGrantScreen != showingGrantScreen) {
            // The app has gained or lost the external storage manager permission
            // so the UI is wrong... Just re-launch the activity to make it right
            Log.i(TAG, "finishing the current activity so we can start a new one with the correct UI");
            Intent intent = new Intent(context, MainActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        Log.i(TAG, "resuming the UI");
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
        String path = folderUri.getLastPathSegment().replace(':', '/');
        if (path.startsWith("primary/")) {
            // primary/foo becomes /sdcard/foo
            path = path.replace("primary/", Environment.getExternalStorageDirectory().getAbsolutePath() + "/");
        } else {
            // bar becomes /storage/bar
            path = "/storage/" + path;
        }
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
        Log.i(TAG, "Stop Work");
        workManager.cancelAllWork();
    }

    void launchService() {
        boolean useService = sharedPrefs.getBoolean("service", false);
        useService = !useService;
        Log.i(TAG, "Launching the foreground service "+useService);
        Intent intent = new Intent(context, MainService.class);
        if (useService) {
            intent.setAction("start");
        } else {
            intent.setAction("stop");
        }
        context.startForegroundService(intent);
        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putBoolean("service", useService);
        editor.apply();
        service.setText(useService ? R.string.service_disable : R.string.service_enable);
    }

    void readLog() {
        try {
            byte[] bytes = impl.readFile(Log.logFile.getPath());
            logView.setText(new String(bytes));

            Layout layout = logView.getLayout();
            if (layout != null) {
                final int scrollAmount = layout.getLineTop(logView.getLineCount()) - logView.getHeight();
                // if there is no need to scroll, scrollAmount will be <=0
                if (scrollAmount > 0) {
                    logView.scrollTo(0, scrollAmount);
                } else {
                    logView.scrollTo(0, 0);
                }
            }

        } catch (IOException e) {
            // oh well
        }
    }
}
