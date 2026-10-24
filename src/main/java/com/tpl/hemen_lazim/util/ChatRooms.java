package com.tpl.hemen_lazim.util;

import java.util.UUID;

/**
 * Birebir sohbet odası kimliği: iki kullanıcının UUID'si sıralanıp "_" ile birleştirilir.
 *
 * Sıralama UUID metniyle yapılır, UUID.compareTo ile değil. compareTo bitleri işaretli sayı
 * olarak karşılaştırdığı için ilk hanesi 8-f olan UUID'lerde farklı sonuç veriyor;
 * veritabanındaki mevcut odalar ise metin sırasıyla oluşturuldu.
 */
public final class ChatRooms {

    private static final String SEPARATOR = "_";

    private ChatRooms() {}

    public static String roomIdFor(UUID a, UUID b) {
        String first = a.toString();
        String second = b.toString();
        return first.compareTo(second) < 0
                ? first + SEPARATOR + second
                : second + SEPARATOR + first;
    }

    /** Oda tam iki UUID'den oluşuyorsa ve biri userId ise üyedir. */
    public static boolean isMember(String roomId, UUID userId) {
        if (roomId == null) return false;
        String[] parts = roomId.split(SEPARATOR, -1);
        if (parts.length != 2 || !isUuid(parts[0]) || !isUuid(parts[1])) return false;
        String me = userId.toString();
        return parts[0].equals(me) || parts[1].equals(me);
    }

    private static boolean isUuid(String s) {
        try {
            // fromString "1-2-3-4-5" gibi kısa biçimleri de kabul ediyor; yalnızca standart biçim geçsin
            return UUID.fromString(s).toString().equals(s);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
