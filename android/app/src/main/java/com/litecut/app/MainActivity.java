package com.litecut.app;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;
import com.litecut.app.slowmo.SmoothSlowMotionPlugin;
import com.litecut.app.export.VideoExportPlugin;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(SmoothSlowMotionPlugin.class);
        registerPlugin(VideoExportPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
