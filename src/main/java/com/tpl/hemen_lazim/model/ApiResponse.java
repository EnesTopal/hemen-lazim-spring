package com.tpl.hemen_lazim.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
public class ApiResponse<T> {
    private String message;
    private T data;

    /**
     * Uygulamanın ayırt etmesi gereken başarılı bir sonuç varsa sabit kodu (ör. teklif zaten
     * bildirilmiş); uygulama metni bundan seçer. Çoğu yanıtta yok ve JSON'a yazılmıyor.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String code;

    public ApiResponse() {
    }

    public ApiResponse(String message) {
        this.message = message;
        this.data = null;
    }

    public ApiResponse(String message, T data) {
        this.message = message;
        this.data = data;
    }

    public static <T> ApiResponse<T> withCode(String code, String message) {
        ApiResponse<T> response = new ApiResponse<>(message);
        response.setCode(code);
        return response;
    }
}
