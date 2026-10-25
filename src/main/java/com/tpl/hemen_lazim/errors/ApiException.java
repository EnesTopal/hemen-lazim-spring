package com.tpl.hemen_lazim.errors;

/**
 * Uygulamaya sabit bir kodla dönen hata. HTTP durumu koddan gelir; GlobalExceptionHandler
 * bunu {@code ApiError(code, message)} gövdesine çevirir.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;

    public ApiException(ErrorCode code) {
        this(code, code.message());
    }

    /** {@code message}: kodun varsayılanından daha ayrıntılı geliştirici metni (İngilizce). */
    public ApiException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode getCode() {
        return code;
    }
}
