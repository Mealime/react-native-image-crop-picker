package com.reactnative.ivpusic.imagepicker;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Environment;

import androidx.core.app.ActivityCompat;

import java.io.File;

class StorageUtil {
    private StorageUtil() {}

    public static File getImageOutputDir(Context context, String child) {
        int status = ActivityCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        File path;
        if (status == PackageManager.PERMISSION_GRANTED) {
            path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        } else {
            path = new File(getTmpDir(context), child);
        }
        path.mkdirs();
        return path;
    }

    public static String getTmpDir(Context context) {
        String tmpDir = context.getCacheDir() + "/rnicp";
        new File(tmpDir).mkdir();
        return tmpDir;
    }
}
