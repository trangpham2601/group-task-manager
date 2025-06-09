package com.example.grouptaskmanager.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.grouptaskmanager.R;
import com.example.grouptaskmanager.model.Comment;
import com.google.firebase.auth.FirebaseAuth;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class CommentsAdapter extends RecyclerView.Adapter<CommentsAdapter.CommentViewHolder> {

    private List<Comment> comments;
    private OnCommentActionListener listener;
    private String currentUserId;

    public interface OnCommentActionListener {
        void onReplyClick(Comment comment);
        void onEditClick(Comment comment);
        void onDeleteClick(Comment comment);
    }

    public CommentsAdapter(OnCommentActionListener listener) {
        this.comments = new ArrayList<>();
        this.listener = listener;
        this.currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null ?
                FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
    }

    @NonNull
    @Override
    public CommentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_comment, parent, false);
        return new CommentViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CommentViewHolder holder, int position) {
        Comment comment = comments.get(position);
        holder.bind(comment);
    }

    @Override
    public int getItemCount() {
        return comments.size();
    }

    public void updateComments(List<Comment> newComments) {
        this.comments.clear();
        this.comments.addAll(newComments);
        notifyDataSetChanged();
    }

    public void addComment(Comment comment) {
        comments.add(comment);
        notifyItemInserted(comments.size() - 1);
    }

    public void updateComment(Comment updatedComment) {
        for (int i = 0; i < comments.size(); i++) {
            if (comments.get(i).getId().equals(updatedComment.getId())) {
                comments.set(i, updatedComment);
                notifyItemChanged(i);
                break;
            }
        }
    }

    public void removeComment(String commentId) {
        for (int i = 0; i < comments.size(); i++) {
            if (comments.get(i).getId().equals(commentId)) {
                comments.remove(i);
                notifyItemRemoved(i);
                break;
            }
        }
    }

    class CommentViewHolder extends RecyclerView.ViewHolder {
        private View replyIndicator;
        private TextView tvReplyTo;
        private TextView tvAuthorName;
        private TextView tvCommentTime;
        private TextView tvCommentContent;
        private TextView btnReply;
        private TextView btnEdit;
        private TextView btnDelete;

        public CommentViewHolder(@NonNull View itemView) {
            super(itemView);
            replyIndicator = itemView.findViewById(R.id.reply_indicator);
            tvReplyTo = itemView.findViewById(R.id.tv_reply_to);
            tvAuthorName = itemView.findViewById(R.id.tv_author_name);
            tvCommentTime = itemView.findViewById(R.id.tv_time_ago);
            tvCommentContent = itemView.findViewById(R.id.tv_comment_content);
            btnReply = itemView.findViewById(R.id.btn_reply);
            btnEdit = itemView.findViewById(R.id.btn_edit);
            btnDelete = itemView.findViewById(R.id.btn_delete);
        }

        public void bind(Comment comment) {
            // Set author name
            tvAuthorName.setText(comment.getAuthorName() != null ? 
                               comment.getAuthorName() : "Ẩn danh");

            // Set comment content
            tvCommentContent.setText(comment.getContent());

            // Set creation time
            if (comment.getCreatedAt() != null) {
                String timeAgo = getTimeAgo(comment.getCreatedAt().toDate());
                tvCommentTime.setText(timeAgo);
            }

            // Handle reply display
            if (comment.isReply() && comment.getReplyToAuthorName() != null) {
                replyIndicator.setVisibility(View.VISIBLE);
                tvReplyTo.setVisibility(View.VISIBLE);
                tvReplyTo.setText("Trả lời @" + comment.getReplyToAuthorName());
            } else {
                replyIndicator.setVisibility(View.GONE);
                tvReplyTo.setVisibility(View.GONE);
            }

            // Show/hide action buttons based on ownership
            boolean isOwnComment = currentUserId != null && 
                                 currentUserId.equals(comment.getAuthorId());
            
            btnEdit.setVisibility(isOwnComment ? View.VISIBLE : View.GONE);
            btnDelete.setVisibility(isOwnComment ? View.VISIBLE : View.GONE);

            // Set click listeners
            btnReply.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onReplyClick(comment);
                }
            });

            btnEdit.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onEditClick(comment);
                }
            });

            btnDelete.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDeleteClick(comment);
                }
            });
        }

        private String getTimeAgo(Date date) {
            if (date == null) return "";

            long now = System.currentTimeMillis();
            long diff = now - date.getTime();

            if (diff < TimeUnit.MINUTES.toMillis(1)) {
                return "Vừa xong";
            } else if (diff < TimeUnit.HOURS.toMillis(1)) {
                long minutes = TimeUnit.MILLISECONDS.toMinutes(diff);
                return minutes + " phút trước";
            } else if (diff < TimeUnit.DAYS.toMillis(1)) {
                long hours = TimeUnit.MILLISECONDS.toHours(diff);
                return hours + " giờ trước";
            } else if (diff < TimeUnit.DAYS.toMillis(7)) {
                long days = TimeUnit.MILLISECONDS.toDays(diff);
                return days + " ngày trước";
            } else {
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                return sdf.format(date);
            }
        }
    }
} 