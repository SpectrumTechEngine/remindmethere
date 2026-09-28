package com.thespectrumtechengine.remindmethere;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * A copy of the call reminders (people and what to mention), kept on the phone so the
 * caller pop-up can read it even when the app is closed. The web app sends a fresh
 * copy (through CallerPlugin.sync) every time places or reminders change.
 *
 * Each entry: { contactName, phones: [..], reminders: [{ text, items: [..] }] }
 */
final class CallerStore {

    private static final String PREFS = "rmt_caller";
    private static final String KEY = "entries";

    private CallerStore() {}

    static void save(Context context, String json) {
        prefs(context).edit().putString(KEY, json).apply();
    }

    static JSONArray load(Context context) {
        try {
            return new JSONArray(prefs(context).getString(KEY, "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    /** Call reminders for whoever owns this phone number. */
    static List<JSONObject> find(Context context, String number) {
        List<JSONObject> matches = new ArrayList<>();
        String wanted = key(number);
        if (wanted.length() < 6) return matches; // hidden or short numbers
        JSONArray entries = load(context);
        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.optJSONObject(i);
            if (entry == null) continue;
            JSONArray phones = entry.optJSONArray("phones");
            if (phones == null) continue;
            for (int j = 0; j < phones.length(); j++) {
                if (wanted.equals(key(phones.optString(j)))) {
                    matches.add(entry);
                    break;
                }
            }
        }
        return matches;
    }

    /**
     * Compares numbers by their last 9 digits, so "087 123 4567", "0871234567" and
     * "+353 87 123 4567" all count as the same number.
     */
    static String key(String number) {
        if (number == null) return "";
        String digits = number.replaceAll("[^0-9]", "");
        return digits.length() > 9 ? digits.substring(digits.length() - 9) : digits;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
