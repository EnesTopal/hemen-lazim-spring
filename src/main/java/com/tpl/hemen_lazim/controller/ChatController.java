package com.tpl.hemen_lazim.controller;

import com.tpl.hemen_lazim.model.Message;
import com.tpl.hemen_lazim.repository.MessageRepository;
import com.tpl.hemen_lazim.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final MessageRepository messageRepository;

    /**
     * Asıl güvenilir gönderim yolu: normal HTTP request/response, Retrofit'in
     * kendi hata/retry yönetimiyle çalışır. Socket bağlı değilken de mesaj kaybolmaz.
     * senderId, request body'sinden değil kimlik doğrulamadan alınır (spoofing önlenir).
     */
    @PostMapping("/api/chat/messages")
    public ResponseEntity<Message> sendMessageRest(@RequestBody Message chatMessage) {
        chatMessage.setSenderId(SecurityUtils.currentUserId().toString());
        Message saved = persistAndForward(chatMessage);
        return ResponseEntity.ok(saved);
    }

    /**
     * Canlı teslimat yolu: karşı taraf o an bağlıysa mesaj anında düşer.
     * Bağlantı kopukken gönderilen mesajlar burada kaybolabilir; istemci
     * yeniden bağlandığında /api/chat/sync ile telafi eder.
     */
    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Payload Message chatMessage) {
        persistAndForward(chatMessage);
    }

    private Message persistAndForward(Message chatMessage) {
        log.info("Persisting message from {} to {} in room {}", chatMessage.getSenderId(), chatMessage.getRecipientId(),
                chatMessage.getRoomId());

        chatMessage.setServerTimestamp(Instant.now().toEpochMilli());
        Message savedMessage = messageRepository.save(chatMessage);
        log.info("Saved message with ID: {}", savedMessage.getId());

        messagingTemplate.convertAndSendToUser(
                chatMessage.getRecipientId(),
                "/queue/messages",
                savedMessage);
        log.info("Forwarded message to user {}", chatMessage.getRecipientId());
        return savedMessage;
    }

    @PostMapping("/api/chat/room")
    public Map<String, String> createRoom(@RequestParam String senderId, @RequestParam String recipientId) {
        String roomId;
        // Deterministic Room ID for 1-to-1
        if (senderId.compareTo(recipientId) < 0) {
            roomId = senderId + "_" + recipientId;
        } else {
            roomId = recipientId + "_" + senderId;
        }
        log.info("Created/Retrieved room ID: {} for users {} and {}", roomId, senderId, recipientId);
        return Collections.singletonMap("roomId", roomId);
    }

    @GetMapping("/api/chat/sync")
    public List<Message> syncMessages(@RequestParam String roomId, @RequestParam Long lastReceivedMessageId) {
        log.info("Syncing messages for room {} since ID {}", roomId, lastReceivedMessageId);
        List<Message> messages = messageRepository.findByRoomIdAndIdGreaterThanOrderByIdAsc(roomId,
                lastReceivedMessageId);
        log.info("Found {} messages", messages.size());
        return messages;
    }

    /**
     * Sohbet geçmişini sayfalı çeker: beforeId verilmezse en yeni "limit" mesajı,
     * verilirse o id'den daha eski "limit" mesajı döner. Yanıt her zaman eskiden
     * yeniye (ascending) sıralıdır.
     */
    @GetMapping("/api/chat/history")
    public List<Message> getHistory(
            @RequestParam String roomId,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(defaultValue = "30") int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        Pageable pageable = PageRequest.of(0, safeLimit);

        List<Message> page = (beforeId == null)
                ? messageRepository.findByRoomIdOrderByIdDesc(roomId, pageable)
                : messageRepository.findByRoomIdAndIdLessThanOrderByIdDesc(roomId, beforeId, pageable);

        Collections.reverse(page);
        log.info("History for room {} (beforeId={}, limit={}) -> {} messages", roomId, beforeId, safeLimit, page.size());
        return page;
    }

    @GetMapping("/api/chat/rooms")
    public List<Message> getUserChats(@RequestParam String userId) {
        log.info("Fetching chats for user {}", userId);
        List<Message> chats = messageRepository.findLatestMessagesForUser(userId);
        log.info("Found {} chats", chats.size());
        return chats;
    }
}
