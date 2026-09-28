package com.thespectrumtechengine.remindmethere;

import android.Manifest;
import android.app.Activity;
import android.app.role.RoleManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.Settings;
import androidx.activity.result.ActivityResult;
import androidx.core.app.NotificationManagerCompat;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Lets the web app use the phone's contacts and set up the caller pop-up.
 * From JavaScript: Capacitor.registerPlugin("RmtCaller").
 */
@CapacitorPlugin(
    name = "RmtCaller",
    permissions = {
        @Permission(alias = "contacts", strings = { Manifest.permission.READ_CONTACTS }),
        @Permission(alias = "notifications", strings = { Manifest.permission.POST_NOTIFICATIONS })
    }
)
public class CallerPlugin extends Plugin {

    /* ---------- Contact picker ---------- */

    @PluginMethod
    public void pickContact(PluginCall call) {
        if (getPermissionState("contacts") != PermissionState.GRANTED) {
            requestPermissionForAlias("contacts", call, "contactsForPicker");
            return;
        }
        openPicker(call);
    }

    @PermissionCallback
    private void contactsForPicker(PluginCall call) {
        if (getPermissionState("contacts") == PermissionState.GRANTED) openPicker(call);
        else call.reject("Contacts permission was not given", "DENIED");
    }

    private void openPicker(PluginCall call) {
        Intent pick = new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI);
        startActivityForResult(call, pick, "contactPicked");
    }

    @ActivityCallback
    private void contactPicked(PluginCall call, ActivityResult result) {
        if (call == null) return;
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) {
            JSObject r = new JSObject();
            r.put("cancelled", true);
            call.resolve(r);
            return;
        }
        try {
            call.resolve(readContact(result.getData().getData()));
        } catch (Exception e) {
            call.reject("Couldn't read that contact", e);
        }
    }

    private JSObject readContact(Uri contactUri) {
        ContentResolver cr = getContext().getContentResolver();
        String id = null, name = "";
        try (Cursor c = cr.query(contactUri, new String[] { ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME }, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                id = c.getString(0);
                name = c.getString(1) == null ? "" : c.getString(1);
            }
        }
        JSObject out = new JSObject();
        out.put("name", name);
        JSArray phones = new JSArray();
        List<String> seen = new ArrayList<>();
        if (id != null) {
            try (Cursor c = cr.query(Phone.CONTENT_URI, new String[] { Phone.NUMBER }, Phone.CONTACT_ID + "=?", new String[] { id }, null)) {
                while (c != null && c.moveToNext()) {
                    String n = c.getString(0);
                    String k = CallerStore.key(n);
                    if (n != null && !k.isEmpty() && !seen.contains(k)) {
                        seen.add(k);
                        phones.put(n);
                    }
                }
            }
            try (Cursor c = cr.query(StructuredPostal.CONTENT_URI,
                    new String[] { StructuredPostal.STREET, StructuredPostal.NEIGHBORHOOD, StructuredPostal.CITY,
                        StructuredPostal.REGION, StructuredPostal.POSTCODE, StructuredPostal.COUNTRY, StructuredPostal.FORMATTED_ADDRESS },
                    StructuredPostal.CONTACT_ID + "=?", new String[] { id }, null)) {
                if (c != null && c.moveToFirst()) {
                    // Same shape as the browser's Contact Picker, so the web code handles both
                    JSObject a = new JSObject();
                    String street = c.getString(0);
                    if (street == null || street.trim().isEmpty()) street = c.getString(6);
                    JSArray lines = new JSArray();
                    if (street != null) for (String l : street.split("\\n")) if (!l.trim().isEmpty()) lines.put(l.trim());
                    a.put("addressLine", lines);
                    a.put("dependentLocality", c.getString(1));
                    a.put("city", c.getString(2));
                    a.put("region", c.getString(3));
                    a.put("postalCode", c.getString(4));
                    a.put("country", c.getString(5));
                    out.put("address", a);
                }
            }
        }
        out.put("phones", phones);
        return out;
    }

    /* ---------- Reminders for the caller pop-up ---------- */

    @PluginMethod
    public void sync(PluginCall call) {
        String json = call.getString("json", "[]");
        try {
            new JSONArray(json); // make sure it's valid before saving
        } catch (Exception e) {
            call.reject("Bad reminder data");
            return;
        }
        CallerStore.save(getContext(), json);
        call.resolve();
    }

    @PluginMethod
    public void testPopup(PluginCall call) {
        JSONArray all = CallerStore.load(getContext());
        if (all.length() == 0) {
            call.reject("Link a place to a contact first", "NO_CONTACTS");
            return;
        }
        List<JSONObject> first = new ArrayList<>();
        first.add(all.optJSONObject(0));
        CallerPopup.show(getContext(), first);
        call.resolve();
    }

    /* ---------- Setup and status ---------- */

    @PluginMethod
    public void status(PluginCall call) {
        call.resolve(statusObject());
    }

    private JSObject statusObject() {
        Context ctx = getContext();
        JSObject s = new JSObject();
        s.put("supported", Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q);
        s.put("contacts", getPermissionState("contacts") == PermissionState.GRANTED);
        s.put("screening", isScreeningApp());
        s.put("overlay", Settings.canDrawOverlays(ctx));
        s.put("notifications", NotificationManagerCompat.from(ctx).areNotificationsEnabled());
        s.put("xiaomi", isXiaomi());
        s.put("linked", CallerStore.load(ctx).length());
        return s;
    }

    private boolean isScreeningApp() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false;
        RoleManager rm = getContext().getSystemService(RoleManager.class);
        return rm != null && rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) && rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING);
    }

    private static boolean isXiaomi() {
        String m = (Build.MANUFACTURER + " " + Build.BRAND).toLowerCase();
        return m.contains("xiaomi") || m.contains("redmi") || m.contains("poco");
    }

    @PluginMethod
    public void requestContacts(PluginCall call) {
        if (getPermissionState("contacts") == PermissionState.GRANTED) call.resolve(statusObject());
        else requestPermissionForAlias("contacts", call, "afterPermission");
    }

    @PluginMethod
    public void requestNotifications(PluginCall call) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || getPermissionState("notifications") == PermissionState.GRANTED) {
            call.resolve(statusObject());
        } else {
            requestPermissionForAlias("notifications", call, "afterPermission");
        }
    }

    @PermissionCallback
    private void afterPermission(PluginCall call) {
        call.resolve(statusObject());
    }

    @PluginMethod
    public void requestScreening(PluginCall call) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            call.reject("Needs Android 10 or newer", "UNSUPPORTED");
            return;
        }
        RoleManager rm = getContext().getSystemService(RoleManager.class);
        if (rm == null || !rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
            call.reject("This phone doesn't let apps see incoming calls", "UNSUPPORTED");
            return;
        }
        if (rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
            call.resolve(statusObject());
            return;
        }
        startActivityForResult(call, rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING), "afterRole");
    }

    @ActivityCallback
    private void afterRole(PluginCall call, ActivityResult result) {
        if (call == null) return;
        JSObject s = statusObject();
        // Some phones never show Android's question (or stop asking after a "no").
        // Open Default apps so the user can pick "Caller ID & spam app" themselves.
        if (!s.optBoolean("screening")) {
            try {
                getActivity().startActivity(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS));
                s.put("openedDefaultApps", true);
            } catch (Exception ignored) {
            }
        }
        call.resolve(s);
    }

    @PluginMethod
    public void openOverlaySettings(PluginCall call) {
        Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getContext().getPackageName()));
        getActivity().startActivity(i);
        call.resolve();
    }

    /** Xiaomi's own permission screen, where "pop-up windows while running in the background" lives. */
    @PluginMethod
    public void openPhonePermissions(PluginCall call) {
        String pkg = getContext().getPackageName();
        Intent miui = new Intent("miui.intent.action.APP_PERM_EDITOR")
            .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
            .putExtra("extra_pkgname", pkg);
        try {
            getActivity().startActivity(miui);
        } catch (Exception e) {
            getActivity().startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg)));
        }
        call.resolve();
    }
}
