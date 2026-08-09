package com.tpl.hemen_lazim.configuration;

import com.tpl.hemen_lazim.security.JwtGenerate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.security.Principal;
import java.util.UUID;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebSocketConfig.class);

    private final JwtGenerate jwtGenerate;

    public WebSocketConfig(JwtGenerate jwtGenerate) {
        this.jwtGenerate = jwtGenerate;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/user", "/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // SockJS YOK: Android istemcisi (StompProtocolAndroid) sadece ham WebSocket
        // konuşuyor, SockJS negotiation'ı desteklemiyor. .withSockJS() ile birlikte
        // istemcinin bağlandığı "/ws" ham WebSocket upgrade'i kabul etmiyordu (SockJS
        // bunun için "/ws/websocket" alt path'ini bekler), bu yüzden STOMP CONNECT hiç
        // gerçekleşmiyor, dolayısıyla canlı push de asla çalışmıyordu.
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String authHeader = accessor.getFirstNativeHeader("Authorization");
                    String token = null;
                    if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
                        token = authHeader.substring(7);
                    }

                    UUID userId = (StringUtils.hasText(token) && jwtGenerate.validateToken(token))
                            ? jwtGenerate.getUserIdFromToken(token)
                            : null;

                    // /ws HTTP seviyesinde permitAll olduğu için tek kimlik kontrolü burası:
                    // geçerli token yoksa bağlantı kurulmadan reddedilir.
                    if (userId == null) {
                        log.warn("STOMP CONNECT reddedildi: geçerli JWT yok");
                        throw new MessagingException("Unauthorized STOMP connection");
                    }

                    // Principal adı UUID string olmalı: convertAndSendToUser(recipientId, ...)
                    // her yerde (Android tarafı dahil) kullanıcıları UUID string ile hedefliyor,
                    // username ile değil.
                    String userIdStr = userId.toString();
                    accessor.setUser((Principal) () -> userIdStr);
                    log.info("STOMP CONNECT kabul edildi, principal={}", userIdStr);
                }
                return message;
            }
        });
    }
}
