package com.tpl.hemen_lazim.services;

import com.tpl.hemen_lazim.model.enums.RequestStatus;
import com.tpl.hemen_lazim.repositories.MaterialRequestRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class RequestExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(RequestExpiryJob.class);

    private final MaterialRequestRepository materialRequestRepository;

    /**
     * Süresi dolan açık talepleri EXPIRED yapar. Yakındaki talepler sorgusu süreyi zaten
     * eliyor; bu iş, durumu gösteren yerlerin (kendi taleplerim gibi) doğru kalması için.
     */
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void expireOverdueRequests() {
        int expired = materialRequestRepository.expireOverdue(RequestStatus.OPEN, RequestStatus.EXPIRED, Instant.now());
        if (expired > 0) {
            log.info("{} talebin süresi doldu, EXPIRED yapıldı", expired);
        }
    }
}
