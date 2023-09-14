package com.reactnative.ivpusic.imagepicker;

import static com.reactnative.ivpusic.imagepicker.StorageUtil.createUri;
import static com.reactnative.ivpusic.imagepicker.StorageUtil.getImageOutputDir;
import static com.reactnative.ivpusic.imagepicker.StorageUtil.getTmpDir;
import static com.reactnative.ivpusic.imagepicker.StorageUtil.resolveRealPath;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeMap;
import com.yalantis.ucrop.UCrop;
import com.yalantis.ucrop.UCropActivity;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.UUID;



class PickerModule extends ReactContextBaseJavaModule implements ActivityEventListener {

    private static final int IMAGE_PICKER_REQUEST = 61110;
    private static final int CAMERA_PICKER_REQUEST = 61111;
    private static final String E_ACTIVITY_DOES_NOT_EXIST = "E_ACTIVITY_DOES_NOT_EXIST";

    private static final String E_PICKER_CANCELLED_KEY = "E_PICKER_CANCELLED";
    private static final String E_PICKER_CANCELLED_MSG = "User cancelled image selection";

    private static final String E_CALLBACK_ERROR = "E_CALLBACK_ERROR";
    private static final String E_FAILED_TO_SHOW_PICKER = "E_FAILED_TO_SHOW_PICKER";
    private static final String E_FAILED_TO_OPEN_CAMERA = "E_FAILED_TO_OPEN_CAMERA";
    private static final String E_NO_IMAGE_DATA_FOUND = "E_NO_IMAGE_DATA_FOUND";
    private static final String E_CAMERA_IS_NOT_AVAILABLE = "E_CAMERA_IS_NOT_AVAILABLE";
    private static final String E_CANNOT_LAUNCH_CAMERA = "E_CANNOT_LAUNCH_CAMERA";
    private static final String E_ERROR_WHILE_CLEANING_FILES = "E_ERROR_WHILE_CLEANING_FILES";

    private static final String E_NO_LIBRARY_PERMISSION_KEY = "E_NO_LIBRARY_PERMISSION";
    private static final String E_NO_LIBRARY_PERMISSION_MSG = "User did not grant library permission.";
    private static final String E_NO_CAMERA_PERMISSION_KEY = "E_NO_CAMERA_PERMISSION";
    private static final String E_NO_CAMERA_PERMISSION_MSG = "User did not grant camera permission.";

    private boolean cropping = false;
    private boolean cropperCircleOverlay = false;
    private boolean freeStyleCropEnabled = false;
    private boolean showCropGuidelines = true;
    private boolean showCropFrame = true;
    private boolean hideBottomControls = false;
    private boolean enableRotationGesture = false;
    private boolean disableCropperColorSetters = false;
    private ReadableMap options;

    private String cropperActiveWidgetColor = null;
    private String cropperStatusBarColor = null;
    private String cropperToolbarColor = null;
    private String cropperToolbarTitle = null;
    private String cropperToolbarWidgetColor = null;

    private int width = 0;
    private int height = 0;

    private Uri mCameraCaptureURI;
    private String mCurrentMediaPath;
    private final ResultCollector resultCollector = new ResultCollector();
    private final Compression compression = new Compression();
    private final ReactApplicationContext reactContext;

    PickerModule(ReactApplicationContext reactContext) {
        super(reactContext);
        reactContext.addActivityEventListener(this);
        this.reactContext = reactContext;
    }


    @Override
    public String getName() {
        return "ImageCropPicker";
    }

