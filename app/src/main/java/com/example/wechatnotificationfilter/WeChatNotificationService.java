package com.example.wechatnotificationfilter;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import androidx.annotation.RequiresApi;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.HashSet;
import java.util.Set;
import java.util.List;

public class WeChatNotificationService extends NotificationListenerService {
    private Set<String> priorityContacts = new HashSet<>();
    private static final String WECHAT_PACKAGE = "com.tencent.mm";
    private static final String TAG = "WeChatFilter";

    @Override
    public void onCreate() {
        super.onCreate();
        loadPriorityContacts();
        Log.d(TAG, "Service started with priority contacts: " + priorityContacts.toString());
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        Log.d(TAG, "Notification received from package: " + sbn.getPackageName());

        // First check if it's from WeChat
        if (!WECHAT_PACKAGE.equals(sbn.getPackageName())) {
            Log.d(TAG, "Not a WeChat notification, ignoring");
            return;
        }

        Notification notification = sbn.getNotification();
        Bundle extras = notification.extras;

        // Log extras to see what's available
        for (String key : extras.keySet()) {
            Log.d(TAG, "Notification Extra - Key: " + key + ", Value: " + extras.get(key));
        }

        String title = extras.getString(Notification.EXTRA_TITLE);
        String text = extras.getString(Notification.EXTRA_TEXT);

        Log.d(TAG, "WeChat notification - Title: " + title);
        Log.d(TAG, "WeChat notification - Text: " + text);

        // Extract sender name
        String senderName = extractSenderName(text);
        Log.d(TAG, "Extracted sender name: " + senderName);

        // Check if this is a priority contact
        boolean titleMatch = title != null && isPriorityContact(title);
        boolean senderMatch = !senderName.isEmpty() && isPriorityContact(senderName);
        boolean isPriority = titleMatch || senderMatch;

        if (titleMatch) {
            Log.d(TAG, "Title matches priority contact: " + title);
        }

        if (senderMatch) {
            Log.d(TAG, "Sender matches priority contact: " + senderName);
        }

        Log.d(TAG, "Is priority contact? " + isPriority);

        // Important: Cancel the original notification to avoid double notification
        cancelNotification(sbn.getKey());
        Log.d(TAG, "Original WeChat notification cancelled");

        // Create custom notification based on priority
        if (isPriority) {
            // Get the matched contact name
            String contactName = titleMatch ? title : senderName;

            // Get custom sound for this contact
            String soundUri = getContactSoundUri(contactName);
            if (soundUri != null && !soundUri.isEmpty()) {
                Log.d(TAG, "Priority contact - creating notification with sound: " + soundUri);
                createNotificationWithSound(title, text, contactName, soundUri);
            } else {
                Log.d(TAG, "Priority contact - creating notification with default sound");
                createNotificationWithSound(title, text, contactName, null);
            }
        } else {
            Log.d(TAG, "Non-priority contact - creating silent notification");
            createSilentNotification(title, text);
        }
    }

