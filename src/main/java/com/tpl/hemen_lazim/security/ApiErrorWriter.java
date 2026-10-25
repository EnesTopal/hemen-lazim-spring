package com.tpl.hemen_lazim.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tpl.hemen_lazim.model.DTOs.ApiError;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Güvenlik filtrelerindeki hatalar controller'a ulaşmadığı için GlobalExceptionHandler'dan
 * geçmiyor; gövdeyi burada aynı biçimde (ApiError, kod = durumun adı) yazıyoruz.
 */
final class ApiErrorWriter {

    private ApiErrorWriter() {
    }

    static void write(HttpServletResponse response, ObjectMapper objectMapper, HttpStatus status, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), new ApiError(status.name(), message, null));
    }
}
