package com.example.grouptaskmanager.notification;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.example.grouptaskmanager.R;
import com.example.grouptaskmanager.chat.GroupChatActivity;
import com.example.grouptaskmanager.utils.NotificationPermissionHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.HashMap;
import java.util.Map;

public class ChatNotificationHelper {
    
    private static final String TAG = "ChatNotificationHelper";
    private static final String CHANNEL_ID = "chat_notifications";
    private static final String CHANNEL_NAME = "Chat Notifications";
    
    private static ChatNotificationHelper instance;
    private Context context;
    private ListenerRegistration notificationListener;
    
    private ChatNotificationHelper(Context context) {
        this.context = context.getApplicationContext();
        createNotificationChannel();
    }
    
    public static synchronized ChatNotificationHelper getInstance(Context context) {
        if (instance == null) {
            instance = new ChatNotificationHelper(context);
        }
        return instance;
    }
    
    /**
     * Bắt đầu lắng nghe thông báo chat cho user hiện tại
     */
    public void startListeningForChatNotifications() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Log.w(TAG, "Cannot start notification listener: User not logged in");
            return;
        }
        
        String userId = currentUser.getUid();
        Log.d(TAG, "Starting notification listener for user: " + userId);
        
        // Stop any existing listener
        stopListeningForChatNotifications();
        
        // Listen for new notifications in user's notifications collection
        notificationListener = FirebaseFirestore.getInstance()
                .collection("users")
                .document(userId)
                .collection("notifications")
                .whereEqualTo("type", "chat_message")
                .addSnapshotListener((queryDocumentSnapshots, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Error listening for notifications", e);
                        return;
                    }
                    
                    if (queryDocumentSnapshots != null) {
                        Log.d(TAG, "Received " + queryDocumentSnapshots.size() + " notifications");
                        queryDocumentSnapshots.getDocumentChanges().forEach(documentChange -> {
                            if (documentChange.getType() == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                                // New notification received
                                Map<String, Object> data = documentChange.getDocument().getData();
                                String notificationId = documentChange.getDocument().getId();
                                Log.d(TAG, "New notification received: " + notificationId + ", data: " + data);
                                handleNewChatNotification(data, notificationId);
                            }
                        });
                    } else {
                        Log.d(TAG, "queryDocumentSnapshots is null");
                    }
                });
        
        Log.d(TAG, "Started listening for chat notifications for user: " + userId);
    }
    
    /**
     * Dừng lắng nghe thông báo
     */
    public void stopListeningForChatNotifications() {
        if (notificationListener != null) {
            notificationListener.remove();
            notificationListener = null;
            Log.d(TAG, "Stopped listening for chat notifications");
        }
    }
    
    /**
     * Xử lý thông báo chat mới
     */
    private void handleNewChatNotification(Map<String, Object> data, String notificationId) {
        Log.d(TAG, "Processing notification: " + notificationId);
        
        // Check if app has notification permission
        if (!NotificationPermissionHelper.hasNotificationPermission(context)) {
            Log.w(TAG, "No notification permission, skipping notification");
            // Still delete the notification to avoid accumulation
            deleteNotification(notificationId);
            return;
        }
        
        String groupId = (String) data.get("groupId");
        String groupName = (String) data.get("groupName");
        String senderName = (String) data.get("senderName");
        String message = (String) data.get("message");
        String senderId = (String) data.get("senderId");
        
        Log.d(TAG, "Notification details - GroupId: " + groupId + ", GroupName: " + groupName + 
              ", SenderName: " + senderName + ", SenderId: " + senderId + ", Message: " + message);
        
        // Don't show notification for own messages
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null && currentUser.getUid().equals(senderId)) {
            Log.d(TAG, "Skipping notification for own message from user: " + senderId);
            // Delete this notification since it's from the current user
            deleteNotification(notificationId);
            return;
        }
        
        Log.d(TAG, "Showing notification for message from: " + senderName);
        showChatNotification(groupId, groupName, senderName, message, notificationId);
    }
    
    /**
     * Hiển thị thông báo chat
     */
    private void showChatNotification(String groupId, String groupName, String senderName, 
                                    String message, String notificationId) {
        Log.d(TAG, "Creating notification for group: " + groupName + " (ID: " + groupId + ")");
        
        Intent intent = new Intent(context, GroupChatActivity.class);
        intent.putExtra("GROUP_ID", groupId);
        intent.putExtra("GROUP_NAME", groupName);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                groupId != null ? groupId.hashCode() : 0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        String notificationTitle = groupName != null ? groupName : "Tin nhắn mới";
        String notificationText = senderName != null ? senderName + ": " + message : message;
        
        Log.d(TAG, "Notification content - Title: " + notificationTitle + ", Text: " + notificationText);
        
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(notificationTitle)
                .setContentText(notificationText)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(notificationText))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent);
        
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            int notificationIdInt = groupId != null ? groupId.hashCode() : (int) System.currentTimeMillis();
            Log.d(TAG, "Showing notification with ID: " + notificationIdInt);
            notificationManager.notify(notificationIdInt, notificationBuilder.build());
            
            // Delete the notification from Firestore after showing it
            Log.d(TAG, "Deleting processed notification: " + notificationId);
            deleteNotification(notificationId);
        } else {
            Log.e(TAG, "NotificationManager is null - cannot show notification");
        }
    }
    
    /**
     * Xóa thông báo khỏi Firestore sau khi đã hiển thị
     */
    private void deleteNotification(String notificationId) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(currentUser.getUid())
                    .collection("notifications")
                    .document(notificationId)
                    .delete()
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "Notification deleted: " + notificationId))
                    .addOnFailureListener(e -> Log.e(TAG, "Error deleting notification", e));
        }
    }
    
    /**
     * Tạo notification channel
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications for new chat messages");
            
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }
} 