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
            String destPath = destFolder + "/" + filename;
            Log.i(TAG, "move from "+sourcePath+" to "+destPath);
            try {
                byte[] data = readFile(sourcePath);
                if (data == null) {
                    Log.w(TAG, "Failed to read source file?!");
                    return;
                }
                writeFile(destPath, data);
                // hopefully the above throws an exception so we don't remove
                // the original file if we have failed to write the copy!
                boolean did = new File(sourcePath).delete();
                if (!did) {
                    Log.w(TAG, "Failed to remove source file?!");
                }
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
        File file = new File(path);
        assert(file.isDirectory());
        String[] list = file.list();
        assert(list != null);
        for (String p: list) {
            if (new File(path+"/"+p).isFile()) {
                callback.run(p);
            }
        }
    }

    byte[] readFile(String path) throws IOException {
        InputStream is = new FileInputStream(path);
        int available = is.available();
        byte[] buffer = new byte[available];
        int got = is.read(buffer);
        if (got != available) {
            return null;
        }
        is.close();
        return buffer;
    }

    void writeFile(String path, byte[] data) throws IOException {
        OutputStream os = new FileOutputStream(path);
        os.write(data);
        os.close();
    }

}
