package net.yasmar.movefiles;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.format.Time;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

public class Log {
    public static File logFile;
    public static boolean LOG_TO_FILE = false;

    static void init(Context context) {
        File externalFiles = context.getExternalFilesDir(null);
        if (externalFiles == null) {
            logFile = null;
            LOG_TO_FILE = false;
        } else {
            logFile = new File(externalFiles  + "/log.txt");
            SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
            LOG_TO_FILE = sharedPrefs.getBoolean("logging", false);
        }
    }

    static void logToFile(String msg) {
        try {
            if (!logFile.exists())
                logFile.createNewFile();
            BufferedWriter buf = new BufferedWriter(new FileWriter(logFile, true));
            Time now = new Time();
            now.setToNow();
            String sTime = now.format("%Y-%m-%d %H:%M:%S ");
            buf.append(sTime);
            buf.append(msg);
            buf.newLine();
            buf.close();
        } catch (IOException e) {
            //throw new RuntimeException(e);
        }
    }

    static void logToFile(Throwable t) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        t.printStackTrace(pw);
        logToFile(sw.toString());
    }

    public static void v(String tag, String msg) {
        android.util.Log.v(tag, msg);
        if (LOG_TO_FILE) {
            logToFile(msg);
        }
    }

    public static void i(String tag, String msg) {
        android.util.Log.i(tag, msg);
        if (LOG_TO_FILE) {
            logToFile(msg);
        }
    }

    public static void w(String tag, String msg) {
        android.util.Log.w(tag, msg);
        if (LOG_TO_FILE) {
            logToFile(msg);
        }
    }

    public static void w(String tag, String msg, Throwable t) {
        android.util.Log.w(tag, msg, t);
        if (LOG_TO_FILE) {
            logToFile(msg);
            logToFile(t);
        }
    }
}
