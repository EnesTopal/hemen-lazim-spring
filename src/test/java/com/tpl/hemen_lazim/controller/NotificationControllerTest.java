package com.tpl.hemen_lazim.controller;

import com.tpl.hemen_lazim.config.GlobalExceptionHandler;
import com.tpl.hemen_lazim.controllers.NotificationController;
import com.tpl.hemen_lazim.security.JwtUserDetails;
import com.tpl.hemen_lazim.services.DeviceService;
import com.tpl.hemen_lazim.services.NotificationService;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Uygulama teklif sonucunu metinden değil koddan okuyor. */
@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    private static final UUID ME = UUID.randomUUID();
    private static final UUID REQUEST_ID = UUID.randomUUID();

    @Mock
    private NotificationService notificationService;

    @Mock
    private DeviceService deviceService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        JwtUserDetails me = new JwtUserDetails(ME, "ben", "", true, List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(me, null, me.getAuthorities()));

        mvc = MockMvcBuilders.standaloneSetup(new NotificationController(notificationService, deviceService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void firstOfferIsReportedAsNotified() throws Exception {
        when(notificationService.sendSupplyOfferNotification(REQUEST_ID, ME)).thenReturn(true);

        offer().andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("REQUESTER_NOTIFIED"))
                .andExpect(jsonPath("$.message").value("Requester notified"));
    }

    @Test
    void repeatedOfferIsReportedAsAlreadyNotified() throws Exception {
        when(notificationService.sendSupplyOfferNotification(REQUEST_ID, ME)).thenReturn(false);

        offer().andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("REQUESTER_ALREADY_NOTIFIED"));
    }

    private org.springframework.test.web.servlet.ResultActions offer() throws Exception {
        return mvc.perform(post("/api/notifications/supply-offer")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"requestId\":\"" + REQUEST_ID + "\"}"));
    }
}
