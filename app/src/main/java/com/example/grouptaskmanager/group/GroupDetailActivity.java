package com.example.grouptaskmanager.group;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spannable;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.grouptaskmanager.R;
import com.example.grouptaskmanager.databinding.ActivityGroupDetailBinding;
import com.example.grouptaskmanager.model.Group;
import com.example.grouptaskmanager.model.Task;
import com.example.grouptaskmanager.model.User;
import com.example.grouptaskmanager.repository.GroupRepository;
import com.example.grouptaskmanager.repository.TaskRepository;
import com.example.grouptaskmanager.repository.UserRepository;
import com.example.grouptaskmanager.task.TaskListActivity;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class GroupDetailActivity extends AppCompatActivity {

    private static final String TAG = "GroupDetailActivity";
    private ActivityGroupDetailBinding binding;
    private GroupRepository groupRepository;
    private UserRepository userRepository;
    private TaskRepository taskRepository;
    private String groupId;
    private Group currentGroup;
    private boolean isGroupCreator = false;
    private Map<String, User> userCache = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityGroupDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        groupRepository = new GroupRepository();
        userRepository = new UserRepository();
        taskRepository = new TaskRepository();

        // Lấy groupId từ intent
        groupId = getIntent().getStringExtra("GROUP_ID");
        if (groupId == null) {
            Toast.makeText(this, "Không tìm thấy thông tin nhóm", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setupToolbar();
        setupListeners();
        loadGroupDetails();
        loadTaskStats();
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            
            // Set title with white color using SpannableString
            SpannableString title = new SpannableString(getString(R.string.group_details));
            title.setSpan(new ForegroundColorSpan(getResources().getColor(android.R.color.white)), 
                0, title.length(), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
            getSupportActionBar().setTitle(title);
        }
    }

    private void setupListeners() {
        binding.btnLeaveGroup.setOnClickListener(v -> showLeaveGroupDialog());
        
        binding.btnShareInvite.setOnClickListener(v -> {
            if (currentGroup != null && currentGroup.getInviteCode() != null) {
                shareInviteCode(currentGroup.getInviteCode());
            }
        });
        
        binding.btnGenerateQr.setOnClickListener(v -> {
            // Implement QR code generation in future feature
            Toast.makeText(this, "Tính năng đang được phát triển", Toast.LENGTH_SHORT).show();
        });
        
        binding.btnViewTasks.setOnClickListener(v -> {
            Intent intent = new Intent(this, TaskListActivity.class);
            intent.putExtra("GROUP_ID", groupId);
            intent.putExtra("GROUP_NAME", currentGroup.getName());
            startActivity(intent);
        });
        
        binding.btnGenerateInvite.setOnClickListener(v -> {
            if (isGroupCreator) {
                generateNewInviteCode();
            } else {
                Toast.makeText(this, "Chỉ người tạo nhóm mới có thể tạo mã mời mới", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadGroupDetails() {
        showLoading(true);
        
        groupRepository.getGroupDetails(groupId)
                .addOnSuccessListener(this::processGroupDetails)
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Log.e(TAG, "Error loading group details", e);
                    Toast.makeText(this, "Lỗi khi tải thông tin nhóm", Toast.LENGTH_SHORT).show();
                });
                
        // Kiểm tra xem người dùng có phải là người tạo nhóm hay không
        groupRepository.isGroupCreator(groupId)
                .addOnSuccessListener(documentSnapshot -> {
                    isGroupCreator = (documentSnapshot != null);
                    invalidateOptionsMenu(); // Cập nhật menu với quyền tương ứng
                });
    }

    private void loadTaskStats() {
        taskRepository.getGroupTasks(groupId)
            .addOnSuccessListener(this::processTaskStats)
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error loading task statistics", e);
            });
    }

    private void processTaskStats(QuerySnapshot querySnapshot) {
        if (querySnapshot == null || querySnapshot.isEmpty()) {
            updateTaskStats(0, 0, 0);
            return;
        }

        List<Task> tasks = new ArrayList<>();
        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
            Task task = doc.toObject(Task.class);
            if (task != null) {
                tasks.add(task);
            }
        }

        int totalTasks = tasks.size();
        int completedTasks = 0;
        
        for (Task task : tasks) {
            if (Task.STATUS_DONE.equals(task.getStatus())) {
                completedTasks++;
            }
        }
        
        int completionRate = totalTasks > 0 ? (completedTasks * 100) / totalTasks : 0;
        
        updateTaskStats(totalTasks, tasks.size() - completedTasks, completionRate);
    }

    private void updateTaskStats(int totalTasks, int activeTasks, int completionRate) {
        binding.tvTasksCount.setText(String.valueOf(totalTasks));
        binding.tvMembersCount.setText(String.valueOf(currentGroup != null && currentGroup.getMembers() != null ? 
                currentGroup.getMembers().size() : 0));
        binding.tvCompletionRate.setText(completionRate + "%");
    }

    private void processGroupDetails(DocumentSnapshot document) {
        if (document.exists()) {
            currentGroup = document.toObject(Group.class);
            
            if (currentGroup != null) {
                displayGroupInfo();
                loadGroupMembers();
            }
        } else {
            Toast.makeText(this, "Không tìm thấy thông tin nhóm", Toast.LENGTH_SHORT).show();
            finish();
        }
        
        showLoading(false);
    }

    private void displayGroupInfo() {
        binding.tvGroupName.setText(currentGroup.getName());
        
        // Hiển thị mô tả nếu có
        if (currentGroup.getDescription() != null && !currentGroup.getDescription().isEmpty()) {
            binding.tvGroupDescription.setText(currentGroup.getDescription());
            binding.tvGroupDescription.setVisibility(View.VISIBLE);
        } else {
            binding.tvGroupDescription.setVisibility(View.GONE);
        }
        
        // Log để kiểm tra thông tin nhóm
        Log.d(TAG, "Group privacy: " + currentGroup.isPrivate());
        Log.d(TAG, "Invite code: " + currentGroup.getInviteCode());
        
        // Hiển thị mã mời nếu có
        if (currentGroup.getInviteCode() != null && !currentGroup.getInviteCode().isEmpty()) {
            binding.cardInviteCode.setVisibility(View.VISIBLE);
            binding.tvInviteCode.setText(currentGroup.getInviteCode());
            Log.d(TAG, "Displaying invite code: " + currentGroup.getInviteCode());
        } else {
            Log.d(TAG, "No invite code available");
            binding.cardInviteCode.setVisibility(View.GONE);
            
            // Thêm Toast để thông báo cho người dùng
            if (isGroupCreator) {
                Toast.makeText(this, "Nhóm này chưa có mã mời. Hãy cập nhật nhóm để tạo mã mời.", Toast.LENGTH_SHORT).show();
            }
        }
        
        // Load thông tin người tạo và hiển thị với ngày tạo
        if (currentGroup.getCreatedBy() != null) {
            loadUserDetails(currentGroup.getCreatedBy(), user -> {
                String createdInfo = "Người tạo: ";
                if (user != null && user.getName() != null) {
                    createdInfo += user.getName();
                } else {
                    createdInfo += "Không xác định";
                }
                
                // Thêm thông tin ngày tạo
                Timestamp createdAt = currentGroup.getCreatedAt();
                if (createdAt != null) {
                    Date date = createdAt.toDate();
                    SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                    createdInfo += " • " + dateFormat.format(date);
                }
                
                binding.tvCreatedBy.setText(createdInfo);
            });
        } else {
            // Nếu không có thông tin người tạo, chỉ hiển thị ngày tạo
            Timestamp createdAt = currentGroup.getCreatedAt();
            if (createdAt != null) {
                Date date = createdAt.toDate();
                SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                binding.tvCreatedBy.setText("Ngày tạo: " + dateFormat.format(date));
            }
        }
    }

    private void loadGroupMembers() {
        if (currentGroup.getMembers() == null || currentGroup.getMembers().isEmpty()) {
            return;
        }
        
        showLoading(true);
        
        List<String> memberIds = currentGroup.getMembers();
        List<User> membersList = new ArrayList<>();
        final int[] processedCount = {0};
        
        for (String userId : memberIds) {
            // Kiểm tra xem đã có thông tin user trong cache chưa
            if (userCache.containsKey(userId)) {
                membersList.add(userCache.get(userId));
                processedCount[0]++;
                
                // Nếu đã xử lý tất cả thành viên, hiển thị danh sách
                if (processedCount[0] == memberIds.size()) {
                    displayMembersList(membersList);
                    showLoading(false);
                }
            } else {
                // Nếu chưa có trong cache, lấy từ Firestore
                userRepository.getUserById(userId)
                    .addOnSuccessListener(documentSnapshot -> {
                        User user = documentSnapshot.toObject(User.class);
                        if (user != null) {
                            // Đảm bảo ID được thiết lập
                            user.setId(documentSnapshot.getId());
                            userCache.put(userId, user);
                            membersList.add(user);
                        }
                        
                        processedCount[0]++;
                        
                        // Nếu đã xử lý tất cả thành viên, hiển thị danh sách
                        if (processedCount[0] == memberIds.size()) {
                            displayMembersList(membersList);
                            showLoading(false);
                        }
                    })
                    .addOnFailureListener(e -> {
                        processedCount[0]++;
                        Log.e(TAG, "Error loading user data for " + userId, e);
                        
                        // Nếu đã xử lý tất cả thành viên, hiển thị danh sách
                        if (processedCount[0] == memberIds.size()) {
                            displayMembersList(membersList);
                            showLoading(false);
                        }
                    });
            }
        }
    }

    private void displayMembersList(List<User> members) {
        // Sử dụng RecyclerView.Adapter để hiển thị danh sách thành viên
        MembersAdapter adapter = new MembersAdapter(members);
        binding.rvMembers.setLayoutManager(new LinearLayoutManager(this));
        binding.rvMembers.setAdapter(adapter);
    }

    private void loadUserDetails(String userId, OnUserLoadedListener listener) {
        // Kiểm tra xem đã có thông tin user trong cache chưa
        if (userCache.containsKey(userId)) {
            listener.onUserLoaded(userCache.get(userId));
            return;
        }
        
        // Nếu chưa có trong cache, lấy từ Firestore
        userRepository.getUserById(userId)
            .addOnSuccessListener(documentSnapshot -> {
                User user = documentSnapshot.toObject(User.class);
                if (user != null) {
                    // Đảm bảo ID được thiết lập
                    user.setId(documentSnapshot.getId());
                    userCache.put(userId, user);
                }
                listener.onUserLoaded(user);
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error loading user details for " + userId, e);
                listener.onUserLoaded(null);
            });
    }

    private void shareInviteCode(String inviteCode) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name));
        
        String shareMessage = "Tham gia nhóm " + currentGroup.getName() + " với mã mời: " + inviteCode;
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareMessage);
        
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_invite_code)));
    }

    private void showLeaveGroupDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.leave_group)
                .setMessage(R.string.confirm_leave_group)
                .setPositiveButton(R.string.yes, (dialog, which) -> leaveGroup())
                .setNegativeButton(R.string.no, null)
                .show();
    }

    private void showDeleteGroupDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_group)
                .setMessage(R.string.confirm_delete_group)
                .setPositiveButton(R.string.yes, (dialog, which) -> deleteGroup())
                .setNegativeButton(R.string.no, null)
                .show();
    }

    private void leaveGroup() {
        showLoading(true);
        
        groupRepository.leaveGroup(groupId)
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    Toast.makeText(GroupDetailActivity.this, "Đã rời khỏi nhóm", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Log.e(TAG, "Error leaving group", e);
                    Toast.makeText(GroupDetailActivity.this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void deleteGroup() {
        showLoading(true);
        
        groupRepository.deleteGroup(groupId)
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    Toast.makeText(GroupDetailActivity.this, "Đã xóa nhóm", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Log.e(TAG, "Error deleting group", e);
                    Toast.makeText(GroupDetailActivity.this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void generateNewInviteCode() {
        if (currentGroup == null) return;
        
        showLoading(true);
        
        groupRepository.generateNewInviteCode(groupId)
            .addOnSuccessListener(aVoid -> {
                showLoading(false);
                Toast.makeText(this, "Đã tạo mã mời mới", Toast.LENGTH_SHORT).show();
                
                // Tải lại thông tin nhóm để lấy mã mời mới
                loadGroupDetails();
            })
            .addOnFailureListener(e -> {
                showLoading(false);
                Log.e(TAG, "Error generating new invite code", e);
                Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_group_detail, menu);
        
        // Chỉ hiển thị menu edit và delete nếu người dùng là người tạo nhóm
        menu.findItem(R.id.action_edit_group).setVisible(isGroupCreator);
        menu.findItem(R.id.action_delete_group).setVisible(isGroupCreator);
        
        // Tô màu trắng cho các menu items
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            if (item.getIcon() != null) {
                item.getIcon().setTint(getResources().getColor(android.R.color.white));
            }
        }
        
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        
        if (itemId == android.R.id.home) {
            onBackPressed();
            return true;
        } else if (itemId == R.id.action_edit_group) {
            // Chuyển đến màn hình chỉnh sửa nhóm
            Intent intent = new Intent(this, EditGroupActivity.class);
            intent.putExtra("GROUP_ID", groupId);
            startActivity(intent);
            return true;
        } else if (itemId == R.id.action_delete_group) {
            showDeleteGroupDialog();
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }

    private void showLoading(boolean isLoading) {
        binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
    }

    interface OnUserLoadedListener {
        void onUserLoaded(User user);
    }

    private class MembersAdapter extends RecyclerView.Adapter<MembersAdapter.MemberViewHolder> {

        private final List<User> membersList;

        public MembersAdapter(List<User> membersList) {
            this.membersList = membersList;
        }

        @NonNull
        @Override
        public MemberViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = getLayoutInflater().inflate(R.layout.item_member, parent, false);
            return new MemberViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull MemberViewHolder holder, int position) {
            User user = membersList.get(position);
            holder.textView.setText(user.getName());
            
            // Highlight group creator if applicable
            if (currentGroup != null && user.getId() != null && 
                user.getId().equals(currentGroup.getCreatedBy())) {
                holder.textView.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_star, 0);
            } else {
                holder.textView.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            }
        }

        @Override
        public int getItemCount() {
            return membersList.size();
        }

        class MemberViewHolder extends RecyclerView.ViewHolder {
            final TextView textView;

            MemberViewHolder(@NonNull View itemView) {
                super(itemView);
                textView = itemView.findViewById(android.R.id.text1);
            }
        }
    }
} 