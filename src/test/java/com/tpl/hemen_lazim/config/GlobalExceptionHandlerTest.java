package com.tpl.hemen_lazim.config;

import com.tpl.hemen_lazim.controllers.MaterialRequestController;
import com.tpl.hemen_lazim.errors.ApiException;
import com.tpl.hemen_lazim.errors.ErrorCode;
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
    void missingRequestIsNotFoundWithItsCode() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.get(id)).thenThrow(new ApiException(ErrorCode.REQUEST_NOT_FOUND));

        mvc.perform(get("/api/requests/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REQUEST_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Request not found"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    @Test
    void apiExceptionKeepsItsDetailedMessage() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.get(id)).thenThrow(new ApiException(ErrorCode.REQUEST_NOT_OPEN, "Only open requests can be extended"));

        mvc.perform(get("/api/requests/{id}", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUEST_NOT_OPEN"))
                .andExpect(jsonPath("$.message").value("Only open requests can be extended"));
    }

    @Test
    void frameworkErrorsUseTheStatusNameAsCode() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.get(id)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Gone"));

        mvc.perform(get("/api/requests/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Gone"));
    }

    @Test
    void invalidBodyIsBadRequestWithFieldErrors() throws Exception {
        mvc.perform(post("/api/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'title')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'expiresInMinutes')]").exists());

        verifyNoInteractions(service);
    }

    @Test
    void extendOutsideTheDurationRangeIsBadRequest() throws Exception {
        mvc.perform(post("/api/requests/{id}/extend", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"minutes\":5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'minutes')]").exists());

        verifyNoInteractions(service);
    }

    @Test
    void malformedIdIsBadRequest() throws Exception {
        mvc.perform(get("/api/requests/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Bad request"));
    }

    @Test
    void missingAuthenticationIsForbidden() throws Exception {
        // SecurityContext boş: SecurityUtils.currentUserId() AccessDeniedException fırlatır
        mvc.perform(get("/api/requests/mine"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Access denied"));
    }

    @Test
    void unexpectedErrorHidesInternalDetails() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.get(id)).thenThrow(new IllegalStateException("connection refused: db-host:3306"));

        mvc.perform(get("/api/requests/{id}", id))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("Unexpected error"))
                .andExpect(content().string(not(containsString("db-host"))));
    }
}
