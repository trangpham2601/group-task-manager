package com.example.grouptaskmanager.repository;

import com.example.grouptaskmanager.model.Comment;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.Timestamp;

import java.util.HashMap;
import java.util.Map;

public class CommentRepository {
    
    private static final String COLLECTION_COMMENTS = "comments";
    private final FirebaseFirestore db;
    private final FirebaseAuth auth;

    public CommentRepository() {
        this.db = FirebaseFirestore.getInstance();
        this.auth = FirebaseAuth.getInstance();
    }

    /**
     * Get all comments for a specific task, ordered by creation time
     */
    public Task<QuerySnapshot> getTaskComments(String groupId, String taskId) {
        return db.collection(COLLECTION_COMMENTS)
                .whereEqualTo("groupId", groupId)
                .whereEqualTo("taskId", taskId)
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .get();
    }

    /**
     * Add a new comment
     */
    public Task<DocumentReference> addComment(String groupId, String taskId, String content, String authorName) {
        String currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            throw new IllegalStateException("User must be logged in to comment");
        }

        Comment comment = new Comment(taskId, groupId, content, currentUserId, authorName);
        return db.collection(COLLECTION_COMMENTS).add(comment);
    }

    /**
     * Add a reply to an existing comment
     */
    public Task<DocumentReference> addReply(String groupId, String taskId, String content, 
                                           String authorName, String replyToCommentId, String replyToAuthorName) {
        String currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            throw new IllegalStateException("User must be logged in to reply");
        }

        Comment reply = new Comment(taskId, groupId, content, currentUserId, authorName, 
                                   replyToCommentId, replyToAuthorName);
        return db.collection(COLLECTION_COMMENTS).add(reply);
    }

    /**
     * Update an existing comment
     */
    public Task<Void> updateComment(String commentId, String newContent) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("content", newContent);
        updates.put("updatedAt", Timestamp.now());
        
        return db.collection(COLLECTION_COMMENTS)
                .document(commentId)
                .update(updates);
    }

    /**
     * Delete a comment
     */
    public Task<Void> deleteComment(String commentId) {
        return db.collection(COLLECTION_COMMENTS)
                .document(commentId)
                .delete();
    }

    /**
     * Check if current user can modify a comment (only author can modify)
     */
    public Task<Boolean> canModifyComment(String commentId) {
        String currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            return com.google.android.gms.tasks.Tasks.forResult(false);
        }

        return db.collection(COLLECTION_COMMENTS)
                .document(commentId)
                .get()
                .continueWith(task -> {
                    if (task.isSuccessful() && task.getResult().exists()) {
                        String authorId = task.getResult().getString("authorId");
                        return currentUserId.equals(authorId);
                    }
                    return false;
                });
    }

    /**
     * Get total comment count for a task
     */
    public Task<Integer> getCommentCount(String groupId, String taskId) {
        return db.collection(COLLECTION_COMMENTS)
                .whereEqualTo("groupId", groupId)
                .whereEqualTo("taskId", taskId)
                .get()
                .continueWith(task -> {
                    if (task.isSuccessful()) {
                        return task.getResult().size();
                    }
                    return 0;
                });
    }

    /**
     * Listen for real-time comment updates
     */
    public void addCommentsListener(String groupId, String taskId, 
                                   com.google.firebase.firestore.EventListener<QuerySnapshot> listener) {
        db.collection(COLLECTION_COMMENTS)
                .whereEqualTo("groupId", groupId)
                .whereEqualTo("taskId", taskId)
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .addSnapshotListener(listener);
    }

    private String getCurrentUserId() {
        return auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
    }
} 