    private void setConfiguration(final ReadableMap options) {
        width = options.hasKey("width") ? options.getInt("width") : 0;
        height = options.hasKey("height") ? options.getInt("height") : 0;
        cropping = options.hasKey("cropping") && options.getBoolean("cropping");
        cropperActiveWidgetColor = options.hasKey("cropperActiveWidgetColor") ? options.getString("cropperActiveWidgetColor") : null;
        cropperStatusBarColor = options.hasKey("cropperStatusBarColor") ? options.getString("cropperStatusBarColor") : null;
        cropperToolbarColor = options.hasKey("cropperToolbarColor") ? options.getString("cropperToolbarColor") : null;
        cropperToolbarTitle = options.hasKey("cropperToolbarTitle") ? options.getString("cropperToolbarTitle") : null;
        cropperToolbarWidgetColor = options.hasKey("cropperToolbarWidgetColor") ? options.getString("cropperToolbarWidgetColor") : null;
        cropperCircleOverlay = options.hasKey("cropperCircleOverlay") && options.getBoolean("cropperCircleOverlay");
        freeStyleCropEnabled = options.hasKey("freeStyleCropEnabled") && options.getBoolean("freeStyleCropEnabled");
        showCropGuidelines = !options.hasKey("showCropGuidelines") || options.getBoolean("showCropGuidelines");
        showCropFrame = !options.hasKey("showCropFrame") || options.getBoolean("showCropFrame");
        hideBottomControls = options.hasKey("hideBottomControls") && options.getBoolean("hideBottomControls");
        enableRotationGesture = options.hasKey("enableRotationGesture") && options.getBoolean("enableRotationGesture");
        disableCropperColorSetters = options.hasKey("disableCropperColorSetters") && options.getBoolean("disableCropperColorSetters");
        this.options = options;
    }

