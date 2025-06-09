# Troubleshooting Chat Notifications

## Vấn đề: Không nhận được thông báo tin nhắn

### 🔍 Các bước kiểm tra:

#### 1. Kiểm tra quyền thông báo
```bash
# Mở Settings > Apps > Group Task Manager > Notifications
# Đảm bảo "Allow notifications" được bật
```

#### 2. Kiểm tra logs trong Logcat
```bash
# Filter theo các tags sau:
- ChatRepository
- ChatNotificationHelper  
- ChatNotificationService
- NotificationTestHelper
```

#### 3. Kiểm tra dữ liệu Firestore
```
- users/{userId}/notifications (có notifications mới không?)
- users/{userId}.fcmToken (có FCM token không?)
- groups/{groupId}.memberIds (user có trong danh sách thành viên không?)
```

### 🛠️ Các bước debug:

#### Bước 1: Kiểm tra trạng thái hệ thống
```java
// Thêm vào MainActivity.onCreate()
NotificationTestHelper.checkNotificationSystem(this);
```

#### Bước 2: Test notification listener
```java
// Thêm vào GroupChatActivity
NotificationTestHelper.restartNotificationListener(this);
```

#### Bước 3: Tạo test notification
```java
// Trong GroupChatActivity, thêm button test
NotificationTestHelper.createTestNotification(this, groupId, groupName);
```

### 📋 Checklist debug:

- [ ] **FCM Service**: Chỉ có 1 service trong AndroidManifest ✅ 
- [ ] **User đăng nhập**: FirebaseAuth.getCurrentUser() != null
- [ ] **Quyền thông báo**: NotificationPermissionHelper.hasNotificationPermission() = true
- [ ] **FCM Token**: users/{userId}.fcmToken có giá trị
- [ ] **Thành viên nhóm**: users/{userId} có trong groups/{groupId}.memberIds
- [ ] **Listener active**: ChatNotificationHelper.startListeningForChatNotifications() đã được gọi
- [ ] **Firestore rules**: Cho phép read/write collections notifications

### 🐛 Lỗi thường gặp:

#### 1. "User not logged in"
**Giải pháp**: Đảm bảo đăng nhập thành công trước khi gửi tin nhắn

#### 2. "No notification permission"  
**Giải pháp**: Request permission hoặc mở Settings manually

#### 3. "FCM Token exists: false"
**Giải pháp**: 
```java
// Gọi trong MainActivity
NotificationHelper.updateCurrentToken();
```

#### 4. "Group not found" 
**Giải pháp**: Kiểm tra groupId có đúng không

#### 5. "No member IDs found"
**Giải pháp**: Kiểm tra trường memberIds trong document nhóm

### 📱 Test trên device thật:

#### Tình huống test:
1. **Device A** gửi tin nhắn → **Device B** phải nhận notification
2. **App ở background** → Vẫn nhận notification  
3. **App ở foreground** → Nhận notification và badge update
4. **Tự gửi tin nhắn** → KHÔNG nhận notification (đúng)

#### Log cần xem:
```
D/ChatRepository: Sending notifications for group: [groupId]
D/ChatRepository: Sending notification to member: [memberId]  
D/ChatNotificationHelper: New notification received: [notificationId]
D/ChatNotificationHelper: Showing notification for message from: [senderName]
D/ChatNotificationService: Showing notification with ID: [id]
```

### 🔧 Fix nhanh:

#### Fix 1: Restart notification listener
```java
// Trong MainActivity.onResume()
if (chatNotificationHelper != null) {
    chatNotificationHelper.stopListeningForChatNotifications();
    chatNotificationHelper.startListeningForChatNotifications();  
}
```

#### Fix 2: Clear & recreate FCM token
```java
// Trong ProfileFragment
FirebaseMessaging.getInstance().deleteToken()
    .addOnCompleteListener(task -> {
        NotificationHelper.updateCurrentToken();
    });
```

#### Fix 3: Check Firestore Security Rules
```javascript
// Trong Firestore Rules
match /users/{userId}/notifications/{notificationId} {
  allow read, write: if request.auth != null && request.auth.uid == userId;
}
```

### 📞 Support:

Nếu vẫn không hoạt động, cung cấp logs sau:
1. Full logcat output với filter "ChatRepository|ChatNotificationHelper|ChatNotificationService"
2. Screenshot Firestore collections: users/{userId}/notifications  
3. Xác nhận quyền thông báo đã được cấp trong Settings

---
**Lưu ý**: Hệ thống notification hiện tại sử dụng Firestore realtime listener thay vì FCM push notification để đảm bảo tin cậy và đơn giản hóa setup. 