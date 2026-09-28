package com.thespectrumtechengine.remindmethere;

import android.net.Uri;
import android.os.Build;
import android.telecom.Call;
import android.telecom.CallScreeningService;
import android.util.Log;
import java.util.List;
import org.json.JSONObject;

/**
 * Android tells this service about each incoming call once the user has made
 * Remind Me There their "caller ID app". It never blocks, silences or answers calls:
 * it lets every call through and, if the number belongs to a linked contact,
 * shows that contact's reminders.
 */
public class CallerScreeningService extends CallScreeningService {

    private static final String TAG = "RmtCaller";

    @Override
    public void onScreenCall(Call.Details details) {
        // Let the call ring as normal. Android needs an answer within 5 seconds.
        respondToCall(details, new CallResponse.Builder().build());

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    && details.getCallDirection() != Call.Details.DIRECTION_INCOMING) {
                return;
            }
            Uri handle = details.getHandle();
            if (handle == null) return;
            List<JSONObject> matches = CallerStore.find(this, handle.getSchemeSpecificPart());
            if (!matches.isEmpty()) CallerPopup.show(getApplicationContext(), matches);
        } catch (Exception e) {
            Log.e(TAG, "Couldn't show caller reminders", e);
        }
    }
}
