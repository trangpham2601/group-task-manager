package com.example.grouptaskmanager.task;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.grouptaskmanager.R;
import com.example.grouptaskmanager.model.Task;
import com.example.grouptaskmanager.model.User;
import com.google.android.material.chip.Chip;
import com.google.firebase.Timestamp;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Map;

public class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.TaskViewHolder> {

    private List<Task> taskList;
    private final OnTaskClickListener listener;
    private final Context context;
    private final Map<String, User> userCache;

    public TaskAdapter(Context context, List<Task> taskList, OnTaskClickListener listener, Map<String, User> userCache) {
        this.context = context;
        this.taskList = taskList;
        this.listener = listener;
        this.userCache = userCache;
    }

    @NonNull
    @Override
    public TaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_task, parent, false);
        return new TaskViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TaskViewHolder holder, int position) {
        Task task = taskList.get(position);
        holder.bind(task);
    }

    @Override
    public int getItemCount() {
        return taskList.size();
    }

    public void updateTasks(List<Task> newTaskList) {
        // Create a new list to avoid reference issues
        this.taskList = new ArrayList<>(newTaskList);
        notifyDataSetChanged();
    }

    public interface OnTaskClickListener {
        void onTaskClick(Task task);
    }

    class TaskViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvTaskTitle;
        private final TextView tvTaskDescription;
        private final TextView tvAssignedTo;
        private final TextView tvDeadline;
        private final Chip chipPriority;
        private final Chip chipStatus;
        private final View statusIndicator;

        TaskViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTaskTitle = itemView.findViewById(R.id.tv_task_title);
            tvTaskDescription = itemView.findViewById(R.id.tv_task_description);
            tvAssignedTo = itemView.findViewById(R.id.tv_assigned_to);
            tvDeadline = itemView.findViewById(R.id.tv_deadline);
            chipPriority = itemView.findViewById(R.id.chip_priority);
            chipStatus = itemView.findViewById(R.id.chip_status);
            statusIndicator = itemView.findViewById(R.id.status_indicator);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    listener.onTaskClick(taskList.get(position));
                }
            });
        }

        void bind(Task task) {
            tvTaskTitle.setText(task.getTitle());
            
            if (task.getDescription() != null && !task.getDescription().isEmpty()) {
                tvTaskDescription.setText(task.getDescription());
                tvTaskDescription.setVisibility(View.VISIBLE);
            } else {
                tvTaskDescription.setVisibility(View.GONE);
            }
            
            // Hiển thị tên người được giao thay vì ID
            String assignedTo = task.getAssignedTo();
            if (assignedTo != null && !assignedTo.isEmpty()) {
                if (userCache.containsKey(assignedTo)) {
                    User user = userCache.get(assignedTo);
                    if (user != null && user.getName() != null && !user.getName().isEmpty()) {
                        tvAssignedTo.setText(user.getName());
                    } else if (user != null && user.getEmail() != null) {
                        // Fallback to email if name is not available
                        tvAssignedTo.setText(user.getEmail());
                    } else {
                        // If no name or email, show user ID as fallback
                        tvAssignedTo.setText(assignedTo);
                    }
                } else {
                    // If user is not in cache, show user ID as fallback
                    tvAssignedTo.setText(assignedTo);
                }
                tvAssignedTo.setVisibility(View.VISIBLE);
            } else {
                tvAssignedTo.setText(R.string.unassigned);
                tvAssignedTo.setVisibility(View.VISIBLE);
            }
            
            // Format và hiển thị deadline
            Timestamp deadline = task.getDeadline();
            if (deadline != null) {
                Date date = deadline.toDate();
                SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                tvDeadline.setText(dateFormat.format(date));
            }
            
            // Hiển thị mức độ ưu tiên
            setPriorityChip(chipPriority, task.getPriority());
            
            // Hiển thị trạng thái
            setStatusChip(chipStatus, task.getStatus());

            // Set status color
            int colorRes;
            switch (task.getStatus()) {
                case Task.STATUS_DONE:
                    colorRes = R.color.task_done;
                    break;
                case Task.STATUS_IN_PROGRESS:
                    colorRes = R.color.task_in_progress;
                    break;
                default:
                    colorRes = R.color.task_todo;
            }
            statusIndicator.setBackgroundColor(ContextCompat.getColor(context, colorRes));
            
            // Check if task is overdue and set deadline text color accordingly
            if (isTaskOverdue(task) && !Task.STATUS_DONE.equals(task.getStatus())) {
                tvDeadline.setTextColor(ContextCompat.getColor(context, R.color.error));
            } else {
                tvDeadline.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
            }
        }
        
        private boolean isTaskOverdue(Task task) {
            if (task.getDeadline() != null) {
                Date deadline = task.getDeadline().toDate();
                Date now = new Date();
                return deadline.before(now);
            }
            return false;
        }
        
        private void setPriorityChip(Chip chip, String priority) {
            int colorRes;
            String text;
            
            switch (priority) {
                case Task.PRIORITY_HIGH:
                    colorRes = R.color.priority_high;
                    text = context.getString(R.string.priority_high);
                    break;
                case Task.PRIORITY_MEDIUM:
                    colorRes = R.color.priority_medium;
                    text = context.getString(R.string.priority_medium);
                    break;
                case Task.PRIORITY_LOW:
                default:
                    colorRes = R.color.priority_low;
                    text = context.getString(R.string.priority_low);
                    break;
            }
            
            int color = ContextCompat.getColor(context, colorRes);
            chip.setChipBackgroundColor(ColorStateList.valueOf(color));
            chip.setText(text);
        }
        
        private void setStatusChip(Chip chip, String status) {
            int colorRes;
            String text;
            
            switch (status) {
                case Task.STATUS_DONE:
                    colorRes = R.color.task_done;
                    text = context.getString(R.string.status_done);
                    break;
                case Task.STATUS_IN_PROGRESS:
                    colorRes = R.color.task_in_progress;
                    text = context.getString(R.string.status_in_progress);
                    break;
                case Task.STATUS_TODO:
                default:
                    colorRes = R.color.task_todo;
                    text = context.getString(R.string.status_todo);
                    break;
            }
            
            int color = ContextCompat.getColor(context, colorRes);
            chip.setChipBackgroundColor(ColorStateList.valueOf(color));
            chip.setText(text);
        }
    }
} 