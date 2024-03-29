/*
 *  Copyright 2016 Google Inc. All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.google.android.apps.forscience.whistlepunk;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.support.annotation.IntDef;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.SparseArray;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Static permission functions shared across many parts of the app.
 */
public class PermissionUtils {
    public static final String TAG = "PermissionUtils";

    @IntDef({REQUEST_WRITE_EXTERNAL_STORAGE,
            REQUEST_CAMERA,
            REQUEST_RECORD_AUDIO,
            REQUEST_ACCESS_COARSE_LOCATION})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Requests{}

    static final int REQUEST_WRITE_EXTERNAL_STORAGE = 0;
    public static final int REQUEST_CAMERA = 1;
    static final int REQUEST_RECORD_AUDIO = 2;
    public static final int REQUEST_ACCESS_COARSE_LOCATION = 3;

    @IntDef({DENIED,
            GRANTED,
            PERMANENTLY_DENIED})
    @Retention(RetentionPolicy.SOURCE)
    @interface PermissionState{}

    static final int DENIED = 0;
    static final int GRANTED = 1;
    static final int PERMANENTLY_DENIED = 2;

    private static final String[] PERMISSIONS = {
            Manifest.permission.WRITE_EXTERNAL_STORAGE, // 0
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA,                 // 1
            Manifest.permission.RECORD_AUDIO,           // 2
            Manifest.permission.ACCESS_COARSE_LOCATION  // 3
    };

    public interface PermissionListener {
        void onPermissionGranted();
        void onPermissionDenied();
        void onPermissionPermanentlyDenied();
    }

    private static SparseArray<PermissionListener> mPermissionListeners = new SparseArray<>();

    public static void tryRequestingPermission(Activity activity, @Requests int permission,
            PermissionListener listener) {
        if (hasPermission(activity, permission)) {
            listener.onPermissionGranted();
            return;
        }
        mPermissionListeners.put(permission, listener);
        ActivityCompat.requestPermissions(activity, new String[]{PERMISSIONS[permission]},
                permission);
    }

    public static boolean hasPermission(Context context, @Requests int permission) {
        return hasPermission(context, PERMISSIONS[permission]);
    }

    private static boolean hasPermission(Context context, String permission) {
        int permissionCheck = ContextCompat.checkSelfPermission(context, permission);
        return permissionCheck == PackageManager.PERMISSION_GRANTED;
    }

    public static void onRequestPermissionsResult(Activity activity,
            @Requests int requestCode, String permissions[], int[] grantResults) {
        PermissionListener permissionListener = mPermissionListeners.get(requestCode);
        if (permissionListener == null || permissions.length == 0 || grantResults.length == 0) {
            return;
        }
        final boolean granted = grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (granted) {
            permissionListener.onPermissionGranted();
        } else if (ActivityCompat.shouldShowRequestPermissionRationale(activity, permissions[0])) {
            permissionListener.onPermissionDenied();
        } else {
            // If the permission was denied AND we shouldn't show the user more information about
            // why we want the permission, then they have permanently denied it.
            permissionListener.onPermissionPermanentlyDenied();
        }
        mPermissionListeners.remove(requestCode);
    }
}
