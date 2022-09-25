package net.yasmar.movefiles;

import android.content.ContentResolver;
import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
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
                // hopefully the above throw an exception so we don't remove
                // the original file if we have failed to write the copy!
                new File(sourcePath).delete();
            } catch (IOException e) {
                Log.w(TAG, "Failed to move the file?!", e);
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
        if (file == null) {
            Log.w(TAG, "failed to create File from path?!");
            return;
        }
        for (String p: file.list()) {
            Log.i(TAG, "file "+p);
            callback.run(p);
        }
    }

    byte[] readFile(String path) throws IOException {
        InputStream is = new FileInputStream(path);
        byte[] buffer = new byte[is.available()];
        is.read(buffer);
        is.close();
        return buffer;
    }

    void writeFile(String path, byte[] data) throws IOException {
        OutputStream os = new FileOutputStream(path);
        os.write(data);
        os.close();
    }

}
