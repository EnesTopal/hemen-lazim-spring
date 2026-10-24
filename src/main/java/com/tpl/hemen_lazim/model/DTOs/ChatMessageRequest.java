package com.tpl.hemen_lazim.model.DTOs;

/**
 * Mesaj gönderme isteği. Uygulama Message nesnesinin tamamını gönderiyor; oda, gönderici ve
 * okundu bilgisi bilerek burada yok, sunucu bunları kendisi belirler.
 */
public record ChatMessageRequest(String recipientId, String content) {
}
