package com.example.grouptaskmanager.repository;

import com.example.grouptaskmanager.model.Group;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class GroupRepository {

    private static final String COLLECTION_GROUPS = "groups";
    private static final String COLLECTION_USERS = "users";
    private static final String FIELD_GROUPS = "groups";
    private static final String FIELD_MEMBERS = "members";
    private static final String FIELD_CREATED_AT = "createdAt";
    private static final String FIELD_UPDATED_AT = "updatedAt";
    private static final int INVITE_CODE_LENGTH = 6;

    private final FirebaseFirestore db;
    private final FirebaseAuth auth;

    public GroupRepository() {
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
    }

    /**
     * Tạo nhóm mới
     */
    public Task<DocumentReference> createGroup(String name, String description, boolean isPrivate) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            return null;
        }

        // Tạo dữ liệu cho nhóm mới
        Map<String, Object> groupData = new HashMap<>();
        groupData.put("name", name);
        groupData.put("description", description);
        groupData.put("createdBy", currentUser.getUid());
        groupData.put("members", Collections.singletonList(currentUser.getUid()));
        groupData.put("createdAt", FieldValue.serverTimestamp());
        groupData.put("updatedAt", FieldValue.serverTimestamp());
        groupData.put("isPrivate", isPrivate);
        
        // Tạo mã mời nếu là nhóm riêng tư
        if (isPrivate) {
            groupData.put("inviteCode", generateInviteCode());
        }

        // Thêm nhóm vào Firestore
        return db.collection(COLLECTION_GROUPS)
                .add(groupData)
                .addOnSuccessListener(documentReference -> {
                    // Cập nhật ID của nhóm
                    String groupId = documentReference.getId();
                    documentReference.update("id", groupId);
                    
                    // Thêm groupId vào danh sách nhóm của người dùng
                    db.collection(COLLECTION_USERS)
                            .document(currentUser.getUid())
                            .update(FIELD_GROUPS, FieldValue.arrayUnion(groupId));
                });
    }

    /**
     * Lấy danh sách nhóm của người dùng hiện tại
     */
    public Task<QuerySnapshot> getUserGroups() {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            return null;
        }

        return db.collection(COLLECTION_GROUPS)
                .whereArrayContains(FIELD_MEMBERS, currentUser.getUid())
                .orderBy(FIELD_CREATED_AT, Query.Direction.DESCENDING)
                .get();
    }

    /**
     * Tham gia nhóm bằng mã mời
     */
    public Task<QuerySnapshot> joinGroupByInviteCode(String inviteCode) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            return null;
        }

        return db.collection(COLLECTION_GROUPS)
                .whereEqualTo("inviteCode", inviteCode)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        DocumentSnapshot document = queryDocumentSnapshots.getDocuments().get(0);
                        String groupId = document.getId();
                        
                        // Thêm người dùng vào nhóm
                        db.collection(COLLECTION_GROUPS)
                                .document(groupId)
                                .update(FIELD_MEMBERS, FieldValue.arrayUnion(currentUser.getUid()),
                                        FIELD_UPDATED_AT, FieldValue.serverTimestamp());
                        
                        // Thêm groupId vào danh sách nhóm của người dùng
                        db.collection(COLLECTION_USERS)
                                .document(currentUser.getUid())
                                .update(FIELD_GROUPS, FieldValue.arrayUnion(groupId));
                    }
                });
    }

    /**
     * Lấy thông tin chi tiết của một nhóm
     */
    public Task<DocumentSnapshot> getGroupDetails(String groupId) {
        return db.collection(COLLECTION_GROUPS).document(groupId).get();
    }

    /**
     * Rời khỏi nhóm
     */
    public Task<Void> leaveGroup(String groupId) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            return null;
        }

        // Xóa người dùng khỏi danh sách thành viên nhóm
        Task<Void> removeFromGroup = db.collection(COLLECTION_GROUPS)
                .document(groupId)
                .update(FIELD_MEMBERS, FieldValue.arrayRemove(currentUser.getUid()),
                        FIELD_UPDATED_AT, FieldValue.serverTimestamp());

        // Xóa groupId khỏi danh sách nhóm của người dùng
        db.collection(COLLECTION_USERS)
                .document(currentUser.getUid())
                .update(FIELD_GROUPS, FieldValue.arrayRemove(groupId));
        
        return removeFromGroup;
    }

    /**
     * Cập nhật thông tin nhóm
     */
    public Task<Void> updateGroup(String groupId, String name, String description) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("name", name);
        updates.put("description", description);
        updates.put(FIELD_UPDATED_AT, FieldValue.serverTimestamp());

        return db.collection(COLLECTION_GROUPS)
                .document(groupId)
                .update(updates);
    }

    /**
     * Cập nhật thông tin nhóm bao gồm trạng thái riêng tư
     */
    public Task<Void> updateGroupWithPrivacy(String groupId, String name, String description, boolean isPrivate) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("name", name);
        updates.put("description", description);
        updates.put("isPrivate", isPrivate);
        updates.put(FIELD_UPDATED_AT, FieldValue.serverTimestamp());
        
        // Nếu không còn là nhóm riêng tư, xóa mã mời
        if (!isPrivate) {
            updates.put("inviteCode", null);
        }
        // Nếu chuyển thành nhóm riêng tư mà chưa có mã mời, tạo mã mời mới
        else if (isPrivate) {
            // Trường hợp này sẽ được xử lý bởi generateNewInviteCode sau khi cập nhật
        }

        return db.collection(COLLECTION_GROUPS)
                .document(groupId)
                .update(updates);
    }

    /**
     * Tạo mã mời ngẫu nhiên
     */
    private String generateInviteCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random random = new Random();
        StringBuilder builder = new StringBuilder();
        
        for (int i = 0; i < INVITE_CODE_LENGTH; i++) {
            int index = random.nextInt(chars.length());
            builder.append(chars.charAt(index));
        }
        
        return builder.toString();
    }
    
    /**
     * Tạo mã mời mới cho nhóm
     */
    public Task<Void> generateNewInviteCode(String groupId) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            return null;
        }
        
        // Tạo mã mời mới
        String newInviteCode = generateInviteCode();
        
        // Cập nhật mã mời và đánh dấu nhóm là riêng tư
        Map<String, Object> updates = new HashMap<>();
        updates.put("inviteCode", newInviteCode);
        updates.put("isPrivate", true);
        updates.put(FIELD_UPDATED_AT, FieldValue.serverTimestamp());
        
        return db.collection(COLLECTION_GROUPS)
                .document(groupId)
                .update(updates);
    }
    
    /**
     * Kiểm tra xem người dùng có phải là người tạo nhóm không
     */
    public Task<DocumentSnapshot> isGroupCreator(String groupId) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            return null;
        }
        
        return db.collection(COLLECTION_GROUPS)
                .document(groupId)
                .get()
                .continueWith(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        String creatorId = task.getResult().getString("createdBy");
                        if (creatorId != null && creatorId.equals(currentUser.getUid())) {
                            return task.getResult();
                        }
                    }
                    return null;
                });
    }
    
    /**
     * Xóa nhóm (chỉ người tạo nhóm mới có quyền xóa)
     */
    public Task<Void> deleteGroup(String groupId) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            return null;
        }
        
        // Lấy thông tin nhóm để kiểm tra quyền và lấy danh sách thành viên
        return db.collection(COLLECTION_GROUPS)
                .document(groupId)
                .get()
                .continueWithTask(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        Group group = task.getResult().toObject(Group.class);
                        
                        if (group != null) {
                            // Kiểm tra quyền xóa nhóm
                            if (!currentUser.getUid().equals(group.getCreatedBy())) {
                                throw new IllegalStateException("Chỉ người tạo nhóm mới có quyền xóa nhóm");
                            }
                            
                            // Xóa groupId khỏi danh sách nhóm của tất cả thành viên
                            List<Task<Void>> memberTasks = new ArrayList<>();
                            if (group.getMembers() != null) {
                                for (String memberId : group.getMembers()) {
                                    Task<Void> memberTask = db.collection(COLLECTION_USERS)
                                            .document(memberId)
                                            .update(FIELD_GROUPS, FieldValue.arrayRemove(groupId));
                                    memberTasks.add(memberTask);
                                }
                            }
                            
                            // Đợi tất cả các thao tác cập nhật hoàn thành
                            return com.google.android.gms.tasks.Tasks.whenAll(memberTasks)
                                    .continueWithTask(t -> {
                                        // Xóa tất cả các tài liệu con của nhóm (như tasks)
                                        return db.collection(COLLECTION_GROUPS)
                                                .document(groupId)
                                                .collection("tasks")
                                                .get()
                                                .continueWithTask(tasksSnapshot -> {
                                                    List<Task<Void>> deleteTasks = new ArrayList<>();
                                                    for (DocumentSnapshot doc : tasksSnapshot.getResult().getDocuments()) {
                                                        deleteTasks.add(doc.getReference().delete());
                                                    }
                                                    return com.google.android.gms.tasks.Tasks.whenAll(deleteTasks);
                                                });
                                    })
                                    .continueWithTask(t -> {
                                        // Cuối cùng, xóa nhóm
                                        return db.collection(COLLECTION_GROUPS).document(groupId).delete();
                                    });
                        }
                    }
                    
                    return null;
                });
    }
} 