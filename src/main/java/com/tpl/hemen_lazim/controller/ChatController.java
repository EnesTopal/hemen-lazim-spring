package com.tpl.hemen_lazim.controller;

import com.tpl.hemen_lazim.model.DTOs.RoomUnread;
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
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private static final int MAX_SYNC_BATCH = 200;

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

        // Id her zaman sunucuda üretilir; client'ın (varsayılan 0 dahil) gönderdiği
        // id'ye güvenilirse Hibernate save()'i update sanıp var olmayan satırı
        // arar ve StaleObjectStateException fırlatır.
        chatMessage.setId(null);
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
        // Üst sınır: istemci lastReceivedMessageId=0 ile gelirse (ör. geçmiş yüklemesi
        // başarısız olmuşsa) tüm odayı tek seferde çekmesin.
        List<Message> messages = messageRepository.findByRoomIdAndIdGreaterThanOrderByIdAsc(roomId,
                lastReceivedMessageId, PageRequest.of(0, MAX_SYNC_BATCH));
        return messages;
    }

    /**
     * Oda bazında okunmamış mesaj sayıları. Hem sohbet listesindeki kırmızı nokta hem de
     * alt bardaki rozet bunu kullanır; okunma durumu sunucuda tutulduğu için uygulama
     * kapalıyken gelen mesajlar da açılışta doğru görünür.
     */
    @GetMapping("/api/chat/unread")
    public Map<String, Long> getUnreadCounts() {
        String me = SecurityUtils.currentUserId().toString();
        Map<String, Long> counts = messageRepository.countUnreadByRoom(me).stream()
                .collect(Collectors.toMap(RoomUnread::getRoomId, RoomUnread::getUnreadCount));
        log.info("Unread counts for {}: {}", me, counts);
        return counts;
    }

    /** Kullanıcı odayı açtığında/okuduğunda çağrılır. */
    @PostMapping("/api/chat/read")
    public ResponseEntity<Void> markRoomRead(@RequestParam String roomId) {
        String me = SecurityUtils.currentUserId().toString();
        int updated = messageRepository.markRoomRead(roomId, me);
        log.info("Marked {} messages read in room {} for {}", updated, roomId, me);
        return ResponseEntity.noContent().build();
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
