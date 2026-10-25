package com.tpl.hemen_lazim.controller;

import com.tpl.hemen_lazim.config.GlobalExceptionHandler;
import com.tpl.hemen_lazim.errors.ApiException;
import com.tpl.hemen_lazim.errors.ErrorCode;
import com.tpl.hemen_lazim.model.Message;
import com.tpl.hemen_lazim.security.JwtUserDetails;
import com.tpl.hemen_lazim.services.ChatService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Uygulamanın bugün gönderdiği istekler değişmeden çalışmalı, ama içindeki kimlikler yok sayılmalı. */
@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    private static final UUID ME = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();
    private static final UUID SPOOFED = UUID.randomUUID();

    @Mock
    private ChatService chatService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        JwtUserDetails me = new JwtUserDetails(ME, "ben", "", true, List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(me, null, me.getAuthorities()));

        mvc = MockMvcBuilders.standaloneSetup(new ChatController(chatService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void sendIgnoresRoomAndSenderFromTheAppsPayload() throws Exception {
        when(chatService.send(ME, OTHER.toString(), "selam")).thenReturn(Message.builder().id(7L).build());

        // ChatViewModel.sendMessage'ın gönderdiği Message nesnesinin tamamı
        String body = """
                {"id":0,"roomId":"%s_%s","senderId":"%s","recipientId":"%s","content":"selam",
                 "serverTimestamp":1,"clientId":"c-1","status":"SENDING","read":true}
                """.formatted(SPOOFED, OTHER, SPOOFED, OTHER);

        mvc.perform(post("/api/chat/messages").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7));

        verify(chatService).send(ME, OTHER.toString(), "selam");
    }

    @Test
    void roomListIsAlwaysTheCallersOwn() throws Exception {
        when(chatService.latestPerRoom(ME)).thenReturn(List.of());

        mvc.perform(get("/api/chat/rooms").param("userId", SPOOFED.toString()))
                .andExpect(status().isOk());

        verify(chatService).latestPerRoom(ME);
    }

    @Test
    void createRoomUsesTheCallerAsSender() throws Exception {
        when(chatService.roomWith(ME, OTHER.toString())).thenReturn("room-id");

        mvc.perform(post("/api/chat/room")
                        .param("senderId", SPOOFED.toString())
                        .param("recipientId", OTHER.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId").value("room-id"));
    }

    @Test
    void foreignRoomHistoryIsForbidden() throws Exception {
        when(chatService.history(eq(ME), any(), any(), anyInt()))
                .thenThrow(new ApiException(ErrorCode.NOT_CHAT_MEMBER));

        mvc.perform(get("/api/chat/history").param("roomId", OTHER + "_" + SPOOFED))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_CHAT_MEMBER"))
                .andExpect(jsonPath("$.message").value("You are not a member of this chat"));
    }
}
