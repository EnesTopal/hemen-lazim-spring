package com.tpl.hemen_lazim.services;

import com.tpl.hemen_lazim.errors.ApiException;
import com.tpl.hemen_lazim.errors.ErrorCode;
import com.tpl.hemen_lazim.model.Message;
import com.tpl.hemen_lazim.repositories.UserRepository;
import com.tpl.hemen_lazim.repository.MessageRepository;
import com.tpl.hemen_lazim.util.ChatRooms;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    private static final UUID ME = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private ChatService chatService;

    @Test
    void sendDerivesRoomAndSenderOnTheServer() {
        when(userRepository.existsById(OTHER)).thenReturn(true);
        when(messageRepository.save(any())).thenAnswer(returnsFirstArg());

        Message saved = chatService.send(ME, OTHER.toString(), "selam");

        assertEquals(ChatRooms.roomIdFor(ME, OTHER), saved.getRoomId());
        assertEquals(ME.toString(), saved.getSenderId());
        assertEquals(OTHER.toString(), saved.getRecipientId());
        assertEquals("selam", saved.getContent());
        assertFalse(saved.isRead());
        assertNull(saved.getId());
        verify(messagingTemplate).convertAndSendToUser(OTHER.toString(), "/queue/messages", saved);
    }

    @Test
    void sendAcceptsContentAtTheColumnLimit() {
        when(userRepository.existsById(OTHER)).thenReturn(true);
        when(messageRepository.save(any())).thenAnswer(returnsFirstArg());

        chatService.send(ME, OTHER.toString(), "a".repeat(ChatService.MAX_CONTENT_LENGTH));

        verify(messageRepository).save(any());
    }

    @Test
    void sendRejectsBlankOrTooLongContent() {
        when(userRepository.existsById(OTHER)).thenReturn(true);

        assertCode(ErrorCode.MESSAGE_EMPTY, () -> chatService.send(ME, OTHER.toString(), "   "));
        assertCode(ErrorCode.MESSAGE_EMPTY, () -> chatService.send(ME, OTHER.toString(), null));
        assertCode(ErrorCode.MESSAGE_TOO_LONG,
                () -> chatService.send(ME, OTHER.toString(), "a".repeat(ChatService.MAX_CONTENT_LENGTH + 1)));
        verify(messageRepository, never()).save(any());
    }

    @Test
    void sendRejectsMessagesToSelf() {
        assertCode(ErrorCode.CHAT_WITH_SELF, () -> chatService.send(ME, ME.toString(), "selam"));
        verifyNoInteractions(messageRepository, messagingTemplate);
    }

    @Test
    void sendRejectsUnknownOrMalformedRecipient() {
        when(userRepository.existsById(OTHER)).thenReturn(false);

        assertCode(ErrorCode.USER_NOT_FOUND, () -> chatService.send(ME, OTHER.toString(), "selam"));
        assertCode(ErrorCode.INVALID_USER_ID, () -> chatService.send(ME, "not-a-uuid", "selam"));
        assertCode(ErrorCode.INVALID_USER_ID, () -> chatService.send(ME, null, "selam"));
        verifyNoInteractions(messageRepository, messagingTemplate);
    }

    @Test
    void outsiderCannotReadHistoryOrSync() {
        String strangersRoom = ChatRooms.roomIdFor(OTHER, UUID.randomUUID());

        assertCode(ErrorCode.NOT_CHAT_MEMBER, () -> chatService.history(ME, strangersRoom, null, 30));
        assertCode(ErrorCode.NOT_CHAT_MEMBER, () -> chatService.sync(ME, strangersRoom, 0L));
        verifyNoInteractions(messageRepository);
    }

    @Test
    void historyIsOldestFirstAndPageIsCapped() {
        String room = ChatRooms.roomIdFor(ME, OTHER);
        Message m1 = Message.builder().id(1L).roomId(room).build();
        Message m2 = Message.builder().id(2L).roomId(room).build();
        when(messageRepository.findByRoomIdOrderByIdDesc(eq(room), any())).thenReturn(List.of(m2, m1));

        List<Message> history = chatService.history(ME, room, null, 500);

        assertEquals(List.of(m1, m2), history);
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(messageRepository).findByRoomIdOrderByIdDesc(eq(room), page.capture());
        assertEquals(100, page.getValue().getPageSize());
    }

    @Test
    void roomWithValidatesTheOtherUser() {
        when(userRepository.existsById(OTHER)).thenReturn(true);

        assertEquals(ChatRooms.roomIdFor(ME, OTHER), chatService.roomWith(ME, OTHER.toString()));
        assertCode(ErrorCode.CHAT_WITH_SELF, () -> chatService.roomWith(ME, ME.toString()));
    }

    private static void assertCode(ErrorCode expected, Executable call) {
        ApiException e = assertThrows(ApiException.class, call);
        assertEquals(expected, e.getCode());
    }
}
