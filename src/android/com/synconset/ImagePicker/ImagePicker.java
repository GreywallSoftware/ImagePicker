package com.synconset;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.util.ArrayList;

import android.os.Bundle;

public class ImagePicker extends CordovaPlugin {
    private static final String TAG = "ImagePicker";
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final int REQUEST_PICK_IMAGE = 1;
    private static final int REQUEST_MULTI_PICK = 2;

    private CallbackContext callbackContext;
    private JSONObject params;

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        this.callbackContext = callbackContext;
        if ("getPictures".equals(action)) {
            params = args.optJSONObject(0);
            if (!hasGalleryPermissions()) {
                requestGalleryPermissions();
            } else {
                launchPickFlow();
            }
            return true;
        }
        return false;
    }

    private boolean hasGalleryPermissions() {
        Activity activity = cordova.getActivity();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_MEDIA_IMAGES)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestGalleryPermissions() {
        Activity activity = cordova.getActivity();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            cordova.requestPermissions(this, PERMISSION_REQUEST_CODE,
                    new String[]{Manifest.permission.READ_MEDIA_IMAGES});
        } else {
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    PERMISSION_REQUEST_CODE);
        }
        callbackContext.success();
    }

    @Override
    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) throws JSONException {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launchPickFlow();
            } else {
                callbackContext.error("Permission to access gallery denied");
            }
        }
    }

    private void launchPickFlow() {
        try {
            Intent i = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            i.setType("image/*");

            if (i.resolveActivity(cordova.getActivity().getPackageManager()) != null) {
                cordova.startActivityForResult(this, Intent.createChooser(i, "Select Pictures"), REQUEST_PICK_IMAGE);
            } else {
                fallbackToLegacy(params);
            }
        } catch (Exception e) {
            Log.w(TAG, "System picker failed: " + e.getMessage());
            fallbackToLegacy(this.params);
        }
    }

    private void fallbackToLegacy(JSONObject params) {
        Intent intent = new Intent(cordova.getActivity(), MultiImageChooserActivity.class);
        intent.putExtra("MAX_IMAGES", params.optInt("maximumImagesCount", 20));
        intent.putExtra("WIDTH", params.optInt("width", 0));
        intent.putExtra("HEIGHT", params.optInt("height", 0));
        intent.putExtra("QUALITY", params.optInt("quality", 100));
        intent.putExtra("OUTPUT_TYPE", params.optInt("outputType", 0));
        cordova.startActivityForResult(this, intent, REQUEST_MULTI_PICK);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == REQUEST_PICK_IMAGE) {
            handleSystemResult(resultCode, intent);
        } else if (requestCode == REQUEST_MULTI_PICK) {
            handleLegacyResult(resultCode, intent);
        }
    }

    private void handleSystemResult(int resultCode, Intent intent) {
        if (resultCode == Activity.RESULT_OK && intent != null) {
            JSONArray out = new JSONArray();
            if (intent.getClipData() != null) {
                int count = intent.getClipData().getItemCount();
                for (int i = 0; i < count; i++) {
                    Uri uri = intent.getClipData().getItemAt(i).getUri();
                    out.put(buildFileInfo(uri));
                }
            } else if (intent.getData() != null) {
                out.put(buildFileInfo(intent.getData()));
            }
            callbackContext.success(out);
        } else {
            callbackContext.error("Image picking cancelled or failed");
        }
    }

    private void handleLegacyResult(int resultCode, Intent intent) {
        if (resultCode == Activity.RESULT_OK && intent != null) {
            int sync = intent.getIntExtra("bigdata:synccode", -1);
            final Bundle big = ResultIPC.get().getLargeData(sync);
            ArrayList<String> names = big.getStringArrayList("MULTIPLEFILENAMES");
            JSONArray out = new JSONArray(names);
            callbackContext.success(out);
        } else if (resultCode == Activity.RESULT_CANCELED) {
            callbackContext.success(new JSONArray());
        } else {
            callbackContext.error("Image picking cancelled or failed");
        }
    }

    private JSONObject buildFileInfo(Uri uri) {
        JSONObject info = new JSONObject();
        try {
            String displayName = "unknown";
            String copiedPath = null;

            ContentResolver resolver = cordova.getActivity().getContentResolver();
            String[] proj = { MediaStore.Images.Media.DISPLAY_NAME };
            try (Cursor cursor = resolver.query(uri, proj, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int ni = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME);
                    if (ni != -1) displayName = cursor.getString(ni);
                }
            }

            copiedPath = copyToCache(uri, displayName);

            info.put("uri", uri.toString());
            info.put("name", displayName);
            info.put("path", copiedPath != null ? copiedPath : JSONObject.NULL);
        } catch (Exception e) {
            Log.e(TAG, "Error building file info", e);
        }
        return info;
    }

    private String copyToCache(Uri uri, String displayName) {
        try (InputStream in = cordova.getActivity().getContentResolver().openInputStream(uri)) {
            if (in == null) return null;
            File dst = new File(cordova.getActivity().getCacheDir(),
                    System.currentTimeMillis() + "_" + displayName);
            try (OutputStream out = new FileOutputStream(dst)) {
                byte[] buf = new byte[4096];
                int r;
                while ((r = in.read(buf)) != -1) out.write(buf, 0, r);
            }
            return dst.getAbsolutePath();
        } catch (IOException e) {
            Log.e(TAG, "Error copying URI to cache", e);
            return null;
        }
    }
}
