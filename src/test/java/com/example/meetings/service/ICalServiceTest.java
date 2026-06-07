package com.example.meetings.service;

import com.example.meetings.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ICalService — pure function, no mocks needed.
 */
public class ICalServiceTest {

    private ICalService iCalService;
    private User owner;

    @BeforeEach
    void setUp() {
        iCalService = new ICalService();
        owner = new User("alice", "alice@example.com", "hash");
    }

    @Test
    void render_emptyMeetings_containsCalendarHeaders() {
        String result = iCalService.render(owner, List.of());
        assertTrue(result.contains("BEGIN:VCALENDAR"));
        assertTrue(result.contains("END:VCALENDAR"));
        assertTrue(result.contains("VERSION:2.0"));
        assertTrue(result.contains("PRODID:-//meetings-app//EN"));
    }

    @Test
    void render_emptyMeetings_containsOwnerName() {
        String result = iCalService.render(owner, List.of());
        assertTrue(result.contains("alice's meetings"));
    }

    @Test
    void render_usesCRLFLineEndings() {
        String result = iCalService.render(owner, List.of());
        assertTrue(result.contains("\r\n"));
        assertFalse(result.contains("\r\n\n"));
    }

    @Test
    void render_singleMeeting_containsVEVENT() {
        Instant start = Instant.parse("2025-06-10T10:00:00Z");
        Instant end   = Instant.parse("2025-06-10T11:00:00Z");
        Meeting m = new Meeting("Standup", "Daily sync", start, end, owner);
        m.addParticipant(new MeetingParticipant(m, owner, InviteStatus.ACCEPTED));

        String result = iCalService.render(owner, List.of(m));
        assertTrue(result.contains("BEGIN:VEVENT"));
        assertTrue(result.contains("END:VEVENT"));
    }

    @Test
    void render_singleMeeting_containsTitle() {
        Instant start = Instant.parse("2025-06-10T10:00:00Z");
        Instant end   = Instant.parse("2025-06-10T11:00:00Z");
        Meeting m = new Meeting("Standup", "desc", start, end, owner);
        m.addParticipant(new MeetingParticipant(m, owner, InviteStatus.ACCEPTED));

        String result = iCalService.render(owner, List.of(m));
        assertTrue(result.contains("SUMMARY:Standup"));
    }

    @Test
    void render_singleMeeting_containsDescription() {
        Instant start = Instant.parse("2025-06-10T10:00:00Z");
        Instant end   = Instant.parse("2025-06-10T11:00:00Z");
        Meeting m = new Meeting("Standup", "Daily sync", start, end, owner);
        m.addParticipant(new MeetingParticipant(m, owner, InviteStatus.ACCEPTED));

        String result = iCalService.render(owner, List.of(m));
        assertTrue(result.contains("DESCRIPTION:Daily sync"));
    }

    @Test
    void render_blankDescription_noDescriptionLine() {
        Instant start = Instant.parse("2025-06-10T10:00:00Z");
        Instant end   = Instant.parse("2025-06-10T11:00:00Z");
        Meeting m = new Meeting("Standup", "   ", start, end, owner);
        m.addParticipant(new MeetingParticipant(m, owner, InviteStatus.ACCEPTED));

        String result = iCalService.render(owner, List.of(m));
        assertFalse(result.contains("DESCRIPTION:"));
    }

    @Test
    void render_singleMeeting_containsStartAndEnd() {
        Instant start = Instant.parse("2025-06-10T10:00:00Z");
        Instant end   = Instant.parse("2025-06-10T11:00:00Z");
        Meeting m = new Meeting("Standup", "desc", start, end, owner);
        m.addParticipant(new MeetingParticipant(m, owner, InviteStatus.ACCEPTED));

        String result = iCalService.render(owner, List.of(m));
        assertTrue(result.contains("DTSTART:20250610T100000Z"));
        assertTrue(result.contains("DTEND:20250610T110000Z"));
    }