    private void createSilentNotification(String title, String text) {
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        String channelId = "silent_channel";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (notificationManager.getNotificationChannel(channelId) == null) {
                NotificationChannel channel = new NotificationChannel(
                        channelId,
                        "Silent WeChat Messages",
                        NotificationManager.IMPORTANCE_DEFAULT
                );
                channel.setSound(null, null);
                channel.enableVibration(false);
                notificationManager.createNotificationChannel(channel);
                Log.d(TAG, "Created silent notification channel");
            }
        }

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, channelId);
        } else {
            builder = new Notification.Builder(this)
                    .setPriority(Notification.PRIORITY_DEFAULT)
                    .setSound(null)
                    .setVibrate(null);
        }

        builder.setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true);

        int notificationId = (title != null) ? title.hashCode() : (int) System.currentTimeMillis();
        notificationManager.notify(notificationId, builder.build());
        Log.d(TAG, "Silent notification posted with ID: " + notificationId);
    }

    private void createNotificationWithSound(String title, String text, String contactName, String customSoundUri) {
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        Log.d(TAG, "Creating notification with sound for: " + contactName);

        // Use a consistent channel ID for each contact, plus a sound hash suffix
        String soundHash = customSoundUri != null ? String.valueOf(customSoundUri.hashCode()) : "default";
        String channelId = "priority_contact_" + (contactName != null ? contactName.hashCode() : "default") + "_" + soundHash;
        String channelName = "Priority: " + (contactName != null ? contactName : "Unknown");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Clean up old channels for this contact
            cleanupOldChannels(notificationManager, contactName);

            // Create new channel with current sound
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    channelName,
                    NotificationManager.IMPORTANCE_HIGH
            );

            // Set custom sound
            Uri soundUri;
            if (customSoundUri != null && !customSoundUri.isEmpty()) {
                Log.d(TAG, "Using custom sound URI: " + customSoundUri);
                soundUri = Uri.parse(customSoundUri);
            } else {
                Log.d(TAG, "Using default notification sound");
                soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            }

            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build();

            channel.setSound(soundUri, audioAttributes);
            channel.enableVibration(true);
            notificationManager.createNotificationChannel(channel);
            Log.d(TAG, "Created notification channel: " + channelId + " with name: " + channelName);
        }

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, channelId);
        } else {
            builder = new Notification.Builder(this)
                    .setPriority(Notification.PRIORITY_HIGH);

            // Set sound for pre-Oreo
            if (customSoundUri != null && !customSoundUri.isEmpty()) {
                builder.setSound(Uri.parse(customSoundUri));
            } else {
                builder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
            }
        }

        builder.setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true);

        int notificationId = (int) System.currentTimeMillis();
        notificationManager.notify(notificationId, builder.build());
        Log.d(TAG, "Notification posted with ID: " + notificationId);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void cleanupOldChannels(NotificationManager notificationManager, String contactName) {
        if (contactName == null) return;

        String contactIdPrefix = "priority_contact_" + contactName.hashCode();

        // Get all notification channels
        List<NotificationChannel> channels = notificationManager.getNotificationChannels();

        // Find and delete old channels for this contact
        for (NotificationChannel channel : channels) {
            String channelId = channel.getId();
            // If this is a channel for the same contact but not the current one
            if (channelId.startsWith(contactIdPrefix)) {
                notificationManager.deleteNotificationChannel(channelId);
                Log.d(TAG, "Deleted old channel: " + channelId);
            }
        }
    }
    private String extractSenderName(String text) {
        if (text == null) return "";

        // Pattern 1: Message count format "[数条].: 消息"
        if (text.contains("].:")) {
            int startIndex = text.indexOf("].") + 2;
            int endIndex = text.indexOf(":", startIndex);
            if (endIndex > startIndex) {
                return text.substring(startIndex, endIndex).trim();
            }
        }
        // Pattern 2: Direct message format "姓名: 消息"
        else if (text.contains(":")) {
            return text.substring(0, text.indexOf(":")).trim();
        }

        return "";
    }

    private boolean isPriorityContact(String contactName) {
        if (contactName == null || contactName.isEmpty()) {
            return false;
        }

        // Reload contacts each time to ensure we have the latest list
        loadPriorityContacts();
        boolean isInList = priorityContacts.contains(contactName);
        Log.d(TAG, "Checking if '" + contactName + "' is in priority list: " + isInList);
        return isInList;
    }

    private void loadPriorityContacts() {
        SharedPreferences prefs = getSharedPreferences("PriorityContacts", MODE_PRIVATE);
        Set<String> savedContacts = prefs.getStringSet("contacts", new HashSet<>());
        priorityContacts = new HashSet<>(savedContacts);
        Log.d(TAG, "Loaded priority contacts: " + priorityContacts.toString());
    }

    private String getContactSoundUri(String contactName) {
        if (contactName == null) return null;

        SharedPreferences soundPrefs = getSharedPreferences("ContactSounds", MODE_PRIVATE);
        try {
            String soundsJson = soundPrefs.getString("soundsMap", "{}");
            JSONObject contactSounds = new JSONObject(soundsJson);
            if (contactSounds.has(contactName)) {
                return contactSounds.getString(contactName);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error reading contact sound", e);
        }
        return null;
    }

    public static void saveContactSound(Context context, String contactName, String soundUri) {
        SharedPreferences soundPrefs = context.getSharedPreferences("ContactSounds", MODE_PRIVATE);
        try {
            String soundsJson = soundPrefs.getString("soundsMap", "{}");
            JSONObject contactSounds = new JSONObject(soundsJson);
            contactSounds.put(contactName, soundUri);

            SharedPreferences.Editor editor = soundPrefs.edit();
            editor.putString("soundsMap", contactSounds.toString());
            editor.apply();

            Log.d("WeChatFilter", "Saved sound for " + contactName + ": " + soundUri);
        } catch (JSONException e) {
            Log.e("WeChatFilter", "Error saving contact sound", e);
        }
    }
}