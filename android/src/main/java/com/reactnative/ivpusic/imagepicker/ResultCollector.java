package com.reactnative.ivpusic.imagepicker;

import android.util.Log;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.WritableMap;

/**
 * Created by ipusic on 12/28/16.
 */

class ResultCollector {
    private Promise promise;
    private boolean resultSent;

    synchronized void setup(Promise promise) {
        this.promise = promise;
        this.resultSent = false;
    }

    synchronized private boolean isRequestValid() {
        if (resultSent) {
            Log.w("image-crop-picker", "Skipping result, already sent...");
            return false;
        }

        if (promise == null) {
            Log.w("image-crop-picker", "Trying to notify success but promise is not set");
            return false;
        }

        return true;
    }

    synchronized void notifySuccess(WritableMap result) {
        if (!isRequestValid()) {
            return;
        }
        promise.resolve(result);
        resultSent = true;
    }

    synchronized void notifyProblem(String code, String message) {
        if (!isRequestValid()) {
            return;
        }

        Log.e("image-crop-picker", "Promise rejected. " + message);
        promise.reject(code, message);
        resultSent = true;
    }

    synchronized void notifyProblem(String code, Throwable throwable) {
        if (!isRequestValid()) {
            return;
        }

        Log.e("image-crop-picker", "Promise rejected. " + throwable.getMessage());
        promise.reject(code, throwable);
        resultSent = true;
    }
}
