package com.claims.dto;

import java.math.BigDecimal;

public record LiabilityResponse(BigDecimal pendingExposure, BigDecimal approvedAwaitingSettlement) {}
