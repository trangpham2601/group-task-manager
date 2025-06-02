package com.example.grouptaskmanager.task;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.Toast;
import android.widget.TextView;
import android.widget.BaseAdapter;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.widget.TextViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.content.res.ColorStateList;

import com.example.grouptaskmanager.R;
import com.example.grouptaskmanager.adapter.CommentsAdapter;
import com.example.grouptaskmanager.databinding.ActivityTaskDetailBinding;
import com.example.grouptaskmanager.model.Comment;
import com.example.grouptaskmanager.model.Group;
import com.example.grouptaskmanager.model.Task;
import com.example.grouptaskmanager.model.User;
import com.example.grouptaskmanager.repository.CommentRepository;
import com.example.grouptaskmanager.repository.GroupRepository;
import com.example.grouptaskmanager.repository.TaskRepository;
import com.example.grouptaskmanager.repository.UserRepository;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;

public class TaskDetailActivity extends AppCompatActivity implements CommentsAdapter.OnCommentActionListener {

    private static final String TAG = "TaskDetailActivity";
    private ActivityTaskDetailBinding binding;
    private TaskRepository taskRepository;
    private GroupRepository groupRepository;
    private UserRepository userRepository;
    private CommentRepository commentRepository;
    private String groupId;
    private String taskId;
    private String groupName;
    private Task currentTask;
    private Calendar deadlineCalendar;
    private List<String> membersList;
    private List<User> membersUserList;
    private UserSpinnerAdapter membersAdapter;
    private boolean canModifyTask = false;
    private boolean isEditMode = false;
    private Map<String, User> userCache = new HashMap<>();
    
    // Comments related
    private CommentsAdapter commentsAdapter;
    private List<Comment> commentsList;
    private Comment replyingToComment;
    private String currentUserName;
    
    // Modern UI components
    private TextView tvTaskTitleDisplay;
    private TextView tvDeadlineChip;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTaskDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Lấy thông tin từ intent
        groupId = getIntent().getStringExtra("GROUP_ID");
        taskId = getIntent().getStringExtra("TASK_ID");
        groupName = getIntent().getStringExtra("GROUP_NAME");
        
