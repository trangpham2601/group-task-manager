package com.example.grouptaskmanager.task;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.grouptaskmanager.R;
import com.example.grouptaskmanager.databinding.ActivityTaskListBinding;
import com.example.grouptaskmanager.model.Task;
import com.example.grouptaskmanager.model.User;
import com.example.grouptaskmanager.repository.TaskRepository;
import com.example.grouptaskmanager.repository.UserRepository;
import com.google.android.material.chip.Chip;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TaskListActivity extends AppCompatActivity implements TaskAdapter.OnTaskClickListener {

    private static final String TAG = "TaskListActivity";
    private ActivityTaskListBinding binding;
    private TaskRepository taskRepository;
    private UserRepository userRepository;
    private TaskAdapter taskAdapter;
    private List<Task> taskList;
    private List<Task> filteredTaskList;
    private String groupId;
    private String groupName;
    private String currentFilter = "all"; // Bộ lọc mặc định
    private String searchQuery = "";
    private Map<String, User> userCache = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTaskListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Lấy thông tin nhóm từ intent
        groupId = getIntent().getStringExtra("GROUP_ID");
        groupName = getIntent().getStringExtra("GROUP_NAME");
        
        if (groupId == null) {
            Toast.makeText(this, "Không tìm thấy thông tin nhóm", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        taskRepository = new TaskRepository();
        userRepository = new UserRepository();
        taskList = new ArrayList<>();
        filteredTaskList = new ArrayList<>();

        setupToolbar();
        setupRecyclerView();
        setupFilterChips();
        setupSearchView();
        setupListeners();
        loadTasks();
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            
            // Hiển thị tên nhóm trong tiêu đề nếu có
            String title;
            if (groupName != null && !groupName.isEmpty()) {
                title = groupName + " - " + getString(R.string.tasks);
            } else {
                title = getString(R.string.task_list);
            }
            
            // Đặt tiêu đề trực tiếp trên toolbar
            getSupportActionBar().setTitle(title);
        }
    }

    private void setupRecyclerView() {
        taskAdapter = new TaskAdapter(this, filteredTaskList, this, userCache);
        binding.rvTasks.setLayoutManager(new LinearLayoutManager(this));
        binding.rvTasks.setAdapter(taskAdapter);
    }

    private void setupFilterChips() {
        // Sử dụng ChipGroup để quản lý việc chọn chip
        binding.chipGroupFilter.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                // Nếu không có chip nào được chọn, chọn lại chip "Tất cả"
                binding.chipFilterAll.setChecked(true);
                currentFilter = "all";
            } else {
                int checkedId = checkedIds.get(0);
                
                if (checkedId == R.id.chip_filter_all) {
                    currentFilter = "all";
                } else if (checkedId == R.id.chip_filter_todo) {
                    currentFilter = Task.STATUS_TODO;
                } else if (checkedId == R.id.chip_filter_in_progress) {
                    currentFilter = Task.STATUS_IN_PROGRESS;
                } else if (checkedId == R.id.chip_filter_done) {
                    currentFilter = Task.STATUS_DONE;
                } else if (checkedId == R.id.chip_filter_overdue) {
                    currentFilter = "overdue";
                }
            }
            
            applyFilters();
        });
        
        // Đặt chip "Tất cả" là được chọn mặc định
        binding.chipFilterAll.setChecked(true);
    }
    
    private void setupSearchView() {
        binding.searchTasks.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                searchQuery = s.toString().toLowerCase().trim();
                applyFilters();
            }
        });
        
        binding.searchContainer.setEndIconOnClickListener(v -> {
            binding.searchTasks.setText("");
        });
    }

    private void setupListeners() {
        binding.fabAddTask.setOnClickListener(v -> {
            Intent intent = new Intent(this, CreateTaskActivity.class);
            intent.putExtra("GROUP_ID", groupId);
            intent.putExtra("GROUP_NAME", groupName);
            startActivity(intent);
        });
    }

    private void loadTasks() {
        showLoading(true);
        taskRepository.getGroupTasks(groupId)
                .addOnSuccessListener(this::processTaskData)
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Log.e(TAG, "Error loading tasks", e);
                    showEmptyState(true);
                });
    }

    private void processTaskData(QuerySnapshot querySnapshot) {
        taskList.clear();
        
        if (querySnapshot.isEmpty()) {
            showEmptyState(true);
            showLoading(false);
            return;
        }
        
        for (DocumentSnapshot document : querySnapshot.getDocuments()) {
            Task task = document.toObject(Task.class);
            if (task != null) {
                task.setId(document.getId());
                taskList.add(task);
            }
        }
        
        // Load user data for tasks
        loadUserData();
    }
    
    private void loadUserData() {
        List<String> userIds = new ArrayList<>();
        
        // Collect all user IDs from tasks
        for (Task task : taskList) {
            String assignedTo = task.getAssignedTo();
            if (assignedTo != null && !assignedTo.isEmpty() && !userIds.contains(assignedTo)) {
                userIds.add(assignedTo);
            }
        }
        
        if (userIds.isEmpty()) {
            // No users to load, continue with filtering
            applyFilters();
            showLoading(false);
            return;
        }
        
        // Track number of processed user requests
        final int[] processedUsers = {0};
        final int totalUsers = userIds.size();
        
        // Load user data for each user ID
        for (String userId : userIds) {
            userRepository.getUserById(userId)
                .addOnSuccessListener(documentSnapshot -> {
                    User user = documentSnapshot.toObject(User.class);
                    if (user != null) {
                        user.setId(documentSnapshot.getId());
                        userCache.put(userId, user);
                    }
                    
                    processedUsers[0]++;
                    if (processedUsers[0] >= totalUsers) {
                        // All users loaded, apply filters and update UI
                        applyFilters();
                        showLoading(false);
                    }
                })
                .addOnFailureListener(e -> {
                    processedUsers[0]++;
                    Log.e(TAG, "Error loading user data", e);
                    
                    if (processedUsers[0] >= totalUsers) {
                        // Continue even if some users failed to load
                        applyFilters();
                        showLoading(false);
                    }
                });
        }
    }
    
    private void applyFilters() {
        filteredTaskList.clear();
        
        // First apply status filter
        for (Task task : taskList) {
            if (currentFilter.equals("all") || 
                currentFilter.equals(task.getStatus()) ||
                (currentFilter.equals("overdue") && isTaskOverdue(task))) {
                filteredTaskList.add(task);
            }
        }
        
        // Then apply search filter
        if (!searchQuery.isEmpty()) {
            List<Task> searchResults = new ArrayList<>();
            for (Task task : filteredTaskList) {
                if (matchesSearchQuery(task, searchQuery)) {
                    searchResults.add(task);
                }
            }
            filteredTaskList.clear();
            filteredTaskList.addAll(searchResults);
        }
        
        // Hiển thị số lượng nhiệm vụ
        updateTaskCount(filteredTaskList.size());
        
        // Cập nhật adapter và hiển thị trạng thái trống nếu cần
        taskAdapter.notifyDataSetChanged();
        showEmptyState(filteredTaskList.isEmpty());
    }
    
    // Thêm phương thức mới để hiển thị số lượng nhiệm vụ
    private void updateTaskCount(int count) {
        String taskCountText = count + " nhiệm vụ";
        binding.tvTaskCount.setText(taskCountText);
    }
    
    private boolean isTaskOverdue(Task task) {
        if (task.getStatus().equals(Task.STATUS_DONE)) {
            return false; // Nhiệm vụ đã hoàn thành không bị coi là quá hạn
        }
        
        Timestamp deadline = task.getDeadline();
        if (deadline != null) {
            return deadline.toDate().before(new Date());
        }
        return false;
    }
    
    private boolean matchesSearchQuery(Task task, String query) {
        String assigneeName = "";
        if (task.getAssignedTo() != null && userCache.containsKey(task.getAssignedTo())) {
            User user = userCache.get(task.getAssignedTo());
            if (user != null && user.getName() != null) {
                assigneeName = user.getName().toLowerCase();
            }
        }
        
        return (task.getTitle() != null && task.getTitle().toLowerCase().contains(query)) ||
               (task.getDescription() != null && task.getDescription().toLowerCase().contains(query)) ||
               assigneeName.contains(query);
    }

    private void showEmptyState(boolean isEmpty) {
        binding.emptyStateContainer.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        binding.rvTasks.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    private void showLoading(boolean isLoading) {
        binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        
        if (isLoading) {
            // Khi đang tải, ẩn cả RecyclerView và empty state
            binding.rvTasks.setVisibility(View.GONE);
            binding.emptyStateContainer.setVisibility(View.GONE);
        }
        
        binding.fabAddTask.setEnabled(!isLoading);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadTasks();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onTaskClick(Task task) {
        Intent intent = new Intent(this, TaskDetailActivity.class);
        intent.putExtra("GROUP_ID", groupId);
        intent.putExtra("TASK_ID", task.getId());
        intent.putExtra("GROUP_NAME", groupName);
        startActivity(intent);
    }
} 