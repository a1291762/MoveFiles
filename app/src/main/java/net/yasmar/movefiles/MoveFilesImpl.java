package net.yasmar.movefiles;

import android.content.ContentResolver;
import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class MoveFilesImpl {

    private static final String TAG = "MoveFilesImpl";

    Context context;
    ContentResolver contentResolver;

    MoveFilesImpl(Context context) {
        this.context = context;
        contentResolver = context.getContentResolver();
    }

    void moveFiles(String sourceFolder, String destFolder) {
        filesForFolder(sourceFolder, (filename) -> {
            String sourcePath = sourceFolder + "/" + filename;
            Log.i(TAG, "source path "+sourcePath);
            String destPath = destFolder + "/" + filename;
            Log.i(TAG, "dest path "+destPath);
            try {
                byte[] data = readFile(sourcePath);
                writeFile(destPath, data);
                new File(sourcePath).delete();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    interface FilesForFolderCallback {
        void run(String path);
    }

    // Returns the files (documentUri) for a folder.
    // Does not recurse
    void filesForFolder(String path, FilesForFolderCallback callback) {
        Log.i(TAG, "files for folder "+path);
        File file = new File(path);
        Log.i(TAG, "files for folder "+file);
        for (String p: file.list()) {
            Log.i(TAG, "file "+p);
            callback.run(p);
        }
    }

    byte[] readFile(String path) {
        try {
            InputStream is = new FileInputStream(path);
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            is.close();
            return buffer;
        } catch (Exception e) {
            Log.i(TAG, "Failed to read "+path);
            e.printStackTrace();
            assert(false);
        }
        return null;
    }

    void writeFile(String path, byte[] data) {
        try {
            OutputStream os = new FileOutputStream(path);
            os.write(data);
            os.close();
        } catch (Exception e) {
            Log.i(TAG, "Failed to write "+path);
            e.printStackTrace();
            assert(false);
        }
    }

}
