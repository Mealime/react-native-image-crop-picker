package com.reactnative.ivpusic.imagepicker;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.webkit.MimeTypeMap;

import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

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

    public static Uri createUri(Context reactContext, File file) {
        String authority = reactContext.getApplicationContext().getPackageName() + ".imagepickerprovider";
        return FileProvider.getUriForFile(reactContext, authority, file);
    }

    public static String getTmpDir(Context context) {
        String tmpDir = context.getCacheDir() + "/rnicp";
        new File(tmpDir).mkdir();
        return tmpDir;
    }


    public static String resolveRealPath(Activity activity, Uri uri, boolean isCamera, String mediaPath) throws IOException {
        String path;

        if (isCamera) {
            Uri mediaUri = Uri.parse(mediaPath);
            path = mediaUri.getPath();
        } else {
            path = RealPathUtil.getRealPathFromURI(activity, uri);
        }

        if (path == null) {
            return null;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            String externalCacheDirPath = Uri.fromFile(activity.getExternalCacheDir()).getPath();
            String externalFilesDirPath = Uri.fromFile(activity.getExternalFilesDir(null)).getPath();
            String cacheDirPath = Uri.fromFile(activity.getCacheDir()).getPath();
            String FilesDirPath = Uri.fromFile(activity.getFilesDir()).getPath();
            if (!path.startsWith(externalCacheDirPath)
                && !path.startsWith(externalFilesDirPath)
                && !path.startsWith(cacheDirPath)
                && !path.startsWith(FilesDirPath)) {
                File copiedFile = createExternalStoragePrivateFile(activity, uri);
                path = RealPathUtil.getRealPathFromURI(activity, Uri.fromFile(copiedFile));
            }
        }

        return path;
    }

    public static File createExternalStoragePrivateFile(Context context, Uri uri) throws FileNotFoundException {
        InputStream inputStream = context.getContentResolver().openInputStream(uri);

        String extension = getExtension(context, uri);
        File file = new File(context.getExternalCacheDir(), "/temp/" + System.currentTimeMillis() + "." + extension);
        File parentFile = file.getParentFile();
        if (parentFile != null) {
            parentFile.mkdirs();
        }

        try {
            // Very simple code to copy a picture from the application's
            // resource into the external file.  Note that this code does
            // no error checking, and assumes the picture is small (does not
            // try to copy it in chunks).  Note that if external storage is
            // not currently mounted this will silently fail.
            OutputStream outputStream = new FileOutputStream(file);
            byte[] data = new byte[inputStream.available()];
            inputStream.read(data);
            outputStream.write(data);
            inputStream.close();
            outputStream.close();
        } catch (IOException e) {
            // Unable to create file, likely because external storage is
            // not currently mounted.
            Log.w("image-crop-picker", "Error writing " + file, e);
        }

        return file;
    }

    public static String getExtension(Context context, Uri uri) {
        String extension;

        //Check uri format to avoid null
        if (uri.getScheme().equals(ContentResolver.SCHEME_CONTENT)) {
            //If scheme is a content
            final MimeTypeMap mime = MimeTypeMap.getSingleton();
            extension = mime.getExtensionFromMimeType(context.getContentResolver().getType(uri));
        } else {
            //If scheme is a File
            //This will replace white spaces with %20 and also other special characters. This will avoid returning null values on file name with spaces and special characters.
            extension = MimeTypeMap.getFileExtensionFromUrl(Uri.fromFile(new File(uri.getPath())).toString());

        }

        return extension;
    }

}
