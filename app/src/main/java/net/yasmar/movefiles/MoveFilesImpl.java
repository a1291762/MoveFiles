package net.yasmar.movefiles;

import android.content.ContentResolver;
import android.content.Context;

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
        filesForFolder(sourceFolder, (filename) -> moveFile(sourceFolder, destFolder, filename));
    }

    byte[] buffer = new byte[1000000];
    void moveFile(String sourceFolder, String destFolder, String filename) {
        File sourceFile = new File(sourceFolder + "/" + filename);
        File destFile = new File(destFolder + "/" + filename);
        Log.i(TAG, "move from "+sourceFile+" to "+destFile);
        try {
            InputStream is = new FileInputStream(sourceFile);
            int available = is.available();
            OutputStream os = new FileOutputStream(destFile);
            while (available > 0) {
                int got = is.read(buffer);
                os.write(buffer, 0, got);
                available -= got;
            }
            is.close();
            os.close();

            // hopefully the above throws an exception so we don't remove
            // the original file if we have failed to write the copy!
            boolean did = sourceFile.delete();
            if (!did) {
                Log.w(TAG, "Failed to remove source file?!");
            }
        } catch (IOException e) {
            Log.w(TAG, "Failed to move the file?!", e);
            // try to remove the partially-written destination file, but don't worry if we fail
            //noinspection ResultOfMethodCallIgnored
            destFile.delete();
        }
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

}
