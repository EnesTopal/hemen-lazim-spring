package com.tpl.hemen_lazim.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityErrorResponsesTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void missingTokenIsUnauthorizedWithTheSameBodyAsOtherErrors() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new JwtAuthEntryPoint(objectMapper).commence(new MockHttpServletRequest(), response,
                new InsufficientAuthenticationException("no token"));

        JsonNode body = assertJson(response, 401);
        assertEquals("UNAUTHORIZED", body.get("code").asText());
        assertEquals("Authentication required", body.get("message").asText());
    }

    @Test
    void missingRoleIsForbiddenWithTheSameBodyAsOtherErrors() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new JwtAccessDeniedHandler(objectMapper).handle(new MockHttpServletRequest(), response,
                new AccessDeniedException("admin only"));

        JsonNode body = assertJson(response, 403);
        assertEquals("FORBIDDEN", body.get("code").asText());
        assertEquals("Access denied", body.get("message").asText());
    }

    private JsonNode assertJson(MockHttpServletResponse response, int status) throws Exception {
        assertEquals(status, response.getStatus());
        assertTrue(response.getContentType().startsWith("application/json"), response.getContentType());
        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertTrue(body.path("fieldErrors").isMissingNode(), "fieldErrors yalnızca doğrulama hatasında olmalı");
        return body;
    }
}
