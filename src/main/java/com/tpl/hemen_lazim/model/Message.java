package com.tpl.hemen_lazim.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "messages")
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String roomId;

    @Column(nullable = false)
    private String senderId;

    @Column(nullable = false)
    private String recipientId;

    @Column(nullable = false)
    private String content;

    @Column(nullable = false)
    private Long serverTimestamp; // Unix timestamp

    /**
     * Alıcının bu mesajı görüp görmediği. Birebir sohbette her mesajın tek bir alıcısı
     * olduğu için okunma durumunu mesajın kendisinde tutmak yeterli; ayrı bir
     * "oda-kullanıcı okuma imleci" tablosuna gerek yok.
     *
     * Okunmamış rozetinin uygulama kapatılıp açıldığında da doğru görünmesi
     * gerektiğinden bu bilgi sunucuda kalıcı olmak zorunda.
     */
    @Column(name = "is_read", nullable = false)
    private boolean read = false;
}
