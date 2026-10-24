package com.tpl.hemen_lazim.repositories;

import com.tpl.hemen_lazim.model.MaterialRequest;
import com.tpl.hemen_lazim.model.User;
import com.tpl.hemen_lazim.model.enums.Category;
import com.tpl.hemen_lazim.model.enums.RequestStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

// application.properties'teki ${DB_*} yer tutucuları burada çözülsün diye verilir;
// @DataJpaTest veri kaynağını zaten bellek içi H2 ile değiştiriyor.
@DataJpaTest
@TestPropertySource(properties = {"DB_URL=jdbc:h2:mem:unused", "DB_USER=sa", "DB_PASS="})
class MaterialRequestRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private MaterialRequestRepository repository;

    @Test
    void boundingBoxQueryLeavesOutTheCallersOwnRequests() {
        User me = persistUser("ben");
        User neighbour = persistUser("komsu");
        persistRequest(me, "benim talebim");
        MaterialRequest theirs = persistRequest(neighbour, "komsunun talebi");
        em.flush();
        em.clear();

        List<MaterialRequest> found = repository.findInBoundingBox(
                40.9, 41.1, 28.9, 29.1, RequestStatus.OPEN, Instant.now(), me.getId());

        assertEquals(List.of(theirs.getId()), found.stream().map(MaterialRequest::getId).toList());
        assertEquals("komsu", found.get(0).getRequester().getUserName());
    }

    @Test
    void expireOverdueClosesOnlyOpenRequestsPastTheirTime() {
        User owner = persistUser("sahip");
        Instant now = Instant.now();
        MaterialRequest overdue = persistRequest(owner, "süresi geçmiş", RequestStatus.OPEN, now.minus(Duration.ofMinutes(1)));
        MaterialRequest running = persistRequest(owner, "süresi var", RequestStatus.OPEN, now.plus(Duration.ofMinutes(30)));
        MaterialRequest cancelled = persistRequest(owner, "iptal", RequestStatus.CANCELLED, now.minus(Duration.ofMinutes(1)));
        em.flush();
        em.clear();

        int changed = repository.expireOverdue(RequestStatus.OPEN, RequestStatus.EXPIRED, now);

        assertEquals(1, changed);
        assertEquals(RequestStatus.EXPIRED, em.find(MaterialRequest.class, overdue.getId()).getStatus());
        assertEquals(RequestStatus.OPEN, em.find(MaterialRequest.class, running.getId()).getStatus());
        assertEquals(RequestStatus.CANCELLED, em.find(MaterialRequest.class, cancelled.getId()).getStatus());
    }

    private User persistUser(String name) {
        User user = new User();
        user.setUserName(name);
        user.setEmail(name + "@example.com");
        user.setPasswordHash("x");
        return em.persist(user);
    }

    private MaterialRequest persistRequest(User owner, String title) {
        return persistRequest(owner, title, RequestStatus.OPEN, null);
    }

    private MaterialRequest persistRequest(User owner, String title, RequestStatus status, Instant expiresAt) {
        MaterialRequest request = new MaterialRequest();
        request.setStatus(status);
        request.setExpiresAt(expiresAt);
        request.setRequester(owner);
        request.setTitle(title);
        request.setCategory(Category.YEMEK_MALZEMESI);
        request.setLatitude(41.0);
        request.setLongitude(29.0);
        request.setRadiusMeters(1_000);
        return em.persist(request);
    }
}
