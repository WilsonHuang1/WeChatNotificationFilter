package com.example.wechatnotificationfilter;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class MainActivity extends AppCompatActivity {
    private EditText contactInput;
    private ListView contactsList;
    private ArrayList<String> contacts;
    private ArrayAdapter<String> adapter;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                    android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1);
            }
        }

        // Check if notification access is enabled
        if (!isNotificationServiceEnabled()) {
            // Show dialog to enable notification access
            Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
            startActivity(intent);
        }

        // Initialize views
        contactInput = findViewById(R.id.contactNameInput);
        Button addButton = findViewById(R.id.addButton);
        Button testButton = findViewById(R.id.testButton);
        contactsList = findViewById(R.id.contactsList);

        // Initialize contacts list
        prefs = getSharedPreferences("PriorityContacts", MODE_PRIVATE);
        Set<String> savedContacts = prefs.getStringSet("contacts", new HashSet<>());
        contacts = new ArrayList<>(savedContacts);

        // Set up adapter
        adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, contacts);
        contactsList.setAdapter(adapter);

        // Add button click listener
        addButton.setOnClickListener(v -> {
            String name = contactInput.getText().toString().trim();
            if (!name.isEmpty()) {
                contacts.add(name);
                adapter.notifyDataSetChanged();
                saveContacts();
                contactInput.setText("");
            }
        });

        // Test button click listener
        testButton.setOnClickListener(v -> {
            if (!contacts.isEmpty()) {
                // Simulate a notification from the first contact in the list
                String testContact = contacts.get(0);
                sendTestNotification(testContact);
            } else {
                Toast.makeText(this, "Add a contact first", Toast.LENGTH_SHORT).show();
            }
        });

        // Long press to remove contact
        contactsList.setOnItemLongClickListener((parent, view, position, id) -> {
            contacts.remove(position);
            adapter.notifyDataSetChanged();
            saveContacts();
            Toast.makeText(MainActivity.this, "Contact removed", Toast.LENGTH_SHORT).show();
            return true;
        });
    }

    private void sendTestNotification(String contactName) {
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "test_channel",
                    "Test Notifications",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            notificationManager.createNotificationChannel(channel);
        }

        Notification.Builder builder;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, "test_channel");
        } else {
            builder = new Notification.Builder(this);
        }

        builder.setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(contactName)
                .setContentText("Test message from " + contactName)
                .setAutoCancel(true);

        notificationManager.notify(1, builder.build());
        Toast.makeText(this, "Test notification sent", Toast.LENGTH_SHORT).show();
    }

    private boolean isNotificationServiceEnabled() {
        String pkgName = getPackageName();
        String flat = Settings.Secure.getString(getContentResolver(),
                "enabled_notification_listeners");
        if (flat != null && !flat.isEmpty()) {
            return flat.contains(pkgName);
        }
        return false;
    }

    private void saveContacts() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putStringSet("contacts", new HashSet<>(contacts));
        editor.apply();
    }
}