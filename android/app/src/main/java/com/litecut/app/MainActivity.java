package com.litecut.app;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;
import com.litecut.app.slowmo.SmoothSlowMotionPlugin;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(SmoothSlowMotionPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
