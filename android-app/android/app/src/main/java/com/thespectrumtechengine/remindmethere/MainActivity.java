package com.thespectrumtechengine.remindmethere;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(CallerPlugin.class); // contacts + caller pop-up
        super.onCreate(savedInstanceState);
    }
}