    @Test
    void render_allAccepted_statusConfirmed() {
        Instant start = Instant.parse("2025-06-10T10:00:00Z");
        Instant end   = Instant.parse("2025-06-10T11:00:00Z");
        User bob = new User("bob", "bob@example.com", "hash");
        Meeting m = new Meeting("Standup", "desc", start, end, owner);
        m.addParticipant(new MeetingParticipant(m, owner, InviteStatus.ACCEPTED));
        m.addParticipant(new MeetingParticipant(m, bob, InviteStatus.ACCEPTED));

        String result = iCalService.render(owner, List.of(m));
        assertTrue(result.contains("STATUS:CONFIRMED"));
    }

    @Test
    void render_pendingParticipant_statusTentative() {
        Instant start = Instant.parse("2025-06-10T10:00:00Z");
        Instant end   = Instant.parse("2025-06-10T11:00:00Z");
        User bob = new User("bob", "bob@example.com", "hash");
        Meeting m = new Meeting("Standup", "desc", start, end, owner);
        m.addParticipant(new MeetingParticipant(m, owner, InviteStatus.ACCEPTED));
        m.addParticipant(new MeetingParticipant(m, bob, InviteStatus.PENDING));

        String result = iCalService.render(owner, List.of(m));
        assertTrue(result.contains("STATUS:TENTATIVE"));
    }

    @Test
    void render_specialCharsInTitle_escaped() {
        Instant start = Instant.parse("2025-06-10T10:00:00Z");
        Instant end   = Instant.parse("2025-06-10T11:00:00Z");
        Meeting m = new Meeting("Meet; Alice, Bob", "desc", start, end, owner);
        m.addParticipant(new MeetingParticipant(m, owner, InviteStatus.ACCEPTED));

        String result = iCalService.render(owner, List.of(m));
        assertTrue(result.contains("SUMMARY:Meet\\; Alice\\, Bob"));
    }

    @Test
    void render_attendeePartStatAccepted() {
        Instant start = Instant.parse("2025-06-10T10:00:00Z");
        Instant end   = Instant.parse("2025-06-10T11:00:00Z");
        Meeting m = new Meeting("Standup", "desc", start, end, owner);
        m.addParticipant(new MeetingParticipant(m, owner, InviteStatus.ACCEPTED));

        String result = iCalService.render(owner, List.of(m));
        assertTrue(result.contains("PARTSTAT=ACCEPTED"));
    }

    @Test
    void render_attendeePartStatPending() {
        Instant start = Instant.parse("2025-06-10T10:00:00Z");
        Instant end   = Instant.parse("2025-06-10T11:00:00Z");
        User bob = new User("bob", "bob@example.com", "hash");
        Meeting m = new Meeting("Standup", "desc", start, end, owner);
        m.addParticipant(new MeetingParticipant(m, owner, InviteStatus.ACCEPTED));
        m.addParticipant(new MeetingParticipant(m, bob, InviteStatus.PENDING));

        String result = iCalService.render(owner, List.of(m));
        assertTrue(result.contains("PARTSTAT=NEEDS-ACTION"));
    }

    @Test
    void render_multipleMeetings_allPresent() {
        Instant s1 = Instant.parse("2025-06-10T10:00:00Z");
        Instant e1 = Instant.parse("2025-06-10T11:00:00Z");
        Instant s2 = Instant.parse("2025-06-11T10:00:00Z");
        Instant e2 = Instant.parse("2025-06-11T11:00:00Z");
        Meeting m1 = new Meeting("Standup", "desc", s1, e1, owner);
        Meeting m2 = new Meeting("Retro",   "desc", s2, e2, owner);
        m1.addParticipant(new MeetingParticipant(m1, owner, InviteStatus.ACCEPTED));
        m2.addParticipant(new MeetingParticipant(m2, owner, InviteStatus.ACCEPTED));

        String result = iCalService.render(owner, List.of(m1, m2));
        assertTrue(result.contains("SUMMARY:Standup"));
        assertTrue(result.contains("SUMMARY:Retro"));
    }
}
