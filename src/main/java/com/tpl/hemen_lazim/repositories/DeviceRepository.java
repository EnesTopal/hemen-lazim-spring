package com.tpl.hemen_lazim.repositories;

import com.tpl.hemen_lazim.model.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceRepository extends JpaRepository<Device, UUID> {
    Optional<Device> findByFcmToken(String fcmToken);
    List<Device> findAllByUserIdAndPushEnabled(UUID userId, boolean enabled);

    @Modifying
    @Query("update Device d set d.pushEnabled=false where d.fcmToken=:token")
    void disableByToken(@Param("token") String token);
}