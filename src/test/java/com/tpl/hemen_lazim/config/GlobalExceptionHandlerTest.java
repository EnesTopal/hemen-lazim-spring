package com.tpl.hemen_lazim.config;

import com.tpl.hemen_lazim.controllers.MaterialRequestController;
import com.tpl.hemen_lazim.services.MaterialRequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock
    private MaterialRequestService service;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new MaterialRequestController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
                .build();
    }

    @Test
    void missingRequestIsNotFoundWithItsMessage() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.get(id)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Talep bulunamadı"));

        mvc.perform(get("/api/requests/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Talep bulunamadı"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    @Test
    void invalidBodyIsBadRequestWithFieldErrors() throws Exception {
        mvc.perform(post("/api/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Gönderilen bilgiler geçersiz"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'title')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'expiresInMinutes')]").exists());

        verifyNoInteractions(service);
    }

    @Test
    void malformedIdIsBadRequest() throws Exception {
        mvc.perform(get("/api/requests/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("İstek geçersiz"));
    }

    @Test
    void missingAuthenticationIsForbidden() throws Exception {
        // SecurityContext boş: SecurityUtils.currentUserId() AccessDeniedException fırlatır
        mvc.perform(get("/api/requests/mine"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Bu işlem için yetkin yok"));
    }

    @Test
    void unexpectedErrorHidesInternalDetails() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.get(id)).thenThrow(new IllegalStateException("connection refused: db-host:3306"));

        mvc.perform(get("/api/requests/{id}", id))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Beklenmeyen bir hata oluştu"))
                .andExpect(content().string(not(containsString("db-host"))));
    }
}
