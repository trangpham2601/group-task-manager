package com.example.grouptaskmanager.profile;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.grouptaskmanager.MainActivity;
import com.example.grouptaskmanager.R;
import com.example.grouptaskmanager.auth.LoginActivity;
import com.example.grouptaskmanager.group.CreateGroupActivity;
import com.example.grouptaskmanager.model.Group;
import com.example.grouptaskmanager.profile.EditProfileActivity;
import com.example.grouptaskmanager.utils.NotificationPermissionHelper;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import de.hdodenhof.circleimageview.CircleImageView;

public class ProfileFragment extends Fragment implements GroupSmallAdapter.OnGroupClickListener {

    private static final String TAG = "ProfileFragment";

    // UI elements
    private TextView tvUserName, tvUserEmail, tvJoinedDate, tvNoGroups;
    private CircleImageView ivProfile;
    private RecyclerView rvGroups;
    private CircularProgressIndicator progressGroups;
    private Button btnLogout, btnEditProfile;
    private SwitchCompat switchNotifications;

    // Firebase & data
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private SharedPreferences sharedPreferences;
    private List<Group> userGroups;
    private GroupSmallAdapter groupAdapter;

    // Activity result launcher for edit profile
    private final ActivityResultLauncher<Intent> editProfileLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == getActivity().RESULT_OK) {
                    // Refresh user info after editing
                    loadUserInfo();
                }
            });

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext());
        userGroups = new ArrayList<>();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Reload user's groups when returning to the fragment
        loadUserGroups();
        // Update notification switch state when user returns from settings
        updateNotificationSwitch();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Initialize UI elements
        tvUserName = view.findViewById(R.id.tv_user_name);
        tvUserEmail = view.findViewById(R.id.tv_user_email);
        tvJoinedDate = view.findViewById(R.id.tv_joined_date);
        tvNoGroups = view.findViewById(R.id.tv_no_groups);
        ivProfile = view.findViewById(R.id.iv_profile);
        rvGroups = view.findViewById(R.id.rv_groups);
        progressGroups = view.findViewById(R.id.progress_groups);
        btnLogout = view.findViewById(R.id.btn_logout);
        btnEditProfile = view.findViewById(R.id.btn_edit_profile);
        switchNotifications = view.findViewById(R.id.switch_notifications);
        
        // Initialize notification switch state
        updateNotificationSwitch();
        
        // Logout button
        btnLogout.setOnClickListener(v -> logout());
        
        // Edit profile button
        btnEditProfile.setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), EditProfileActivity.class);
            editProfileLauncher.launch(intent);
        });
        
        // Set up notification switch
        setupNotificationSwitch();
        
        // Setup RecyclerView
        setupRecyclerView();
        
        // Load user info and groups
        loadUserInfo();
        loadUserGroups();
    }

    private void setupNotificationSwitch() {
        switchNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                // User wants to enable notifications
                if (!NotificationPermissionHelper.hasNotificationPermission(requireContext())) {
                    // Request permission through MainActivity to avoid lifecycle issues
                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).requestNotificationPermissionForProfile(
                                granted -> {
                                    if (granted) {
                                        Toast.makeText(getContext(), "Thông báo đã được bật", Toast.LENGTH_SHORT).show();
                                        updateNotificationSwitch();
                                    } else {
                                        switchNotifications.setChecked(false);
                                        Toast.makeText(getContext(), "Không thể bật thông báo", Toast.LENGTH_SHORT).show();
                                    }
                                }
                        );
                    }
                } else {
                    // Already have permission
                    Toast.makeText(getContext(), "Thông báo đã được bật", Toast.LENGTH_SHORT).show();
                }
            } else {
                // User wants to disable notifications - show settings
                com.example.grouptaskmanager.utils.PermissionUtils.openNotificationSettings(requireContext());
                Toast.makeText(getContext(), "Vui lòng tắt thông báo trong Cài đặt", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateNotificationSwitch() {
        if (switchNotifications != null) {
            boolean hasPermission = NotificationPermissionHelper.hasNotificationPermission(requireContext());
            switchNotifications.setChecked(hasPermission);
        }
    }

    private void setupRecyclerView() {
        groupAdapter = new GroupSmallAdapter(getContext(), userGroups, this);
        rvGroups.setLayoutManager(new LinearLayoutManager(getContext()));
        rvGroups.setAdapter(groupAdapter);
    }

    private void loadUserInfo() {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null) {
            tvUserName.setText(currentUser.getDisplayName() != null ? 
                              currentUser.getDisplayName() : "Người dùng");
            tvUserEmail.setText(currentUser.getEmail());
            
            // Load profile photo
            if (currentUser.getPhotoUrl() != null) {
                Glide.with(this)
                        .load(currentUser.getPhotoUrl())
                        .circleCrop()
                        .placeholder(R.drawable.profile_placeholder)
                        .into(ivProfile);
            } else {
                ivProfile.setImageResource(R.drawable.profile_placeholder);
            }
            
            // Set joined date (using creation time as approximation)
            if (currentUser.getMetadata() != null) {
                long creationTimestamp = currentUser.getMetadata().getCreationTimestamp();
                Date joinDate = new Date(creationTimestamp);
                SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                tvJoinedDate.setText(dateFormat.format(joinDate));
            }
        }
    }

    private void loadUserGroups() {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) return;

        progressGroups.setVisibility(View.VISIBLE);
        tvNoGroups.setVisibility(View.GONE);

        db.collection("groups")
                .whereArrayContains("members", currentUser.getUid())
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    userGroups.clear();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        Group group = document.toObject(Group.class);
                        group.setId(document.getId());
                        userGroups.add(group);
                    }
                    
                    progressGroups.setVisibility(View.GONE);
                    if (userGroups.isEmpty()) {
                        tvNoGroups.setVisibility(View.VISIBLE);
                    } else {
                        tvNoGroups.setVisibility(View.GONE);
                    }
                    
                    groupAdapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading user groups", e);
                    progressGroups.setVisibility(View.GONE);
                    tvNoGroups.setVisibility(View.VISIBLE);
                    Toast.makeText(getContext(), "Lỗi khi tải danh sách nhóm", Toast.LENGTH_SHORT).show();
                });
    }

    private void logout() {
        auth.signOut();
        Intent intent = new Intent(getActivity(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        if (getActivity() != null) {
            getActivity().finish();
        }
    }

    @Override
    public void onGroupClick(Group group) {
        // Handle group click if needed
        Toast.makeText(getContext(), "Đã chọn nhóm: " + group.getName(), Toast.LENGTH_SHORT).show();
    }

    /**
     * Interface for MainActivity to handle permission result
     */
    public interface PermissionResultCallback {
        void onResult(boolean granted);
    }
} 