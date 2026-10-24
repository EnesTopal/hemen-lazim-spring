package com.tpl.hemen_lazim.model.DTOs;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String code, String message, List<FieldError> fieldErrors) {
    public record FieldError(String field, String message) {
    }
}
