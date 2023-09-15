package com.reactnative.ivpusic.imagepicker;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;

import androidx.activity.result.ActivityResultCaller;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;



class PickerModule extends ReactContextBaseJavaModule implements ActivityEventListener {

    PickerModule(ReactApplicationContext reactContext) {
        super(reactContext);
        reactContext.addActivityEventListener(this);
        PickerModuleImpl.INSTANCE.reactContext = reactContext;
    }

    @Override
    public String getName() {
        return "ImageCropPicker";
    }


    @ReactMethod
    public void clean(final Promise promise) {
    }

    @ReactMethod
    public void cleanSingle(final String pathToDelete, final Promise promise) {

    }

    @ReactMethod
    public void openCamera(final ReadableMap options, final Promise promise) {

    }


    @ReactMethod
    public void openPicker(final ReadableMap options, final Promise promise) {
        PickerModuleImpl.INSTANCE.openPicker(promise, new Config(options));
    }


    @ReactMethod
    public void openCropper(final ReadableMap options, final Promise promise) {

    }

    @Override
    public void onActivityResult(Activity activity, int requestCode, int resultCode, @Nullable Intent data) {

    }

    @Override
    public void onNewIntent(Intent intent) {

    }
}
