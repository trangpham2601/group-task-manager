package com.example.grouptaskmanager.task;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SearchView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.grouptaskmanager.R;
import com.example.grouptaskmanager.MainActivity;
import com.example.grouptaskmanager.model.Task;
import com.example.grouptaskmanager.model.User;
import com.example.grouptaskmanager.model.Group;
import com.example.grouptaskmanager.repository.GroupRepository;
import com.example.grouptaskmanager.repository.TaskRepository;
import com.example.grouptaskmanager.repository.UserRepository;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TasksFragment extends Fragment implements TaskAdapter.OnTaskClickListener {

    private static final String TAG = "TasksFragment";
    
    private RecyclerView rvTasks;
    private CircularProgressIndicator progressBar;
    private ChipGroup chipGroupFilter, chipGroupOwnerFilter;
    private Chip chipFilterAll, chipFilterTodo, chipFilterInProgress, chipFilterDone;
    private Chip chipOwnerAll, chipOwnerMine;
    private SearchView searchView;
    private TextView tvTotalCount, tvTodoCount, tvInProgressCount, tvDoneCount;
    private View layoutEmptyTasks;
    
    private TaskRepository taskRepository;
    private GroupRepository groupRepository;
    private UserRepository userRepository;
    private TaskAdapter taskAdapter;
    private List<Task> taskList;
    private List<Task> filteredTaskList;
    private Map<String, User> userCache = new HashMap<>();
    private String currentFilter = "all"; // Bộ lọc trạng thái mặc định
    private String currentOwnerFilter = "all"; // Bộ lọc owner mặc định: "all" hoặc "mine"
    private String currentSearchQuery = "";
    private boolean isLoading = false; // Flag to track loading state
    private FirebaseAuth auth;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        taskRepository = new TaskRepository();
        groupRepository = new GroupRepository();
        userRepository = new UserRepository();
        taskList = new ArrayList<>();
        filteredTaskList = new ArrayList<>();
        auth = FirebaseAuth.getInstance();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_tasks, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Ánh xạ các view
        rvTasks = view.findViewById(R.id.rv_tasks);
        layoutEmptyTasks = view.findViewById(R.id.layout_empty_tasks);
        progressBar = view.findViewById(R.id.progress_bar);
        chipGroupFilter = view.findViewById(R.id.chip_group_filter);
        chipFilterAll = view.findViewById(R.id.chip_filter_all);
        chipFilterTodo = view.findViewById(R.id.chip_filter_todo);
        chipFilterInProgress = view.findViewById(R.id.chip_filter_in_progress);
        chipFilterDone = view.findViewById(R.id.chip_filter_done);
        chipGroupOwnerFilter = view.findViewById(R.id.chip_group_owner_filter);
        chipOwnerAll = view.findViewById(R.id.chip_owner_all);
        chipOwnerMine = view.findViewById(R.id.chip_owner_mine);
        searchView = view.findViewById(R.id.search_view);
        
        // Ánh xạ các TextView hiển thị số liệu thống kê
        tvTotalCount = view.findViewById(R.id.tv_total_count);
        tvTodoCount = view.findViewById(R.id.tv_todo_count);
        tvInProgressCount = view.findViewById(R.id.tv_in_progress_count);
        tvDoneCount = view.findViewById(R.id.tv_done_count);
        
        // Empty state button listener
        view.findViewById(R.id.btn_view_groups).setOnClickListener(v -> {
            // Chuyển về tab nhóm
            if (getActivity() != null && getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).navigateToGroupsTab();
            }
        });
        
        setupRecyclerView();
        setupFilterChips();
        setupSearchView();
        loadAllTasks();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Only load tasks if not already loading and view is created
        if (!isLoading && getView() != null) {
            loadAllTasks();
        }
    }

    private void setupRecyclerView() {
        taskAdapter = new TaskAdapter(requireContext(), filteredTaskList, this, userCache);
        rvTasks.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvTasks.setAdapter(taskAdapter);
    }

    private void setupFilterChips() {
        chipFilterAll.setOnClickListener(v -> {
            currentFilter = "all";
            applyFilters();
        });
        
        chipFilterTodo.setOnClickListener(v -> {
            currentFilter = Task.STATUS_TODO;
            applyFilters();
        });
        
        chipFilterInProgress.setOnClickListener(v -> {
            currentFilter = Task.STATUS_IN_PROGRESS;
            applyFilters();
        });
        
        chipFilterDone.setOnClickListener(v -> {
            currentFilter = Task.STATUS_DONE;
            applyFilters();
        });
        
        chipOwnerAll.setOnClickListener(v -> {
            currentOwnerFilter = "all";
            applyFilters();
        });
        
        chipOwnerMine.setOnClickListener(v -> {
            currentOwnerFilter = "mine";
            applyFilters();
        });
    }
    
    private void setupSearchView() {
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                currentSearchQuery = query.toLowerCase().trim();
                applyFilters();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                currentSearchQuery = newText.toLowerCase().trim();
                applyFilters();
                return true;
            }
        });
    }

    private void loadAllTasks() {
        // Set loading flag to prevent duplicate loads
        isLoading = true;
        showLoading(true);
        
        // Clear existing tasks to prevent duplicates
        taskList.clear();
        filteredTaskList.clear();
        
        // Đầu tiên, lấy danh sách tất cả các nhóm mà người dùng đã tham gia
        groupRepository.getUserGroups()
                .addOnSuccessListener(this::processGroupsAndLoadTasks)
                .addOnFailureListener(e -> {
                    isLoading = false; // Reset loading flag
                    showLoading(false);
                    Log.e(TAG, "Error loading groups", e);
                    showEmptyState(true);
                });
    }
    
    private void processGroupsAndLoadTasks(QuerySnapshot groupSnapshot) {
        if (groupSnapshot.isEmpty()) {
            isLoading = false; // Reset loading flag
            showLoading(false); // Ẩn loading
            showEmptyState(true); // Hiển thị empty state
            updateTaskStatistics(); // Cập nhật stats với 0
            Log.d(TAG, "No groups found for user");
            return;
        }
        
        // taskList already cleared in loadAllTasks()
        List<String> groupIds = new ArrayList<>();
        
        // Lấy ID của tất cả các nhóm
        for (DocumentSnapshot document : groupSnapshot.getDocuments()) {
            groupIds.add(document.getId());
        }
        
        Log.d(TAG, "Found " + groupIds.size() + " groups: " + groupIds);
        
        // Lấy ID của user hiện tại
        String currentUserId = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
        if (currentUserId == null) {
            isLoading = false;
            showLoading(false);
            showEmptyState(true);
            Log.e(TAG, "Current user ID is null");
            return;
        }
        
        Log.d(TAG, "Current user ID: " + currentUserId);
        
        // Biến đếm số nhóm đã xử lý
        final int[] processedGroups = {0};
        final int totalGroups = groupIds.size();
        
        // Lấy TẤT CẢ nhiệm vụ từ mỗi nhóm 
        for (String groupId : groupIds) {
            Log.d(TAG, "Loading tasks from group: " + groupId);
            taskRepository.getGroupTasks(groupId)
                    .addOnSuccessListener(tasksSnapshot -> {
                        Log.d(TAG, "Found " + tasksSnapshot.size() + " tasks in group " + groupId);
                        for (DocumentSnapshot document : tasksSnapshot.getDocuments()) {
                            Task task = document.toObject(Task.class);
                            if (task != null) {
                                // Set group ID for each task
                                task.setGroupId(groupId);
                                task.setId(document.getId());
                                taskList.add(task);
                                Log.d(TAG, "Added task: " + task.getTitle() + " (assigned to: " + task.getAssignedTo() + ", created by: " + task.getCreatedBy() + ")");
                            }
                        }
                        
                        processedGroups[0]++;
                        
                        // Khi đã xử lý hết tất cả các nhóm, cập nhật UI
                        if (processedGroups[0] == totalGroups) {
                            Log.d(TAG, "Finished loading all tasks. Total: " + taskList.size());
                            // Cập nhật thống kê
                            updateTaskStatistics();
                            
                            // Load user info for each task
                            loadAssigneeInfo();
                        }
                    })
                    .addOnFailureListener(e -> {
                        processedGroups[0]++;
                        
                        if (processedGroups[0] == totalGroups) {
                            // Cập nhật thống kê
                            updateTaskStatistics();
                            
                            // Load user info for each task
                            loadAssigneeInfo();
                        }
                        
                        Log.e(TAG, "Error loading tasks for group: " + groupId, e);
                    });
        }
    }
    
    private void loadAssigneeInfo() {
        // Track the number of tasks with loaded assignee info
        final int[] processedTasks = {0};
        final int totalTasks = taskList.size();
        
        if (totalTasks == 0) {
            showLoading(false);
            isLoading = false; // Reset loading flag
            applyFilters(); // Gọi applyFilters để hiển thị empty state
            return;
        }
        
        for (Task task : taskList) {
            String assigneeId = task.getAssignedTo();
            if (assigneeId == null) {
                processedTasks[0]++;
                if (processedTasks[0] == totalTasks) {
                    showLoading(false);
                    isLoading = false; // Reset loading flag
                    applyFilters();
                }
                continue;
            }
            
            // Check if we already have the user info in cache
            if (userCache.containsKey(assigneeId)) {
                processedTasks[0]++;
                if (processedTasks[0] == totalTasks) {
                    showLoading(false);
                    isLoading = false; // Reset loading flag
                    applyFilters();
                }
            } else {
                // Load user info from Firestore
                userRepository.getUserById(assigneeId)
                    .addOnSuccessListener(documentSnapshot -> {
                        User user = documentSnapshot.toObject(User.class);
                        if (user != null) {
                            user.setId(documentSnapshot.getId());
                            userCache.put(assigneeId, user);
                        }
                        
                        processedTasks[0]++;
                        if (processedTasks[0] == totalTasks) {
                            showLoading(false);
                            isLoading = false; // Reset loading flag
                            applyFilters();
                        }
                    })
                    .addOnFailureListener(e -> {
                        processedTasks[0]++;
                        Log.e(TAG, "Error loading user data", e);
                        
                        if (processedTasks[0] == totalTasks) {
                            showLoading(false);
                            isLoading = false; // Reset loading flag
                            applyFilters();
                        }
                    });
            }
        }
    }
    
    private void updateTaskStatistics() {
        // Count tasks by status
        int todoCount = 0;
        int inProgressCount = 0;
        int doneCount = 0;
        
        Date now = new Date();
        
        for (Task task : taskList) {
            // Count by status
            if (Task.STATUS_TODO.equals(task.getStatus())) {
                todoCount++;
            } else if (Task.STATUS_IN_PROGRESS.equals(task.getStatus())) {
                inProgressCount++;
            } else if (Task.STATUS_DONE.equals(task.getStatus())) {
                doneCount++;
            }
        }
        
        // Update UI with counts
        tvTotalCount.setText(String.valueOf(taskList.size()));
        tvTodoCount.setText(String.valueOf(todoCount));
        tvInProgressCount.setText(String.valueOf(inProgressCount));
        tvDoneCount.setText(String.valueOf(doneCount));
    }
    
    private void applyFilters() {
        filteredTaskList.clear();
        
        // Get current date for overdue comparison
        Date now = new Date();
        String currentUserId = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
        
        Log.d(TAG, "Applying filters - Total tasks: " + taskList.size() + ", Owner filter: " + currentOwnerFilter + ", Status filter: " + currentFilter);
        
        // Filter tasks based on current filter and search query
        for (Task task : taskList) {
            boolean matchesFilter = false;
            
            // Owner filter - kiểm tra trước
            boolean matchesOwner = false;
            if ("all".equals(currentOwnerFilter)) {
                matchesOwner = true;
            } else if ("mine".equals(currentOwnerFilter) && currentUserId != null) {
                // "Task của tôi" bao gồm: được assign cho tôi HOẶC tôi tạo ra
                matchesOwner = (currentUserId.equals(task.getAssignedTo()) || 
                               currentUserId.equals(task.getCreatedBy()));
            }
            
            Log.d(TAG, "Task: " + task.getTitle() + " - Owner match: " + matchesOwner + " (assigned: " + task.getAssignedTo() + ", created: " + task.getCreatedBy() + ", current: " + currentUserId + ")");
            
            // Nếu không match owner filter thì skip
            if (!matchesOwner) {
                continue;
            }
            
            // Status filter
            if ("all".equals(currentFilter)) {
                matchesFilter = true;
            } else {
                matchesFilter = currentFilter.equals(task.getStatus());
            }
            
            // Search query
            if (matchesFilter && !currentSearchQuery.isEmpty()) {
                // Search by title
                boolean matchesSearch = task.getTitle() != null && 
                                       task.getTitle().toLowerCase().contains(currentSearchQuery);
                
                // Search by description
                if (!matchesSearch && task.getDescription() != null) {
                    matchesSearch = task.getDescription().toLowerCase().contains(currentSearchQuery);
                }
                
                // Search by assignee name
                if (!matchesSearch && task.getAssignedTo() != null && userCache.containsKey(task.getAssignedTo())) {
                    User assignee = userCache.get(task.getAssignedTo());
                    if (assignee.getName() != null) {
                        matchesSearch = assignee.getName().toLowerCase().contains(currentSearchQuery);
                    } else if (assignee.getEmail() != null) {
                        matchesSearch = assignee.getEmail().toLowerCase().contains(currentSearchQuery);
                    }
                }
                
                // Search by priority
                if (!matchesSearch && task.getPriority() != null) {
                    String priorityEnglish = task.getPriority().toLowerCase();
                    String priorityVietnamese = getPriorityDisplayName(task.getPriority()).toLowerCase();
                    matchesSearch = priorityEnglish.contains(currentSearchQuery) || 
                                   priorityVietnamese.contains(currentSearchQuery);
                }
                
                matchesFilter = matchesSearch;
            }
            
            if (matchesFilter) {
                filteredTaskList.add(task);
                Log.d(TAG, "Task passed all filters: " + task.getTitle());
            }
        }
        
        Log.d(TAG, "Filter complete - Filtered tasks: " + filteredTaskList.size());
        
        // Update adapter with filtered list
        taskAdapter.updateTasks(filteredTaskList);
        showEmptyState(filteredTaskList.isEmpty());
    }

    private void showEmptyState(boolean isEmpty) {
        if (isEmpty) {
            rvTasks.setVisibility(View.GONE);
            layoutEmptyTasks.setVisibility(View.VISIBLE);
            progressBar.setVisibility(View.GONE); // Đảm bảo ẩn progress bar
        } else {
            rvTasks.setVisibility(View.VISIBLE);
            layoutEmptyTasks.setVisibility(View.GONE);
        }
    }

    private void showLoading(boolean isLoading) {
        if (isLoading) {
            progressBar.setVisibility(View.VISIBLE);
            rvTasks.setVisibility(View.GONE);
            layoutEmptyTasks.setVisibility(View.GONE); // Đảm bảo ẩn empty state
        } else {
            progressBar.setVisibility(View.GONE);
            // Không set visibility cho rvTasks và layoutEmptyTasks ở đây
            // Để applyFilters() quyết định hiển thị cái nào
        }
    }

    @Override
    public void onTaskClick(Task task) {
        // Navigate to task detail
        Intent intent = new Intent(requireContext(), TaskDetailActivity.class);
        intent.putExtra("GROUP_ID", task.getGroupId());
        intent.putExtra("TASK_ID", task.getId());
        
        // Get group name from cache or fetch it
        getGroupName(task.getGroupId(), groupName -> {
            intent.putExtra("GROUP_NAME", groupName);
            startActivity(intent);
        });
    }
    
    private String getPriorityDisplayName(String priority) {
        if (priority == null) return "";
        
        switch (priority.toLowerCase()) {
            case "high":
                return getString(R.string.priority_high);
            case "medium":
                return getString(R.string.priority_medium);
            case "low":
                return getString(R.string.priority_low);
            default:
                return priority;
        }
    }

    // Helper method to get group name
    private void getGroupName(String groupId, OnGroupNameFetchedListener listener) {
        groupRepository.getGroupDetails(groupId)
                .addOnSuccessListener(documentSnapshot -> {
                    Group group = documentSnapshot.toObject(Group.class);
                    String groupName = "";
                    if (group != null && group.getName() != null) {
                        groupName = group.getName();
                    }
                    listener.onGroupNameFetched(groupName);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading group details", e);
                    listener.onGroupNameFetched("");
                });
    }
    
    // Interface for callback
    private interface OnGroupNameFetchedListener {
        void onGroupNameFetched(String groupName);
    }
} 