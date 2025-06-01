package com.example.grouptaskmanager.repository;

import com.example.grouptaskmanager.model.ChatMessage;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

import android.util.Log;

public class ChatRepository {

    private static final String COLLECTION_GROUPS = "groups";
    private static final String COLLECTION_MESSAGES = "messages";
    private static final String COLLECTION_USER_READS = "userReads";
    private static final String FIELD_TIMESTAMP = "timestamp";
    private static final String FIELD_LAST_MESSAGE_AT = "lastMessageAt";
    private static final String FIELD_LAST_READ_AT = "lastReadAt";

    private final FirebaseFirestore db;
    private final FirebaseAuth auth;

    public ChatRepository() {
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
    }

    /**
     * Gửi tin nhắn mới
     */
    public Task<DocumentReference> sendMessage(String groupId, String message) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            return Tasks.forException(new Exception("User not authenticated"));
        }

        // Tạo dữ liệu tin nhắn
        Map<String, Object> messageData = new HashMap<>();
        messageData.put("groupId", groupId);
        messageData.put("senderId", currentUser.getUid());
        messageData.put("senderName", currentUser.getDisplayName() != null ? 
                       currentUser.getDisplayName() : currentUser.getEmail());
        messageData.put("senderPhotoUrl", currentUser.getPhotoUrl() != null ? 
                       currentUser.getPhotoUrl().toString() : null);
        messageData.put("message", message);
        messageData.put("messageType", "text");
        messageData.put("timestamp", FieldValue.serverTimestamp());
        messageData.put("isEdited", false);

        // Thêm tin nhắn vào collection
        return db.collection(COLLECTION_GROUPS)
                .document(groupId)
                .collection(COLLECTION_MESSAGES)
                .add(messageData)
                .addOnSuccessListener(documentReference -> {
                    // Cập nhật thời gian tin nhắn cuối của nhóm
                    updateGroupLastMessageTime(groupId);
                    // Gửi notification đến các thành viên khác
                    sendNotificationToGroupMembers(groupId, message, currentUser.getUid());
                });
    }

    /**
     * Lấy tin nhắn realtime với listener
     */
    public ListenerRegistration getMessagesRealtime(String groupId, 
                                                   OnMessagesChangedListener listener) {
        return db.collection(COLLECTION_GROUPS)
                .document(groupId)
                .collection(COLLECTION_MESSAGES)
                .orderBy(FIELD_TIMESTAMP, Query.Direction.ASCENDING)
                .addSnapshotListener((queryDocumentSnapshots, e) -> {
                    if (e != null) {
                        listener.onError(e);
                        return;
                    }
                    
                    if (queryDocumentSnapshots != null) {
                        listener.onMessagesChanged(queryDocumentSnapshots);
                    }
                });
    }

    /**
     * Lấy tin nhắn với pagination (load more)
     */
    public Task<QuerySnapshot> getMessagesPaginated(String groupId, int limit) {
        return db.collection(COLLECTION_GROUPS)
                .document(groupId)
                .collection(COLLECTION_MESSAGES)
                .orderBy(FIELD_TIMESTAMP, Query.Direction.DESCENDING)
                .limit(limit)
                .get();
    }

    /**
     * Lấy tin nhắn cuối cùng của nhóm
     */
    public Task<QuerySnapshot> getLastMessage(String groupId) {
        return db.collection(COLLECTION_GROUPS)
                .document(groupId)
                .collection(COLLECTION_MESSAGES)
                .orderBy(FIELD_TIMESTAMP, Query.Direction.DESCENDING)
                .limit(1)
                .get();
    }

    /**
     * Đếm số tin nhắn chưa đọc của user trong nhóm
     */
    public Task<QuerySnapshot> getUnreadMessagesCount(String groupId, String userId) {
        Log.d("ChatRepository", "Getting unread count for group: " + groupId + ", user: " + userId);
        
        // Lấy thời gian đọc cuối của user
        return db.collection(COLLECTION_GROUPS)
                .document(groupId)
                .collection(COLLECTION_USER_READS)
                .document(userId)
                .get()
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) {
                        throw task.getException();
                    }
                    
                    Object lastReadAt = null;
                    if (task.getResult().exists()) {
                        lastReadAt = task.getResult().get(FIELD_LAST_READ_AT);
                        Log.d("ChatRepository", "Last read at: " + lastReadAt);
                    } else {
                        Log.d("ChatRepository", "No read record found for user");
                    }
                    
                    // Query tin nhắn sau thời gian đọc cuối
                    Query messagesQuery = db.collection(COLLECTION_GROUPS)
                            .document(groupId)
                            .collection(COLLECTION_MESSAGES)
                            .whereNotEqualTo("senderId", userId); // Không tính tin nhắn của chính mình
                    
                    if (lastReadAt != null) {
                        messagesQuery = messagesQuery.whereGreaterThan(FIELD_TIMESTAMP, lastReadAt);
                        Log.d("ChatRepository", "Looking for messages after: " + lastReadAt);
                    } else {
                        Log.d("ChatRepository", "Looking for all messages not from user");
                    }
                    
                    return messagesQuery.get();
                })
                .addOnSuccessListener(querySnapshot -> {
                    int unreadCount = querySnapshot != null ? querySnapshot.size() : 0;
                    Log.d("ChatRepository", "Unread count for group " + groupId + ": " + unreadCount);
                })
                .addOnFailureListener(e -> {
                    Log.e("ChatRepository", "Error getting unread count for group " + groupId, e);
                });
    }

    /**
     * Đánh dấu nhóm đã đọc cho user hiện tại
     */
    public Task<Void> markGroupAsRead(String groupId) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            return Tasks.forException(new Exception("User not authenticated"));
        }

        Map<String, Object> readData = new HashMap<>();
        readData.put(FIELD_LAST_READ_AT, FieldValue.serverTimestamp());
        readData.put("userId", currentUser.getUid());

        // Update read timestamp
        Task<Void> updateReadTask = db.collection(COLLECTION_GROUPS)
                .document(groupId)
                .collection(COLLECTION_USER_READS)
                .document(currentUser.getUid())
                .set(readData);
        
        // Also clean up notifications for this group
        Task<Void> cleanupNotificationsTask = cleanupNotificationsForGroup(currentUser.getUid(), groupId);
        
        return Tasks.whenAll(updateReadTask, cleanupNotificationsTask);
    }
    
    /**
     * Dọn dẹp notifications cho group cụ thể sau khi đã đọc
     */
    private Task<Void> cleanupNotificationsForGroup(String userId, String groupId) {
        return db.collection("users")
                .document(userId)
                .collection("notifications")
                .whereEqualTo("type", "chat_message")
                .whereEqualTo("groupId", groupId)
                .get()
                .continueWith(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        // Delete all notifications for this group
                        for (DocumentSnapshot notificationDoc : task.getResult().getDocuments()) {
                            notificationDoc.getReference().delete();
                        }
                    }
                    return null;
                });
    }

    /**
     * Xóa tin nhắn
     */
    public Task<Void> deleteMessage(String groupId, String messageId) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            return Tasks.forException(new Exception("User not authenticated"));
        }

        return db.collection(COLLECTION_GROUPS)
                .document(groupId)
                .collection(COLLECTION_MESSAGES)
                .document(messageId)
                .delete();
    }

    /**
     * Sửa tin nhắn
     */
    public Task<Void> editMessage(String groupId, String messageId, String newMessage) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("message", newMessage);
        updates.put("isEdited", true);
        updates.put("editedAt", FieldValue.serverTimestamp());

        return db.collection(COLLECTION_GROUPS)
                .document(groupId)
                .collection(COLLECTION_MESSAGES)
                .document(messageId)
                .update(updates);
    }

    /**
     * Cập nhật thời gian tin nhắn cuối của nhóm
     */
    private void updateGroupLastMessageTime(String groupId) {
        db.collection(COLLECTION_GROUPS)
                .document(groupId)
                .update(FIELD_LAST_MESSAGE_AT, FieldValue.serverTimestamp());
    }

    /**
     * Gửi notification đến các thành viên khác trong nhóm
     */
    private void sendNotificationToGroupMembers(String groupId, String message, String senderId) {
        Log.d("ChatRepository", "Sending notifications for group: " + groupId + ", message: " + message);
        
        // Get group info and members
        db.collection(COLLECTION_GROUPS)
                .document(groupId)
                .get()
                .addOnSuccessListener(groupDoc -> {
                    if (!groupDoc.exists()) {
                        Log.w("ChatRepository", "Group not found: " + groupId);
                        return;
                    }
                    
                    String groupName = groupDoc.getString("name");
                    List<String> memberIds = (List<String>) groupDoc.get("members");
                    
                    Log.d("ChatRepository", "Group name: " + groupName + ", Member count: " + 
                          (memberIds != null ? memberIds.size() : 0));
                    
                    if (memberIds != null) {
                        // Get sender info
                        FirebaseUser currentUser = auth.getCurrentUser();
                        String senderName = currentUser != null && currentUser.getDisplayName() != null ? 
                                          currentUser.getDisplayName() : "Thành viên nhóm";
                        
                        Log.d("ChatRepository", "Sender name: " + senderName + ", Sender ID: " + senderId);
                        
                        // Send notification to each member (except sender)
                        int notificationCount = 0;
                        for (String memberId : memberIds) {
                            if (!memberId.equals(senderId)) {
                                Log.d("ChatRepository", "Sending notification to member: " + memberId);
                                sendNotificationToUser(memberId, groupId, groupName, senderName, message, senderId);
                                notificationCount++;
                            } else {
                                Log.d("ChatRepository", "Skipping notification for sender: " + memberId);
                            }
                        }
                        
                        Log.d("ChatRepository", "Total notifications sent: " + notificationCount);
                    } else {
                        Log.w("ChatRepository", "No member IDs found for group: " + groupId);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("ChatRepository", "Error getting group members for notification", e);
                });
    }
    
    /**
     * Gửi notification đến một user cụ thể
     */
    private void sendNotificationToUser(String userId, String groupId, String groupName, 
                                       String senderName, String message, String senderId) {
        // Get user's FCM token
        db.collection("users")
                .document(userId)
                .get()
                .addOnSuccessListener(userDoc -> {
                    if (!userDoc.exists()) return;
                    
                    String fcmToken = userDoc.getString("fcmToken");
                    if (fcmToken != null && !fcmToken.isEmpty()) {
                        // Create notification data
                        Map<String, Object> notificationData = new HashMap<>();
                        notificationData.put("type", "chat_message");
                        notificationData.put("groupId", groupId);
                        notificationData.put("groupName", groupName);
                        notificationData.put("senderName", senderName);
                        notificationData.put("message", message);
                        notificationData.put("senderId", senderId);
                        notificationData.put("timestamp", System.currentTimeMillis());
                        
                        // Save notification to user's notifications collection
                        db.collection("users")
                                .document(userId)
                                .collection("notifications")
                                .add(notificationData)
                                .addOnSuccessListener(docRef -> {
                                    Log.d("ChatRepository", "Notification saved for user: " + userId);
                                    // Trigger local notification if user is online
                                    triggerLocalNotification(userId, groupId, groupName, senderName, message);
                                })
                                .addOnFailureListener(e -> {
                                    Log.e("ChatRepository", "Error saving notification", e);
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("ChatRepository", "Error getting user FCM token", e);
                });
    }
    
    /**
     * Trigger local notification nếu user đang online
     */
    private void triggerLocalNotification(String userId, String groupId, String groupName, 
                                        String senderName, String message) {
        // Check if current user is the recipient
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null && currentUser.getUid().equals(userId)) {
            // User is currently logged in - show local notification
            // This will be handled by the ChatNotificationService when app receives FCM message
            Log.d("ChatRepository", "Would show local notification to current user");
        }
    }

    /**
     * Interface cho realtime listener
     */
    public interface OnMessagesChangedListener {
        void onMessagesChanged(QuerySnapshot snapshot);
        void onError(Exception e);
    }
    
    /**
     * Interface cho unread count updates
     */
    public interface OnUnreadUpdateListener {
        void onUnreadUpdate(String groupId, int unreadCount);
    }
    
    /**
     * Listen for unread count updates across all groups for a user
     */
    public ListenerRegistration listenForUnreadUpdates(String userId, OnUnreadUpdateListener listener) {
        // Listen for new messages in all groups that user is a member of
        return db.collection(COLLECTION_GROUPS)
                .whereArrayContains("members", userId)
                .addSnapshotListener((queryDocumentSnapshots, e) -> {
                    if (e != null) {
                        Log.e("ChatRepository", "Error listening for unread updates", e);
                        return;
                    }
                    
                    if (queryDocumentSnapshots != null) {
                        // When any group changes, recalculate unread count for all groups
                        for (DocumentSnapshot groupDoc : queryDocumentSnapshots.getDocuments()) {
                            String groupId = groupDoc.getId();
                            getUnreadMessagesCount(groupId, userId)
                                    .addOnSuccessListener(querySnapshot -> {
                                        int unreadCount = querySnapshot.size();
                                        listener.onUnreadUpdate(groupId, unreadCount);
                                    })
                                    .addOnFailureListener(error -> {
                                        Log.e("ChatRepository", "Error getting unread count for group: " + groupId, error);
                                    });
                        }
                    }
                });
    }

    /**
     * Get total unread messages count across all groups for a user
     */
    public Task<Integer> getTotalUnreadMessagesCount(String userId) {
        // Get all groups user is a member of
        return db.collection(COLLECTION_GROUPS)
                .whereArrayContains("members", userId)
                .get()
                .continueWithTask(groupTask -> {
                    if (!groupTask.isSuccessful() || groupTask.getResult() == null) {
                        return Tasks.forResult(0);
                    }
                    
                    List<Task<QuerySnapshot>> unreadTasks = new ArrayList<>();
                    
                    // For each group, get unread count
                    for (DocumentSnapshot groupDoc : groupTask.getResult().getDocuments()) {
                        String groupId = groupDoc.getId();
                        unreadTasks.add(getUnreadMessagesCount(groupId, userId));
                    }
                    
                    if (unreadTasks.isEmpty()) {
                        return Tasks.forResult(0);
                    }
                    
                    // Wait for all unread count tasks to complete
                    return Tasks.whenAllSuccess(unreadTasks)
                            .continueWith(allUnreadTask -> {
                                int totalUnread = 0;
                                List<Object> results = allUnreadTask.getResult();
                                if (results != null) {
                                    for (Object result : results) {
                                        if (result instanceof QuerySnapshot) {
                                            QuerySnapshot querySnapshot = (QuerySnapshot) result;
                                            totalUnread += querySnapshot.size();
                                        }
                                    }
                                }
                                Log.d("ChatRepository", "Total unread messages: " + totalUnread);
                                return totalUnread;
                            });
                });
    }
} 