package com.synconset;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
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

import java.util.ArrayList;

public class ImagePicker extends CordovaPlugin {

    private static final String TAG = "ImagePickerPlugin";
    private static final String ACTION_GET_PICTURES = "getPictures";
    private static final String ACTION_HAS_READ_PERMISSION = "hasReadPermission";
    private static final String ACTION_REQUEST_READ_PERMISSION = "requestReadPermission";
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int REQUEST_PICK_IMAGE = 1;
    private static final int REQUEST_MULTI_IMAGE_CHOOSER = 2;

    private CallbackContext callbackContext;

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        this.callbackContext = callbackContext;

        switch (action) {
            case ACTION_HAS_READ_PERMISSION:
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, hasReadPermission()));
                return true;

            case ACTION_REQUEST_READ_PERMISSION:
                requestReadPermission();
                return true;

            case ACTION_GET_PICTURES:
                return handleGetPictures(args);

            default:
                return false;
        }
    }

    private boolean handleGetPictures(JSONArray args) throws JSONException {
        if (!hasReadPermission()) {
            requestReadPermission();
            return true;
        }

        try {
            // Use system gallery picker first
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            intent.setType("image/*");

            if (intent.resolveActivity(cordova.getActivity().getPackageManager()) != null) {
                cordova.startActivityForResult(this, Intent.createChooser(intent, "Select Picture"), REQUEST_PICK_IMAGE);
            } else {
                Log.w(TAG, "System image picker not available. Falling back to MultiImageChooserActivity.");
                fallbackToLegacyPicker(args.getJSONObject(0));
            }

        } catch (Exception e) {
            Log.e(TAG, "System picker error: " + e.getMessage() + ", falling back.");
            fallbackToLegacyPicker(args.getJSONObject(0));
        }

        return true;
    }

    private void fallbackToLegacyPicker(JSONObject params) throws JSONException {
        Intent intent = new Intent(cordova.getActivity(), MultiImageChooserActivity.class);

        intent.putExtra("MAX_IMAGES", params.optInt("maximumImagesCount", 20));
        intent.putExtra("WIDTH", params.optInt("width", 0));
        intent.putExtra("HEIGHT", params.optInt("height", 0));
        intent.putExtra("QUALITY", params.optInt("quality", 100));
        intent.putExtra("OUTPUT_TYPE", params.optInt("outputType", 0));

        cordova.startActivityForResult(this, intent, REQUEST_MULTI_IMAGE_CHOOSER);
    }

    @SuppressLint("InlinedApi")
    private boolean hasReadPermission() {
        Activity activity = cordova.getActivity();
        return Build.VERSION.SDK_INT < 23 ||
                ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressLint("InlinedApi")
    private void requestReadPermission() {
        Activity activity = cordova.getActivity();
        if (Build.VERSION.SDK_INT < 33) {
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    PERMISSION_REQUEST_CODE);
        } else {
            cordova.requestPermissions(this, PERMISSION_REQUEST_CODE,
                    new String[]{Manifest.permission.READ_MEDIA_IMAGES});
        }
        callbackContext.success(); // Must re-trigger from JS
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == REQUEST_PICK_IMAGE) {
            handleSystemImagePickerResult(resultCode, intent);
        } else if (requestCode == REQUEST_MULTI_IMAGE_CHOOSER) {
            handleLegacyPickerResult(resultCode, intent);
        }
    }

    private void handleSystemImagePickerResult(int resultCode, Intent intent) {
        if (resultCode == Activity.RESULT_OK && intent != null) {
            ArrayList<String> results = new ArrayList<>();
            if (intent.getClipData() != null) {
                int count = intent.getClipData().getItemCount();
                for (int i = 0; i < count; i++) {
                    Uri imageUri = intent.getClipData().getItemAt(i).getUri();
                    results.add(imageUri.toString());
                }
            } else if (intent.getData() != null) {
                results.add(intent.getData().toString());
            }

            callbackContext.success(new JSONArray(results));
        } else {
            callbackContext.error("Image picking cancelled or failed");
        }
    }

    private void handleLegacyPickerResult(int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK && data != null) {
            int sync = data.getIntExtra("bigdata:synccode", -1);
            final Bundle bigData = ResultIPC.get().getLargeData(sync);
            ArrayList<String> fileNames = bigData.getStringArrayList("MULTIPLEFILENAMES");
            callbackContext.success(new JSONArray(fileNames));

        } else if (resultCode == Activity.RESULT_CANCELED && data != null) {
            callbackContext.error(data.getStringExtra("ERRORMESSAGE"));

        } else if (resultCode == Activity.RESULT_CANCELED) {
            callbackContext.success(new JSONArray());
        } else {
            callbackContext.error("No images selected");
        }
    }

    @Override
    public void onRestoreStateForActivityResult(Bundle state, CallbackContext callbackContext) {
        this.callbackContext = callbackContext;
    }
}
