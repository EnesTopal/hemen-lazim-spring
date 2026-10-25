package com.tpl.hemen_lazim.config;

import com.tpl.hemen_lazim.model.DTOs.ApiError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;

/**
 * Hata yanıtlarını tek biçime (ApiError) çevirir. Uygulama hata gövdesinden yalnızca
 * "message" alanını okuyup gösteriyor; Spring'in varsayılan hata gövdesinde bu alan yok.
 *
 * ResponseEntityExceptionHandler'dan türüyor ki Spring'in kendi istisnaları (okunamayan gövde,
 * eksik parametre, 404, 405...) kendi durum kodlarıyla kalsın, en alttaki 500'e düşmesin.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        List<ApiError.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new ApiError.FieldError(e.getField(), e.getDefaultMessage()))
                .toList();
        ApiError body = new ApiError(codeOf(status), "Validation failed", fieldErrors);
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex,
                                                             @Nullable Object body,
                                                             HttpHeaders headers,
                                                             HttpStatusCode statusCode,
                                                             WebRequest request) {
        if (!(body instanceof ApiError)) {
            body = new ApiError(codeOf(statusCode), messageOf(ex, statusCode), null);
        }
        if (statusCode.is5xxServerError()) {
            log.error("İstek işlenemedi", ex);
        }
        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex) {
        return error(HttpStatus.FORBIDDEN, "Access denied");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        log.error("Beklenmeyen hata", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiError(status.name(), message, null));
    }

    // ResponseStatusException'larda mesajı biz yazıyoruz. Diğer istisnaların mesajı iç ayrıntı
    // içerebiliyor, o yüzden duruma göre genel bir metin gider. Metinler İngilizce; arayüz
    // kendi diline sonra çevirecek.
    private static String messageOf(Exception ex, HttpStatusCode status) {
        if (ex instanceof ResponseStatusException rse && rse.getReason() != null) {
            return rse.getReason();
        }
        return switch (status.value()) {
            case 400 -> "Bad request";
            case 401 -> "Authentication required";
            case 403 -> "Access denied";
            case 404 -> "Not found";
            case 405 -> "Method not allowed";
            case 409 -> "Conflict";
            default -> status.is5xxServerError() ? "Unexpected error" : "Request could not be processed";
        };
    }

    private static String codeOf(HttpStatusCode status) {
        HttpStatus known = HttpStatus.resolve(status.value());
        return known != null ? known.name() : String.valueOf(status.value());
    }
}