        if (groupId == null || taskId == null) {
            Toast.makeText(this, "Không tìm thấy thông tin nhiệm vụ", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        taskRepository = new TaskRepository();
        groupRepository = new GroupRepository();
        userRepository = new UserRepository();
        commentRepository = new CommentRepository();
        membersList = new ArrayList<>();
        membersUserList = new ArrayList<>();
        commentsList = new ArrayList<>();
        deadlineCalendar = Calendar.getInstance();
        
        setupToolbar();
        setupComments();
        setupModernUI();
        checkModifyPermission();
        loadTaskDetails();
        loadGroupMembers();
        loadCurrentUserName();
        setupListeners();
        loadComments();
    }
    
    private void setupComments() {
        commentsAdapter = new CommentsAdapter(this);
        binding.recyclerComments.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerComments.setAdapter(commentsAdapter);
        binding.recyclerComments.setNestedScrollingEnabled(false);
    }
    
    private void setupModernUI() {
        tvTaskTitleDisplay = binding.tvTaskTitleDisplay;
        tvDeadlineChip = binding.tvDeadlineChip;
        
        // Setup status change button
        binding.btnChangeStatus.setOnClickListener(v -> toggleStatusSelector());
    }
    
    private void toggleStatusSelector() {
        if (binding.cardStatusSelector.getVisibility() == View.VISIBLE) {
            binding.cardStatusSelector.setVisibility(View.GONE);
        } else {
            binding.cardStatusSelector.setVisibility(View.VISIBLE);
        }
    }
    
    private void loadCurrentUserName() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            userRepository.getUserById(currentUser.getUid())
                .addOnSuccessListener(documentSnapshot -> {
                    User user = documentSnapshot.toObject(User.class);
                    if (user != null) {
                        currentUserName = user.getName() != null ? user.getName() : 
                                        (user.getEmail() != null ? user.getEmail() : currentUser.getUid());
                    } else {
                        currentUserName = currentUser.getEmail() != null ? currentUser.getEmail() : currentUser.getUid();
                    }
                })
                .addOnFailureListener(e -> {
                    currentUserName = currentUser.getEmail() != null ? currentUser.getEmail() : currentUser.getUid();
                });
        }
    }
    
    private void loadComments() {
        commentRepository.getTaskComments(groupId, taskId)
            .addOnSuccessListener(querySnapshot -> {
                commentsList.clear();
                for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                    Comment comment = doc.toObject(Comment.class);
                    if (comment != null) {
                        comment.setId(doc.getId());
                        commentsList.add(comment);
                    }
                }
                
                commentsAdapter.updateComments(commentsList);
                updateCommentsUI();
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error loading comments", e);
                Toast.makeText(this, "Lỗi tải bình luận: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
    }
    
    private void updateCommentsUI() {
        int commentCount = commentsList.size();
        binding.tvCommentCount.setText(String.valueOf(commentCount));
        
        if (commentCount == 0) {
            binding.tvNoComments.setVisibility(View.VISIBLE);
            binding.recyclerComments.setVisibility(View.GONE);
        } else {
            binding.tvNoComments.setVisibility(View.GONE);
            binding.recyclerComments.setVisibility(View.VISIBLE);
        }
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            
            // Đặt tiêu đề mặc định cho màn hình
            String title = "";
            if (groupName != null && !groupName.isEmpty()) {
                title = groupName + " - " + getString(R.string.task_detail);
            } else {
                title = getString(R.string.task_detail);
            }
            
            // Set the title directly on the toolbar
            getSupportActionBar().setTitle(title);
            
            // Đảm bảo các biểu tượng hiển thị màu trắng
            binding.toolbar.setTitleTextColor(getResources().getColor(android.R.color.white));
        }
    }

    private void checkModifyPermission() {
        taskRepository.canModifyTask(groupId, taskId)
                .addOnSuccessListener(canModify -> {
                    canModifyTask = canModify != null && canModify;
                    invalidateOptionsMenu();
                });
    }

    private void loadTaskDetails() {
        showLoading(true);
        
        taskRepository.getTaskDetails(groupId, taskId)
                .addOnSuccessListener(documentSnapshot -> {
                    currentTask = documentSnapshot.toObject(Task.class);
                    if (currentTask != null) {
                        displayTaskDetails();
                        loadUserData();
                    } else {
                        Toast.makeText(this, "Không tìm thấy thông tin nhiệm vụ", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                    showLoading(false);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading task details", e);
                    showLoading(false);
                    Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    private void loadUserData() {
        // Check if currentTask is null first
        if (currentTask == null) {
            Log.w(TAG, "currentTask is null, cannot load user data");
            return;
        }
        
        // Load creator info
        if (currentTask.getCreatedBy() != null) {
            userRepository.getUserById(currentTask.getCreatedBy())
                .addOnSuccessListener(documentSnapshot -> {
                    User creator = documentSnapshot.toObject(User.class);
                    if (creator != null) {
                        creator.setId(documentSnapshot.getId());
                        userCache.put(creator.getId(), creator);
                        updateCreatorInfo(creator);
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error loading creator info", e));
        }
        
        // Load assignee info for all members
        for (String memberId : membersList) {
            if (!userCache.containsKey(memberId)) {
                userRepository.getUserById(memberId)
                    .addOnSuccessListener(documentSnapshot -> {
                        User user = documentSnapshot.toObject(User.class);
                        if (user != null) {
                            user.setId(documentSnapshot.getId());
                            userCache.put(user.getId(), user);
                            updateMembersList();
                        }
                    })
                    .addOnFailureListener(e -> Log.e(TAG, "Error loading user data", e));
            }
        }
    }

    private void updateCreatorInfo(User creator) {
        if (creator != null) {
            String creatorName = creator.getName() != null ? creator.getName() : 
                                (creator.getEmail() != null ? creator.getEmail() : creator.getId());
            binding.tvCreatedBy.setText(getString(R.string.created_by, creatorName));
        }
    }

    private void updateMembersList() {
        membersUserList.clear();
        for (String memberId : membersList) {
            User user = userCache.get(memberId);
            if (user != null) {
                membersUserList.add(user);
            } else {
                // Create a temporary user with ID as name until real data is loaded
                User tempUser = new User(memberId, memberId, null);
                membersUserList.add(tempUser);
            }
        }
        
        if (membersAdapter != null) {
            membersAdapter.notifyDataSetChanged();
            
            // Re-select the current assignee - add null check for currentTask
            if (currentTask != null && currentTask.getAssignedTo() != null) {
                for (int i = 0; i < membersUserList.size(); i++) {
                    if (membersUserList.get(i).getId().equals(currentTask.getAssignedTo())) {
                        binding.spinnerAssignee.setSelection(i);
                        break;
                    }
                }
            }
        }
    }

    private void loadGroupMembers() {
        groupRepository.getGroupDetails(groupId)
                .addOnSuccessListener(documentSnapshot -> {
                    Group group = documentSnapshot.toObject(Group.class);
                    if (group != null && group.getMembers() != null) {
                        membersList.clear();
                        membersList.addAll(group.getMembers());
                        setupMembersSpinner();
                        loadUserData(); // Load user data after getting members
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading group members", e);
                });
    }

    private void setupMembersSpinner() {
        // Initialize with existing data
        updateMembersList();
        
        // Create custom adapter
        membersAdapter = new UserSpinnerAdapter();
        binding.spinnerAssignee.setAdapter(membersAdapter);
        
        // Nếu đã có dữ liệu task, chọn người được giao hiện tại - add null check
        if (currentTask != null && currentTask.getAssignedTo() != null) {
            for (int i = 0; i < membersUserList.size(); i++) {
                if (membersUserList.get(i).getId().equals(currentTask.getAssignedTo())) {
                    binding.spinnerAssignee.setSelection(i);
                    break;
                }
            }
        }
    }

    private void displayTaskDetails() {
        // Add null check at the beginning
        if (currentTask == null) {
            Log.w(TAG, "currentTask is null, cannot display task details");
            Toast.makeText(this, "Không tìm thấy thông tin nhiệm vụ", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        // Update hero card
        updateHeroCard();
        
        // Hiển thị tiêu đề và toolbar
        if (currentTask.getTitle() != null && !currentTask.getTitle().isEmpty()) {
            // Keep the group name in title if available
            String title;
            if (groupName != null && !groupName.isEmpty()) {
                title = groupName + " - " + currentTask.getTitle();
            } else {
                title = currentTask.getTitle();
            }
            
            // Set title on support action bar
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle(title);
            }
        }
        
        // Hiển thị thông tin trong form
        binding.etTaskTitle.setText(currentTask.getTitle() != null ? currentTask.getTitle() : "");
        binding.etTaskDescription.setText(currentTask.getDescription() != null ? currentTask.getDescription() : "");
        
        // Hiển thị deadline
        if (currentTask.getDeadline() != null) {
            Date date = currentTask.getDeadline().toDate();
            deadlineCalendar.setTime(date);
            updateDeadlineText();
            updateDeadlineChip();
        }
        
        // Hiển thị metadata
        if (currentTask.getCreatedAt() != null) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
            String createdDate = dateFormat.format(currentTask.getCreatedAt().toDate());
            binding.tvCreatedDate.setText(getString(R.string.created_date, createdDate));
        }
        
        if (currentTask.getUpdatedAt() != null) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
            String updatedDate = dateFormat.format(currentTask.getUpdatedAt().toDate());
            binding.tvUpdatedDate.setText(getString(R.string.updated_date, updatedDate));
        }
        
        // Chọn radio button cho mức độ ưu tiên
        if (currentTask.getPriority() != null) {
            switch (currentTask.getPriority()) {
                case Task.PRIORITY_HIGH:
                    binding.radioPriorityHigh.setChecked(true);
                    break;
                case Task.PRIORITY_MEDIUM:
                    binding.radioPriorityMedium.setChecked(true);
                    break;
                case Task.PRIORITY_LOW:
                    binding.radioPriorityLow.setChecked(true);
                    break;
            }
        }
        
        // Update status chips and buttons
        updateStatusChips();
        setupStatusButtons();
        
        // Mặc định hiển thị ở chế độ xem
        setEditMode(false);
    }

    private void updateHeroCard() {
        if (currentTask == null) return;
        
        // Update task title in hero card
        if (tvTaskTitleDisplay != null && currentTask.getTitle() != null) {
            tvTaskTitleDisplay.setText(currentTask.getTitle());
        }
        
        updateStatusChips();
        updateDeadlineChip();
    }

    private void updateDeadlineText() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        String formattedDate = dateFormat.format(deadlineCalendar.getTime());
        binding.tvDeadline.setText(formattedDate);
    }

    private void updateDeadlineChip() {
        if (currentTask != null && currentTask.getDeadline() != null && tvDeadlineChip != null) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            String formattedDate = dateFormat.format(currentTask.getDeadline().toDate());
            tvDeadlineChip.setText(formattedDate);
            tvDeadlineChip.setVisibility(View.VISIBLE);
        } else if (tvDeadlineChip != null) {
            tvDeadlineChip.setVisibility(View.GONE);
        }
    }

    private void updateStatusChips() {
        if (currentTask == null) return;
        
        // Update status chip
        if (currentTask.getStatus() != null) {
            switch (currentTask.getStatus()) {
                case Task.STATUS_TODO:
                    binding.chipStatus.setText("Cần làm");
                    binding.chipStatus.setChipBackgroundColorResource(R.color.task_todo);
                    break;
                case Task.STATUS_IN_PROGRESS:
                    binding.chipStatus.setText("Đang làm");
                    binding.chipStatus.setChipBackgroundColorResource(R.color.task_in_progress);
                    break;
                case Task.STATUS_DONE:
                    binding.chipStatus.setText("Hoàn thành");
                    binding.chipStatus.setChipBackgroundColorResource(R.color.task_done);
                    break;
            }
        }
        
        // Update priority chip
        if (currentTask.getPriority() != null) {
            switch (currentTask.getPriority()) {
                case Task.PRIORITY_HIGH:
                    binding.chipPriority.setText("Cao");
                    binding.chipPriority.setChipIconTintResource(R.color.priority_high);
                    break;
                case Task.PRIORITY_MEDIUM:
                    binding.chipPriority.setText("Trung bình");
                    binding.chipPriority.setChipIconTintResource(R.color.priority_medium);
                    break;
                case Task.PRIORITY_LOW:
                    binding.chipPriority.setText("Thấp");
                    binding.chipPriority.setChipIconTintResource(R.color.priority_low);
                    break;
            }
        }
    }

    private void setupStatusButtons() {
        // Add null check for currentTask
        if (currentTask == null) {
            Log.w(TAG, "currentTask is null, cannot setup status buttons");
            return;
        }
        
        // Thiết lập onClick cho các nút trạng thái
        binding.btnStatusTodo.setOnClickListener(v -> {
            updateTaskStatus(Task.STATUS_TODO);
            binding.cardStatusSelector.setVisibility(View.GONE);
        });
        binding.btnStatusInProgress.setOnClickListener(v -> {
            updateTaskStatus(Task.STATUS_IN_PROGRESS);
            binding.cardStatusSelector.setVisibility(View.GONE);
        });
        binding.btnStatusDone.setOnClickListener(v -> {
            updateTaskStatus(Task.STATUS_DONE);
            binding.cardStatusSelector.setVisibility(View.GONE);
        });
        
        // Visual feedback for selected status
        updateStatusButtonsAppearance();
    }
    
    private void updateStatusButtonsAppearance() {
        // Reset all buttons to outlined style
        resetStatusButton(binding.btnStatusTodo, R.color.task_todo);
        resetStatusButton(binding.btnStatusInProgress, R.color.task_in_progress);
        resetStatusButton(binding.btnStatusDone, R.color.task_done);
        
        // Highlight current status button
        if (currentTask != null && currentTask.getStatus() != null) {
            switch (currentTask.getStatus()) {
                case Task.STATUS_TODO:
                    setSelectedStatusButton(binding.btnStatusTodo, R.color.task_todo);
                    break;
                case Task.STATUS_IN_PROGRESS:
                    setSelectedStatusButton(binding.btnStatusInProgress, R.color.task_in_progress);
                    break;
                case Task.STATUS_DONE:
                    setSelectedStatusButton(binding.btnStatusDone, R.color.task_done);
                    break;
            }
        }
    }
    
    private void resetStatusButton(MaterialButton button, int colorResId) {
        button.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(android.R.color.transparent)));
        button.setTextColor(getResources().getColor(colorResId));
        button.setStrokeColorResource(colorResId);
    }
    
    private void setSelectedStatusButton(MaterialButton button, int colorResId) {
        button.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(colorResId)));
        button.setTextColor(getResources().getColor(R.color.white));
        button.setStrokeColorResource(colorResId);
    }

    private void updateTaskStatus(String status) {
        if (currentTask != null && !status.equals(currentTask.getStatus())) {
            showLoading(true);
            
            taskRepository.updateTaskStatus(groupId, taskId, status)
                    .addOnSuccessListener(aVoid -> {
                        currentTask.setStatus(status);
                        
                        // Cập nhật UI
                        updateStatusButtonsAppearance();
                        updateStatusChips(); // Update hero card chips
                        
                        // Update timestamp
                        currentTask.setUpdatedAt(Timestamp.now());
                        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
                        String updatedDate = dateFormat.format(currentTask.getUpdatedAt().toDate());
                        binding.tvUpdatedDate.setText(getString(R.string.updated_date, updatedDate));
                        
                        showLoading(false);
                        Toast.makeText(this, "Đã cập nhật trạng thái", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> {
                        showLoading(false);
                        Log.e(TAG, "Error updating task status", e);
                        Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        }
    }

    private void setupListeners() {
        // Thiết lập DatePicker cho deadline
        binding.tvDeadline.setOnClickListener(v -> {
            if (isEditMode) {
                showDatePickerDialog();
            }
        });
        
        // Thiết lập nút lưu
        binding.btnSaveTask.setOnClickListener(v -> {
            if (validateInputs()) {
                updateTask();
            }
        });
        
        // Thiết lập nút hủy
        binding.btnCancel.setOnClickListener(v -> setEditMode(false));
        
        // Comment listeners
        binding.btnSendComment.setOnClickListener(v -> sendComment());
        binding.btnCancelReply.setOnClickListener(v -> cancelReply());
    }
    
    private void sendComment() {
        String content = binding.etComment.getText().toString().trim();
        if (TextUtils.isEmpty(content)) {
            Toast.makeText(this, "Vui lòng nhập nội dung bình luận", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (currentUserName == null) {
            Toast.makeText(this, "Đang tải thông tin người dùng...", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (replyingToComment != null) {
            // Send reply
            commentRepository.addReply(groupId, taskId, content, currentUserName, 
                                    replyingToComment.getId(), replyingToComment.getAuthorName())
                .addOnSuccessListener(documentReference -> {
                    binding.etComment.setText("");
                    cancelReply();
                    loadComments(); // Reload comments
                    Toast.makeText(this, "Đã gửi phản hồi", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error sending reply", e);
                    Toast.makeText(this, "Lỗi gửi phản hồi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
        } else {
            // Send normal comment
            commentRepository.addComment(groupId, taskId, content, currentUserName)
                .addOnSuccessListener(documentReference -> {
                    binding.etComment.setText("");
                    loadComments(); // Reload comments
                    Toast.makeText(this, "Đã gửi bình luận", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error sending comment", e);
                    Toast.makeText(this, "Lỗi gửi bình luận: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
        }
    }
    
    private void cancelReply() {
        replyingToComment = null;
        binding.layoutReplyIndicator.setVisibility(View.GONE);
        binding.etComment.setHint("Viết bình luận...");
    }

    // CommentsAdapter.OnCommentActionListener implementation
    @Override
    public void onReplyClick(Comment comment) {
        replyingToComment = comment;
        binding.layoutReplyIndicator.setVisibility(View.VISIBLE);
        binding.tvReplyingTo.setText("Đang trả lời @" + comment.getAuthorName());
        binding.etComment.setHint("Trả lời @" + comment.getAuthorName() + "...");
        binding.etComment.requestFocus();
    }

    @Override
    public void onEditClick(Comment comment) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Sửa bình luận");
        
        final EditText input = new EditText(this);
        input.setText(comment.getContent());
        input.setSelection(comment.getContent().length());
        builder.setView(input);
        
        builder.setPositiveButton("Lưu", (dialog, which) -> {
            String newContent = input.getText().toString().trim();
            if (!TextUtils.isEmpty(newContent)) {
                commentRepository.updateComment(comment.getId(), newContent)
                    .addOnSuccessListener(aVoid -> {
                        loadComments();
                        Toast.makeText(this, "Đã cập nhật bình luận", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error updating comment", e);
                        Toast.makeText(this, "Lỗi cập nhật bình luận: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
            }
        });
        
        builder.setNegativeButton("Hủy", null);
        builder.show();
    }

    @Override
    public void onDeleteClick(Comment comment) {
        new AlertDialog.Builder(this)
            .setTitle("Xóa bình luận")
            .setMessage("Bạn có chắc chắn muốn xóa bình luận này?")
            .setPositiveButton("Xóa", (dialog, which) -> {
                commentRepository.deleteComment(comment.getId())
                    .addOnSuccessListener(aVoid -> {
                        loadComments();
                        Toast.makeText(this, "Đã xóa bình luận", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error deleting comment", e);
                        Toast.makeText(this, "Lỗi xóa bình luận: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
            })
            .setNegativeButton("Hủy", null)
            .show();
    }

    private void showDatePickerDialog() {
        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    deadlineCalendar.set(Calendar.YEAR, year);
                    deadlineCalendar.set(Calendar.MONTH, month);
                    deadlineCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    updateDeadlineText();
                },
                deadlineCalendar.get(Calendar.YEAR),
                deadlineCalendar.get(Calendar.MONTH),
                deadlineCalendar.get(Calendar.DAY_OF_MONTH)
        );
        datePickerDialog.show();
    }

    private boolean validateInputs() {
        String title = binding.etTaskTitle.getText().toString().trim();
        
        if (TextUtils.isEmpty(title)) {
            binding.tilTaskTitle.setError(getString(R.string.error_task_title_empty));
            return false;
        }
        
        if (binding.spinnerAssignee.getSelectedItem() == null) {
            Toast.makeText(this, R.string.error_no_assignee, Toast.LENGTH_SHORT).show();
            return false;
        }
        
        binding.tilTaskTitle.setError(null);
        return true;
    }

    private void updateTask() {
        // Add null check for currentTask
        if (currentTask == null) {
            Log.w(TAG, "currentTask is null, cannot update task");
            Toast.makeText(this, "Lỗi: Không tìm thấy thông tin nhiệm vụ", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String title = binding.etTaskTitle.getText().toString().trim();
        String description = binding.etTaskDescription.getText().toString().trim();
        
        // Get the selected user ID
        User selectedUser = (User) binding.spinnerAssignee.getSelectedItem();
        String assignedTo = selectedUser.getId();
        
        Date deadlineDate = deadlineCalendar.getTime();
        Timestamp deadline = new Timestamp(deadlineDate);
        
        // Xác định mức độ ưu tiên từ RadioGroup
        String priority;
        int selectedPriorityId = binding.radioGroupPriority.getCheckedRadioButtonId();
        if (selectedPriorityId == R.id.radio_priority_high) {
            priority = Task.PRIORITY_HIGH;
        } else if (selectedPriorityId == R.id.radio_priority_medium) {
            priority = Task.PRIORITY_MEDIUM;
        } else {
            priority = Task.PRIORITY_LOW;
        }
        
        showLoading(true);
        
        taskRepository.updateTask(groupId, taskId, title, description, assignedTo, deadline, priority)
                .addOnSuccessListener(aVoid -> {
                    // Cập nhật thông tin task hiện tại
                    currentTask.setTitle(title);
                    currentTask.setDescription(description);
                    currentTask.setAssignedTo(assignedTo);
                    currentTask.setDeadline(deadline);
                    currentTask.setPriority(priority);
                    
                    if (getSupportActionBar() != null) {
                        getSupportActionBar().setTitle(title);
                    }
                    
                    // Cập nhật thời gian cập nhật
                    currentTask.setUpdatedAt(Timestamp.now());
                    SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
                    String updatedDate = dateFormat.format(currentTask.getUpdatedAt().toDate());
                    binding.tvUpdatedDate.setText(getString(R.string.updated_date, updatedDate));
                    
                    showLoading(false);
                    setEditMode(false);
                    Toast.makeText(this, R.string.task_updated_success, Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Log.e(TAG, "Error updating task", e);
                    Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void deleteTask() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_task)
                .setMessage(R.string.confirm_delete_task)
                .setPositiveButton(R.string.yes, (dialog, which) -> {
                    showLoading(true);
                    
                    taskRepository.deleteTask(groupId, taskId)
                            .addOnSuccessListener(aVoid -> {
                                showLoading(false);
                                Toast.makeText(this, R.string.task_deleted_success, Toast.LENGTH_SHORT).show();
                                finish();
                            })
                            .addOnFailureListener(e -> {
                                showLoading(false);
                                Log.e(TAG, "Error deleting task", e);
                                Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            });
                })
                .setNegativeButton(R.string.no, null)
                .show();
    }

    private void setEditMode(boolean editMode) {
        isEditMode = editMode;
        
        // Hiển thị/ẩn các view dựa trên mode
        binding.viewModeContainer.setVisibility(editMode ? View.GONE : View.VISIBLE);
        binding.editModeContainer.setVisibility(editMode ? View.VISIBLE : View.GONE);
        
        // Điều chỉnh trạng thái có thể edit của các trường
        binding.etTaskTitle.setEnabled(editMode);
        binding.etTaskDescription.setEnabled(editMode);
        binding.spinnerAssignee.setEnabled(editMode);
        binding.tvDeadline.setEnabled(editMode);
        binding.radioPriorityLow.setEnabled(editMode);
        binding.radioPriorityMedium.setEnabled(editMode);
        binding.radioPriorityHigh.setEnabled(editMode);
    }

    private void showLoading(boolean isLoading) {
        binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_task_detail, menu);
        // Hiển thị menu tùy theo quyền
        menu.findItem(R.id.action_edit_task).setVisible(canModifyTask);
        menu.findItem(R.id.action_delete_task).setVisible(canModifyTask);
        
        // Đảm bảo icon có màu trắng
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
        int id = item.getItemId();
        
        if (id == android.R.id.home) {
            onBackPressed();
            return true;
        } else if (id == R.id.action_edit_task) {
            setEditMode(true);
            return true;
        } else if (id == R.id.action_delete_task) {
            deleteTask();
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }

    // Custom adapter for the spinner to display user names
    private class UserSpinnerAdapter extends BaseAdapter {
        
        @Override
        public int getCount() {
            return membersUserList.size();
        }
        
        @Override
        public Object getItem(int position) {
            return membersUserList.get(position);
        }
        
        @Override
        public long getItemId(int position) {
            return position;
        }
        
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = LayoutInflater.from(TaskDetailActivity.this).inflate(android.R.layout.simple_spinner_item, parent, false);
            }
            
            TextView textView = (TextView) view;
            User user = membersUserList.get(position);
            
            if (user.getName() != null && !user.getName().isEmpty()) {
                textView.setText(user.getName());
            } else if (user.getEmail() != null && !user.getEmail().isEmpty()) {
                textView.setText(user.getEmail());
            } else {
                textView.setText(user.getId());
            }
            
            return view;
        }
        
        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = LayoutInflater.from(TaskDetailActivity.this).inflate(android.R.layout.simple_spinner_dropdown_item, parent, false);
            }
            
            TextView textView = (TextView) view;
            User user = membersUserList.get(position);
            
            if (user.getName() != null && !user.getName().isEmpty()) {
                textView.setText(user.getName());
            } else if (user.getEmail() != null && !user.getEmail().isEmpty()) {
                textView.setText(user.getEmail());
            } else {
                textView.setText(user.getId());
            }
            
            return view;
        }
    }
} 