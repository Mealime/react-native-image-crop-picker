package com.reactnative.ivpusic.imagepicker;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultCaller;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia;
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult;
import androidx.activity.result.contract.ActivityResultContracts.TakePicture;
import androidx.core.content.FileProvider;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeMap;
import com.yalantis.ucrop.UCrop;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

class PickerModuleImpl {
    public static final PickerModuleImpl INSTANCE = new PickerModuleImpl();

    private PickerModuleImpl() {
    }

    ReactContext reactContext;

    private ActivityResultLauncher<PickVisualMediaRequest> pickMedia;
    private ActivityResultCallback<Uri> pickMediaCallback;

    private ActivityResultLauncher<Intent> cropMedia;
    private ActivityResultCallback<ActivityResult> cropMediaCallback;

    private ActivityResultLauncher<Uri> takePicture;
    private ActivityResultCallback<Boolean> takePictureCallback;

    public void register(ActivityResultCaller caller) {
        // ref: https://developer.android.com/training/data-storage/shared/photopicker#select-single-item
        // Registers a photo picker activity launcher in single-select mode.
        pickMedia =
            caller.registerForActivityResult(new PickVisualMedia(), uri -> {
                if (pickMediaCallback != null) {
                    pickMediaCallback.onActivityResult(uri);
                }
            });

        takePicture = caller.registerForActivityResult(new TakePicture(), result -> {
            if (takePictureCallback != null) {
                takePictureCallback.onActivityResult(result);
            }
        });

        cropMedia = caller.registerForActivityResult(new StartActivityForResult(), result -> {
            if (cropMediaCallback != null) {
                cropMediaCallback.onActivityResult(result);
            }
        });
    }


    public void openPicker(Promise promise, Config config) {
        // Launch the photo picker and let the user choose images and videos.
        pickMediaCallback = uri -> {
            if (uri != null) {
                openCropper(uri, promise, config);
            } else {
                promise.reject("NO_IMAGE_SELECTED", "An image was not selected");
            }
        };

        // Android Studio reports an error, but it's not a real error! Code compiles just fine
        // ref: https://stackoverflow.com/a/74000104
        // Fix is to update to androidx.activity 1.7.0, but we can't do that yet so we are stuck with this
        PickVisualMedia.VisualMediaType mediaType =
            (PickVisualMedia.VisualMediaType) PickVisualMedia.ImageOnly.INSTANCE;
        pickMedia.launch(new PickVisualMediaRequest.Builder()
            .setMediaType(mediaType)
            .build());
    }


    public void openCamera(Promise promise, Config config) {
        try {
            File output = createTempImage();
            Uri outputUri = Uri.fromFile(output);
            String uriAuthority = outputUri.getAuthority();
            String authority = reactContext.getApplicationInfo().packageName + ".rnicp.provider";
            Uri extUri = FileProvider.getUriForFile(reactContext, authority, output);

            takePictureCallback = success -> openCropper(outputUri, promise, config);
            takePicture.launch(extUri);
        } catch (IOException ex) {
            promise.reject("IOException", ex.getMessage());
        } catch (Exception ex){
            promise.reject("Exception", ex.getMessage());
        }

    }

    private File createTempImage() throws IOException {
        return File.createTempFile(UUID.randomUUID().toString(), ".jpg", reactContext.getExternalMediaDirs()[0]);
    }

    private void openCropper(Uri uri, Promise promise, Config config) {
        UCrop.Options options = new UCrop.Options();
        options.setCompressionFormat(Bitmap.CompressFormat.JPEG);
        options.setCompressionQuality(100);
        options.setCircleDimmedLayer(false);
        options.setFreeStyleCropEnabled(false);
        options.setShowCropGrid(true);
        options.setShowCropFrame(true);
        options.setHideBottomControls(true);

        try {
            File output = createTempImage();
            String outputPath = Uri.fromFile(output).toString();
            UCrop uCrop = UCrop
                .of(uri, Uri.fromFile(output))
                .withOptions(options);

            if (config.width > 0 && config.height > 0) {
                uCrop.withAspectRatio(config.width, config.height);
                uCrop.withMaxResultSize(config.width, config.height);
            }

            cropMediaCallback = result -> {
                if (result.getData() == null) {
                    promise.reject("CROP_CANCELED", "Crop was closed without a result");
                    return;
                }
                WritableMap map = new WritableNativeMap();
                map.putString("path", outputPath);
                map.putInt("width", result.getData().getIntExtra(UCrop.EXTRA_OUTPUT_IMAGE_WIDTH, -1));
                map.putInt("height", result.getData().getIntExtra(UCrop.EXTRA_OUTPUT_IMAGE_HEIGHT, -1));
                promise.resolve(map);
            };
            cropMedia.launch(uCrop.getIntent(reactContext));
        } catch (IOException ex) {
            promise.reject("IOException", ex.getMessage());
        }


    }

}
