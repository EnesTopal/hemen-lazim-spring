package com.tpl.hemen_lazim.services;

import com.tpl.hemen_lazim.model.MaterialRequest;
import com.tpl.hemen_lazim.model.User;
import com.tpl.hemen_lazim.model.DTOs.MaterialRequestCreateDTO;
import com.tpl.hemen_lazim.model.DTOs.MaterialRequestDTO;
import com.tpl.hemen_lazim.model.enums.Category;
import com.tpl.hemen_lazim.model.enums.RequestStatus;
import com.tpl.hemen_lazim.repositories.MaterialRequestRepository;
import com.tpl.hemen_lazim.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MaterialRequestService {

    private final MaterialRequestRepository materialRequestRepository;
    private final UserRepository userRepository;

    private static final int DEFAULT_RADIUS_M = 1500;

    // ---------- Create ----------
    @Transactional
    public MaterialRequestDTO create(UUID requesterId, MaterialRequestCreateDTO dto) {
        User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new IllegalArgumentException("Requester not found"));

        MaterialRequest entity = new MaterialRequest();
        entity.setRequester(requester);
        entity.setTitle(dto.title());
        entity.setDescription(dto.description());
        entity.setCategory(dto.category() != null ? dto.category() : Category.OTHER);
        entity.setQuantity(dto.quantity());
        entity.setUnit(dto.unit());
        entity.setLatitude(dto.latitude());
        entity.setLongitude(dto.longitude());
        entity.setRadiusMeters(dto.radiusMeters() != null ? dto.radiusMeters() : DEFAULT_RADIUS_M);
        entity.setStatus(RequestStatus.OPEN);
        entity.setExpiresAt(dto.expiresAt()); // @PrePersist default +1h veriyor

        MaterialRequest saved = materialRequestRepository.save(entity);
        return toDto(saved);
    }

    // ---------- Get by id ----------
    @Transactional(readOnly = true)
    public MaterialRequestDTO get(UUID requestId) {
        MaterialRequest r = materialRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found"));
        return toDto(r);
    }

    // ---------- List mine ----------
    @Transactional(readOnly = true)
    public List<MaterialRequestDTO> listMine(UUID requesterId, RequestStatus status) {
        List<MaterialRequest> list = (status == null)
                ? materialRequestRepository.findAllByRequester_Id(requesterId)
                : materialRequestRepository.findAllByRequester_IdAndStatus(requesterId, status);
        return list.stream()
                .sorted(Comparator.comparing(MaterialRequest::getCreatedAt).reversed())
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    // ---------- Nearby (ANLIK) ----------
    // Kullanıcıdan gelen (lat, lng, myRadiusMeters) ile: distance(req, me) <= req.radius + myRadius
    @Transactional(readOnly = true)
    public List<MaterialRequestDTO> listNearby(double myLat,
                                               double myLng,
                                               Integer myRadiusMeters,
                                               Category optionalCategory) {

        int rMe = (myRadiusMeters == null || myRadiusMeters <= 0) ? DEFAULT_RADIUS_M : myRadiusMeters;

        // Bounding-box daraltması (yaklaşık)
        double[] box = boundingBox(myLat, myLng, rMe + 5000); // biraz geniş tutuyoruz; kesin filtre aşağıda
        double minLat = box[0], maxLat = box[1], minLng = box[2], maxLng = box[3];

        List<MaterialRequest> candidates = materialRequestRepository.findInBoundingBox(
                minLat, maxLat, minLng, maxLng, RequestStatus.OPEN, Instant.now()
        );

        // Kesişim kontrolü: d(center_me, center_req) <= r_me + r_req
        return candidates.stream()
                .filter(r -> optionalCategory == null || r.getCategory() == optionalCategory)
                .filter(r -> {
                    double d = haversineMeters(myLat, myLng, r.getLatitude(), r.getLongitude());
                    int sumR = rMe + (r.getRadiusMeters() != null ? r.getRadiusMeters() : DEFAULT_RADIUS_M);
                    return d <= sumR;
                })
                .sorted(Comparator.comparingDouble(r ->
                        haversineMeters(myLat, myLng, r.getLatitude(), r.getLongitude())))
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    // ---------- Status ops ----------
    @Transactional
    public void cancel(UUID requesterId, UUID requestId) {
        MaterialRequest r = mustBeOwner(requesterId, requestId);
        if (r.getStatus() != RequestStatus.OPEN) {
            throw new IllegalStateException("Only OPEN requests can be cancelled");
        }
        r.setStatus(RequestStatus.CANCELLED);
    }

    @Transactional
    public void complete(UUID requesterId, UUID requestId) {
        MaterialRequest r = mustBeOwner(requesterId, requestId);
        if (r.getStatus() != RequestStatus.OPEN) {
            throw new IllegalStateException("Only OPEN requests can be completed");
        }
        r.setStatus(RequestStatus.COMPLETED);
    }

    @Transactional
    public void delete(UUID requesterId, UUID requestId) {
        MaterialRequest r = mustBeOwner(requesterId, requestId);
        materialRequestRepository.delete(r);
    }

    // ---------- Helpers ----------
    private MaterialRequest mustBeOwner(UUID requesterId, UUID requestId) {
        MaterialRequest r = materialRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found"));
        if (r.getRequester() == null || r.getRequester().getId() == null ||
                !r.getRequester().getId().equals(requesterId)) {
            throw new AccessDeniedException("Only owner can modify this request");
        }
        return r;
    }

    private MaterialRequestDTO toDto(MaterialRequest r) {
        return new MaterialRequestDTO(
                r.getId(),
                r.getRequester() != null ? r.getRequester().getId() : null,
                r.getRequester() != null ? r.getRequester().getUserName() : null,
                r.getTitle(),
                r.getDescription(),
                r.getCategory(),
                r.getQuantity(),
                r.getUnit(),
                r.getLatitude(),
                r.getLongitude(),
                r.getRadiusMeters(),
                r.getStatus(),
                r.getExpiresAt(),
                r.getCreatedAt()
        );
    }

    // Haversine (metre)
    private static double haversineMeters(double lat1, double lng1, double lat2, double lng2) {
        double R = 6371000d;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat/2) * Math.sin(dLat/2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng/2) * Math.sin(dLng/2);
        return 2 * R * Math.asin(Math.sqrt(a));
    }

    // Yaklaşık bounding-box (derece)
    private static double[] boundingBox(double lat, double lng, int radiusMeters) {
        double dLat = radiusMeters / 111_000d;
        double dLng = radiusMeters / (111_000d * Math.cos(Math.toRadians(lat)));
        double minLat = lat - dLat, maxLat = lat + dLat;
        double minLng = lng - dLng, maxLng = lng + dLng;
        return new double[]{minLat, maxLat, minLng, maxLng};
    }
}
