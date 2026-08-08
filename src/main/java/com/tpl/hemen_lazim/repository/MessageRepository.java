package com.tpl.hemen_lazim.repository;

import com.tpl.hemen_lazim.model.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findByRoomIdAndIdGreaterThanOrderByIdAsc(String roomId, Long lastId);

    // En yeni mesajlardan geriye doğru sayfalama (ilk yükleme)
    List<Message> findByRoomIdOrderByIdDesc(String roomId, Pageable pageable);

    // Verilen id'den daha eski mesajlar (yukarı kaydırınca "daha fazla yükle")
    List<Message> findByRoomIdAndIdLessThanOrderByIdDesc(String roomId, Long beforeId, Pageable pageable);

    @Query("SELECT m FROM Message m WHERE m.id IN (SELECT MAX(m2.id) FROM Message m2 WHERE m2.senderId = :userId OR m2.recipientId = :userId GROUP BY m2.roomId) ORDER BY m.serverTimestamp DESC")
    List<Message> findLatestMessagesForUser(@Param("userId") String userId);
}
