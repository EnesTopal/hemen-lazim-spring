package com.tpl.hemen_lazim.services;

import com.tpl.hemen_lazim.model.ApiResponse;
import com.tpl.hemen_lazim.model.MaterialRequest;
import com.tpl.hemen_lazim.model.RequestLimits;
import com.tpl.hemen_lazim.model.User;
import com.tpl.hemen_lazim.model.DTOs.MaterialRequestCreateDTO;
import com.tpl.hemen_lazim.model.DTOs.MaterialRequestDTO;
import com.tpl.hemen_lazim.model.enums.Category;
import com.tpl.hemen_lazim.model.enums.RequestStatus;
import com.tpl.hemen_lazim.model.enums.Role;
import com.tpl.hemen_lazim.repositories.MaterialRequestRepository;
import com.tpl.hemen_lazim.repositories.UserRepository;
import com.tpl.hemen_lazim.util.GeoUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MaterialRequestService {

    private final MaterialRequestRepository materialRequestRepository;
    private final UserRepository userRepository;

    private static final int DEFAULT_RADIUS_M = RequestLimits.DEFAULT_RADIUS_M;
    // Yoğun bölgelerde yanıt sınırsız büyümesin; en yakınlar önce
    private static final int NEARBY_LIMIT = 200;

    // ---------- Create ----------
//    @Transactional
//    public MaterialRequestDTO create(UUID requesterId, MaterialRequestCreateDTO dto) {
//        User requester = userRepository.findById(requesterId)
//                .orElseThrow(() -> new IllegalArgumentException("Requester not found"));
//
//        MaterialRequest entity = new MaterialRequest();
//        entity.setRequester(requester);
//        entity.setTitle(dto.title());
//        entity.setDescription(dto.description());
//        entity.setCategory(dto.category() != null ? dto.category() : Category.OTHER);
//        entity.setQuantity(dto.quantity());
//        entity.setUnit(dto.unit());
//        entity.setLatitude(dto.latitude());
//        entity.setLongitude(dto.longitude());
//        entity.setRadiusMeters(dto.radiusMeters() != null ? dto.radiusMeters() : DEFAULT_RADIUS_M);
//        entity.setStatus(RequestStatus.OPEN);
//        entity.setExpiresAt(dto.expiresAt()); // @PrePersist default +1h veriyor
//
//        MaterialRequest saved = materialRequestRepository.save(entity);
//        return toDto(saved);
//    }

    @Transactional
    public MaterialRequestDTO create(UUID requesterId, MaterialRequestCreateDTO dto) {
        User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new IllegalArgumentException("Requester not found"));

        MaterialRequest entity = new MaterialRequest();
        entity.setRequester(requester);
        entity.setTitle(dto.title());
        entity.setDescription(dto.description());
        entity.setCategory(dto.category() != null ? dto.category() : Category.DIGER);
        entity.setQuantity(dto.quantity());
        entity.setUnits(dto.units());
        entity.setLatitude(dto.latitude());
        entity.setLongitude(dto.longitude());
        entity.setRadiusMeters(dto.radiusMeters() != null ? dto.radiusMeters() : DEFAULT_RADIUS_M);
        entity.setStatus(RequestStatus.OPEN);
        // Süre DTO'da [MIN_DURATION_MIN, MAX_DURATION_MIN] aralığında doğrulanmış gelir
        entity.setExpiresAt(Instant.now().plus(dto.expiresInMinutes(), ChronoUnit.MINUTES));

        return toDto(materialRequestRepository.save(entity));
    }

    // ---------- Get by id ----------
    @Transactional(readOnly = true)
    public MaterialRequestDTO get(UUID requestId) {
        MaterialRequest r = materialRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));
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
    // İki daire kesişiyorsa eşleşir: distance(req, me) <= req.radius + myRadius.
    // Arayanın kendi talepleri sorguda elenir.
    @Transactional(readOnly = true)
    public List<MaterialRequestDTO> listNearby(UUID me,
                                               double myLat,
                                               double myLng,
                                               Integer myRadiusMeters,
                                               Category optionalCategory) {

        int rMe = clampRadius(myRadiusMeters != null ? myRadiusMeters : RequestLimits.DEFAULT_RADIUS_M);

        // Ön filtre kutusu en büyük talep yarıçapını da kapsamalı: aksi halde uzaktaki geniş
        // yarıçaplı bir talep, daireler kesişse bile aday listesine hiç girmez.
        double[] box = GeoUtils.boundingBox(myLat, myLng, rMe + RequestLimits.MAX_RADIUS_M);
        double minLat = box[0], maxLat = box[1], minLng = box[2], maxLng = box[3];

        List<MaterialRequest> candidates = materialRequestRepository.findInBoundingBox(
                minLat, maxLat, minLng, maxLng, RequestStatus.OPEN, Instant.now(), me
        );

        record Candidate(MaterialRequest request, double distance) {}

        return candidates.stream()
                .filter(r -> optionalCategory == null || r.getCategory() == optionalCategory)
                .map(r -> new Candidate(r, GeoUtils.haversineMeters(myLat, myLng, r.getLatitude(), r.getLongitude())))
                .filter(c -> c.distance() <= rMe + radiusOf(c.request()))
                .sorted(Comparator.comparingDouble(Candidate::distance))
                .limit(NEARBY_LIMIT)
                .map(c -> toDto(c.request(), c.distance()))
                .collect(Collectors.toList());
    }

    // ---------- Status ops ----------
    // Bulunamayan talep, yetkisiz kullanıcı ve kapalı talep istisna olarak fırlatılır;
    // GlobalExceptionHandler bunları 404 / 403 / 409'a çeviriyor.

    @Transactional
    public ResponseEntity<ApiResponse<String>> cancel(UUID requesterId, UUID requestId) {
        MaterialRequest r = mustBeOwnerOrAdmin(requesterId, requestId);
        requireOpen(r, "Only open requests can be cancelled");
        r.setStatus(RequestStatus.CANCELLED);
        return ResponseEntity.ok(new ApiResponse<>("Request cancelled successfully"));
    }

    @Transactional
    public ResponseEntity<ApiResponse<String>> complete(UUID requesterId, UUID requestId) {
        MaterialRequest r = mustBeOwnerOrAdmin(requesterId, requestId);
        requireOpen(r, "Only open requests can be completed");
        r.setStatus(RequestStatus.COMPLETED);
        return ResponseEntity.ok(new ApiResponse<>("Request completed successfully"));
    }

    @Transactional
    public ResponseEntity<ApiResponse<String>> delete(UUID requesterId, UUID requestId) {
        MaterialRequest r = mustBeOwnerOrAdmin(requesterId, requestId);
        materialRequestRepository.delete(r);
        return ResponseEntity.ok(new ApiResponse<>("Request deleted successfully"));
    }

    /**
     * Açık talebin süresini uzatır: yeni bitiş, eski bitişe (geçmişse şimdiye) eklenen süre.
     * Bir talep hiçbir zaman şu andan en uzun süreden (24 sa) daha ileriye açık kalmaz.
     */
    @Transactional
    public MaterialRequestDTO extend(UUID requesterId, UUID requestId, int minutes) {
        MaterialRequest r = mustBeOwnerOrAdmin(requesterId, requestId);
        requireOpen(r, "Only open requests can be extended");

        Instant now = Instant.now();
        Instant from = (r.getExpiresAt() == null || r.getExpiresAt().isBefore(now)) ? now : r.getExpiresAt();
        Instant extended = from.plus(minutes, ChronoUnit.MINUTES);
        Instant latest = now.plus(RequestLimits.MAX_DURATION_MIN, ChronoUnit.MINUTES);
        r.setExpiresAt(extended.isAfter(latest) ? latest : extended);
        return toDto(r);
    }


    // ---------- Helpers ----------
    private MaterialRequest mustBeOwnerOrAdmin(UUID requesterId, UUID requestId) {
        MaterialRequest r = materialRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));

        User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (requester.getRole() == Role.ADMIN) {
            return r;
        }

        if (r.getRequester() == null || r.getRequester().getId() == null ||
                !r.getRequester().getId().equals(requesterId)) {
            throw new AccessDeniedException("Only owner or admin can modify this request");
        }

        return r;
    }

    private static void requireOpen(MaterialRequest r, String message) {
        if (r.getStatus() != RequestStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, message);
        }
    }

    private static int clampRadius(int radiusMeters) {
        return Math.max(RequestLimits.MIN_RADIUS_M, Math.min(RequestLimits.MAX_RADIUS_M, radiusMeters));
    }

    private static int radiusOf(MaterialRequest r) {
        return r.getRadiusMeters() != null ? r.getRadiusMeters() : RequestLimits.DEFAULT_RADIUS_M;
    }

    private MaterialRequestDTO toDto(MaterialRequest r) {
        return toDto(r, null);
    }

    // distanceMeters yalnızca arayana göre hesaplandığı yerde (nearby) dolu
    private MaterialRequestDTO toDto(MaterialRequest r, Double distanceMeters) {
        return new MaterialRequestDTO(
                r.getId(),
                r.getRequester() != null ? r.getRequester().getId() : null,
                r.getRequester() != null ? r.getRequester().getUserName() : null,
                r.getTitle(),
                r.getDescription(),
                r.getCategory(),
                r.getQuantity(),
                r.getUnits(),
                r.getLatitude(),
                r.getLongitude(),
                r.getRadiusMeters(),
                r.getStatus(),
                r.getExpiresAt(),
                r.getCreatedAt(),
                distanceMeters
        );
    }
}
