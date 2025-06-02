package com.example.grouptaskmanager.task;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.grouptaskmanager.R;
import com.example.grouptaskmanager.databinding.ActivityCreateTaskBinding;
import com.example.grouptaskmanager.model.Group;
import com.example.grouptaskmanager.model.Task;
import com.example.grouptaskmanager.model.User;
import com.example.grouptaskmanager.notification.NotificationHelper;
import com.example.grouptaskmanager.repository.GroupRepository;
import com.example.grouptaskmanager.repository.TaskRepository;
import com.example.grouptaskmanager.repository.UserRepository;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CreateTaskActivity extends AppCompatActivity {

    private static final String TAG = "CreateTaskActivity";
    private ActivityCreateTaskBinding binding;
    private TaskRepository taskRepository;
    private GroupRepository groupRepository;
    private UserRepository userRepository;
    private String groupId;
    private String groupName;
    private Calendar deadlineCalendar;
    private List<String> memberIds;
    private List<User> membersList;
    private UserSpinnerAdapter membersAdapter;
    private Map<String, User> userCache;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCreateTaskBinding.inflate(getLayoutInflater());
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
        groupRepository = new GroupRepository();
        userRepository = new UserRepository();
        memberIds = new ArrayList<>();
        membersList = new ArrayList<>();
        userCache = new HashMap<>();
        deadlineCalendar = Calendar.getInstance();
        
        setupToolbar();
        loadGroupMembers();
        setupListeners();
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            
            // Hiển thị tên nhóm trong tiêu đề nếu có
            if (groupName != null && !groupName.isEmpty()) {
                getSupportActionBar().setTitle(getString(R.string.create_task) + " - " + groupName);
            }
        }
    }

    private void loadGroupMembers() {
        showLoading(true);
        
        groupRepository.getGroupDetails(groupId)
                .addOnSuccessListener(documentSnapshot -> {
                    Group group = documentSnapshot.toObject(Group.class);
                    if (group != null && group.getMembers() != null) {
                        memberIds.clear();
                        memberIds.addAll(group.getMembers());
                        loadMembers();
                    }
                    showLoading(false);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading group members", e);
                    showLoading(false);
                    Toast.makeText(this, "Không thể tải danh sách thành viên", Toast.LENGTH_SHORT).show();
                });
    }

    private void loadMembers() {
        showLoading(true);
        
        if (memberIds.isEmpty()) {
            showLoading(false);
            return;
        }
        
        membersList.clear();
        userCache.clear();
        
        int[] processedCount = {0};
        
        for (String memberId : memberIds) {
            userRepository.getUserById(memberId)
                    .addOnSuccessListener(documentSnapshot -> {
                        User user = documentSnapshot.toObject(User.class);
                        if (user != null) {
                            user.setId(documentSnapshot.getId());
                            membersList.add(user);
                            userCache.put(user.getId(), user);
                        }
                        
                        processedCount[0]++;
                        if (processedCount[0] == memberIds.size()) {
                            setupMembersSpinner();
                            showLoading(false);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error loading member " + memberId, e);
                        
                        processedCount[0]++;
                        if (processedCount[0] == memberIds.size()) {
                            setupMembersSpinner();
                            showLoading(false);
                        }
                    });
        }
    }

    private void setupMembersSpinner() {
        membersAdapter = new UserSpinnerAdapter(this, membersList);
        binding.spinnerAssignee.setAdapter(membersAdapter);
    }

    private void setupListeners() {
        // Thiết lập DatePicker cho deadline
        binding.tvDeadline.setOnClickListener(v -> showDatePickerDialog());
        
        // Thiết lập nút lưu
        binding.btnSaveTask.setOnClickListener(v -> {
            if (validateInputs()) {
                createTask();
            }
        });
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

    private void updateDeadlineText() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        String formattedDate = dateFormat.format(deadlineCalendar.getTime());
        binding.tvDeadline.setText(formattedDate);
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
        
        if (binding.tvDeadline.getText().toString().equals(getString(R.string.set_deadline))) {
            Toast.makeText(this, R.string.error_no_deadline, Toast.LENGTH_SHORT).show();
            return false;
        }
        
        binding.tilTaskTitle.setError(null);
        return true;
    }

    private void createTask() {
        // Validate input
        if (!validateInputs()) {
            return;
        }

        showLoading(true);

        String title = binding.etTaskTitle.getText().toString().trim();
        String description = binding.etTaskDescription.getText().toString().trim();
        User selectedMember = (User) binding.spinnerAssignee.getSelectedItem();
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
        
        Task task = new Task();
        task.setTitle(title);
        task.setDescription(description);
        task.setAssignedTo(selectedMember.getId());
        task.setPriority(priority);
        task.setDeadline(deadline);
        task.setStatus(Task.STATUS_TODO);
        task.setCreatedBy(FirebaseAuth.getInstance().getCurrentUser().getUid());
        task.setCreatedAt(Timestamp.now());
        task.setUpdatedAt(Timestamp.now());

        taskRepository.createTask(groupId, task)
                .addOnSuccessListener(documentReference -> {
                    showLoading(false);
                    Toast.makeText(this, R.string.task_created_success, Toast.LENGTH_SHORT).show();
                    
                    // Send notification to the assignee
                    if (selectedMember.getId() != null && !selectedMember.getId().equals(task.getCreatedBy())) {
                        task.setId(documentReference.getId());
                        task.setGroupId(groupId);
                        NotificationHelper.sendTaskAssignedNotification(task, selectedMember.getId());
                    }
                    
                    finish();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Log.e(TAG, "Error creating task", e);
                    Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void showLoading(boolean isLoading) {
        binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        binding.btnSaveTask.setEnabled(!isLoading);
        binding.etTaskTitle.setEnabled(!isLoading);
        binding.etTaskDescription.setEnabled(!isLoading);
        binding.spinnerAssignee.setEnabled(!isLoading);
        binding.tvDeadline.setEnabled(!isLoading);
        binding.radioGroupPriority.setEnabled(!isLoading);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // Custom adapter để hiển thị tên user trong spinner
    private static class UserSpinnerAdapter extends BaseAdapter {
        private final List<User> users;
        private final LayoutInflater inflater;

        public UserSpinnerAdapter(AppCompatActivity context, List<User> users) {
            this.users = users;
            this.inflater = LayoutInflater.from(context);
        }

        @Override
        public int getCount() {
            return users.size();
        }

        @Override
        public Object getItem(int position) {
            return users.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = inflater.inflate(android.R.layout.simple_spinner_item, parent, false);
            }
            
            TextView textView = (TextView) convertView;
            User user = users.get(position);
            
            // Hiển thị tên user, nếu không có thì hiển thị email, cuối cùng mới hiển thị ID
            if (user.getName() != null && !user.getName().isEmpty()) {
                textView.setText(user.getName());
            } else if (user.getEmail() != null && !user.getEmail().isEmpty()) {
                textView.setText(user.getEmail());
            } else {
                textView.setText(user.getId());
            }
            
            return convertView;
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = inflater.inflate(android.R.layout.simple_spinner_dropdown_item, parent, false);
            }
            
            TextView textView = (TextView) convertView;
            User user = users.get(position);
            
            // Hiển thị tên user, nếu không có thì hiển thị email, cuối cùng mới hiển thị ID
            if (user.getName() != null && !user.getName().isEmpty()) {
                textView.setText(user.getName());
            } else if (user.getEmail() != null && !user.getEmail().isEmpty()) {
                textView.setText(user.getEmail());
            } else {
                textView.setText(user.getId());
            }
            
            return convertView;
        }
    }
} 