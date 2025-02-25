package com.example.wechatnotificationfilter;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import org.json.JSONException;
import org.json.JSONObject;
import android.util.Log;

public class MainActivity extends AppCompatActivity {
    private EditText contactInput;
    private ListView contactsList;
    private ArrayList<String> contacts;
    private ArrayAdapter<String> adapter;
    private SharedPreferences prefs;
    private JSONObject contactSounds;
    private static final String TAG = "WeChatFilter";

    private static final int PICK_RINGTONE_REQUEST = 1;
    private String currentContact;

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

        Log.d(TAG, "Loaded contacts list on app start: " + contacts.toString());

        // Load contact sounds
        loadContactSounds();

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
                Log.d(TAG, "Added contact to priority list: " + name);
                Log.d(TAG, "Updated contacts list: " + contacts.toString());
                Toast.makeText(this, "Added: " + name, Toast.LENGTH_SHORT).show();

                // Restart the service to ensure it picks up the new contact
                restartNotificationService();
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
            String removedContact = contacts.get(position);
            contacts.remove(position);
            adapter.notifyDataSetChanged();
            saveContacts();
            Log.d(TAG, "Removed contact from priority list: " + removedContact);
            Log.d(TAG, "Updated contacts list: " + contacts.toString());
            Toast.makeText(MainActivity.this, "Removed: " + removedContact, Toast.LENGTH_SHORT).show();

            // Restart the service to ensure it picks up the removed contact
            restartNotificationService();
            return true;
        });

        // Click to set custom sound
        contactsList.setOnItemClickListener((parent, view, position, id) -> {
            currentContact = contacts.get(position);
            Toast.makeText(MainActivity.this, "Setting sound for: " + currentContact, Toast.LENGTH_SHORT).show();

            Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Sound for " + currentContact);

            try {
                if (contactSounds.has(currentContact)) {
                    String soundUri = contactSounds.getString(currentContact);
                    if (!soundUri.isEmpty()) {
                        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(soundUri));
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }

            startActivityForResult(intent, PICK_RINGTONE_REQUEST);
        });
    }

    private void restartNotificationService() {
        Log.d(TAG, "Restarting notification service to apply changes");
        // Force reload the service to pick up changes
        Intent serviceIntent = new Intent(this, WeChatNotificationService.class);
        stopService(serviceIntent);
        startService(serviceIntent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_RINGTONE_REQUEST) {
            if (resultCode == Activity.RESULT_OK) {
                Uri ringtoneUri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
                if (ringtoneUri != null) {
                    // Log the selected URI
                    String uriString = ringtoneUri.toString();
                    Log.d(TAG, "Selected sound URI: " + uriString + " for contact: " + currentContact);

                    // Save the selected sound for this contact
                    try {
                        contactSounds.put(currentContact, uriString);
                        saveContactSounds();
                        WeChatNotificationService.saveContactSound(this, currentContact, uriString);
                        Toast.makeText(this, "Sound set for " + currentContact, Toast.LENGTH_SHORT).show();

                        // Restart the service to pick up the new sound
                        restartNotificationService();
                    } catch (JSONException e) {
                        e.printStackTrace();
                        Toast.makeText(this, "Error saving sound", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Log.d(TAG, "No ringtone selected (null URI)");
                    Toast.makeText(this, "No sound selected", Toast.LENGTH_SHORT).show();
                }
            } else {
                Log.d(TAG, "Sound selection canceled");
                Toast.makeText(this, "Sound selection canceled", Toast.LENGTH_SHORT).show();
            }
        }
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
        Log.d(TAG, "Test notification sent for contact: " + contactName);
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
        Log.d(TAG, "Saved contacts list to shared preferences: " + contacts.toString());
    }

    private void loadContactSounds() {
        SharedPreferences soundPrefs = getSharedPreferences("ContactSounds", MODE_PRIVATE);
        String soundsJson = soundPrefs.getString("soundsMap", "{}");

        try {
            contactSounds = new JSONObject(soundsJson);
            Log.d(TAG, "Loaded contact sounds: " + contactSounds.toString());
        } catch (JSONException e) {
            contactSounds = new JSONObject();
            Log.e(TAG, "Error loading contact sounds", e);
        }
    }

    private void saveContactSounds() {
        SharedPreferences soundPrefs = getSharedPreferences("ContactSounds", MODE_PRIVATE);
        SharedPreferences.Editor editor = soundPrefs.edit();
        editor.putString("soundsMap", contactSounds.toString());
        editor.apply();

        // Log the saved sounds for debugging
        Log.d(TAG, "Saved contact sounds: " + contactSounds.toString());
    }
}