    private void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            for (File child : Objects.requireNonNull(fileOrDirectory.listFiles())) {
                deleteRecursive(child);
            }
        }

        fileOrDirectory.delete();
    }

    @ReactMethod
    public void clean(final Promise promise) {
        resultCollector.setup(promise);
        final Activity activity = getCurrentActivity();
        if (activity == null) {
            resultCollector.notifyProblem(E_ACTIVITY_DOES_NOT_EXIST, "Activity doesn't exist");
            return;
        }

        try {
            File file = new File(getTmpDir(activity));
            if (!file.exists()) throw new Exception("File does not exist");
            deleteRecursive(file);
            resultCollector.notifySuccess(null);
        } catch (Exception ex) {
            ex.printStackTrace();
            resultCollector.notifyProblem(E_ERROR_WHILE_CLEANING_FILES, ex.getMessage());
        }
    }

    @ReactMethod
    public void cleanSingle(final String pathToDelete, final Promise promise) {
        resultCollector.setup(promise);
        if (pathToDelete == null) {
            resultCollector.notifyProblem(E_ERROR_WHILE_CLEANING_FILES, "Cannot cleanup empty path");
            return;
        }
        resultCollector.setup(promise);
        final Activity activity = getCurrentActivity();
        final PickerModule module = this;

        if (activity == null) {
            resultCollector.notifyProblem(E_ACTIVITY_DOES_NOT_EXIST, "Activity doesn't exist");
            return;
        }

        try {
            String path = pathToDelete;
            final String filePrefix = "file://";
            if (path.startsWith(filePrefix)) {
                path = path.substring(filePrefix.length());
            }
            File file = new File(path);
            if (!file.exists()) {
                throw new Exception("File does not exist. Path: " + path);
            }
            module.deleteRecursive(file);
            resultCollector.notifySuccess(null);
        } catch (Exception ex) {
            ex.printStackTrace();
            resultCollector.notifyProblem(E_ERROR_WHILE_CLEANING_FILES, ex.getMessage());
        }
    }

    @ReactMethod
    public void openCamera(final ReadableMap options, final Promise promise) {
        resultCollector.setup(promise);
        final Activity activity = getCurrentActivity();

        if (activity == null) {
            resultCollector.notifyProblem(E_ACTIVITY_DOES_NOT_EXIST, "Activity doesn't exist");
            return;
        }

        if (!isCameraAvailable(activity)) {
            resultCollector.notifyProblem(E_CAMERA_IS_NOT_AVAILABLE, "Camera not available");
            return;
        }

        setConfiguration(options);
        initiateCamera(activity);
    }

    private void initiateCamera(Activity activity) {
        try {
            String intent = MediaStore.ACTION_IMAGE_CAPTURE;
            File dataFile = createImageFile(activity);
            Intent cameraIntent = new Intent(intent);
            mCameraCaptureURI = createUri(reactContext, dataFile);
            cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, mCameraCaptureURI);
            cameraIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            startActivityForResult(cameraIntent, CAMERA_PICKER_REQUEST);
        } catch (Exception e) {
            resultCollector.notifyProblem(E_FAILED_TO_OPEN_CAMERA, e);
        }
    }

    @ReactMethod
    public void openPicker(final ReadableMap options, final Promise promise) {
        resultCollector.setup(promise);
        final Activity activity = getCurrentActivity();

        if (activity == null) {
            resultCollector.notifyProblem(E_ACTIVITY_DOES_NOT_EXIST, "Activity doesn't exist");
            return;
        }

        setConfiguration(options);
        initiatePicker(activity);
    }

    private void initiatePicker(final Activity activity) {
        try {
            final Intent galleryIntent;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                galleryIntent = new Intent(Intent.ACTION_PICK);
            } else {
                galleryIntent = new Intent(MediaStore.ACTION_PICK_IMAGES);
            }
            galleryIntent.setType("image/*");
            String[] mimetypes = {"image/jpeg", "image/png"};
            galleryIntent.putExtra(Intent.EXTRA_MIME_TYPES, mimetypes);
            galleryIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            galleryIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false);
            Intent chooserIntent = Intent.createChooser(galleryIntent, "Pick an image");
            startActivityForResult(chooserIntent, IMAGE_PICKER_REQUEST);
        } catch (Exception e) {
            resultCollector.notifyProblem(E_FAILED_TO_SHOW_PICKER, e);
        }
    }

    @ReactMethod
    public void openCropper(final ReadableMap options, final Promise promise) {
        resultCollector.setup(promise);
        final Activity activity = getCurrentActivity();

        if (activity == null) {
            resultCollector.notifyProblem(E_ACTIVITY_DOES_NOT_EXIST, "Activity doesn't exist");
            return;
        }

        setConfiguration(options);
        final Uri uri = Uri.parse(options.getString("path"));
        startCropping(activity, uri);
    }


    private void startCropping(final Activity activity, final Uri uri) {
        UCrop.Options options = new UCrop.Options();
        options.setCompressionFormat(Bitmap.CompressFormat.JPEG);
        options.setCompressionQuality(100);
        options.setCircleDimmedLayer(cropperCircleOverlay);
        options.setFreeStyleCropEnabled(freeStyleCropEnabled);
        options.setShowCropGrid(showCropGuidelines);
        options.setShowCropFrame(showCropFrame);
        options.setHideBottomControls(hideBottomControls);

        if (cropperToolbarTitle != null) {
            options.setToolbarTitle(cropperToolbarTitle);
        }

        if (enableRotationGesture) {
            // UCropActivity.ALL = enable both rotation & scaling
            options.setAllowedGestures(
                UCropActivity.ALL, // When 'scale'-tab active
                UCropActivity.ALL, // When 'rotate'-tab active
                UCropActivity.ALL  // When 'aspect ratio'-tab active
            );
        }

        if (!disableCropperColorSetters) {
            configureCropperColors(options);
        }

        UCrop uCrop = UCrop
            .of(uri, Uri.fromFile(new File(getTmpDir(activity), UUID.randomUUID().toString() + ".jpg")))
            .withOptions(options);

        if (width > 0 && height > 0) {
            uCrop.withAspectRatio(width, height);
        }

        startActivityForResult(uCrop.getIntent(activity), UCrop.REQUEST_CROP);
    }

    private WritableMap getSelection(Activity activity, Uri uri, boolean isCamera) throws Exception {
        String path = resolveRealPath(activity, uri, isCamera, mCurrentMediaPath);
        if (path == null || path.isEmpty()) {
            throw new Exception("Cannot resolve asset path.");
        }
        return getImage(activity, path);
    }

    private void getAsyncSelection(final Activity activity, Uri uri) throws Exception {
        String path = resolveRealPath(activity, uri, false, mCurrentMediaPath);
        if (path == null || path.isEmpty()) {
            resultCollector.notifyProblem(E_NO_IMAGE_DATA_FOUND, "Cannot resolve asset path.");
            return;
        }
        resultCollector.notifySuccess(getImage(activity, path));
    }

    private BitmapFactory.Options validateImage(String path) throws Exception {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        options.inDither = true;

        BitmapFactory.decodeFile(path, options);

        if (options.outMimeType == null || options.outWidth == 0 || options.outHeight == 0) {
            throw new Exception("Invalid image selected");
        }

        return options;
    }

    private WritableMap getImage(final Activity activity, String path) throws Exception {
        WritableMap image = new WritableNativeMap();

        if (path.startsWith("http://") || path.startsWith("https://")) {
            throw new Exception("Cannot select remote files");
        }
        BitmapFactory.Options original = validateImage(path);

        // if compression options are provided image will be compressed. If none options is provided,
        // then original image will be returned
        File compressedImage = compression.compressImage(this.reactContext, options, path, original);
        String compressedImagePath = compressedImage.getPath();
        BitmapFactory.Options options = validateImage(compressedImagePath);
        long modificationDate = new File(path).lastModified();

        image.putString("path", "file://" + compressedImagePath);
        image.putInt("width", options.outWidth);
        image.putInt("height", options.outHeight);
        image.putString("mime", options.outMimeType);
        image.putInt("size", (int) new File(compressedImagePath).length());
        image.putString("modificationDate", String.valueOf(modificationDate));
        return image;
    }

    private void configureCropperColors(UCrop.Options options) {
        if (cropperActiveWidgetColor != null) {
            options.setActiveControlsWidgetColor(Color.parseColor(cropperActiveWidgetColor));
        }

        if (cropperToolbarColor != null) {
            options.setToolbarColor(Color.parseColor(cropperToolbarColor));
        }

        if (cropperStatusBarColor != null) {
            options.setStatusBarColor(Color.parseColor(cropperStatusBarColor));
        }

        if (cropperToolbarWidgetColor != null) {
            options.setToolbarWidgetColor(Color.parseColor(cropperToolbarWidgetColor));
        }
    }


    private void imagePickerResult(Activity activity, final int requestCode, final int resultCode, final Intent data) {
        if (resultCode == Activity.RESULT_CANCELED) {
            resultCollector.notifyProblem(E_PICKER_CANCELLED_KEY, E_PICKER_CANCELLED_MSG);
        } else if (resultCode == Activity.RESULT_OK) {
            Uri uri = data.getData();
            if (uri == null) {
                resultCollector.notifyProblem(E_NO_IMAGE_DATA_FOUND, "Cannot resolve image url");
                return;
            }
            if (cropping) {
                startCropping(activity, uri);
            } else {
                try {
                    getAsyncSelection(activity, uri);
                } catch (Exception ex) {
                    resultCollector.notifyProblem(E_NO_IMAGE_DATA_FOUND, ex.getMessage());
                }
            }
        }
    }

    private void cameraPickerResult(Activity activity, final int requestCode, final int resultCode, final Intent data) {
        if (resultCode == Activity.RESULT_CANCELED) {
            resultCollector.notifyProblem(E_PICKER_CANCELLED_KEY, E_PICKER_CANCELLED_MSG);
        } else if (resultCode == Activity.RESULT_OK) {
            Uri uri = mCameraCaptureURI;
            if (uri == null) {
                resultCollector.notifyProblem(E_NO_IMAGE_DATA_FOUND, "Cannot resolve image url");
                return;
            }
            if (cropping) {
                UCrop.Options options = new UCrop.Options();
                options.setCompressionFormat(Bitmap.CompressFormat.JPEG);
                startCropping(activity, uri);
            } else {
                try {
                    WritableMap result = getSelection(activity, uri, true);
                    resultCollector.notifySuccess(result);
                } catch (Exception ex) {
                    resultCollector.notifyProblem(E_NO_IMAGE_DATA_FOUND, ex.getMessage());
                }
            }
        }
    }

    private void croppingResult(Activity activity, final int requestCode, final int resultCode, final Intent data) {
        if (data != null) {
            Uri resultUri = UCrop.getOutput(data);

            if (resultUri != null) {
                try {
                    if (width > 0 && height > 0) {
                        File resized = compression.resize(this.reactContext, resultUri.getPath(), width, height, width, height, 100);
                        resultUri = Uri.fromFile(resized);
                    }

                    WritableMap result = getSelection(activity, resultUri, false);
                    resultCollector.notifySuccess(result);
                } catch (Exception ex) {
                    resultCollector.notifyProblem(E_NO_IMAGE_DATA_FOUND, ex.getMessage());
                }
            } else {
                resultCollector.notifyProblem(E_NO_IMAGE_DATA_FOUND, "Cannot find image data");
            }
        } else {
            resultCollector.notifyProblem(E_PICKER_CANCELLED_KEY, E_PICKER_CANCELLED_MSG);
        }
    }

    @Override
    public void onActivityResult(Activity activity, final int requestCode, final int resultCode, final Intent data) {
        if (requestCode == IMAGE_PICKER_REQUEST) {
            imagePickerResult(activity, requestCode, resultCode, data);
        } else if (requestCode == CAMERA_PICKER_REQUEST) {
            cameraPickerResult(activity, requestCode, resultCode, data);
        } else if (requestCode == UCrop.REQUEST_CROP) {
            croppingResult(activity, requestCode, resultCode, data);
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
    }

    private boolean isCameraAvailable(Activity activity) {
        return activity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY);
    }


    private File createImageFile(Activity activity) throws IOException {

        String imageFileName = "image-" + UUID.randomUUID().toString();
        File path = getImageOutputDir(activity, "camera");

        if (!path.exists() && !path.isDirectory()) {
            path.mkdirs();
        }

        File image = File.createTempFile(imageFileName, ".jpg", path);

        // Save a file: path for use with ACTION_VIEW intents
        mCurrentMediaPath = "file:" + image.getAbsolutePath();

        return image;
    }

    private static WritableMap getCroppedRectMap(Intent data) {
        final int DEFAULT_VALUE = -1;
        final WritableMap map = new WritableNativeMap();

        map.putInt("x", data.getIntExtra(UCrop.EXTRA_OUTPUT_OFFSET_X, DEFAULT_VALUE));
        map.putInt("y", data.getIntExtra(UCrop.EXTRA_OUTPUT_OFFSET_Y, DEFAULT_VALUE));
        map.putInt("width", data.getIntExtra(UCrop.EXTRA_OUTPUT_IMAGE_WIDTH, DEFAULT_VALUE));
        map.putInt("height", data.getIntExtra(UCrop.EXTRA_OUTPUT_IMAGE_HEIGHT, DEFAULT_VALUE));

        return map;
    }

    protected void startActivityForResult(Intent intent, int request) {
        StartActivityRef.StartActivityDelegate delegate = StartActivityRef.REF.get();
        if (delegate != null) {
            delegate.startActivityForResult(intent, request);
            return;
        }
        Activity activity = this.reactContext.getCurrentActivity();
        if (activity == null) {
            // throw on purpose for the catch block to reject the promise
            throw new IllegalStateException("getCurrentActivity is null");
        }
        activity.startActivityForResult(intent, request);
    }
}
