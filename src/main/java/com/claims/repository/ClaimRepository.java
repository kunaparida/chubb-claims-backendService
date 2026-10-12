package com.claims.repository;

import com.claims.entity.Claim;
import com.claims.entity.ClaimStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClaimRepository extends JpaRepository<Claim, Long> {
    List<Claim> findByClaimantIdOrderByCreatedAtDesc(Long claimantId);
    List<Claim> findByAssignedOfficerIdOrderByCreatedAtDesc(Long officerId);
    long countByAssignedOfficerIdAndStatusIn(Long officerId, List<ClaimStatus> statuses);
    long countByAssignedOfficerId(Long officerId);
    long countByStatus(ClaimStatus status);

    @Query("select coalesce(sum(c.amountClaimed), 0) from Claim c where c.status in :statuses")
    java.math.BigDecimal sumAmountByStatusIn(@Param("statuses") List<ClaimStatus> statuses);

    @Query("select c.status, count(c) from Claim c group by c.status")
    List<Object[]> countGroupedByStatus();
}
