package com.rcl.androidstreamcontrol;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.rcl.androidstreamcontrol.databinding.ActivityTasksBinding;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class TasksActivity extends AppCompatActivity {

    private static final String TAG = "TasksActivity";
    private static final String SUPABASE_URL = "https://qxzozsdgrgahvgzpsfmm.supabase.co";
    private static final String ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InF4em96c2RncmdhaHZnenBzZm1tIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Nzk4MDY4NjAsImV4cCI6MjA5NTM4Mjg2MH0.inUzMp-S9BRjNQYHynTAOPRo4EeY3Whqv9okvEeySB4";

    private ActivityTasksBinding binding;
    private TasksAdapter adapter;
    private List<TaskItem> allItems;
    private List<TaskItem> displayedItems;
    private boolean showingHabits = false;

    private String uid;
    private String token;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTasksBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        SharedPreferences prefs = getSharedPreferences("PortalPrefs", MODE_PRIVATE);
        uid = prefs.getString("supabase_uid", null);
        token = prefs.getString("supabase_token", null);

        if (uid == null || token == null) {
            Toast.makeText(this, "Not logged in!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        allItems = new ArrayList<>();
        displayedItems = new ArrayList<>();
        adapter = new TasksAdapter(displayedItems, new TasksAdapter.OnItemClickListener() {
            @Override
            public void onToggleCompleted(TaskItem item, boolean isCompleted) {
                item.isCompleted = isCompleted;
                updateItemInSupabase(item);
                filterList();
            }

            @Override
            public void onDeleteClick(TaskItem item) {
                deleteItemFromSupabase(item);
            }
        });

        binding.itemsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.itemsRecyclerView.setAdapter(adapter);

        binding.tabTasks.setOnClickListener(v -> {
            showingHabits = false;
            binding.tabTasks.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFD81B60));
            binding.tabTasks.setTextColor(0xFFFFFFFF);
            binding.tabHabits.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF151520));
            binding.tabHabits.setTextColor(0xFF888899);
            filterList();
        });

        binding.tabHabits.setOnClickListener(v -> {
            showingHabits = true;
            binding.tabHabits.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFD81B60));
            binding.tabHabits.setTextColor(0xFFFFFFFF);
            binding.tabTasks.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF151520));
            binding.tabTasks.setTextColor(0xFF888899);
            filterList();
        });

        binding.addItemButton.setOnClickListener(v -> {
            String title = binding.itemInput.getText().toString().trim();
            if (!title.isEmpty()) {
                binding.itemInput.setText("");
                createItemInSupabase(title, showingHabits);
            }
        });

        binding.backButton.setOnClickListener(v -> finish());

        loadItemsFromSupabase();
    }

    private void filterList() {
        displayedItems.clear();
        for (TaskItem item : allItems) {
            if (item.isHabit == showingHabits) {
                displayedItems.add(item);
            }
        }
        runOnUiThread(() -> adapter.notifyDataSetChanged());
    }

    private void loadItemsFromSupabase() {
        new Thread(() -> {
            try {
                // Fetch tasks
                fetchTable("tasks", false);
                // Fetch habits
                fetchTable("habits", true);
                
                runOnUiThread(this::filterList);
            } catch (Exception e) {
                Log.e(TAG, "Failed to load items", e);
            }
        }).start();
    }

    private void fetchTable(String table, boolean isHabit) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(SUPABASE_URL + "/rest/v1/" + table + "?user_id=eq." + uid + "&select=*").openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("apikey", ANON_KEY);
        conn.setRequestProperty("Authorization", "Bearer " + token);

        if (conn.getResponseCode() == 200) {
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) response.append(line);
            in.close();

            JSONArray arr = new JSONArray(response.toString());
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String id = obj.getString("id");
                String title = obj.getString("title");
                boolean isCompleted = obj.getBoolean("is_completed");
                allItems.add(new TaskItem(id, title, isCompleted, isHabit));
            }
        }
    }

    private void createItemInSupabase(String title, boolean isHabit) {
        new Thread(() -> {
            try {
                String table = isHabit ? "habits" : "tasks";
                JSONObject payload = new JSONObject();
                payload.put("user_id", uid);
                payload.put("title", title);
                payload.put("is_completed", false);

                HttpURLConnection conn = (HttpURLConnection) new URL(SUPABASE_URL + "/rest/v1/" + table).openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("apikey", ANON_KEY);
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Prefer", "return=representation");
                conn.setDoOutput(true);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload.toString().getBytes());
                }

                if (conn.getResponseCode() == 201) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) response.append(line);
                    in.close();

                    JSONArray arr = new JSONArray(response.toString());
                    if (arr.length() > 0) {
                        JSONObject obj = arr.getJSONObject(0);
                        String id = obj.getString("id");
                        TaskItem newItem = new TaskItem(id, title, false, isHabit);
                        allItems.add(newItem);
                        runOnUiThread(this::filterList);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to create item", e);
            }
        }).start();
    }

    private void updateItemInSupabase(TaskItem item) {
        new Thread(() -> {
            try {
                String table = item.isHabit ? "habits" : "tasks";
                JSONObject payload = new JSONObject();
                payload.put("is_completed", item.isCompleted);

                HttpURLConnection conn = (HttpURLConnection) new URL(SUPABASE_URL + "/rest/v1/" + table + "?id=eq." + item.id).openConnection();
                conn.setRequestMethod("PATCH");
                conn.setRequestProperty("apikey", ANON_KEY);
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload.toString().getBytes());
                }
                conn.getResponseCode();
            } catch (Exception e) {
                Log.e(TAG, "Failed to update item", e);
            }
        }).start();
    }

    private void deleteItemFromSupabase(TaskItem item) {
        new Thread(() -> {
            try {
                String table = item.isHabit ? "habits" : "tasks";
                HttpURLConnection conn = (HttpURLConnection) new URL(SUPABASE_URL + "/rest/v1/" + table + "?id=eq." + item.id).openConnection();
                conn.setRequestMethod("DELETE");
                conn.setRequestProperty("apikey", ANON_KEY);
                conn.setRequestProperty("Authorization", "Bearer " + token);

                if (conn.getResponseCode() == 200 || conn.getResponseCode() == 204) {
                    allItems.remove(item);
                    runOnUiThread(this::filterList);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to delete item", e);
            }
        }).start();
    }
}
