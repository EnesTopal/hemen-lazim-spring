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
        ApiError body = new ApiError(codeOf(status), "Gönderilen bilgiler geçersiz", fieldErrors);
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
        return error(HttpStatus.FORBIDDEN, "Bu işlem için yetkin yok");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        log.error("Beklenmeyen hata", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Beklenmeyen bir hata oluştu");
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiError(status.name(), message, null));
    }

    // ResponseStatusException'larda mesajı biz yazıyoruz. Diğer istisnaların mesajı İngilizce
    // ve iç ayrıntı içerebiliyor, o yüzden kullanıcıya genel bir metin gider.
    private static String messageOf(Exception ex, HttpStatusCode status) {
        if (ex instanceof ResponseStatusException rse && rse.getReason() != null) {
            return rse.getReason();
        }
        return switch (status.value()) {
            case 400 -> "İstek geçersiz";
            case 401 -> "Oturum açman gerekiyor";
            case 403 -> "Bu işlem için yetkin yok";
            case 404 -> "Bulunamadı";
            case 405 -> "Bu işlem desteklenmiyor";
            case 409 -> "İstek mevcut durumla çelişiyor";
            default -> status.is5xxServerError() ? "Beklenmeyen bir hata oluştu" : "İstek işlenemedi";
        };
    }

    private static String codeOf(HttpStatusCode status) {
        HttpStatus known = HttpStatus.resolve(status.value());
        return known != null ? known.name() : String.valueOf(status.value());
    }
}
