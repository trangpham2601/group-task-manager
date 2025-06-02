package com.example.grouptaskmanager.chat;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.grouptaskmanager.R;
import com.example.grouptaskmanager.model.GroupWithUnread;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class GroupChatAdapter extends RecyclerView.Adapter<GroupChatAdapter.GroupChatViewHolder> {

    private Context context;
    private List<GroupWithUnread> groupList;
    private OnGroupChatClickListener listener;

    public GroupChatAdapter(Context context, List<GroupWithUnread> groupList, OnGroupChatClickListener listener) {
        this.context = context;
        this.groupList = groupList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public GroupChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_group_chat, parent, false);
        return new GroupChatViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GroupChatViewHolder holder, int position) {
        GroupWithUnread groupWithUnread = groupList.get(position);
        holder.bind(groupWithUnread);
    }

    @Override
    public int getItemCount() {
        return groupList.size();
    }

    public class GroupChatViewHolder extends RecyclerView.ViewHolder {
        private ImageView ivGroupIcon;
        private TextView tvGroupName;
        private TextView tvLastMessage;
        private TextView tvLastMessageTime;
        private TextView tvMemberCount;
        private TextView tvUnreadBadge;
        private View viewOnlineIndicator;

        public GroupChatViewHolder(@NonNull View itemView) {
            super(itemView);
            ivGroupIcon = itemView.findViewById(R.id.iv_group_icon);
            tvGroupName = itemView.findViewById(R.id.tv_group_name);
            tvLastMessage = itemView.findViewById(R.id.tv_last_message);
            tvLastMessageTime = itemView.findViewById(R.id.tv_last_message_time);
            tvMemberCount = itemView.findViewById(R.id.tv_member_count);
            tvUnreadBadge = itemView.findViewById(R.id.tv_unread_badge);
            viewOnlineIndicator = itemView.findViewById(R.id.view_online_indicator);

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onGroupChatClick(groupList.get(getAdapterPosition()).getGroup());
                }
            });
        }

        public void bind(GroupWithUnread groupWithUnread) {
            // Set group name
            tvGroupName.setText(groupWithUnread.getGroup().getName());

            // Set member count
            int memberCount = groupWithUnread.getGroup().getMembers() != null ? 
                            groupWithUnread.getGroup().getMembers().size() : 0;
            tvMemberCount.setText(memberCount + " thành viên");

            // Set group icon
            if (groupWithUnread.getGroup().isPrivate()) {
                ivGroupIcon.setImageResource(R.drawable.ic_group_private);
            } else {
                ivGroupIcon.setImageResource(R.drawable.ic_group_public);
            }

            // Set last message
            if (groupWithUnread.getLastMessage() != null) {
                String lastMessageText = groupWithUnread.getLastMessage().getMessage();
                String senderName = groupWithUnread.getLastMessage().getSenderName();
                
                // Format: "Tên người gửi: Nội dung tin nhắn"
                if (senderName != null && !senderName.isEmpty()) {
                    tvLastMessage.setText(senderName + ": " + lastMessageText);
                } else {
                    tvLastMessage.setText(lastMessageText);
                }
                
                // Set last message time
                if (groupWithUnread.getLastMessage().getTimestamp() != null) {
                    Date messageDate = groupWithUnread.getLastMessage().getTimestamp().toDate();
                    setFormattedTime(messageDate);
                } else {
                    tvLastMessageTime.setText("");
                }
            } else {
                tvLastMessage.setText("Nhấn để bắt đầu trò chuyện");
                
                // Use group's updated time if no messages
                if (groupWithUnread.getGroup().getUpdatedAt() != null) {
                    setFormattedTime(groupWithUnread.getGroup().getUpdatedAt().toDate());
                } else {
                    tvLastMessageTime.setText("");
                }
            }

            // Set unread badge
            if (groupWithUnread.hasUnreadMessages()) {
                tvUnreadBadge.setVisibility(View.VISIBLE);
                int unreadCount = groupWithUnread.getUnreadCount();
                if (unreadCount > 99) {
                    tvUnreadBadge.setText("99+");
                } else {
                    tvUnreadBadge.setText(String.valueOf(unreadCount));
                }
                
                // Make group name bold if has unread messages
                tvGroupName.setTextColor(context.getColor(R.color.text_primary));
                tvLastMessage.setTextColor(context.getColor(R.color.text_primary));
            } else {
                tvUnreadBadge.setVisibility(View.GONE);
                
                // Normal text color if no unread messages
                tvGroupName.setTextColor(context.getColor(R.color.text_primary));
                tvLastMessage.setTextColor(context.getColor(R.color.text_secondary));
            }

            // Online indicator (can be enhanced later with real online status)
            viewOnlineIndicator.setVisibility(View.GONE);
        }

        private void setFormattedTime(Date date) {
            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
            SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM", Locale.getDefault());
            
            Date now = new Date();
            
            // If today, show time; if not today, show date
            if (isSameDay(now, date)) {
                tvLastMessageTime.setText(timeFormat.format(date));
            } else {
                tvLastMessageTime.setText(dateFormat.format(date));
            }
        }

        private boolean isSameDay(Date date1, Date date2) {
            SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
            return fmt.format(date1).equals(fmt.format(date2));
        }
    }

    public interface OnGroupChatClickListener {
        void onGroupChatClick(com.example.grouptaskmanager.model.Group group);
    }
} 