/**
 * An Image Picker Plugin for Cordova/PhoneGap.
 */
package com.synconset;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;

import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;

import android.app.Activity;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;

public class ImagePicker extends CordovaPlugin {

    private static final String ACTION_GET_PICTURES = "getPictures";
    private static final String ACTION_HAS_READ_PERMISSION = "hasReadPermission";
    private static final String ACTION_REQUEST_READ_PERMISSION = "requestReadPermission";

    private CallbackContext callbackContext;

    public boolean execute(String action, final JSONArray args, final CallbackContext callbackContext) throws JSONException {
        this.callbackContext = callbackContext;

        if (ACTION_HAS_READ_PERMISSION.equals(action)) {
            // Android Photo Picker requires no permission
            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, true));
            return true;

        } else if (ACTION_REQUEST_READ_PERMISSION.equals(action)) {
            // Android Photo Picker requires no permission
            callbackContext.success();
            return true;

        } else if (ACTION_GET_PICTURES.equals(action)) {
            final JSONObject params = args.getJSONObject(0);
            int max = 20;
            if (params.has("maximumImagesCount")) {
                max = params.getInt("maximumImagesCount");
            }

            final Intent intent;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 13+: use the system Photo Picker — no READ_MEDIA_IMAGES needed
                intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
                if (max > 1) {
                    intent.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, Math.min(max, MediaStore.getPickImagesMaxLimit()));
                }
            } else {
                // Android < 13: ACTION_GET_CONTENT with multi-select — no storage permission needed
                intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                if (max > 1) {
                    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                }
            }

            cordova.startActivityForResult(this, intent, 0);
            return true;
        }
        return false;
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK && data != null) {
            JSONArray res = new JSONArray();
            ArrayList<Uri> selectedUris = new ArrayList<>();

            ClipData clipData = data.getClipData();
            if (clipData != null) {
                for (int i = 0; i < clipData.getItemCount(); i++) {
                    selectedUris.add(clipData.getItemAt(i).getUri());
                }
            } else if (data.getData() != null) {
                selectedUris.add(data.getData());
            }

            for (Uri uri : selectedUris) {
                String filePath = copyContentToTempFile(uri);
                if (filePath != null) {
                    try {
                        res.put(filePath);
                    } catch (JSONException e) {
                        // skip this file
                    }
                }
            }

            callbackContext.success(res);

        } else if (resultCode == Activity.RESULT_CANCELED) {
            callbackContext.success(new JSONArray());
        } else {
            callbackContext.error("No images selected");
        }
    }

    private String copyContentToTempFile(Uri contentUri) {
        try {
            ContentResolver resolver = cordova.getActivity().getContentResolver();
            String mimeType = resolver.getType(contentUri);
            String extension = (mimeType != null && mimeType.endsWith("png")) ? ".png" : ".jpg";
            File cacheDir = cordova.getActivity().getCacheDir();
            File tempFile = File.createTempFile("img_picker_", extension, cacheDir);
            try (InputStream in = resolver.openInputStream(contentUri);
                 FileOutputStream out = new FileOutputStream(tempFile)) {
                if (in == null) {
                    return null;
                }
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }
            return Uri.fromFile(tempFile).toString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Choosing a picture launches another Activity, so we need to implement the
     * save/restore APIs to handle the case where the CordovaActivity is killed by the OS
     * before we get the launched Activity's result.
     *
     * @see http://cordova.apache.org/docs/en/dev/guide/platforms/android/plugin.html#launching-other-activities
     */
    public void onRestoreStateForActivityResult(Bundle state, CallbackContext callbackContext) {
        this.callbackContext = callbackContext;
    }
}
