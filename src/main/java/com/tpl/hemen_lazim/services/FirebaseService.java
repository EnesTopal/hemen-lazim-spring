package com.tpl.hemen_lazim.services;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;

@Service
public class FirebaseService {

    private static final Logger log = LoggerFactory.getLogger(FirebaseService.class);

    @Value("${firebase.service-account-file:#{null}}")
    private String serviceAccountFile;

    @PostConstruct
    public void initialize() {
        try {
            if (serviceAccountFile != null && !serviceAccountFile.isEmpty()) {
                FileInputStream serviceAccount = new FileInputStream(serviceAccountFile);

                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                        .build();

                if (FirebaseApp.getApps().isEmpty()) {
                    FirebaseApp.initializeApp(options);
                    log.info("Firebase Admin SDK initialized successfully");
                }
            } else {
                log.warn("Firebase service account file not configured. FCM notifications will not work.");
                log.warn("Set 'firebase.service-account-file' property in application.properties");
            }
        } catch (IOException e) {
            log.error("Failed to initialize Firebase Admin SDK", e);
        }
    }

    /**
     * Send notification to a specific device using FCM token
     */
    public boolean sendNotification(String fcmToken, String title, String body, Map<String, String> data) {
        if (FirebaseApp.getApps().isEmpty()) {
            log.warn("Firebase not initialized. Cannot send notification.");
            return false;
        }

        try {
            Message.Builder messageBuilder = Message.builder()
                    .setToken(fcmToken)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build());

            if (data != null && !data.isEmpty()) {
                messageBuilder.putAllData(data);
            }

            Message message = messageBuilder.build();
            String response = FirebaseMessaging.getInstance().send(message);
            log.info("Successfully sent message: {}", response);
            return true;
        } catch (com.google.firebase.messaging.FirebaseMessagingException e) {
            // Check if token is unregistered/invalid
            if (e.getMessagingErrorCode() == com.google.firebase.messaging.MessagingErrorCode.UNREGISTERED) {
                log.warn("FCM token is unregistered/invalid: {}", fcmToken);
                return false;
            }
            log.error("Failed to send notification to token: {}", fcmToken, e);
            return false;
        } catch (Exception e) {
            log.error("Failed to send notification to token: {}", fcmToken, e);
            return false;
        }
    }

    /**
     * Send notification without data payload
     */
    public boolean sendNotification(String fcmToken, String title, String body) {
        return sendNotification(fcmToken, title, body, null);
    }
}

