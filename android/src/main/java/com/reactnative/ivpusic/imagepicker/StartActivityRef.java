package com.reactnative.ivpusic.imagepicker;

import android.content.Intent;

import java.lang.ref.WeakReference;

/**
 * Customization of the behavior for triggering startActivityForResult
 * Allows for working correctly in FragmentActivity context
 */
public class StartActivityRef {
    private StartActivityRef() {
    }

    public static WeakReference<StartActivityDelegate> REF = new WeakReference<>(null);

    public interface StartActivityDelegate {
        void startActivityForResult(Intent intent, int requestCode);
    }
}
