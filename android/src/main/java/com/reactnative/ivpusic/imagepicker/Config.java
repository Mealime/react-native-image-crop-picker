package com.reactnative.ivpusic.imagepicker;

import com.facebook.react.bridge.ReadableMap;

public class Config {

    public final int width;
    public final int height;

    public Config(final ReadableMap options) {
        width = options.hasKey("width") ? options.getInt("width") : 0;
        height = options.hasKey("height") ? options.getInt("height") : 0;
    }
}
