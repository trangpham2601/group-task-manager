package com.example.grouptaskmanager.repository;

import com.example.grouptaskmanager.model.User;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UserRepository {

    private static final String COLLECTION_USERS = "users";
    private static final String FIELD_NAME = "name";
    private static final String FIELD_EMAIL = "email";
    private static final String FIELD_PHOTO_URL = "photoURL";
    private static final String FIELD_GROUPS = "groups";
    private static final String FIELD_JOINED_AT = "joinedAt";

    private final FirebaseFirestore db;
    private final FirebaseAuth auth;

    public UserRepository() {
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
    }

    /**
     * Lấy thông tin người dùng hiện tại
     */
    public Task<DocumentSnapshot> getCurrentUser() {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            return null;
        }
        return db.collection(COLLECTION_USERS).document(currentUser.getUid()).get();
    }

    /**
     * Lấy thông tin người dùng theo ID
     */
    public Task<DocumentSnapshot> getUserById(String userId) {
        return db.collection(COLLECTION_USERS).document(userId).get();
    }

    /**
     * Lấy danh sách thông tin người dùng theo danh sách ID
     */
    public Task<QuerySnapshot> getUsersByIds(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return null;
        }
        
        // Firestore không hỗ trợ truy vấn trực tiếp theo ID với whereIn
        // Sử dụng trường UID đã được thiết lập trong document
        return db.collection(COLLECTION_USERS)
                .whereIn("id", userIds)
                .limit(10) // Giới hạn kết quả
                .get();
    }

    /**
     * Lấy danh sách users (method gọi getUsersByIds)
     */
    public Task<QuerySnapshot> getUsers(List<String> userIds) {
        return getUsersByIds(userIds);
    }

    /**
     * Cập nhật thông tin người dùng
     */
    public Task<Void> updateUserProfile(String name, String photoURL) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            return null;
        }
        
        Map<String, Object> updates = new HashMap<>();
        updates.put(FIELD_NAME, name);
        if (photoURL != null) {
            updates.put(FIELD_PHOTO_URL, photoURL);
        }
        
        return db.collection(COLLECTION_USERS)
                .document(currentUser.getUid())
                .update(updates);
    }

    /**
     * Cập nhật thông tin người dùng theo ID
     */
    public Task<Void> updateUserProfile(String userId, String name, String photoURL) {
        Map<String, Object> updates = new HashMap<>();
        updates.put(FIELD_NAME, name);
        if (photoURL != null) {
            updates.put(FIELD_PHOTO_URL, photoURL);
        }
        
        return db.collection(COLLECTION_USERS)
                .document(userId)
                .update(updates);
    }

    /**
     * Tạo người dùng mới trong Firestore
     */
    public Task<Void> createUser(String userId, String name, String email, String photoURL) {
        Map<String, Object> userData = new HashMap<>();
        userData.put("id", userId); // Thêm ID vào document để có thể truy vấn
        userData.put("name", name);
        userData.put("email", email);
        if (photoURL != null) {
            userData.put("photoURL", photoURL);
        }
        userData.put("groups", new ArrayList<>());
        userData.put("joinedAt", com.google.firebase.Timestamp.now());
        
        return db.collection(COLLECTION_USERS)
                .document(userId)
                .set(userData);
    }
} 