package com.tpl.hemen_lazim.repositories;

import com.tpl.hemen_lazim.model.MaterialRequest;
import com.tpl.hemen_lazim.model.enums.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface MaterialRequestRepository extends JpaRepository<MaterialRequest, UUID> {
//    List<MaterialRequest> findAllByRequesterId(UUID requesterId);
//    List<MaterialRequest> findAllByStatus(RequestStatus status);

    List<MaterialRequest> findAllByRequester_Id(UUID requesterId);
    List<MaterialRequest> findAllByRequester_IdAndStatus(UUID requesterId, RequestStatus status);

    @Query("""
           SELECT r FROM MaterialRequest r
           WHERE r.latitude  BETWEEN :minLat AND :maxLat
             AND r.longitude BETWEEN :minLng AND :maxLng
             AND r.status = :status
             AND (r.expiresAt IS NULL OR r.expiresAt > :now)
           """)
    List<MaterialRequest> findInBoundingBox(
            @Param("minLat") double minLat,
            @Param("maxLat") double maxLat,
            @Param("minLng") double minLng,
            @Param("maxLng") double maxLng,
            @Param("status") RequestStatus status,
            @Param("now") Instant now
    );
}