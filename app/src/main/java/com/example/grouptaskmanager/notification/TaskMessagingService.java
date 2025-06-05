package com.example.grouptaskmanager.notification;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.preference.PreferenceManager;

import com.example.grouptaskmanager.R;
import com.example.grouptaskmanager.task.TaskDetailActivity;
import com.example.grouptaskmanager.utils.NotificationPermissionHelper;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class TaskMessagingService extends FirebaseMessagingService {

    private static final String TAG = "TaskMessagingService";
    private static final String CHANNEL_ID = "task_notification_channel";
    private static final String CHANNEL_NAME = "Task Notifications";
    private static final String PREF_KEY_NOTIFICATIONS_ENABLED = "notifications_enabled";

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        Log.d(TAG, "From: " + remoteMessage.getFrom());

        // Check if app has notification permission
        if (!NotificationPermissionHelper.hasNotificationPermission(this)) {
            Log.w(TAG, "No notification permission, skipping notification");
            return;
        }

        // Check if notifications are enabled
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean notificationsEnabled = sharedPreferences.getBoolean(PREF_KEY_NOTIFICATIONS_ENABLED, true);
        
        if (!notificationsEnabled) {
            Log.d(TAG, "Notifications are disabled by user preference");
            return;
        }

        // Check if message contains a notification payload
        if (remoteMessage.getNotification() != null) {
            Log.d(TAG, "Message Notification Body: " + remoteMessage.getNotification().getBody());
            sendNotification(
                    remoteMessage.getNotification().getTitle(),
                    remoteMessage.getNotification().getBody(),
                    remoteMessage.getData());
        } else if (remoteMessage.getData().size() > 0) {
            // Handle data payload
            Log.d(TAG, "Message data payload: " + remoteMessage.getData());
            
            String title = remoteMessage.getData().get("title");
            String message = remoteMessage.getData().get("message");
            
            sendNotification(title, message, remoteMessage.getData());
        }
    }

    @Override
    public void onNewToken(@NonNull String token) {
        Log.d(TAG, "Refreshed token: " + token);
        
        // Save the token to Firestore for the current user
        NotificationHelper.updateUserToken(token);
    }

    private void sendNotification(String title, String messageBody, java.util.Map<String, String> data) {
        // Double check permission before showing notification
        if (!NotificationPermissionHelper.hasNotificationPermission(this)) {
            Log.w(TAG, "No notification permission, cannot show notification");
            return;
        }

        // Extract task and group IDs from data payload
        String taskId = data.get("taskId");
        String groupId = data.get("groupId");
        
        // Create intent for opening the task detail
        Intent intent = new Intent(this, TaskDetailActivity.class);
        if (taskId != null) {
            intent.putExtra("TASK_ID", taskId);
        }
        if (groupId != null) {
            intent.putExtra("GROUP_ID", groupId);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE);

        // Set notification sound
        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        
        // Build notification
        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setContentTitle(title)
                        .setContentText(messageBody)
                        .setAutoCancel(true)
                        .setSound(defaultSoundUri)
                        .setContentIntent(pendingIntent);

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // Create notification channel for Android O and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT);
            notificationManager.createNotificationChannel(channel);
        }

        // Show notification
        int notificationId = (int) System.currentTimeMillis();
        notificationManager.notify(notificationId, notificationBuilder.build());
    }
} 