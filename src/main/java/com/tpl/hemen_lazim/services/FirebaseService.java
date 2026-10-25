package com.tpl.hemen_lazim.services;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
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
     * Bir push denemesinin sonucu. Cihaz yalnızca INVALID_TOKEN'da kapatılmalı: FAILED ağ, kota
     * ya da Firebase'in hiç yapılandırılmamış olması gibi geçici bir durum, token sağlam.
     */
    public enum SendResult { SENT, INVALID_TOKEN, FAILED }

    /**
     * Telefonda uygulamanın kendi dilinde oluşan bildirim: başlık ve metin, uygulamanın
     * strings.xml'indeki anahtarlar; argümanlar metindeki %1$s, %2$s... yerine geçer.
     * Anahtar adları uygulamayla sözleşmedir.
     */
    public record LocalizedNotification(String titleKey, List<String> titleArgs,
                                        String bodyKey, List<String> bodyArgs) {
    }

    /**
     * Send notification to a specific device using FCM token
     */
    public SendResult sendNotification(String fcmToken, LocalizedNotification notification, Map<String, String> data) {
        if (FirebaseApp.getApps().isEmpty()) {
            log.warn("Firebase not initialized. Cannot send notification.");
            return SendResult.FAILED;
        }

        try {
            Message.Builder messageBuilder = Message.builder()
                    .setToken(fcmToken)
                    // Düz başlık ve metin bilerek yok: FCM onları yerelleştirme anahtarından önce
                    // kullanır ve bildirim telefonun değil sunucunun dilinde kalırdı
                    .setAndroidConfig(AndroidConfig.builder()
                            .setNotification(AndroidNotification.builder()
                                    .setTitleLocalizationKey(notification.titleKey())
                                    .addAllTitleLocalizationArgs(notification.titleArgs())
                                    .setBodyLocalizationKey(notification.bodyKey())
                                    .addAllBodyLocalizationArgs(notification.bodyArgs())
                                    .build())
                            .build());

            if (data != null && !data.isEmpty()) {
                messageBuilder.putAllData(data);
            }

            Message message = messageBuilder.build();
            String response = FirebaseMessaging.getInstance().send(message);
            log.info("Successfully sent message: {}", response);
            return SendResult.SENT;
        } catch (FirebaseMessagingException e) {
            // Token artık bu uygulamaya ait değil: silinmiş ya da başka bir Firebase projesine ait
            MessagingErrorCode code = e.getMessagingErrorCode();
            if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.SENDER_ID_MISMATCH) {
                log.warn("FCM token is no longer valid: {}", code);
                return SendResult.INVALID_TOKEN;
            }
            log.error("Failed to send notification", e);
            return SendResult.FAILED;
        } catch (Exception e) {
            log.error("Failed to send notification", e);
            return SendResult.FAILED;
        }
    }
}

