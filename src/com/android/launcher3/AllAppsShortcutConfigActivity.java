package com.android.launcher3;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.util.Log;



/**
 * Shortcut-config activity for the All Apps launcher entry.
 */
public class AllAppsShortcutConfigActivity extends Activity {

    private static final String TAG = "AllAppsShortcutDbg";
    private static final String SHORTCUT_ID = "open_all_apps";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        String action = intent != null ? intent.getAction() : null;
        Log.e(TAG, "onCreate: action=" + action + " intent=" + intent);

        Intent launchIntent = new Intent(Intent.ACTION_ALL_APPS)
            .setPackage(getPackageName())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        if (Intent.ACTION_CREATE_SHORTCUT.equals(action)) {
            Log.e(TAG, "onCreate: handling ACTION_CREATE_SHORTCUT");

            ShortcutInfo shortcutInfo = new ShortcutInfo.Builder(this, SHORTCUT_ID)
                    .setShortLabel(getString(R.string.all_apps_widget_label))
                    .setLongLabel(getString(R.string.all_apps_widget_label))
                    .setIcon(Icon.createWithResource(this, R.mipmap.all_apps_shortcut_icon))
                    .setActivity(new ComponentName(getPackageName(),
                        AllAppsShortcutConfigActivity.class.getName()))
                    .setIntent(launchIntent)
                    .build();

            ShortcutManager shortcutManager = getSystemService(ShortcutManager.class);
            Intent result = shortcutManager.createShortcutResultIntent(shortcutInfo);
            Log.e(TAG, "onCreate: returning shortcut result=" + result);
            setResult(RESULT_OK, result);
            finish();
            return;
        }

        Log.e(TAG, "onCreate: direct launch fallback");
        startActivity(launchIntent);
        finish();
    }
}