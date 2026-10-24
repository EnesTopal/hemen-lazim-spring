package com.tpl.hemen_lazim.configuration;

import com.tpl.hemen_lazim.security.JwtGenerate;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtGenerate jwtGenerate;

    public WebSocketConfig(JwtGenerate jwtGenerate) {
        this.jwtGenerate = jwtGenerate;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // "/user" bilerek listede değil: kullanıcıya özel hedefler
        // UserDestinationMessageHandler tarafından "/queue/messages-user{session}"
        // biçimine çevrilip broker'a öyle veriliyor, yani "/queue" yeterli.
        config.enableSimpleBroker("/topic", "/queue");
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
        registration.interceptors(new StompInboundInterceptor(jwtGenerate));
    }
}
