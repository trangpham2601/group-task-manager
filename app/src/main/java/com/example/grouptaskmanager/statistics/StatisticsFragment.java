package com.example.grouptaskmanager.statistics;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
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
import com.example.grouptaskmanager.task.TaskDetailActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StatisticsFragment extends Fragment implements TaskCompactAdapter.OnTaskClickListener {

    private static final String TAG = "StatisticsFragment";
    
    private TextView tvTotalCount, tvCompletionPercentage;
    private TextView tvTodoCount, tvInProgressCount, tvDoneCount, tvOverdueCount;
    private RecyclerView rvOverdueTasks, rvInProgressTasks;
    private TextView tvNoOverdueTasks, tvNoInProgressTasks;
    private MaterialButton btnViewAllTasks;
    private CircularProgressIndicator progressBar;
    
    private TaskRepository taskRepository;
    private GroupRepository groupRepository;
    private UserRepository userRepository;
    private TaskCompactAdapter overdueTasksAdapter, inProgressTasksAdapter;
    private List<Task> allTasks;
    private Map<String, User> userCache = new HashMap<>();
    private FirebaseAuth auth;
    private boolean isLoading = false;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        taskRepository = new TaskRepository();
        groupRepository = new GroupRepository();
        userRepository = new UserRepository();
        allTasks = new ArrayList<>();
        auth = FirebaseAuth.getInstance();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_statistics, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        initViews(view);
        setupRecyclerViews();
        setupClickListeners();
        loadStatistics();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isLoading) {
            loadStatistics();
        }
    }

    private void initViews(View view) {
        tvTotalCount = view.findViewById(R.id.tv_total_count);
        tvCompletionPercentage = view.findViewById(R.id.tv_completion_percentage);
        tvTodoCount = view.findViewById(R.id.tv_todo_count);
        tvInProgressCount = view.findViewById(R.id.tv_in_progress_count);
        tvDoneCount = view.findViewById(R.id.tv_done_count);
        tvOverdueCount = view.findViewById(R.id.tv_overdue_count);
        
        rvOverdueTasks = view.findViewById(R.id.rv_overdue_tasks);
        rvInProgressTasks = view.findViewById(R.id.rv_in_progress_tasks);
        tvNoOverdueTasks = view.findViewById(R.id.tv_no_overdue_tasks);
        tvNoInProgressTasks = view.findViewById(R.id.tv_no_in_progress_tasks);
        
        btnViewAllTasks = view.findViewById(R.id.btn_view_all_tasks);
        progressBar = view.findViewById(R.id.progress_bar);
    }

    private void setupRecyclerViews() {
        // Setup overdue tasks RecyclerView
        overdueTasksAdapter = new TaskCompactAdapter(requireContext(), new ArrayList<>(), userCache);
        overdueTasksAdapter.setOnTaskClickListener(this);
        rvOverdueTasks.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvOverdueTasks.setAdapter(overdueTasksAdapter);
        rvOverdueTasks.setNestedScrollingEnabled(false);
        
        // Setup in progress tasks RecyclerView
        inProgressTasksAdapter = new TaskCompactAdapter(requireContext(), new ArrayList<>(), userCache);
        inProgressTasksAdapter.setOnTaskClickListener(this);
        rvInProgressTasks.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvInProgressTasks.setAdapter(inProgressTasksAdapter);
        rvInProgressTasks.setNestedScrollingEnabled(false);
    }

    private void setupClickListeners() {
        btnViewAllTasks.setOnClickListener(v -> {
            // Navigate to Tasks tab
            if (getActivity() != null && getActivity() instanceof MainActivity) {
                MainActivity mainActivity = (MainActivity) getActivity();
                // Switch to tasks tab using BottomNavigationView
                androidx.fragment.app.FragmentTransaction transaction = mainActivity.getSupportFragmentManager().beginTransaction();
                transaction.replace(R.id.nav_host_fragment_content_main, new com.example.grouptaskmanager.task.TasksFragment());
                transaction.commitAllowingStateLoss();
                
                // Update bottom navigation selection
                com.google.android.material.bottomnavigation.BottomNavigationView bottomNav = 
                    mainActivity.findViewById(R.id.bottom_navigation);
                if (bottomNav != null) {
                    bottomNav.setSelectedItemId(R.id.navigation_tasks);
                }
            }
        });
    }

    private void loadStatistics() {
        if (isLoading) return;
        
        isLoading = true;
        showLoading(true);
        allTasks.clear();
        
        String currentUserId = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
        if (currentUserId == null) {
            isLoading = false;
            showLoading(false);
            return;
        }
        
        // Load all groups user is part of
        groupRepository.getUserGroups()
                .addOnSuccessListener(this::processGroupsAndLoadTasks)
                .addOnFailureListener(e -> {
                    isLoading = false;
                    showLoading(false);
                    Log.e(TAG, "Error loading groups", e);
                });
    }

    private void processGroupsAndLoadTasks(QuerySnapshot groupSnapshot) {
        if (groupSnapshot.isEmpty()) {
            isLoading = false;
            showLoading(false);
            updateStatistics();
            return;
        }
        
        List<String> groupIds = new ArrayList<>();
        for (DocumentSnapshot document : groupSnapshot.getDocuments()) {
            groupIds.add(document.getId());
        }
        
        final int[] processedGroups = {0};
        final int totalGroups = groupIds.size();
        
        // Load all tasks from all groups
        for (String groupId : groupIds) {
            taskRepository.getGroupTasks(groupId)
                    .addOnSuccessListener(tasksSnapshot -> {
                        for (DocumentSnapshot document : tasksSnapshot.getDocuments()) {
                            Task task = document.toObject(Task.class);
                            if (task != null) {
                                task.setGroupId(groupId);
                                task.setId(document.getId());
                                allTasks.add(task);
                            }
                        }
                        
                        processedGroups[0]++;
                        if (processedGroups[0] == totalGroups) {
                            loadAssigneeInfo();
                        }
                    })
                    .addOnFailureListener(e -> {
                        processedGroups[0]++;
                        if (processedGroups[0] == totalGroups) {
                            loadAssigneeInfo();
                        }
                        Log.e(TAG, "Error loading tasks for group: " + groupId, e);
                    });
        }
    }

    private void loadAssigneeInfo() {
        final int[] processedTasks = {0};
        final int totalTasks = allTasks.size();
        
        if (totalTasks == 0) {
            isLoading = false;
            showLoading(false);
            updateStatistics();
            return;
        }
        
        for (Task task : allTasks) {
            String assigneeId = task.getAssignedTo();
            if (assigneeId == null || userCache.containsKey(assigneeId)) {
                processedTasks[0]++;
                if (processedTasks[0] == totalTasks) {
                    isLoading = false;
                    showLoading(false);
                    updateStatistics();
                }
                continue;
            }
            
            userRepository.getUserById(assigneeId)
                    .addOnSuccessListener(documentSnapshot -> {
                        User user = documentSnapshot.toObject(User.class);
                        if (user != null) {
                            user.setId(documentSnapshot.getId());
                            userCache.put(assigneeId, user);
                        }
                        
                        processedTasks[0]++;
                        if (processedTasks[0] == totalTasks) {
                            isLoading = false;
                            showLoading(false);
                            updateStatistics();
                        }
                    })
                    .addOnFailureListener(e -> {
                        processedTasks[0]++;
                        if (processedTasks[0] == totalTasks) {
                            isLoading = false;
                            showLoading(false);
                            updateStatistics();
                        }
                        Log.e(TAG, "Error loading user data", e);
                    });
        }
    }

    private void updateStatistics() {
        if (getActivity() == null || !isAdded()) return;
        
        Date now = new Date();
        
        int todoCount = 0;
        int inProgressCount = 0;
        int doneCount = 0;
        int overdueCount = 0;
        
        List<Task> overdueTasks = new ArrayList<>();
        List<Task> inProgressTasks = new ArrayList<>();
        
        for (Task task : allTasks) {
            // Count by status
            if (Task.STATUS_TODO.equals(task.getStatus())) {
                todoCount++;
            } else if (Task.STATUS_IN_PROGRESS.equals(task.getStatus())) {
                inProgressCount++;
                inProgressTasks.add(task);
            } else if (Task.STATUS_DONE.equals(task.getStatus())) {
                doneCount++;
            }
            
            // Check if overdue
            if (!Task.STATUS_DONE.equals(task.getStatus()) && 
                task.getDeadline() != null && 
                task.getDeadline().toDate().before(now)) {
                overdueCount++;
                overdueTasks.add(task);
            }
        }
        
        int totalTasks = allTasks.size();
        float completionPercentage = totalTasks > 0 ? (float) doneCount / totalTasks * 100 : 0;
        
        // Update UI
        tvTotalCount.setText(String.valueOf(totalTasks));
        tvCompletionPercentage.setText(String.format("%.0f%% hoàn thành", completionPercentage));
        tvTodoCount.setText(String.valueOf(todoCount));
        tvInProgressCount.setText(String.valueOf(inProgressCount));
        tvDoneCount.setText(String.valueOf(doneCount));
        tvOverdueCount.setText(String.valueOf(overdueCount));
        
        // Update RecyclerViews
        updateTaskLists(overdueTasks, inProgressTasks);
    }

    private void updateTaskLists(List<Task> overdueTasks, List<Task> inProgressTasks) {
        // Show/hide overdue tasks
        if (overdueTasks.isEmpty()) {
            rvOverdueTasks.setVisibility(View.GONE);
            tvNoOverdueTasks.setVisibility(View.VISIBLE);
        } else {
            rvOverdueTasks.setVisibility(View.VISIBLE);
            tvNoOverdueTasks.setVisibility(View.GONE);
            overdueTasksAdapter.updateTasks(overdueTasks);
        }
        
        // Show/hide in progress tasks
        if (inProgressTasks.isEmpty()) {
            rvInProgressTasks.setVisibility(View.GONE);
            tvNoInProgressTasks.setVisibility(View.VISIBLE);
        } else {
            rvInProgressTasks.setVisibility(View.VISIBLE);
            tvNoInProgressTasks.setVisibility(View.GONE);
            inProgressTasksAdapter.updateTasks(inProgressTasks);
        }
    }

    private void showLoading(boolean isLoading) {
        if (progressBar != null) {
            progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
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