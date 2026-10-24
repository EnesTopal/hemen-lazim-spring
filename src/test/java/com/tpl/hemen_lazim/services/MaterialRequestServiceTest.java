package com.tpl.hemen_lazim.services;

import com.tpl.hemen_lazim.model.DTOs.MaterialRequestDTO;
import com.tpl.hemen_lazim.model.MaterialRequest;
import com.tpl.hemen_lazim.model.User;
import com.tpl.hemen_lazim.model.enums.Category;
import com.tpl.hemen_lazim.model.enums.RequestStatus;
import com.tpl.hemen_lazim.repositories.MaterialRequestRepository;
import com.tpl.hemen_lazim.repositories.UserRepository;
import com.tpl.hemen_lazim.util.GeoTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaterialRequestServiceTest {

    private static final UUID ME = UUID.randomUUID();
    private static final double LAT = 41.0082;
    private static final double LNG = 28.9784;

    @Mock
    private MaterialRequestRepository materialRequestRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private MaterialRequestService service;

    // Servisin veritabanına sorduğu kutu: {minLat, maxLat, minLng, maxLng}
    private final double[] queriedBox = new double[4];

    @Test
    void includesRequestWhoseCenterIsInsideMyCircle() {
        repositoryReturns(requestAt(500, 100));

        assertEquals(1, nearby(1_000).size());
    }

    @Test
    void includesRequestWhenCirclesOverlapButItsCenterIsOutsideMyCircle() {
        // Bildirilen durum: talebin merkezi arayanın dairesinin dışında, daireler kesişiyor
        repositoryReturns(requestAt(1_800, 1_000));

        assertEquals(1, nearby(1_000).size());
    }

    @Test
    void touchingCirclesMatchButSlightlySeparatedOnesDoNot() {
        MaterialRequest justInside = requestAt(1_999, 1_000);
        MaterialRequest justOutside = requestAt(2_001, 1_000);
        repositoryReturns(justInside, justOutside);

        assertEquals(List.of(justInside.getId()), ids(nearby(1_000)));
    }

    @Test
    void excludesDisjointCircles() {
        repositoryReturns(requestAt(2_200, 1_000));

        assertTrue(nearby(1_000).isEmpty());
    }

    @Test
    void wideRequestFarAwayIsInsideTheQueriedBoxAndMatches() {
        // Eskiden kutu r_arayan + 5 km'ydi: 7 km uzaktaki 8 km'lik talep hiç sorgulanmıyordu
        MaterialRequest wide = requestAt(7_000, 8_000);
        repositoryReturns(wide);

        assertEquals(1, nearby(500).size());
        assertTrue(wide.getLatitude() >= queriedBox[0] && wide.getLatitude() <= queriedBox[1]);
        assertTrue(wide.getLongitude() >= queriedBox[2] && wide.getLongitude() <= queriedBox[3]);
    }

    @Test
    void sortsByDistanceAndReportsTheDistance() {
        MaterialRequest far = requestAt(1_500, 1_000);
        MaterialRequest near = requestAt(500, 1_000);
        repositoryReturns(far, near);

        List<MaterialRequestDTO> result = nearby(1_000);

        assertEquals(List.of(near.getId(), far.getId()), ids(result));
        assertEquals(500, result.get(0).distanceMeters(), 0.5);
    }

    @Test
    void asksTheRepositoryToLeaveOutTheCallersOwnRequests() {
        repositoryReturns();

        nearby(1_000);

        verify(materialRequestRepository).findInBoundingBox(anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                eq(RequestStatus.OPEN), any(Instant.class), eq(ME));
    }

    private void repositoryReturns(MaterialRequest... candidates) {
        when(materialRequestRepository.findInBoundingBox(anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                eq(RequestStatus.OPEN), any(Instant.class), eq(ME)))
                .thenAnswer(invocation -> {
                    for (int i = 0; i < 4; i++) {
                        queriedBox[i] = invocation.getArgument(i);
                    }
                    return List.of(candidates);
                });
    }

    private List<MaterialRequestDTO> nearby(int myRadiusMeters) {
        return service.listNearby(ME, LAT, LNG, myRadiusMeters, null);
    }

    /** Arayanın doğusunda, verilen uzaklıkta ve yarıçapta açık bir talep. */
    private static MaterialRequest requestAt(double distanceMeters, int radiusMeters) {
        double[] position = GeoTestSupport.destination(LAT, LNG, 90, distanceMeters);

        User owner = new User();
        owner.setId(UUID.randomUUID());
        owner.setUserName("komsu");

        MaterialRequest request = new MaterialRequest();
        request.setId(UUID.randomUUID());
        request.setRequester(owner);
        request.setTitle("un");
        request.setCategory(Category.YEMEK_MALZEMESI);
        request.setLatitude(position[0]);
        request.setLongitude(position[1]);
        request.setRadiusMeters(radiusMeters);
        return request;
    }

    private static List<UUID> ids(List<MaterialRequestDTO> dtos) {
        return dtos.stream().map(MaterialRequestDTO::id).toList();
    }
}
