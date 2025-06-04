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
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.HashMap;
import java.util.Map;

public class ChatNotificationService extends FirebaseMessagingService {

    private static final String TAG = "ChatNotificationService";
    private static final String CHANNEL_ID = "chat_notifications";
    private static final String CHANNEL_NAME = "Chat Notifications";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        Log.d(TAG, "From: " + remoteMessage.getFrom());
        Log.d(TAG, "Message ID: " + remoteMessage.getMessageId());

        // Check if app has notification permission
        if (!NotificationPermissionHelper.hasNotificationPermission(this)) {
            Log.w(TAG, "No notification permission, skipping notification");
            return;
        }

        // Check if message contains data payload
        if (remoteMessage.getData().size() > 0) {
            Log.d(TAG, "Message data payload: " + remoteMessage.getData());
            
            String messageType = remoteMessage.getData().get("type");
            Log.d(TAG, "Message type: " + messageType);
            
            if ("chat_message".equals(messageType)) {
                handleChatMessage(remoteMessage.getData());
            } else if ("task_notification".equals(messageType)) {
                handleTaskMessage(remoteMessage.getData());
            }
        }

        // Check if message contains notification payload
        if (remoteMessage.getNotification() != null) {
            Log.d(TAG, "Message Notification Title: " + remoteMessage.getNotification().getTitle());
            Log.d(TAG, "Message Notification Body: " + remoteMessage.getNotification().getBody());
            
            // Handle notification payload
            showChatNotification(
                remoteMessage.getData().get("groupId"),
                remoteMessage.getNotification().getTitle(),
                "System", 
                remoteMessage.getNotification().getBody()
            );
        }
    }

    @Override
    public void onNewToken(String token) {
        Log.d(TAG, "Refreshed token: " + token);
        sendRegistrationToServer(token);
    }

    private void handleChatMessage(Map<String, String> data) {
        Log.d(TAG, "Handling chat message with data: " + data.toString());
        
        String groupId = data.get("groupId");
        String groupName = data.get("groupName");
        String senderName = data.get("senderName");
        String message = data.get("message");
        String senderId = data.get("senderId");

        // Don't show notification for own messages
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null ?
                FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        
        Log.d(TAG, "Current user ID: " + currentUserId + ", Sender ID: " + senderId);
        
        if (currentUserId != null && currentUserId.equals(senderId)) {
            Log.d(TAG, "Skipping notification for own message");
            return;
        }

        Log.d(TAG, "Showing chat notification for group: " + groupName);
        showChatNotification(groupId, groupName, senderName, message);
    }

    private void handleTaskMessage(Map<String, String> data) {
        Log.d(TAG, "Handling task message with data: " + data.toString());
        
        String taskTitle = data.get("taskTitle");
        String assigneeName = data.get("assigneeName");
        String message = data.get("message");
        
        // Show task notification
        showTaskNotification(taskTitle, assigneeName, message);
    }

    private void showChatNotification(String groupId, String groupName, String senderName, String message) {
        Log.d(TAG, "Creating chat notification for group: " + groupName);
        
        Intent intent = new Intent(this, GroupChatActivity.class);
        intent.putExtra("GROUP_ID", groupId);
        intent.putExtra("GROUP_NAME", groupName);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                groupId != null ? groupId.hashCode() : 0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String notificationTitle = groupName != null ? groupName : "Tin nhắn mới";
        String notificationText = senderName != null ? senderName + ": " + message : message;

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(notificationTitle)
                .setContentText(notificationText)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(notificationText))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent);

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            int notificationId = groupId != null ? groupId.hashCode() : (int) System.currentTimeMillis();
            Log.d(TAG, "Showing notification with ID: " + notificationId);
            notificationManager.notify(notificationId, notificationBuilder.build());
        } else {
            Log.e(TAG, "NotificationManager is null");
        }
    }

    private void showTaskNotification(String taskTitle, String assigneeName, String message) {
        Log.d(TAG, "Creating task notification: " + taskTitle);
        
        String notificationTitle = "Nhiệm vụ mới: " + (taskTitle != null ? taskTitle : "");
        String notificationText = message != null ? message : "Bạn có nhiệm vụ mới";

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(notificationTitle)
                .setContentText(notificationText)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(notificationText))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            int notificationId = taskTitle != null ? taskTitle.hashCode() : (int) System.currentTimeMillis();
            Log.d(TAG, "Showing task notification with ID: " + notificationId);
            notificationManager.notify(notificationId, notificationBuilder.build());
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications for new chat messages");

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private void sendRegistrationToServer(String token) {
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null ?
                FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

        if (currentUserId != null) {
            Map<String, Object> tokenData = new HashMap<>();
            tokenData.put("fcmToken", token);
            tokenData.put("updatedAt", com.google.firebase.firestore.FieldValue.serverTimestamp());

            FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(currentUserId)
                    .update(tokenData)
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "FCM token updated successfully"))
                    .addOnFailureListener(e -> Log.e(TAG, "Error updating FCM token", e));
        }
    }

    /**
     * Send notification to group members (called from ChatRepository)
     */
    public static void sendChatNotification(String groupId, String groupName, String message, String senderId, String senderName) {
        // This would typically be called from a Cloud Function
        // For now, we'll implement a simple version using FCM HTTP API
        Log.d(TAG, "Should send notification to group: " + groupId + " for message: " + message);
        
        // TODO: Implement server-side notification sending via Cloud Functions
        // This requires setting up FCM server key and implementing HTTP requests
    }
} 