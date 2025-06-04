package com.example.grouptaskmanager.profile;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.grouptaskmanager.R;
import com.example.grouptaskmanager.model.Group;
import com.google.android.material.chip.Chip;

import java.util.List;

public class GroupSmallAdapter extends RecyclerView.Adapter<GroupSmallAdapter.GroupViewHolder> {

    private final List<Group> groupList;
    private final OnGroupClickListener listener;
    private final Context context;

    public GroupSmallAdapter(Context context, List<Group> groupList, OnGroupClickListener listener) {
        this.context = context;
        this.groupList = groupList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public GroupViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_group_small, parent, false);
        return new GroupViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GroupViewHolder holder, int position) {
        Group group = groupList.get(position);
        holder.bind(group);
    }

    @Override
    public int getItemCount() {
        return groupList.size();
    }

    public interface OnGroupClickListener {
        void onGroupClick(Group group);
    }

    class GroupViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvGroupName;
        private final TextView tvMemberCount;
        private final ImageView ivGroupIcon;
        private final Chip chipGroupType;

        GroupViewHolder(@NonNull View itemView) {
            super(itemView);
            tvGroupName = itemView.findViewById(R.id.tv_group_name);
            tvMemberCount = itemView.findViewById(R.id.tv_member_count);
            ivGroupIcon = itemView.findViewById(R.id.iv_group_icon);
            chipGroupType = itemView.findViewById(R.id.chip_group_type);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    listener.onGroupClick(groupList.get(position));
                }
            });
        }

        void bind(Group group) {
            tvGroupName.setText(group.getName());
            
            // Display number of members
            int memberCount = group.getMembers() != null ? group.getMembers().size() : 0;
            tvMemberCount.setText(context.getString(R.string.members_count, memberCount));
            
            // Set group type
            String groupType = group.isPrivate() ? "Riêng tư" : "Công khai";
            chipGroupType.setText(groupType);
            
            // First letter of group name as icon (if no custom icon)
            if (group.getName() != null && !group.getName().isEmpty()) {
                String firstLetter = group.getName().substring(0, 1).toUpperCase();
                // Custom icon handling could be added here
            }
        }
    }
} 