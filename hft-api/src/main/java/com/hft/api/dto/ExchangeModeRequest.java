package com.hft.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ExchangeModeRequest(
        @NotBlank String mode
) {}
