package com.example.grouptaskmanager.repository;

import com.example.grouptaskmanager.model.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.HashMap;
import java.util.Map;

public class TaskRepository {

    private static final String COLLECTION_GROUPS = "groups";
    private static final String COLLECTION_TASKS = "tasks";
    private static final String FIELD_CREATED_AT = "createdAt";
    private static final String FIELD_UPDATED_AT = "updatedAt";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_DEADLINE = "deadline";
    private static final String FIELD_ASSIGNED_TO = "assignedTo";

    private final FirebaseFirestore db;
    private final FirebaseAuth auth;

    public TaskRepository() {
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
    }

    /**
     * Tạo nhiệm vụ mới
     */
    public com.google.android.gms.tasks.Task<DocumentReference> createTask(String groupId, String title, String description, 
                                                   String assignedTo, com.google.firebase.Timestamp deadline, 
                                                   String priority) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            return null;
        }

        // Tạo dữ liệu cho nhiệm vụ mới
        Map<String, Object> taskData = new HashMap<>();
        taskData.put("title", title);
        taskData.put("description", description);
        taskData.put("createdBy", currentUser.getUid());
        taskData.put("assignedTo", assignedTo);
        taskData.put("status", Task.STATUS_TODO);
        taskData.put("priority", priority);
        taskData.put("deadline", deadline);
        taskData.put("createdAt", FieldValue.serverTimestamp());
        taskData.put("updatedAt", FieldValue.serverTimestamp());
        taskData.put("commentsCount", 0);

        // Thêm nhiệm vụ vào Firestore
        return db.collection(COLLECTION_GROUPS)
                .document(groupId)
                .collection(COLLECTION_TASKS)
                .add(taskData)
                .addOnSuccessListener(documentReference -> {
                    // Cập nhật ID của nhiệm vụ
                    String taskId = documentReference.getId();
                    documentReference.update("id", taskId);
                });
    }

    /**
     * Tạo nhiệm vụ mới từ đối tượng Task
     */
    public com.google.android.gms.tasks.Task<DocumentReference> createTask(String groupId, Task task) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            return null;
        }

        // Tạo dữ liệu cho nhiệm vụ mới
        Map<String, Object> taskData = new HashMap<>();
        taskData.put("title", task.getTitle());
        taskData.put("description", task.getDescription());
        taskData.put("createdBy", currentUser.getUid());
        taskData.put("assignedTo", task.getAssignedTo());
        taskData.put("status", task.getStatus());
        taskData.put("priority", task.getPriority());
        taskData.put("deadline", task.getDeadline());
        taskData.put("createdAt", FieldValue.serverTimestamp());
        taskData.put("updatedAt", FieldValue.serverTimestamp());
        taskData.put("commentsCount", 0);

        // Thêm nhiệm vụ vào Firestore
        return db.collection(COLLECTION_GROUPS)
                .document(groupId)
                .collection(COLLECTION_TASKS)
                .add(taskData)
                .addOnSuccessListener(documentReference -> {
                    // Cập nhật ID của nhiệm vụ
                    String taskId = documentReference.getId();
                    documentReference.update("id", taskId);
                });
    }

    /**
     * Cập nhật thông tin nhiệm vụ
     */
    public com.google.android.gms.tasks.Task<Void> updateTask(String groupId, String taskId, String title, String description, 
                                      String assignedTo, com.google.firebase.Timestamp deadline, 
                                      String priority) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("title", title);
        updates.put("description", description);
        updates.put("assignedTo", assignedTo);
        updates.put("priority", priority);
        updates.put("deadline", deadline);
        updates.put(FIELD_UPDATED_AT, FieldValue.serverTimestamp());

        return db.collection(COLLECTION_GROUPS)
                .document(groupId)
                .collection(COLLECTION_TASKS)
                .document(taskId)
                .update(updates);
    }

    /**
     * Cập nhật trạng thái nhiệm vụ
     */
    public com.google.android.gms.tasks.Task<Void> updateTaskStatus(String groupId, String taskId, String status) {
        return db.collection(COLLECTION_GROUPS)
                .document(groupId)
                .collection(COLLECTION_TASKS)
                .document(taskId)
                .update(
                        FIELD_STATUS, status,
                        FIELD_UPDATED_AT, FieldValue.serverTimestamp()
                );
    }

    /**
     * Xóa nhiệm vụ
     */
    public com.google.android.gms.tasks.Task<Void> deleteTask(String groupId, String taskId) {
        return db.collection(COLLECTION_GROUPS)
                .document(groupId)
                .collection(COLLECTION_TASKS)
                .document(taskId)
                .delete();
    }

    /**
     * Lấy chi tiết một nhiệm vụ
     */
    public com.google.android.gms.tasks.Task<DocumentSnapshot> getTaskDetails(String groupId, String taskId) {
        return db.collection(COLLECTION_GROUPS)
                .document(groupId)
                .collection(COLLECTION_TASKS)
                .document(taskId)
                .get();
    }

    /**
     * Lấy danh sách nhiệm vụ của nhóm
     */
    public com.google.android.gms.tasks.Task<QuerySnapshot> getGroupTasks(String groupId) {
        return db.collection(COLLECTION_GROUPS)
                .document(groupId)
                .collection(COLLECTION_TASKS)
                .orderBy(FIELD_CREATED_AT, Query.Direction.DESCENDING)
                .get();
    }

    /**
     * Lấy danh sách nhiệm vụ theo trạng thái
     */
    public com.google.android.gms.tasks.Task<QuerySnapshot> getTasksByStatus(String groupId, String status) {
        return db.collection(COLLECTION_GROUPS)
                .document(groupId)
                .collection(COLLECTION_TASKS)
                .whereEqualTo(FIELD_STATUS, status)
                .orderBy(FIELD_CREATED_AT, Query.Direction.DESCENDING)
                .get();
    }

    /**
     * Lấy danh sách nhiệm vụ theo người được giao
     */
    public com.google.android.gms.tasks.Task<QuerySnapshot> getTasksByAssignee(String groupId, String assigneeId) {
        return db.collection(COLLECTION_GROUPS)
                .document(groupId)
                .collection(COLLECTION_TASKS)
                .whereEqualTo(FIELD_ASSIGNED_TO, assigneeId)
                .orderBy(FIELD_CREATED_AT, Query.Direction.DESCENDING)
                .get();
    }

    /**
     * Lấy danh sách nhiệm vụ sắp đến hạn (deadline < ngày hiện tại + số ngày)
     */
    public com.google.android.gms.tasks.Task<QuerySnapshot> getTasksByDeadline(String groupId, com.google.firebase.Timestamp deadlineLimit) {
        return db.collection(COLLECTION_GROUPS)
                .document(groupId)
                .collection(COLLECTION_TASKS)
                .whereEqualTo(FIELD_STATUS, Task.STATUS_TODO)
                .whereLessThanOrEqualTo(FIELD_DEADLINE, deadlineLimit)
                .orderBy(FIELD_DEADLINE, Query.Direction.ASCENDING)
                .get();
    }

    /**
     * Kiểm tra quyền chỉnh sửa/xóa nhiệm vụ (chỉ người tạo hoặc người tạo nhóm)
     */
    public com.google.android.gms.tasks.Task<Boolean> canModifyTask(String groupId, String taskId) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            return null;
        }

        return db.collection(COLLECTION_GROUPS)
                .document(groupId)
                .collection(COLLECTION_TASKS)
                .document(taskId)
                .get()
                .continueWith(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        String createdBy = task.getResult().getString("createdBy");
                        
                        // Nếu là người tạo nhiệm vụ
                        if (createdBy != null && createdBy.equals(currentUser.getUid())) {
                            return true;
                        }
                        
                        // Kiểm tra xem có phải là người tạo nhóm không
                        return db.collection(COLLECTION_GROUPS)
                                .document(groupId)
                                .get()
                                .continueWith(groupTask -> {
                                    if (groupTask.isSuccessful() && groupTask.getResult() != null) {
                                        String groupCreator = groupTask.getResult().getString("createdBy");
                                        return groupCreator != null && groupCreator.equals(currentUser.getUid());
                                    }
                                    return false;
                                }).getResult();
                    }
                    return false;
                });
    }
} 