package com.tpl.hemen_lazim.config;

import com.tpl.hemen_lazim.errors.ApiException;
import com.tpl.hemen_lazim.errors.ErrorCode;
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
 * Hata yanıtlarını tek biçime çevirir: {@code ApiError(code, message, fieldErrors)}. Uygulama
 * kullanıcıya göstereceği metni {@code code}'dan seçiyor (bkz. ErrorCode); Spring'in kendi
 * istisnalarında kod, HTTP durumunun adıdır (NOT_FOUND, BAD_REQUEST...).
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
        ApiError body = new ApiError(ErrorCode.VALIDATION_FAILED.name(), ErrorCode.VALIDATION_FAILED.message(), fieldErrors);
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

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException ex) {
        return ResponseEntity.status(ex.getCode().status())
                .body(new ApiError(ex.getCode().name(), ex.getMessage(), null));
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
    // kullanıcıya koddan seçtiği kendi metnini gösteriyor.
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
