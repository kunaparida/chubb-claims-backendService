package com.claims.service;

import com.claims.dto.*;
import com.claims.entity.*;
import com.claims.exception.BusinessException;
import com.claims.exception.ResourceNotFoundException;
import com.claims.mapper.ClaimMapper;
import com.claims.repository.ClaimRepository;
import com.claims.repository.UserRepository;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.claims.util.ClaimNumberGenerator;

@Service
public class ClaimService {
    private final ClaimRepository claims;
    private final UserRepository users;

    private static final int MAX_PAGE_SIZE = 100;
    private static final Map<ClaimStatus, Set<ClaimStatus>> TRANSITIONS = Map.of(
            ClaimStatus.SUBMITTED, Set.of(ClaimStatus.UNDER_REVIEW),
            ClaimStatus.UNDER_REVIEW, Set.of(ClaimStatus.MORE_INFO_REQUIRED, ClaimStatus.APPROVED, ClaimStatus.REJECTED),
            ClaimStatus.MORE_INFO_REQUIRED, Set.of(ClaimStatus.UNDER_REVIEW),
            ClaimStatus.APPROVED, Set.of(ClaimStatus.SETTLED),
            ClaimStatus.REJECTED, Set.of(),
            ClaimStatus.SETTLED, Set.of());

    public ClaimService(ClaimRepository claims, UserRepository users) {
        this.claims = claims;
        this.users = users;
    }

    @Transactional
    public ClaimResponse create(CreateClaimRequest request, String actorEmail) {
        User claimant = userByEmail(actorEmail);
        if (!claimant.getEmail().equalsIgnoreCase(request.claimantEmail().trim())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Claimant email must match the authenticated account");
        }
        Claim claim = new Claim(ClaimNumberGenerator.generate(), request.policyNumber().trim(),
                request.claimantName().trim(), claimant.getEmail(), request.incidentDate(),
                request.description().trim(), request.amountClaimed(), claimant);
        return ClaimMapper.toResponse(claims.save(claim));
    }

    @Transactional(readOnly = true)
    public PageResponse<ClaimResponse> list(String actorEmail, boolean manager, int page, int size) {
        User actor = userByEmail(actorEmail);
        if (manager && actor.getRole() != Role.MANAGER) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Manager access is required");
        }
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        Page<Claim> result = switch (actor.getRole()) {
            case CLAIMANT -> claims.findByClaimantId(actor.getId(), pageable);
            case OFFICER -> claims.findByAssignedOfficerId(actor.getId(), pageable);
            case MANAGER -> claims.findAll(pageable);
        };
        return PageResponse.of(result.map(ClaimMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public ClaimResponse get(Long id, String actorEmail) {
        User actor = userByEmail(actorEmail);
        Claim claim = findClaim(id);
        verifyCanView(claim, actor);
        return ClaimMapper.toResponse(claim);
    }

    @Transactional
    public ClaimResponse assign(Long id, Long officerId, String actorEmail) {
        User actor = userByEmail(actorEmail);
        Claim claim = findClaim(id);
        User officer = users.findById(officerId)
                .filter(candidate -> candidate.getRole() == Role.OFFICER)
                .orElseThrow(() -> new ResourceNotFoundException("Officer not found"));
        if (actor.getRole() == Role.OFFICER) {
            if (!actor.getId().equals(officer.getId())) {
                throw new BusinessException(HttpStatus.FORBIDDEN, "Officers may only assign claims to themselves");
            }
            if (claim.getAssignedOfficer() != null && !claim.getAssignedOfficer().getId().equals(actor.getId())) {
                throw new BusinessException(HttpStatus.CONFLICT, "Claim is already assigned to another officer");
            }
        } else if (actor.getRole() != Role.MANAGER) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Manager or officer access is required");
        }
        claim.assign(officer);
        return ClaimMapper.toResponse(claims.save(claim));
    }

    @Transactional
    public ClaimResponse review(Long id, String actorEmail) {
        Claim claim = authorizedClaim(id, actorEmail);
        transition(claim, ClaimStatus.UNDER_REVIEW);
        claim.setInformationRequest(null);
        return ClaimMapper.toResponse(claims.save(claim));
    }

    @Transactional
    public ClaimResponse requestInformation(Long id, String note, String actorEmail) {
        Claim claim = authorizedClaim(id, actorEmail);
        transition(claim, ClaimStatus.MORE_INFO_REQUIRED);
        claim.setInformationRequest(note.trim());
        return ClaimMapper.toResponse(claims.save(claim));
    }

    @Transactional
    public ClaimResponse decide(Long id, ClaimStatus next, String note, String actorEmail) {
        Claim claim = authorizedClaim(id, actorEmail);
        transition(claim, next);
        claim.setDecisionNotes(note == null ? null : note.trim());
        return ClaimMapper.toResponse(claims.save(claim));
    }

    private Claim authorizedClaim(Long id, String email) {
        User actor = userByEmail(email);
        Claim claim = findClaim(id);
        if (actor.getRole() != Role.MANAGER) requireAssignedOfficer(claim, actor);
        return claim;
    }

    private void verifyCanView(Claim claim, User actor) {
        if (actor.getRole() == Role.CLAIMANT && !claim.getClaimant().getId().equals(actor.getId())) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Claim not found");
        }
        if (actor.getRole() == Role.OFFICER && (claim.getAssignedOfficer() == null
                || !claim.getAssignedOfficer().getId().equals(actor.getId()))) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Claim not found");
        }
    }

    private void requireAssignedOfficer(Claim claim, User actor) {
        if (actor.getRole() != Role.OFFICER || claim.getAssignedOfficer() == null
                || !claim.getAssignedOfficer().getId().equals(actor.getId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Only the assigned officer or a manager can perform this action");
        }
    }

    private void transition(Claim claim, ClaimStatus next) {
        if (!TRANSITIONS.getOrDefault(claim.getStatus(), Set.of()).contains(next)) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Invalid claim status transition: " + claim.getStatus() + " -> " + next);
        }
        claim.transitionTo(next);
    }

    private User userByEmail(String email) {
        return users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "Authenticated user no longer exists"));
    }

    private Claim findClaim(Long id) {
        return claims.findById(id).orElseThrow(() -> new ResourceNotFoundException("Claim not found"));
    }
}
