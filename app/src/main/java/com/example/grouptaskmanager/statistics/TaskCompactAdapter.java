package com.example.grouptaskmanager.statistics;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.grouptaskmanager.R;
import com.example.grouptaskmanager.model.Task;
import com.example.grouptaskmanager.model.User;
import com.example.grouptaskmanager.repository.GroupRepository;
import com.google.android.material.chip.Chip;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.List;
import java.util.Map;

public class TaskCompactAdapter extends RecyclerView.Adapter<TaskCompactAdapter.TaskCompactViewHolder> {

    private Context context;
    private List<Task> tasks;
    private Map<String, User> userCache;
    private Map<String, String> groupCache;
    private GroupRepository groupRepository;
    private OnTaskClickListener listener;

    public interface OnTaskClickListener {
        void onTaskClick(Task task);
    }

    public TaskCompactAdapter(Context context, List<Task> tasks, Map<String, User> userCache) {
        this.context = context;
        this.tasks = tasks;
        this.userCache = userCache;
        this.groupRepository = new GroupRepository();
        this.groupCache = new java.util.HashMap<>();
    }

    public void setOnTaskClickListener(OnTaskClickListener listener) {
        this.listener = listener;
    }

    public void updateTasks(List<Task> tasks) {
        this.tasks = tasks;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TaskCompactViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_task_compact, parent, false);
        return new TaskCompactViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TaskCompactViewHolder holder, int position) {
        Task task = tasks.get(position);
        holder.bind(task);
    }

    @Override
    public int getItemCount() {
        return tasks.size();
    }

    class TaskCompactViewHolder extends RecyclerView.ViewHolder {
        private View statusDot;
        private TextView tvTaskTitle;
        private TextView tvGroupName;
        private Chip chipPriority;

        public TaskCompactViewHolder(@NonNull View itemView) {
            super(itemView);
            statusDot = itemView.findViewById(R.id.status_dot);
            tvTaskTitle = itemView.findViewById(R.id.tv_task_title);
            tvGroupName = itemView.findViewById(R.id.tv_group_name);
            chipPriority = itemView.findViewById(R.id.chip_priority);

            itemView.setOnClickListener(v -> {
                if (listener != null && getAdapterPosition() != RecyclerView.NO_POSITION) {
                    listener.onTaskClick(tasks.get(getAdapterPosition()));
                }
            });
        }

        public void bind(Task task) {
            // Set task title
            tvTaskTitle.setText(task.getTitle());

            // Set status dot color
            int statusColor = getStatusColor(task.getStatus());
            statusDot.setBackgroundTintList(ContextCompat.getColorStateList(context, statusColor));

            // Set priority
            if (task.getPriority() != null && !task.getPriority().isEmpty()) {
                chipPriority.setText(getPriorityDisplayName(task.getPriority()));
                chipPriority.setVisibility(View.VISIBLE);
                
                // Set priority color
                int priorityColor = getPriorityColor(task.getPriority());
                chipPriority.setChipBackgroundColor(ContextCompat.getColorStateList(context, priorityColor));
            } else {
                chipPriority.setVisibility(View.GONE);
            }

            // Load and set group name
            loadGroupName(task.getGroupId(), tvGroupName);
        }

        private int getStatusColor(String status) {
            if (status == null) return R.color.text_secondary;
            
            switch (status) {
                case Task.STATUS_TODO:
                    return R.color.task_todo;
                case Task.STATUS_IN_PROGRESS:
                    return R.color.task_in_progress;
                case Task.STATUS_DONE:
                    return R.color.task_done;
                default:
                    return R.color.text_secondary;
            }
        }

        private int getPriorityColor(String priority) {
            if (priority == null) return R.color.surface;
            
            switch (priority.toLowerCase()) {
                case "high":
                    return R.color.error;
                case "medium":
                    return R.color.warning;
                case "low":
                    return R.color.success;
                default:
                    return R.color.surface;
            }
        }

        private String getPriorityDisplayName(String priority) {
            if (priority == null) return "";
            
            switch (priority.toLowerCase()) {
                case "high":
                    return context.getString(R.string.priority_high);
                case "medium":
                    return context.getString(R.string.priority_medium);
                case "low":
                    return context.getString(R.string.priority_low);
                default:
                    return priority; // Return original if not found
            }
        }

        private String getStatusDisplayName(String status) {
            if (status == null) return "";
            
            switch (status) {
                case Task.STATUS_TODO:
                    return context.getString(R.string.status_todo);
                case Task.STATUS_IN_PROGRESS:
                    return context.getString(R.string.status_in_progress);
                case Task.STATUS_DONE:
                    return context.getString(R.string.status_done);
                default:
                    return status;
            }
        }

        private void loadGroupName(String groupId, TextView textView) {
            if (groupId == null) {
                textView.setText("Không có nhóm");
                return;
            }

            // Check cache first
            if (groupCache.containsKey(groupId)) {
                textView.setText(groupCache.get(groupId));
                return;
            }

            // Load from Firestore
            groupRepository.getGroupDetails(groupId)
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            String groupName = documentSnapshot.getString("name");
                            if (groupName != null) {
                                groupCache.put(groupId, groupName);
                                textView.setText(groupName);
                            } else {
                                textView.setText("Nhóm không tên");
                            }
                        } else {
                            textView.setText("Nhóm không tồn tại");
                        }
                    })
                    .addOnFailureListener(e -> textView.setText("Lỗi tải nhóm"));
        }
    }
} 