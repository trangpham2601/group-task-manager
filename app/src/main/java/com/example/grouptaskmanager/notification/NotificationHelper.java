package com.example.grouptaskmanager.notification;

import android.util.Log;

import com.example.grouptaskmanager.model.Task;
import com.example.grouptaskmanager.model.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.HashMap;
import java.util.Map;

public class NotificationHelper {
    private static final String TAG = "NotificationHelper";
    private static final String COLLECTION_USERS = "users";
    private static final String FIELD_FCM_TOKEN = "fcmToken";

    /**
     * Update the FCM token for the current user in Firestore
     */
    public static void updateUserToken(String token) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "Cannot update token: User not logged in");
            return;
        }
        
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference userRef = db.collection(COLLECTION_USERS).document(currentUser.getUid());
        
        userRef.update(FIELD_FCM_TOKEN, token)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Token updated successfully"))
                .addOnFailureListener(e -> Log.e(TAG, "Error updating token", e));
    }
    
    /**
     * Get the current FCM token and update it in Firestore
     */
    public static void updateCurrentToken() {
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Log.w(TAG, "Fetching FCM registration token failed", task.getException());
                        return;
                    }

                    // Get new FCM registration token
                    String token = task.getResult();
                    Log.d(TAG, "Current token: " + token);
                    
                    // Update token in Firestore
                    updateUserToken(token);
                });
    }
    
    /**
     * Send a notification when a task is assigned to a user
     */
    public static void sendTaskAssignedNotification(Task task, String assigneeId) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "Cannot send notification: User not logged in");
            return;
        }
        
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        
        // Get the assignee's FCM token
        db.collection(COLLECTION_USERS).document(assigneeId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    User assignee = documentSnapshot.toObject(User.class);
                    if (assignee == null || assignee.getFcmToken() == null) {
                        Log.d(TAG, "Assignee has no FCM token");
                        return;
                    }
                    
                    // The server would handle this in a real app with Firebase Cloud Functions
                    // For this implementation, we'll just log it as if it was sent
                    Log.d(TAG, "Notification would be sent to token: " + assignee.getFcmToken());
                    Log.d(TAG, "Notification data: Task=" + task.getTitle() + ", Assignee=" + assignee.getName());
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error getting assignee info", e));
    }
    
    /**
     * Clear the FCM token for the current user in Firestore when logging out
     */
    public static void clearUserToken() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "Cannot clear token: User not logged in");
            return;
        }
        
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference userRef = db.collection(COLLECTION_USERS).document(currentUser.getUid());
        
        userRef.update(FIELD_FCM_TOKEN, null)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Token cleared successfully"))
                .addOnFailureListener(e -> Log.e(TAG, "Error clearing token", e));
    }
    
    /**
     * Toggle notifications on/off
     */
    public static void toggleNotifications(boolean enabled) {
        if (enabled) {
            // Subscribe to topics
            FirebaseMessaging.getInstance().subscribeToTopic("task_notifications");
        } else {
            // Unsubscribe from topics
            FirebaseMessaging.getInstance().unsubscribeFromTopic("task_notifications");
        }
    }
} 