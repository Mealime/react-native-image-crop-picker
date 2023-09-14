package com.reactnative.ivpusic.imagepicker;

import static com.reactnative.ivpusic.imagepicker.StorageUtil.getTmpDir;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

class RealPathUtil {
    static String getRealPathFromURI(final Context context, final Uri uri) throws IOException {
        if ("content".equalsIgnoreCase(uri.getScheme())) {
            // Return the remote address
            if (isGooglePhotosUri(uri)) {
                return uri.getLastPathSegment();
            }
            return getDataColumn(context, uri);
        }
        // File
        else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }

        return null;
    }

    /**
     * If an image/video has been selected from a cloud storage, this method
     * should be call to download the file in the cache folder.
     *
     * @param context The context
     * @param fileName donwloaded file's name
     * @param uri file's URI
     * @return file that has been written
     */
    private static File writeToFile(Context context, String fileName, Uri uri) {
        String tmpDir = getTmpDir(context);
        fileName = fileName.substring(fileName.lastIndexOf('/') + 1);
        File path = new File(tmpDir);
        File file = new File(path, fileName);
        try {
            FileOutputStream oos = new FileOutputStream(file);
            byte[] buf = new byte[8192];
            InputStream is = context.getContentResolver().openInputStream(uri);
            int c = 0;
            while ((c = is.read(buf, 0, buf.length)) > 0) {
                oos.write(buf, 0, c);
                oos.flush();
            }
            oos.close();
            is.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return file;
    }

    /**
     * Get the value of the data column for this Uri. This is useful for
     * MediaStore Uris, and other file-based ContentProviders.
     *
     * @param context The context.
     * @param uri The Uri to query.
     * @return The value of the _data column, which is typically a file path.
     */
    private static String getDataColumn(Context context, Uri uri) {

        final String[] projection = {
                MediaStore.MediaColumns.DATA,
                MediaStore.MediaColumns.DISPLAY_NAME,
        };
        try (Cursor cursor = context.getContentResolver().query(uri, projection, null, null,
            null)) {
            if (cursor != null && cursor.moveToFirst()) {
                // Fall back to writing to file if _data column does not exist
                final int index = cursor.getColumnIndex(MediaStore.MediaColumns.DATA);
                String path = index > -1 ? cursor.getString(index) : null;
                if (path != null) {
                    return cursor.getString(index);
                } else {
                    final int indexDisplayName = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME);
                    String fileName = cursor.getString(indexDisplayName);
                    File fileWritten = writeToFile(context, fileName, uri);
                    return fileWritten.getAbsolutePath();
                }
            }
        }
        return null;
    }


    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     */
    private static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     */
    private static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     */
    private static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is Google Photos.
     */
    private static boolean isGooglePhotosUri(Uri uri) {
        return "com.google.android.apps.photos.content".equals(uri.getAuthority());
    }

    private static String getPathToNonPrimaryVolume(Context context, String tag) {
        File[] volumes = context.getExternalCacheDirs();
        if (volumes != null) {
            for (File volume : volumes) {
                if (volume != null) {
                    String path = volume.getAbsolutePath();
                    int index = path.indexOf(tag);
                    if (index != -1) {
                        return path.substring(0, index) + tag;
                    }
                }
            }
        }
        return null;
    }

}
