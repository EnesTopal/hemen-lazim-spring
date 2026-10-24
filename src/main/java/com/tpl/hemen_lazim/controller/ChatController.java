package com.tpl.hemen_lazim.controller;

import com.tpl.hemen_lazim.model.DTOs.ChatMessageRequest;
import com.tpl.hemen_lazim.model.Message;
import com.tpl.hemen_lazim.security.SecurityUtils;
import com.tpl.hemen_lazim.services.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Kimlik her zaman oturumdan alınır. Uygulamanın hâlâ gönderdiği senderId ve userId
 * parametreleri artık okunmuyor; bilinmeyen parametreler Spring tarafından yok sayılır.
 */
@RestController
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /**
     * Asıl güvenilir gönderim yolu: normal HTTP request/response, Retrofit'in
     * kendi hata/retry yönetimiyle çalışır. Socket bağlı değilken de mesaj kaybolmaz.
     * Gövdeden yalnızca alıcı ve içerik okunur.
     */
    @PostMapping("/api/chat/messages")
    public ResponseEntity<Message> sendMessageRest(@RequestBody ChatMessageRequest body) {
        Message saved = chatService.send(SecurityUtils.currentUserId(), body.recipientId(), body.content());
        return ResponseEntity.ok(saved);
    }

    /**
     * Soket üzerinden gönderim. Uygulama şu an kullanmıyor; açık olduğu için REST ile aynı
     * kurallardan geçer ve göndericiyi CONNECT'te doğrulanan Principal'dan alır.
     */
    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Payload ChatMessageRequest body, Principal principal) {
        chatService.send(UUID.fromString(principal.getName()), body.recipientId(), body.content());
    }

    @PostMapping("/api/chat/room")
    public Map<String, String> createRoom(@RequestParam String recipientId) {
        return Collections.singletonMap("roomId", chatService.roomWith(SecurityUtils.currentUserId(), recipientId));
    }

    @GetMapping("/api/chat/sync")
    public List<Message> syncMessages(@RequestParam String roomId, @RequestParam Long lastReceivedMessageId) {
        return chatService.sync(SecurityUtils.currentUserId(), roomId, lastReceivedMessageId);
    }

    /**
     * Oda bazında okunmamış mesaj sayıları. Hem sohbet listesindeki kırmızı nokta hem de
     * alt bardaki rozet bunu kullanır; okunma durumu sunucuda tutulduğu için uygulama
     * kapalıyken gelen mesajlar da açılışta doğru görünür.
     */
    @GetMapping("/api/chat/unread")
    public Map<String, Long> getUnreadCounts() {
        return chatService.unreadCounts(SecurityUtils.currentUserId());
    }

    /** Kullanıcı odayı açtığında/okuduğunda çağrılır. */
    @PostMapping("/api/chat/read")
    public ResponseEntity<Void> markRoomRead(@RequestParam String roomId) {
        chatService.markRoomRead(SecurityUtils.currentUserId(), roomId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/chat/history")
    public List<Message> getHistory(
            @RequestParam String roomId,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(defaultValue = "30") int limit) {
        return chatService.history(SecurityUtils.currentUserId(), roomId, beforeId, limit);
    }

    @GetMapping("/api/chat/rooms")
    public List<Message> getUserChats() {
        return chatService.latestPerRoom(SecurityUtils.currentUserId());
    }
}
