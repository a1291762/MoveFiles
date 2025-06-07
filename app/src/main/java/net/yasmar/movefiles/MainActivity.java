package net.yasmar.movefiles;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.FileObserver;
import android.text.Layout;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.TimeUnit;

import androidx.annotation.Nullable;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
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
    Switch logging;
    TextView logLabel;
    TextView logView;

    private SharedPreferences sharedPrefs;
    private boolean workEnabled;
    private boolean serviceEnabled;

    Context context;
    ContentResolver contentResolver;
    WorkManager workManager;
    MoveFilesImpl impl;
    FileObserver fileObserver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        context = this;
        Log.init(context);

        contentResolver = context.getContentResolver();
        workManager = WorkManager.getInstance(context);
        impl = MoveFilesImpl.getInstance();

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

        sharedPrefs = context.getSharedPreferences(context.getPackageName()+"_preferences", Context.MODE_PRIVATE);
        workEnabled = sharedPrefs.getBoolean("enabled", false);
        String sourcePath = sharedPrefs.getString("sourceFolder", null);
        String destPath = sharedPrefs.getString("destFolder", null);
        serviceEnabled = sharedPrefs.getBoolean("service", false);

        source = findViewById(R.id.source);
        if (sourcePath != null) {
            source.setText(sourcePath);
        }
        source.setOnClickListener((view) -> selectSource());

        destination = findViewById(R.id.dest);
        if (destPath != null) {
            destination.setText(destPath);
        }
        destination.setOnClickListener((view) -> selectDestination());

        enable = findViewById(R.id.enable);
        enable.setText(workEnabled ? R.string.disable : R.string.enable);
        enable.setOnClickListener((view) -> toggleWork());

        service = findViewById(R.id.service);
        service.setText(serviceEnabled ? R.string.service_disable : R.string.service_enable);
        service.setOnClickListener((view) -> toggleService());

        logging = findViewById(R.id.logging);
        logging.setChecked(Log.LOG_TO_FILE);
        logging.setOnCheckedChangeListener((CompoundButton b, boolean checked) -> setLogging(checked));

        logLabel = findViewById(R.id.logLabel);
        logView = findViewById(R.id.log);
        logView.setMovementMethod(new ScrollingMovementMethod());

        if (Log.LOG_TO_FILE) {
            // Read the log when the layout is done (so we can reliably scroll to the end)
            LinearLayout layout = findViewById(R.id.layout);
            layout.getViewTreeObserver().addOnGlobalLayoutListener(this::readLog);
        }

        setLogging(Log.LOG_TO_FILE);
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "activity is being destroyed");
        if (fileObserver != null) {
            fileObserver.stopWatching();
        }
        super.onDestroy();
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

        // In the event that the work or service stops unexpectedly, launching
        // the app will cause them to restart due to this logic.
        if (workEnabled) {
            startWork();
        }
        if (serviceEnabled) {
            startService(true);
        }

    }

    @Override
    public void onPause() {
        super.onPause();
        Log.i(TAG, "pausing the UI");
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

    void toggleWork() {
        if (workEnabled) {
            stopWork();
        } else {
            String sourcePath = sharedPrefs.getString("sourceFolder", null);
            String destPath = sharedPrefs.getString("destFolder", null);
            if (sourcePath == null || destPath == null) {
                Log.w(TAG, "Can't start the background job without setting both source and destination folders!");
                return;
            }
            startWork();
        }
        workEnabled = !workEnabled;
        if (enable != null) {
            enable.setText(workEnabled ? R.string.disable : R.string.enable);
        }
        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putBoolean("enabled", workEnabled);
        editor.apply();
    }

    void startWork() {
        Log.i(TAG, "Start background job");
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

    void stopWork() {
        Log.i(TAG, "Stop background job");
        workManager.cancelAllWork();
    }

    void toggleService() {
        if (serviceEnabled) {
            stopService();
        } else {
            String sourcePath = sharedPrefs.getString("sourceFolder", null);
            String destPath = sharedPrefs.getString("destFolder", null);
            if (sourcePath == null || destPath == null) {
                Log.w(TAG, "Can't start the foreground service without setting both source and destination folders!");
                return;
            }
            startService(false);
        }
        serviceEnabled = !serviceEnabled;
        service.setText(serviceEnabled ? R.string.service_disable : R.string.service_enable);
        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putBoolean("service", serviceEnabled);
        editor.apply();
    }

    void startService(boolean restart) {
        Log.i(TAG, "Start foreground service");
        Intent intent = new Intent(context, MainService.class);
        intent.setAction(restart ? "restart" : "start");
        context.startForegroundService(intent);
    }

    void stopService() {
        Log.i(TAG, "Stop foreground service");
        Intent intent = new Intent(context, MainService.class);
        intent.setAction("stop");
        context.startForegroundService(intent);
    }

    byte[] buffer = new byte[1000000];
    @SuppressLint("SetTextI18n")
    void readLog() {
        try {
            InputStream is = new FileInputStream(Log.logFile);
            int available = is.available();
            // If the log happens to be super long, only read the last 1M of it
            if (available > 1000000) {
                //noinspection ResultOfMethodCallIgnored
                is.skip(available - 1000000);
            }
            int got = is.read(buffer);
            is.close();

            logView.setText(new String(buffer, 0, got));

            Layout layout = logView.getLayout();
            if (layout != null) {
                final int scrollAmount = layout.getLineTop(logView.getLineCount()) - logView.getHeight();
                // if there is no need to scroll, scrollAmount will be <=0
                //noinspection ManualMinMaxCalculation
                if (scrollAmount > 0) {
                    logView.scrollTo(0, scrollAmount);
                } else {
                    logView.scrollTo(0, 0);
                }
            }

        } catch (IOException e) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            logView.setText("Exception reading log!\n"+sw);
        }
    }

    void setLogging(boolean logging) {
        logLabel.setVisibility(logging ? View.VISIBLE : View.GONE);
        logView.setVisibility(logging ? View.VISIBLE : View.GONE);

        if (logging == Log.LOG_TO_FILE)
            return;

        if (!logging) {
            // delete the file before we lose the reference
            Log.logFile.delete();
        }

        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putBoolean("logging", logging);
        editor.apply();
        Log.init(context);

        if (logging) {
            // ensure the log exists
            Log.i(TAG, "Starting logging");
            // Read the log when it changes (so we can observe events as they happen)
            fileObserver = new FileObserver(Log.logFile, FileObserver.CLOSE_WRITE) {
                @Override
                public void onEvent(int i, @Nullable String s) {
                    readLog();
                }
            };
            fileObserver.startWatching();
            readLog();
        } else if (fileObserver != null) {
            fileObserver.stopWatching();
            fileObserver = null;
        }

    }
}
