package com.example.grouptaskmanager.group;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SearchView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.grouptaskmanager.databinding.FragmentGroupsBinding;
import com.example.grouptaskmanager.model.Group;
import com.example.grouptaskmanager.repository.GroupRepository;
import com.google.android.material.chip.Chip;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;

public class GroupsFragment extends Fragment implements GroupAdapter.OnGroupClickListener {

    private static final String TAG = "GroupsFragment";
    private FragmentGroupsBinding binding;
    private GroupRepository groupRepository;
    private GroupAdapter groupAdapter;
    private List<Group> groupList;
    private FirebaseAuth auth;
    private String currentFilter = "all";

    // Activity Result Launchers
    private ActivityResultLauncher<Intent> joinGroupLauncher;
    private ActivityResultLauncher<Intent> createGroupLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        groupRepository = new GroupRepository();
        groupList = new ArrayList<>();
        auth = FirebaseAuth.getInstance();
        
        // Khởi tạo Activity Result Launchers
        setupActivityResultLaunchers();
    }

    private void setupActivityResultLaunchers() {
        // Launcher cho JoinGroupActivity
        joinGroupLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == requireActivity().RESULT_OK 
                            && result.getData() != null 
                            && result.getData().getBooleanExtra(JoinGroupActivity.EXTRA_GROUP_JOINED, false)) {
                        // Reload danh sách nhóm khi tham gia thành công
                        loadGroups();
                    }
                }
        );

        // Launcher cho CreateGroupActivity
        createGroupLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == requireActivity().RESULT_OK) {
                        // Reload danh sách nhóm khi tạo nhóm thành công
                        loadGroups();
                    }
                }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentGroupsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupRecyclerView();
        setupListeners();
        setupSearchView();
        setupFilterChips();
        loadGroups();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Reload groups when returning to the fragment
        loadGroups();
    }

    private void setupRecyclerView() {
        groupAdapter = new GroupAdapter(groupList, this);
        binding.rvGroups.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvGroups.setAdapter(groupAdapter);
    }

    private void setupListeners() {
        binding.fabCreateGroup.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), CreateGroupActivity.class);
            createGroupLauncher.launch(intent);
        });

        binding.fabJoinGroup.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), JoinGroupActivity.class);
            joinGroupLauncher.launch(intent);
        });

        // Listener cho nút "Tạo nhóm đầu tiên" trong empty state
        binding.btnCreateFirstGroup.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), CreateGroupActivity.class);
            createGroupLauncher.launch(intent);
        });

        // Listener cho nút "Tham gia nhóm" trong empty state
        binding.btnJoinFirstGroup.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), JoinGroupActivity.class);
            joinGroupLauncher.launch(intent);
        });
    }

    private void setupSearchView() {
        binding.searchGroups.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                groupAdapter.filter(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                groupAdapter.filter(newText);
                return true;
            }
        });
    }

    private void setupFilterChips() {
        binding.chipAllGroups.setOnClickListener(v -> {
            currentFilter = "all";
            applyFilters();
        });

        binding.chipOwnedGroups.setOnClickListener(v -> {
            currentFilter = "owned";
            applyFilters();
        });

        binding.chipJoinedGroups.setOnClickListener(v -> {
            currentFilter = "joined";
            applyFilters();
        });
    }

    private void applyFilters() {
        String currentUserId = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
        groupAdapter.filterByType(currentFilter, currentUserId);
    }

    private void loadGroups() {
        showLoading(true);
        groupRepository.getUserGroups()
                .addOnSuccessListener(this::processGroupData)
                .addOnFailureListener(e -> {
                    if (binding == null) return;
                    showLoading(false);
                    Log.e(TAG, "Error loading groups", e);
                    showEmptyState(true);
                });
    }

    private void processGroupData(QuerySnapshot querySnapshot) {
        if (binding == null) return;
        
        groupList.clear();
        
        if (querySnapshot.isEmpty()) {
            showEmptyState(true);
            showLoading(false);
            updateStatistics(0, 0, 0);
            return;
        }
        
        for (DocumentSnapshot document : querySnapshot.getDocuments()) {
            Group group = document.toObject(Group.class);
            if (group != null) {
                // Ensure ID is set (might be redundant if model has correct structure)
                group.setId(document.getId());
                groupList.add(group);
            }
        }
        
        groupAdapter.updateList(groupList);
        applyFilters();
        showEmptyState(groupList.isEmpty());
        showLoading(false);
        
        // Cập nhật thống kê
        updateStatistics();
    }

    private void updateStatistics() {
        String currentUserId = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
        
        int totalGroups = groupList.size();
        int ownedGroups = 0;
        int joinedGroups = 0;
        
        if (currentUserId != null) {
            for (Group group : groupList) {
                if (currentUserId.equals(group.getCreatedBy())) {
                    ownedGroups++;
                } else {
                    joinedGroups++;
                }
            }
        }
        
        updateStatistics(totalGroups, ownedGroups, joinedGroups);
    }

    private void updateStatistics(int totalGroups, int ownedGroups, int joinedGroups) {
        if (binding == null) return;
        binding.tvTotalGroups.setText(String.valueOf(totalGroups));
        binding.tvOwnedGroups.setText(String.valueOf(ownedGroups));
        binding.tvJoinedGroups.setText(String.valueOf(joinedGroups));
    }

    private void showEmptyState(boolean isEmpty) {
        if (binding == null) return;
        if (isEmpty) {
            binding.rvGroups.setVisibility(View.GONE);
            binding.layoutEmptyGroups.setVisibility(View.VISIBLE);
            binding.progressBar.setVisibility(View.GONE); // Đảm bảo ẩn progress bar
            // Ẩn FABs khi hiển thị empty state để tránh duplicate buttons
            binding.fabCreateGroup.setVisibility(View.GONE);
            binding.fabJoinGroup.setVisibility(View.GONE);
        } else {
            binding.rvGroups.setVisibility(View.VISIBLE);
            binding.layoutEmptyGroups.setVisibility(View.GONE);
            // Hiển thị lại FABs khi có data
            binding.fabCreateGroup.setVisibility(View.VISIBLE);
            binding.fabJoinGroup.setVisibility(View.VISIBLE);
        }
    }

    private void showLoading(boolean isLoading) {
        if (binding == null) return;
        if (isLoading) {
            binding.progressBar.setVisibility(View.VISIBLE);
            binding.rvGroups.setVisibility(View.GONE);
            binding.layoutEmptyGroups.setVisibility(View.GONE); // Đảm bảo ẩn empty state
            binding.fabCreateGroup.setVisibility(View.GONE); // Ẩn FABs khi loading
            binding.fabJoinGroup.setVisibility(View.GONE);
        } else {
            binding.progressBar.setVisibility(View.GONE);
            // Không set visibility cho rvGroups, layoutEmptyGroups và FABs ở đây
            // Để processGroupData() và showEmptyState() quyết định hiển thị cái nào
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onGroupClick(Group group) {
        Intent intent = new Intent(requireContext(), GroupDetailActivity.class);
        intent.putExtra("GROUP_ID", group.getId());
        startActivity(intent);
    }
} 