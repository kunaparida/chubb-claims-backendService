package com.claims.mapper;

import com.claims.dto.ClaimResponse;
import com.claims.entity.Claim;

public final class ClaimMapper {
    private ClaimMapper() {}

    public static ClaimResponse toResponse(Claim claim) {
        return new ClaimResponse(claim.getId(), claim.getClaimNumber(), claim.getPolicyNumber(),
                claim.getClaimantName(), claim.getClaimantEmail(), claim.getIncidentDate(), claim.getDescription(),
                claim.getAmountClaimed(), claim.getStatus(), claim.getClaimant().getId(),
                claim.getAssignedOfficer() == null ? null : claim.getAssignedOfficer().getId(),
                claim.getAssignedOfficer() == null ? null : claim.getAssignedOfficer().getFullName(),
                claim.getInformationRequest(), claim.getDecisionNotes(), claim.getCreatedAt(), claim.getUpdatedAt());
    }
}
