package com.example.wechatnotificationfilter;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import java.util.HashSet;
import java.util.Set;

public class WeChatNotificationService extends NotificationListenerService {
    private Set<String> priorityContacts = new HashSet<>();
    private static final String WECHAT_PACKAGE = "com.tencent.mm";
    private static final String TAG = "WeChatFilter";

    @Override
    public void onCreate() {
        super.onCreate();
        loadPriorityContacts();
        Log.d(TAG, "Loaded priority contacts: " + priorityContacts.toString());
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
        
        // Try to get the real sender name from the text
        String senderName = "";
        if (text != null) {
            // Pattern 1: Message count format "[数条]姓名: 消息"
            if (text.contains("].:")) {
                int startIndex = text.indexOf("].") + 2;
                int endIndex = text.indexOf(":", startIndex);
                if (endIndex > startIndex) {
                    senderName = text.substring(startIndex, endIndex).trim();
                    Log.d(TAG, "Extracted sender name (pattern 1): " + senderName);
                }
            } 
            // Pattern 2: Direct message format "姓名: 消息"
            else if (text.contains(":")) {
                senderName = text.substring(0, text.indexOf(":")).trim();
                Log.d(TAG, "Extracted sender name (pattern 2): " + senderName);
            }
        }
        
        // Check if this is a priority contact
        boolean isPriority = false;
        
        // Check title against priority list
        if (title != null && !title.isEmpty() && isPriorityContact(title)) {
            isPriority = true;
            Log.d(TAG, "Title matches priority contact: " + title);
        }
        
        // Check extracted name against priority list
        if (!isPriority && !senderName.isEmpty() && isPriorityContact(senderName)) {
            isPriority = true;
            Log.d(TAG, "Extracted name matches priority contact: " + senderName);
        }
        
        Log.d(TAG, "Is priority contact? " + isPriority);

        // If it's a priority contact, create a custom notification with sound
        if (isPriority) {
            Log.d(TAG, "Priority contact notification - creating notification with sound");
            createSoundNotification(title, text);
            return;
        }

        // For non-priority contacts, just let the system handle it normally
        // In silent mode, they'll be silent
        Log.d(TAG, "Non-priority contact - no special handling");
    }

    private void createSoundNotification(String title, String text) {
        NotificationManager notificationManager = 
            (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                "priority_channel",
                "Priority Contacts",
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), null);
            notificationManager.createNotificationChannel(channel);
        }

        Notification.Builder builder;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, "priority_channel");
        } else {
            builder = new Notification.Builder(this)
                .setPriority(Notification.PRIORITY_HIGH)
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
        }

        builder.setSmallIcon(R.drawable.ic_notification)
               .setContentTitle(title)
               .setContentText(text)
               .setAutoCancel(true);

        notificationManager.notify((int) System.currentTimeMillis(), builder.build());
    }

    private boolean isPriorityContact(String contactName) {
        // Reload contacts each time to ensure we have the latest list
        loadPriorityContacts();
        return priorityContacts.contains(contactName);
    }

    private void loadPriorityContacts() {
        SharedPreferences prefs = getSharedPreferences("PriorityContacts", MODE_PRIVATE);
        priorityContacts = new HashSet<>(prefs.getStringSet("contacts", new HashSet<>()));
    }
}