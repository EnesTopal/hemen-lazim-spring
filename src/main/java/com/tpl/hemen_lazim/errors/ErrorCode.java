package com.tpl.hemen_lazim.errors;

import org.springframework.http.HttpStatus;

/**
 * Hata yanıtlarındaki sabit kodlar ({@code ApiError.code}). Uygulama kullanıcıya göstereceği
 * metni bu koddan, telefonun dilinde seçiyor; {@code message} yalnızca geliştirici ve log için
 * İngilizce. Kod adları uygulamayla sözleşmedir: değiştirilmez, gerekirse yenisi eklenir.
 */
public enum ErrorCode {

    // Genel
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Validation failed"),

    // Talepler
    REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "Request not found"),
    REQUEST_NOT_OPEN(HttpStatus.CONFLICT, "This request is no longer open"),
    NOT_REQUEST_OWNER(HttpStatus.FORBIDDEN, "Only the owner or an admin can modify this request"),
    OFFER_ON_OWN_REQUEST(HttpStatus.BAD_REQUEST, "You cannot make an offer on your own request"),

    // Kullanıcılar ve sohbet
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "User not found"),
    INVALID_USER_ID(HttpStatus.BAD_REQUEST, "Invalid user id"),
    CHAT_WITH_SELF(HttpStatus.BAD_REQUEST, "You cannot chat with yourself"),
    NOT_CHAT_MEMBER(HttpStatus.FORBIDDEN, "You are not a member of this chat"),
    MESSAGE_EMPTY(HttpStatus.BAD_REQUEST, "Message must not be empty"),
    MESSAGE_TOO_LONG(HttpStatus.BAD_REQUEST, "Message is too long");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }
}
