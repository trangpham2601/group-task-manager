# Hệ Thống Thông Báo Chat - Group Task Manager

## Tổng Quan
Hệ thống thông báo chat đã được hoàn thiện để gửi thông báo đến các thành viên trong nhóm khi có tin nhắn mới.

## Cách Hoạt Động

### 1. Khi Gửi Tin Nhắn Mới
```java
// Trong ChatRepository.sendMessage()
1. Tin nhắn được lưu vào Firestore collection: groups/{groupId}/messages
2. Hệ thống tự động lấy danh sách thành viên của nhóm
3. Tạo thông báo cho từng thành viên (trừ người gửi)
4. Lưu thông báo vào collection: users/{userId}/notifications
```

### 2. Hệ Thống Thông Báo Realtime
```java
// ChatNotificationHelper
- Lắng nghe realtime các thông báo mới trong users/{userId}/notifications
- Tự động hiển thị notification khi có tin nhắn mới
- Xóa thông báo sau khi đã hiển thị để tránh trùng lặp
```

### 3. Quản Lý FCM Token
```java
// NotificationHelper
- Tự động cập nhật FCM token khi user đăng nhập
- Lưu token vào Firestore: users/{userId}.fcmToken
- Token được sử dụng để gửi push notification (sẽ implement sau)
```

## Luồng Hoạt Động Cụ Thể

### Khi User A gửi tin nhắn cho nhóm:
1. **Lưu tin nhắn**: `groups/groupId/messages` ← tin nhắn mới
2. **Tạo thông báo**: Với mỗi thành viên khác (User B, C, D...)
   ```
   users/userB/notifications ← {
     type: "chat_message",
     groupId: "...",
     groupName: "...",
     senderName: "User A",
     message: "Nội dung tin nhắn",
     senderId: "userA_id"
   }
   ```
3. **Hiển thị thông báo**: ChatNotificationHelper phát hiện thông báo mới
4. **Show notification**: Android notification được hiển thị
5. **Cleanup**: Thông báo được xóa khỏi Firestore

### Khi nhấn vào thông báo:
1. Mở `GroupChatActivity` với `GROUP_ID` và `GROUP_NAME`
2. Tự động cuộn đến tin nhắn mới nhất
3. Đánh dấu nhóm đã đọc

## Tính Năng

### ✅ Đã Hoàn Thành
- [x] Gửi thông báo realtime trong ứng dụng
- [x] Tự động lấy danh sách thành viên nhóm
- [x] Không gửi thông báo cho chính người gửi
- [x] Quản lý FCM token
- [x] Xử lý quyền thông báo (Android 13+)
- [x] Tự động cleanup thông báo đã hiển thị
- [x] Intent để mở chat khi nhấn thông báo

### 🔄 Đang Phát Triển
- [ ] Push notification khi app ở background
- [ ] Nhóm thông báo theo group
- [ ] Sound và vibration tùy chỉnh
- [ ] Notification badges

## Cấu Trúc File

```
notification/
├── ChatNotificationHelper.java    # Quản lý thông báo realtime
├── ChatNotificationService.java   # FCM service (cho background)
├── NotificationHelper.java        # Utilities và FCM token
└── TaskMessagingService.java      # Thông báo task

repository/
└── ChatRepository.java           # Logic gửi thông báo

MainActivity.java                  # Initialize notification system
```

## Cách Sử Dụng

### Để bật thông báo chat:
1. Ứng dụng tự động yêu cầu quyền thông báo
2. ChatNotificationHelper tự động bắt đầu lắng nghe
3. Không cần cấu hình thêm gì

### Để test thông báo:
1. Đăng nhập 2 tài khoản trên 2 thiết bị khác nhau
2. Tham gia cùng một nhóm
3. Gửi tin nhắn từ thiết bị A
4. Thiết bị B sẽ nhận được thông báo

## Lưu Ý Kỹ Thuật

### Performance
- Chỉ lắng nghe thông báo khi app ở foreground (onResume/onPause)
- Tự động cleanup notifications đã xử lý
- Sử dụng Firestore realtime listeners hiệu quả

### Security
- Chỉ gửi thông báo cho thành viên của nhóm
- Không gửi thông báo cho chính người gửi
- Validate permissions trước khi hiển thị

### Tương thích
- Android 6.0+ (API 23+)
- Xử lý đặc biệt cho Android 13+ notification permission
- Tương thích với tất cả thiết bị Android

## Troubleshooting

### Không nhận được thông báo?
1. Kiểm tra quyền thông báo: Settings > Apps > Group Task Manager > Notifications
2. Đảm bảo đã đăng nhập và tham gia nhóm
3. Kiểm tra internet connection
4. Xem logcat để debug: `ChatNotificationHelper` tag

### Thông báo trùng lặp?
- Hệ thống tự động xóa notifications đã hiển thị
- Nếu vẫn bị, restart app để reset listeners

### FCM token không cập nhật?
- Token tự động cập nhật khi app khởi động
- Check Firestore: `users/{userId}.fcmToken` phải có giá trị 