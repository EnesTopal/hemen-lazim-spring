package com.tpl.hemen_lazim.configuration;

import com.tpl.hemen_lazim.security.JwtGenerate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;

import java.security.Principal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StompInboundInterceptorTest {

    private static final UUID ME = UUID.randomUUID();
    private static final Principal USER = ME::toString;

    @Mock
    private JwtGenerate jwtGenerate;

    @Mock
    private MessageChannel channel;

    @Test
    void connectWithValidTokenSetsUserIdAsPrincipal() {
        when(jwtGenerate.validateToken("good")).thenReturn(true);
        when(jwtGenerate.getUserIdFromToken("good")).thenReturn(ME);

        Message<?> result = interceptor().preSend(connect(StompCommand.CONNECT, "Bearer good"), channel);

        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(result, StompHeaderAccessor.class);
        assertEquals(ME.toString(), accessor.getUser().getName());
    }

    @Test
    void connectWithoutValidTokenIsRejected() {
        when(jwtGenerate.validateToken("bad")).thenReturn(false);

        assertRejected(connect(StompCommand.CONNECT, "Bearer bad"));
        assertRejected(connect(StompCommand.CONNECT, null));
    }

    @Test
    void stompCommandCannotSkipAuthentication() {
        // STOMP, CONNECT ile aynı işi gören bağlanma komutu; eskiden denetimsiz geçiyordu
        assertRejected(connect(StompCommand.STOMP, null));
    }

    @Test
    void ownQueueSubscriptionIsAllowed() {
        assertAllowed(frame(StompCommand.SUBSCRIBE, StompInboundInterceptor.USER_QUEUE, USER));
    }

    @Test
    void otherSubscriptionsAreRejected() {
        assertRejected(frame(StompCommand.SUBSCRIBE, "/queue/messages-usersomeone", USER));
        assertRejected(frame(StompCommand.SUBSCRIBE, "/topic/anything", USER));
        assertRejected(frame(StompCommand.SUBSCRIBE, StompInboundInterceptor.USER_QUEUE, null));
    }

    @Test
    void sendGoesOnlyToApplicationDestinations() {
        assertAllowed(frame(StompCommand.SEND, "/app/chat.sendMessage", USER));

        // Doğrudan broker'a giden hedefler: mesaj kaydedilmeden karşı tarafa düşerdi
        assertRejected(frame(StompCommand.SEND, "/user/" + UUID.randomUUID() + "/queue/messages", USER));
        assertRejected(frame(StompCommand.SEND, "/queue/messages-usersomeone", USER));
        assertRejected(frame(StompCommand.SEND, "/topic/anything", USER));
        assertRejected(frame(StompCommand.SEND, "/app/chat.sendMessage", null));
    }

    private StompInboundInterceptor interceptor() {
        return new StompInboundInterceptor(jwtGenerate);
    }

    private void assertAllowed(Message<?> message) {
        assertDoesNotThrow(() -> interceptor().preSend(message, channel));
    }

    private void assertRejected(Message<?> message) {
        assertThrows(MessagingException.class, () -> interceptor().preSend(message, channel));
    }

    private static Message<byte[]> connect(StompCommand command, String authorization) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (authorization != null) {
            accessor.setNativeHeader("Authorization", authorization);
        }
        return build(accessor);
    }

    private static Message<byte[]> frame(StompCommand command, String destination, Principal user) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setDestination(destination);
        accessor.setUser(user);
        return build(accessor);
    }

    // Gerçek oturumdaki gibi değiştirilebilir başlıklar: CONNECT'te setUser çalışabilsin
    private static Message<byte[]> build(StompHeaderAccessor accessor) {
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
