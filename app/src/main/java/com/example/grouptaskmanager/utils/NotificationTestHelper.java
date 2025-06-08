package com.example.grouptaskmanager.utils;

import android.content.Context;
import android.util.Log;

import com.example.grouptaskmanager.notification.ChatNotificationHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

/**
 * Helper class để test và debug hệ thống thông báo
 */
public class NotificationTestHelper {
    private static final String TAG = "NotificationTestHelper";
    
    /**
     * Test tạo notification trực tiếp
     */
    public static void createTestNotification(Context context, String groupId, String groupName) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "User not logged in");
            return;
        }
        
        Map<String, Object> testNotification = new HashMap<>();
        testNotification.put("type", "chat_message");
        testNotification.put("groupId", groupId);
        testNotification.put("groupName", groupName);
        testNotification.put("senderName", "Test Sender");
        testNotification.put("message", "Đây là tin nhắn test");
        testNotification.put("senderId", "test_sender_id");
        testNotification.put("timestamp", System.currentTimeMillis());
        
        FirebaseFirestore.getInstance()
                .collection("users")
                .document(currentUser.getUid())
                .collection("notifications")
                .add(testNotification)
                .addOnSuccessListener(docRef -> {
                    Log.d(TAG, "Test notification created: " + docRef.getId());
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error creating test notification", e);
                });
    }
    
    /**
     * Kiểm tra trạng thái notification listener
     */
    public static void checkNotificationSystem(Context context) {
        Log.d(TAG, "=== NOTIFICATION SYSTEM STATUS ===");
        
        // Check user login
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        Log.d(TAG, "User logged in: " + (currentUser != null));
        if (currentUser != null) {
            Log.d(TAG, "User ID: " + currentUser.getUid());
            Log.d(TAG, "User name: " + currentUser.getDisplayName());
        }
        
        // Check notification permission
        boolean hasPermission = NotificationPermissionHelper.hasNotificationPermission(context);
        Log.d(TAG, "Notification permission: " + hasPermission);
        
        // Check FCM token
        if (currentUser != null) {
            FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(currentUser.getUid())
                    .get()
                    .addOnSuccessListener(doc -> {
                        if (doc.exists()) {
                            String fcmToken = doc.getString("fcmToken");
                            Log.d(TAG, "FCM Token exists: " + (fcmToken != null && !fcmToken.isEmpty()));
                            if (fcmToken != null) {
                                Log.d(TAG, "FCM Token (first 20 chars): " + fcmToken.substring(0, Math.min(20, fcmToken.length())) + "...");
                            }
                        } else {
                            Log.w(TAG, "User document not found in Firestore");
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error checking FCM token", e);
                    });
        }
        
        Log.d(TAG, "=== END STATUS CHECK ===");
    }
    
    /**
     * Thử restart notification listener
     */
    public static void restartNotificationListener(Context context) {
        Log.d(TAG, "Restarting notification listener...");
        ChatNotificationHelper helper = ChatNotificationHelper.getInstance(context);
        helper.stopListeningForChatNotifications();
        helper.startListeningForChatNotifications();
        Log.d(TAG, "Notification listener restarted");
    }
} 