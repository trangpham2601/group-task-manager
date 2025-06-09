package com.example.grouptaskmanager;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.example.grouptaskmanager.auth.LoginActivity;
import com.example.grouptaskmanager.chat.ChatFragment;
import com.example.grouptaskmanager.databinding.ActivityMainBinding;
import com.example.grouptaskmanager.group.GroupsFragment;
import com.example.grouptaskmanager.notification.ChatNotificationHelper;
import com.example.grouptaskmanager.profile.ProfileFragment;
import com.example.grouptaskmanager.statistics.StatisticsFragment;
import com.example.grouptaskmanager.task.TasksFragment;
import com.example.grouptaskmanager.utils.NotificationPermissionHelper;
import com.example.grouptaskmanager.repository.ChatRepository;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.badge.BadgeDrawable;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private FirebaseAuth auth;
    private NotificationPermissionHelper notificationPermissionHelper;
    private ChatNotificationHelper chatNotificationHelper;
    private ChatRepository chatRepository;
    private ListenerRegistration totalUnreadListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();
        checkUserAuthentication();

        // Initialize notification permission helper
        notificationPermissionHelper = new NotificationPermissionHelper(this);
        
        // Initialize chat notification helper
        chatNotificationHelper = ChatNotificationHelper.getInstance(this);
        
        // Initialize chat repository for badge updates
        chatRepository = new ChatRepository();
        
        // Initialize FCM token for current user
        com.example.grouptaskmanager.notification.NotificationHelper.updateCurrentToken();

        // Ẩn FAB mặc định (không cần thiết vì đã có các FAB trong GroupsFragment)
        binding.fab.setVisibility(View.GONE);
        
        // Thiết lập bottom navigation
        setupBottomNavigation();
        
        // Hiển thị GroupsFragment khi khởi động
        if (savedInstanceState == null) {
            loadFragment(new GroupsFragment());
        }

        // Request notification permission after a short delay
        // để user có thời gian làm quen với app trước
        binding.getRoot().postDelayed(this::requestNotificationPermissionIfNeeded, 2000);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Start listening for chat notifications when app comes to foreground
        if (chatNotificationHelper != null) {
            chatNotificationHelper.startListeningForChatNotifications();
        }
        
        // Start listening for unread count updates for badge
        startListeningForTotalUnreadCount();
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        // Stop listening when app goes to background to save battery
        if (chatNotificationHelper != null) {
            chatNotificationHelper.stopListeningForChatNotifications();
        }
        
        // Stop listening for unread updates
        stopListeningForTotalUnreadCount();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopListeningForTotalUnreadCount();
    }
    
    private void checkUserAuthentication() {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            // Nếu chưa đăng nhập, chuyển đến màn hình đăng nhập
            Intent intent = new Intent(this, LoginActivity.class);
            startActivity(intent);
            finish();
        }
    }
    
    private void setupBottomNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        bottomNav.setOnItemSelectedListener(this::onNavigationItemSelected);
    }
    
    private boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        
        if (itemId == R.id.navigation_groups) {
            loadFragment(new GroupsFragment());
            return true;
        } else if (itemId == R.id.navigation_tasks) {
            loadFragment(new TasksFragment());
            return true;
        } else if (itemId == R.id.navigation_statistics) {
            loadFragment(new StatisticsFragment());
            return true;
        } else if (itemId == R.id.navigation_chat) {
            loadFragment(new ChatFragment());
            return true;
        } else if (itemId == R.id.navigation_profile) {
            loadFragment(new ProfileFragment());
            return true;
        }
        
        return false;
    }

    private void loadFragment(Fragment fragment) {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.nav_host_fragment_content_main, fragment);
        transaction.commitAllowingStateLoss();
    }

    public void navigateToGroupsTab() {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        bottomNav.setSelectedItemId(R.id.navigation_groups);
        
        // Sử dụng commitAllowingStateLoss để tránh IllegalStateException
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.nav_host_fragment_content_main, new GroupsFragment());
        transaction.commitAllowingStateLoss();
    }

    public void navigateToTasksTab() {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        bottomNav.setSelectedItemId(R.id.navigation_tasks);
        
        // Sử dụng commitAllowingStateLoss để tránh IllegalStateException
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.nav_host_fragment_content_main, new TasksFragment());
        transaction.commitAllowingStateLoss();
    }

    /**
     * Request notification permission nếu chưa có
     */
    private void requestNotificationPermissionIfNeeded() {
        if (!NotificationPermissionHelper.hasNotificationPermission(this)) {
            notificationPermissionHelper.requestNotificationPermission(
                    new NotificationPermissionHelper.NotificationPermissionCallback() {
                        @Override
                        public void onPermissionGranted() {
                            // Permission granted - notifications will work
                        }

                        @Override
                        public void onPermissionDenied() {
                            // Permission denied - app will still work but no notifications
                            // We don't show any error dialog here to avoid being intrusive
                        }
                    }
            );
        }
    }

    /**
     * Public method để trigger notification permission request từ các activities khác
     */
    public void requestNotificationPermission() {
        notificationPermissionHelper.requestNotificationPermissionSilent();
    }

    /**
     * Check if notifications are enabled và show dialog nếu disabled
     */
    public void checkAndShowNotificationDialog() {
        if (!NotificationPermissionHelper.hasNotificationPermission(this)) {
            notificationPermissionHelper.showNotificationDisabledDialog();
        }
    }

    /**
     * Request notification permission specifically for ProfileFragment to avoid lifecycle issues
     */
    public void requestNotificationPermissionForProfile(ProfileFragment.PermissionResultCallback callback) {
        if (NotificationPermissionHelper.hasNotificationPermission(this)) {
            // Already have permission
            callback.onResult(true);
            return;
        }

        notificationPermissionHelper.requestNotificationPermission(
                new NotificationPermissionHelper.NotificationPermissionCallback() {
                    @Override
                    public void onPermissionGranted() {
                        callback.onResult(true);
                    }

                    @Override
                    public void onPermissionDenied() {
                        callback.onResult(false);
                    }
                }
        );
    }

    private void startListeningForTotalUnreadCount() {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null || chatRepository == null) return;
        
        // Listen for changes in groups and messages to update badge
        totalUnreadListener = chatRepository.listenForUnreadUpdates(
            currentUser.getUid(), 
            (groupId, unreadCount) -> {
                // When any group's unread count changes, update the total badge
                updateChatBadge();
            }
        );
        
        // Initial update
        updateChatBadge();
    }
    
    private void stopListeningForTotalUnreadCount() {
        if (totalUnreadListener != null) {
            totalUnreadListener.remove();
            totalUnreadListener = null;
        }
    }
    
    private void updateChatBadge() {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) return;
        
        // Calculate total unread messages across all groups
        chatRepository.getTotalUnreadMessagesCount(currentUser.getUid())
                .addOnSuccessListener(totalUnread -> {
                    runOnUiThread(() -> {
                        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
                        BadgeDrawable badge = bottomNav.getOrCreateBadge(R.id.navigation_chat);
                        
                        if (totalUnread > 0) {
                            badge.setVisible(true);
                            badge.setNumber(totalUnread);
                            badge.setMaxCharacterCount(2); // Show 99+ for numbers > 99
                        } else {
                            badge.setVisible(false);
                        }
                    });
                })
                .addOnFailureListener(e -> {
                    Log.e("MainActivity", "Error updating chat badge", e);
                });
    }
}