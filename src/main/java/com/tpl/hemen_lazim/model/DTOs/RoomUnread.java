package com.tpl.hemen_lazim.model.DTOs;

/** Oda başına okunmamış mesaj sayısı (JPA interface projection). */
public interface RoomUnread {
    String getRoomId();

    long getUnreadCount();
}
