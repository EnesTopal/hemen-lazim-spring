package com.tpl.hemen_lazim.util;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatRoomsTest {

    // compareTo işaretli karşılaştırdığı için 8... < 1... der; metin sırası tersini söyler
    private static final UUID LOW_TEXT = UUID.fromString("1aaaaaaa-0000-0000-0000-000000000000");
    private static final UUID HIGH_TEXT = UUID.fromString("8bbbbbbb-0000-0000-0000-000000000000");

    @Test
    void roomIdUsesTextOrderLikeExistingRooms() {
        String expected = LOW_TEXT + "_" + HIGH_TEXT;

        assertEquals(expected, ChatRooms.roomIdFor(LOW_TEXT, HIGH_TEXT));
        assertEquals(expected, ChatRooms.roomIdFor(HIGH_TEXT, LOW_TEXT));
    }

    @Test
    void bothParticipantsAreMembers() {
        String room = ChatRooms.roomIdFor(LOW_TEXT, HIGH_TEXT);

        assertTrue(ChatRooms.isMember(room, LOW_TEXT));
        assertTrue(ChatRooms.isMember(room, HIGH_TEXT));
    }

    @Test
    void outsiderIsNotMember() {
        String room = ChatRooms.roomIdFor(LOW_TEXT, HIGH_TEXT);

        assertFalse(ChatRooms.isMember(room, UUID.randomUUID()));
    }

    @Test
    void malformedRoomsHaveNoMembers() {
        assertFalse(ChatRooms.isMember(null, LOW_TEXT));
        assertFalse(ChatRooms.isMember(LOW_TEXT.toString(), LOW_TEXT));
        assertFalse(ChatRooms.isMember(LOW_TEXT + "_", LOW_TEXT));
        assertFalse(ChatRooms.isMember(LOW_TEXT + "_x", LOW_TEXT));
        assertFalse(ChatRooms.isMember(LOW_TEXT + "_" + HIGH_TEXT + "_" + LOW_TEXT, LOW_TEXT));
        assertFalse(ChatRooms.isMember(LOW_TEXT + "_1-2-3-4-5", LOW_TEXT));
    }
}
