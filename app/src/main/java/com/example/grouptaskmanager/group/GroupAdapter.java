package com.example.grouptaskmanager.group;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.example.grouptaskmanager.R;
import com.example.grouptaskmanager.model.Group;
import com.example.grouptaskmanager.repository.TaskRepository;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class GroupAdapter extends RecyclerView.Adapter<GroupAdapter.GroupViewHolder> {

    private List<Group> groupList;
    private List<Group> filteredGroupList;
    private final OnGroupClickListener listener;
    private final TaskRepository taskRepository;
    private final FirebaseAuth auth;
    private final Map<String, Integer> taskCountCache = new HashMap<>();

    public GroupAdapter(List<Group> groupList, OnGroupClickListener listener) {
        this.groupList = groupList;
        this.filteredGroupList = new ArrayList<>(groupList);
        this.listener = listener;
        this.taskRepository = new TaskRepository();
        this.auth = FirebaseAuth.getInstance();
    }

    @NonNull
    @Override
    public GroupViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_group, parent, false);
        return new GroupViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GroupViewHolder holder, int position) {
        Group group = filteredGroupList.get(position);
        holder.bind(group);
    }

    @Override
    public int getItemCount() {
        return filteredGroupList.size();
    }

    public void updateList(List<Group> newList) {
        this.groupList = new ArrayList<>(newList);
        this.filteredGroupList = new ArrayList<>(newList);
        notifyDataSetChanged();
    }

    public void filter(String query) {
        filteredGroupList.clear();

        if (query.isEmpty()) {
            filteredGroupList.addAll(groupList);
        } else {
            String lowerCaseQuery = query.toLowerCase();
            for (Group group : groupList) {
                if (group.getName().toLowerCase().contains(lowerCaseQuery) ||
                        (group.getDescription() != null && group.getDescription().toLowerCase().contains(lowerCaseQuery))) {
                    filteredGroupList.add(group);
                }
            }
        }

        notifyDataSetChanged();
    }

    public void filterByType(String filterType, String currentUserId) {
        filteredGroupList.clear();

        if (filterType.equals("all")) {
            filteredGroupList.addAll(groupList);
        } else if (filterType.equals("owned") && currentUserId != null) {
            for (Group group : groupList) {
                if (currentUserId.equals(group.getCreatedBy())) {
                    filteredGroupList.add(group);
                }
            }
        } else if (filterType.equals("joined") && currentUserId != null) {
            for (Group group : groupList) {
                if (!currentUserId.equals(group.getCreatedBy())) {
                    filteredGroupList.add(group);
                }
            }
        }

        notifyDataSetChanged();
    }

    private void loadTaskCount(String groupId, GroupViewHolder holder) {
        // Kiểm tra cache trước
        if (taskCountCache.containsKey(groupId)) {
            int count = taskCountCache.get(groupId);
            holder.tvTaskCount.setText(count + " nhiệm vụ");
            return;
        }

        // Load từ Firebase
        taskRepository.getGroupTasks(groupId)
                .addOnSuccessListener(querySnapshot -> {
                    int taskCount = querySnapshot.size();
                    taskCountCache.put(groupId, taskCount);
                    holder.tvTaskCount.setText(taskCount + " nhiệm vụ");
                })
                .addOnFailureListener(e -> {
                    holder.tvTaskCount.setText("0 nhiệm vụ");
                });
    }

    public interface OnGroupClickListener {
        void onGroupClick(Group group);
    }

    class GroupViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvGroupName;
        private final TextView tvGroupDescription;
        private final TextView tvMemberCount;
        private final TextView tvTaskCount;
        private final TextView tvRoleBadge;
        private final TextView tvGroupIcon;
        private final TextView tvCreatedDate;
        private final ImageView ivPrivateBadge;
        private final CardView cvGroupIcon;

        GroupViewHolder(@NonNull View itemView) {
            super(itemView);
            tvGroupName = itemView.findViewById(R.id.tv_group_name);
            tvGroupDescription = itemView.findViewById(R.id.tv_group_description);
            tvMemberCount = itemView.findViewById(R.id.tv_member_count);
            tvTaskCount = itemView.findViewById(R.id.tv_task_count);
            tvRoleBadge = itemView.findViewById(R.id.tv_role_badge);
            tvGroupIcon = itemView.findViewById(R.id.tv_group_icon);
            tvCreatedDate = itemView.findViewById(R.id.tv_created_date);
            ivPrivateBadge = itemView.findViewById(R.id.iv_private_badge);
            cvGroupIcon = itemView.findViewById(R.id.cv_group_icon);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    listener.onGroupClick(filteredGroupList.get(position));
                }
            });
        }

        void bind(Group group) {
            tvGroupName.setText(group.getName());
            
            // Hiển thị icon nhóm (chữ cái đầu tiên)
            String groupName = group.getName();
            if (groupName != null && !groupName.isEmpty()) {
                tvGroupIcon.setText(String.valueOf(groupName.charAt(0)).toUpperCase());
            } else {
                tvGroupIcon.setText("G");
            }
            
            if (group.getDescription() != null && !group.getDescription().isEmpty()) {
                tvGroupDescription.setText(group.getDescription());
                tvGroupDescription.setVisibility(View.VISIBLE);
            } else {
                tvGroupDescription.setVisibility(View.GONE);
            }
            
            // Hiển thị số lượng thành viên
            int memberCount = group.getMembers() != null ? group.getMembers().size() : 0;
            tvMemberCount.setText(memberCount + " thành viên");
            
            // Load và hiển thị số lượng nhiệm vụ
            loadTaskCount(group.getId(), this);
            
            // Hiển thị role badge
            String currentUserId = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
            if (currentUserId != null && currentUserId.equals(group.getCreatedBy())) {
                tvRoleBadge.setText("Admin");
                tvRoleBadge.setSelected(true); // Sử dụng state selected cho admin
            } else {
                tvRoleBadge.setText("Member");
                tvRoleBadge.setSelected(false);
            }
            
            // Hiển thị badge riêng tư
            ivPrivateBadge.setVisibility(group.isPrivate() ? View.VISIBLE : View.GONE);
            
            // Hiển thị ngày tạo nhóm (định dạng ngắn)
            Timestamp createdAt = group.getCreatedAt();
            if (createdAt != null) {
                Date date = createdAt.toDate();
                SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                tvCreatedDate.setText(dateFormat.format(date));
            } else {
                tvCreatedDate.setText("--/--/----");
            }
        }
    }
} 