package com.example.grouptaskmanager.utils;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.example.grouptaskmanager.R;

public class NotificationPermissionHelper {

    private static final String TAG = "NotificationPermissionHelper";
    private static final String NOTIFICATION_PERMISSION = Manifest.permission.POST_NOTIFICATIONS;

    private FragmentActivity activity;
    private ActivityResultLauncher<String> permissionLauncher;
    private NotificationPermissionCallback callback;

    public NotificationPermissionHelper(FragmentActivity activity) {
        this.activity = activity;
        setupPermissionLauncher();
    }

    private void setupPermissionLauncher() {
        permissionLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (callback != null) {
                        if (isGranted) {
                            Log.d(TAG, "Notification permission granted");
                            callback.onPermissionGranted();
                        } else {
                            Log.d(TAG, "Notification permission denied");
                            callback.onPermissionDenied();
                        }
                    }
                }
        );
    }

    /**
     * Kiểm tra xem có quyền thông báo không
     */
    public static boolean hasNotificationPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(context, NOTIFICATION_PERMISSION) 
                   == PackageManager.PERMISSION_GRANTED;
        }
        // Trước Android 13 không cần permission
        return true;
    }

    /**
     * Request notification permission với callback
     */
    public void requestNotificationPermission(NotificationPermissionCallback callback) {
        this.callback = callback;

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            // Trước Android 13 không cần permission
            if (callback != null) {
                callback.onPermissionGranted();
            }
            return;
        }

        if (hasNotificationPermission(activity)) {
            // Đã có permission
            if (callback != null) {
                callback.onPermissionGranted();
            }
            return;
        }

        // Check if we should show rationale
        if (activity.shouldShowRequestPermissionRationale(NOTIFICATION_PERMISSION)) {
            showPermissionRationale();
        } else {
            // Request permission directly
            permissionLauncher.launch(NOTIFICATION_PERMISSION);
        }
    }

    /**
     * Request permission without callback - cho các trường hợp không cần xử lý kết quả
     */
    public void requestNotificationPermissionSilent() {
        requestNotificationPermission(new NotificationPermissionCallback() {
            @Override
            public void onPermissionGranted() {
                Log.d(TAG, "Notification permission granted silently");
            }

            @Override
            public void onPermissionDenied() {
                Log.d(TAG, "Notification permission denied silently");
            }
        });
    }

    /**
     * Hiển thị dialog giải thích tại sao cần quyền thông báo
     */
    private void showPermissionRationale() {
        new AlertDialog.Builder(activity)
                .setTitle("Quyền thông báo")
                .setMessage("Ứng dụng cần quyền thông báo để:\n\n" +
                           "• Thông báo tin nhắn mới trong nhóm\n" +
                           "• Thông báo khi được giao nhiệm vụ mới\n" +
                           "• Nhắc nhở về hạn chót nhiệm vụ\n\n" +
                           "Bạn có muốn cấp quyền không?")
                .setPositiveButton("Cấp quyền", (dialog, which) -> {
                    permissionLauncher.launch(NOTIFICATION_PERMISSION);
                })
                .setNegativeButton("Không", (dialog, which) -> {
                    if (callback != null) {
                        callback.onPermissionDenied();
                    }
                })
                .setCancelable(false)
                .show();
    }

    /**
     * Hiển thị dialog hướng dẫn bật thông báo trong Settings
     */
    public void showNotificationDisabledDialog() {
        new AlertDialog.Builder(activity)
                .setTitle("Thông báo bị tắt")
                .setMessage("Để nhận thông báo về tin nhắn mới và nhiệm vụ được giao, " +
                           "vui lòng bật thông báo trong Cài đặt ứng dụng.")
                .setPositiveButton("Mở Cài đặt", (dialog, which) -> {
                    PermissionUtils.openAppSettings(activity);
                })
                .setNegativeButton("Để sau", (dialog, which) -> dialog.dismiss())
                .show();
    }

    /**
     * Interface cho callback khi request permission
     */
    public interface NotificationPermissionCallback {
        void onPermissionGranted();
        void onPermissionDenied();
    }
} 