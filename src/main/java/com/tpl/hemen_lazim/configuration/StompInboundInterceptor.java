package com.tpl.hemen_lazim.configuration;

import com.tpl.hemen_lazim.security.JwtGenerate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.util.StringUtils;

import java.security.Principal;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * İstemciden gelen STOMP çerçevelerinin kapısı. /ws HTTP seviyesinde permitAll olduğu için
 * kimlik ve yetki denetiminin tamamı burada:
 * - CONNECT ve STOMP (aynı işi gören diğer bağlanma komutu) geçerli JWT ister.
 * - SUBSCRIBE yalnızca kişinin kendi kuyruğuna yapılabilir.
 * - SEND yalnızca uygulama hedeflerine (/app/...) gidebilir. /user, /queue ve /topic
 *   hedeflerine doğrudan gönderilen çerçeve broker'a gidip kaydedilmeden başka bir
 *   kullanıcının ekranına düşerdi.
 */
public class StompInboundInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(StompInboundInterceptor.class);

    /** Uygulamanın abone olduğu tek hedef (WebSocketManager.USER_QUEUE). */
    static final String USER_QUEUE = "/user/queue/messages";
    private static final String APP_PREFIX = "/app/";

    private final JwtGenerate jwtGenerate;

    public StompInboundInterceptor(JwtGenerate jwtGenerate) {
        this.jwtGenerate = jwtGenerate;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message; // kalp atışı
        }

        switch (accessor.getCommand()) {
            case CONNECT, STOMP -> authenticate(accessor);
            case SUBSCRIBE -> requireUserAndDestination(accessor, USER_QUEUE::equals);
            case SEND -> requireUserAndDestination(accessor, destination -> destination.startsWith(APP_PREFIX));
            default -> {
                // UNSUBSCRIBE, DISCONNECT, ACK... kimseye bir şey iletmiyor
            }
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        String token = null;
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }

        UUID userId = (StringUtils.hasText(token) && jwtGenerate.validateToken(token))
                ? jwtGenerate.getUserIdFromToken(token)
                : null;

        if (userId == null) {
            log.warn("STOMP {} reddedildi: geçerli JWT yok", accessor.getCommand());
            throw new MessagingException("Unauthorized STOMP connection");
        }

        // Principal adı UUID string olmalı: convertAndSendToUser(recipientId, ...)
        // her yerde (Android tarafı dahil) kullanıcıları UUID string ile hedefliyor,
        // username ile değil.
        String userIdStr = userId.toString();
        accessor.setUser((Principal) () -> userIdStr);
        log.info("STOMP CONNECT kabul edildi, principal={}", userIdStr);
    }

    private static void requireUserAndDestination(StompHeaderAccessor accessor, Predicate<String> allowed) {
        Principal user = accessor.getUser();
        String destination = accessor.getDestination();
        if (user == null || destination == null || !allowed.test(destination)) {
            log.warn("STOMP {} reddedildi: hedef={}, kullanıcı={}", accessor.getCommand(), destination,
                    user != null ? user.getName() : null);
            throw new MessagingException("Forbidden STOMP " + accessor.getCommand());
        }
    }
}
