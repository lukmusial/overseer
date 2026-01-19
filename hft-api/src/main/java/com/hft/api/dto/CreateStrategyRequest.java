package com.hft.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.Map;

public record CreateStrategyRequest(
        @NotBlank String type,
        @NotEmpty List<String> symbols,
        @NotBlank String exchange,
        Map<String, Object> parameters
) {}
