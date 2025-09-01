package com.tpl.hemen_lazim.model.DTOs;

import java.util.List;

public record ApiError(String code, String message, List<FieldError> fieldErrors) {
    public record FieldError(String field, String message) {
    }
}
