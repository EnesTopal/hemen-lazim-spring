package com.tpl.hemen_lazim.repository;

import com.tpl.hemen_lazim.model.DTOs.RoomUnread;
import com.tpl.hemen_lazim.model.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {
    // Kopan bağlantı sonrası telafi; sınırsız büyümesin diye sayfalı
    List<Message> findByRoomIdAndIdGreaterThanOrderByIdAsc(String roomId, Long lastId, Pageable pageable);

    // En yeni mesajlardan geriye doğru sayfalama (ilk yükleme)
    List<Message> findByRoomIdOrderByIdDesc(String roomId, Pageable pageable);

    // Verilen id'den daha eski mesajlar (yukarı kaydırınca "daha fazla yükle")
    List<Message> findByRoomIdAndIdLessThanOrderByIdDesc(String roomId, Long beforeId, Pageable pageable);

    @Query("SELECT m FROM Message m WHERE m.id IN (SELECT MAX(m2.id) FROM Message m2 WHERE m2.senderId = :userId OR m2.recipientId = :userId GROUP BY m2.roomId) ORDER BY m.serverTimestamp DESC")
    List<Message> findLatestMessagesForUser(@Param("userId") String userId);

    /** Kullanıcının okumadığı mesajların oda bazında sayısı (rozetler için). */
    @Query("SELECT m.roomId AS roomId, COUNT(m) AS unreadCount FROM Message m " +
            "WHERE m.recipientId = :userId AND m.read = false GROUP BY m.roomId")
    List<RoomUnread> countUnreadByRoom(@Param("userId") String userId);

    /** Bir oda açıldığında o odadaki bana gelmiş tüm mesajları okundu işaretler. */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Message m SET m.read = true WHERE m.roomId = :roomId AND m.recipientId = :userId AND m.read = false")
    int markRoomRead(@Param("roomId") String roomId, @Param("userId") String userId);
}
