package com.synconset;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Window;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaWebView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class ImagePicker extends CordovaPlugin {
    private static final String TAG = "ImagePickerPlugin";

    private CallbackContext callbackContext;
    private static final int PICK_IMAGE_REQUEST = 1;

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        this.callbackContext = callbackContext;

        if (action.equals("getPictures")) {
            pickImages();
            return true;
        }

        return false;
    }

    private void pickImages() {
        disableEdgeToEdge();

        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.setType("image/*");

        cordova.startActivityForResult(this, Intent.createChooser(intent, "Select Picture"), PICK_IMAGE_REQUEST);
    }

    private void disableEdgeToEdge() {
        cordova.getActivity().runOnUiThread(() -> {
            try {
                Activity activity = cordova.getActivity();
                if (activity != null && activity.getWindow() != null) {
                    Log.d(TAG, "Disabling edge-to-edge before image picker...");

                    Window window = activity.getWindow();

                    // Disable edge-to-edge mode
                    WindowCompat.setDecorFitsSystemWindows(window, true);

                    // Show system bars if hidden
                    WindowInsetsControllerCompat insetsController =
                            new WindowInsetsControllerCompat(window, window.getDecorView());
                    insetsController.show(WindowInsetsCompat.Type.systemBars());
                } else {
                    Log.w(TAG, "Activity or window is null in disableEdgeToEdge()");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error disabling edge-to-edge: " + e.getMessage());
            }
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == PICK_IMAGE_REQUEST) {
            if (resultCode == Activity.RESULT_OK && intent != null) {
                ArrayList<String> results = new ArrayList<>();

                if (intent.getClipData() != null) {
                    int count = intent.getClipData().getItemCount();
                    for (int i = 0; i < count; i++) {
                        Uri imageUri = intent.getClipData().getItemAt(i).getUri();
                        results.add(imageUri.toString());
                    }
                } else if (intent.getData() != null) {
                    Uri imageUri = intent.getData();
                    results.add(imageUri.toString());
                }

                JSONArray jsonResults = new JSONArray(results);
                callbackContext.success(jsonResults);
            } else {
                callbackContext.error("Image picking cancelled or failed");
            }
        }
    }
}
