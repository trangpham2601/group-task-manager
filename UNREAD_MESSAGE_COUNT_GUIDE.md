# Tính Năng Hiển Thị Số Tin Nhắn Chưa Đọc

## ✅ Đã Hoàn Thành

### 1. **Badge Trên Danh Sách Chat (ChatFragment)**
- ✅ Hiển thị số tin nhắn chưa đọc cho mỗi nhóm
- ✅ Badge màu đỏ với số lượng (99+ nếu > 99)
- ✅ Cập nhật realtime khi có tin nhắn mới
- ✅ Tự động ẩn khi không có tin nhắn chưa đọc

### 2. **Badge Trên Tab Chat (BottomNavigation)**
- ✅ Hiển thị tổng số tin nhắn chưa đọc của tất cả nhóm
- ✅ Badge màu đỏ trên icon chat
- ✅ Cập nhật realtime
- ✅ Tự động ẩn khi không có tin nhắn chưa đọc

### 3. **Cập Nhật Realtime**
- ✅ Tự động refresh khi có tin nhắn mới
- ✅ Tự động cập nhật khi đọc tin nhắn
- ✅ Sử dụng Firestore listeners hiệu quả

## Cách Hoạt Động

### Logic Tính Toán Unread Count
```java
// Trong ChatRepository.getUnreadMessagesCount()
1. Lấy thời gian đọc cuối của user trong nhóm
2. Đếm tin nhắn được gửi sau thời gian đó
3. Loại trừ tin nhắn của chính user
4. Trả về số lượng tin nhắn chưa đọc
```

### Cập Nhật Realtime
```java
// ChatFragment
- Lắng nghe thay đổi trong notifications collection
- Cập nhật unread count cho từng nhóm khi có tin nhắn mới
- Refresh UI ngay lập tức

// MainActivity
- Lắng nghe tổng unread count
- Cập nhật badge trên tab chat
- Hiển thị/ẩn badge tự động
```

### Đánh Dấu Đã Đọc
```java
// GroupChatActivity.markGroupAsRead()
- Được gọi khi user mở cuộc trò chuyện (onResume)
- Lưu timestamp vào userReads collection
- Tự động cập nhật unread count = 0
```

## Thiết Kế UI

### Badge Trên Item Chat (`item_group_chat.xml`)
```xml
<TextView
    android:id="@+id/tv_unread_badge"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:layout_gravity="top|end"
    android:layout_marginTop="-8dp"
    android:layout_marginEnd="-8dp"
    android:background="@drawable/unread_badge_background"
    android:gravity="center"
    android:textColor="@color/unread_badge_text"
    android:textSize="10sp"
    android:textStyle="bold"
    android:elevation="8dp" />
```

### Màu Sắc
```xml
<!-- colors.xml -->
<color name="unread_badge_background">#FF4444</color>
<color name="unread_badge_text">#FFFFFF</color>
```

### Background Shape
```xml
<!-- unread_badge_background.xml -->
<shape android:shape="oval">
    <solid android:color="@color/unread_badge_background" />
    <size android:width="18dp" android:height="18dp" />
    <padding android:left="3dp" android:right="3dp" 
             android:top="1dp" android:bottom="1dp" />
</shape>
```

## Luồng Hoạt Động

### Khi có tin nhắn mới:
1. **Gửi tin nhắn** → `ChatRepository.sendMessage()`
2. **Tạo notification** → Lưu vào `users/{userId}/notifications`
3. **Trigger listener** → `ChatFragment` và `MainActivity` nhận update
4. **Cập nhật UI** → Badge hiển thị số mới

### Khi đọc tin nhắn:
1. **Mở chat** → `GroupChatActivity.onResume()`
2. **Mark as read** → `ChatRepository.markGroupAsRead()`
3. **Update timestamp** → Lưu vào `userReads/{userId}`
4. **Cập nhật count** → Badge tự động ẩn hoặc giảm số

### Refresh khi quay lại:
1. **ChatFragment.onResume()** → Reload unread counts
2. **MainActivity.onResume()** → Update total badge
3. **Realtime listeners** → Tiếp tục cập nhật

## Performance

### Tối Ưu Hóa
- ✅ Chỉ listen khi app ở foreground (onResume/onPause)
- ✅ Cleanup listeners khi không cần (onDestroy)
- ✅ Sử dụng efficient Firestore queries
- ✅ Update UI trên main thread

### Memory Management
- ✅ Remove listeners properly
- ✅ Use application context cho singleton
- ✅ Avoid memory leaks với lifecycle awareness

## Cấu Trúc Code

### Models
```
GroupWithUnread.java
├── Group group
├── int unreadCount  
├── ChatMessage lastMessage
└── boolean hasUnreadMessages()
```

### Repository Methods
```
ChatRepository.java
├── getUnreadMessagesCount(groupId, userId)
├── getTotalUnreadMessagesCount(userId)
├── listenForUnreadUpdates(userId, listener)
└── markGroupAsRead(groupId)
```

### UI Components
```
ChatFragment.java
├── loadGroupsWithUnreadCounts()
├── startListeningForUnreadUpdates()
└── onUnreadUpdate(groupId, count)

GroupChatAdapter.java
├── bind(GroupWithUnread)
└── Update badge visibility/text

MainActivity.java
├── updateChatBadge()
└── startListeningForTotalUnreadCount()
```

## Testing

### Test Cases
1. **Gửi tin nhắn** → Badge tăng ngay lập tức
2. **Đọc tin nhắn** → Badge giảm hoặc ẩn
3. **Nhiều nhóm** → Tổng badge đúng
4. **Restart app** → Badge vẫn đúng
5. **Background/foreground** → Listeners hoạt động

### Debug Logs
```
ChatRepository: Notification saved for user: {userId}
ChatNotificationHelper: Started listening for user: {userId} 
MainActivity: Total unread count: {count}
GroupChatActivity: Group marked as read
```

## Kết Quả

### ✅ **Tính năng hoàn chỉnh:**
- 🔴 Badge đỏ trên mỗi nhóm chat với số tin nhắn chưa đọc
- 🔴 Badge đỏ trên tab chat với tổng số tin nhắn
- 🔄 Cập nhật realtime không delay
- 👁️ Tự động ẩn/hiện dựa trên trạng thái đọc
- 📱 UX mượt mà và responsive

### 🎯 **User Experience:**
- User luôn biết nhóm nào có tin nhắn mới
- Dễ dàng theo dõi tổng số tin nhắn chưa đọc
- Không bỏ sót tin nhắn quan trọng
- Interface trực quan và dễ hiểu 