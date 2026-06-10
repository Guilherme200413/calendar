package com.example.meetings.unit;

import com.example.meetings.model.*;
import com.example.meetings.service.ICalService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ICalServiceTest {

    private ICalService iCalService;
    private User owner;

    @BeforeEach
    void setUp() {
        iCalService = new ICalService();
        owner = new User("alice", "alice@example.com", "hash");
    }

    // -------------------------------------------------------------------------
    // Calendar structure
    // -------------------------------------------------------------------------

    /**
     * Verifies that the rendered output contains the mandatory VCALENDAR
     * begin/end tags and the VERSION and PRODID properties required by RFC 5545.
     */
    @Test
    void render_emptyMeetings_containsCalendarHeaders() {
        String result = iCalService.render(owner, List.of());
        assertTrue(result.contains("BEGIN:VCALENDAR"));
        assertTrue(result.contains("END:VCALENDAR"));
        assertTrue(result.contains("VERSION:2.0"));
        assertTrue(result.contains("PRODID:-//meetings-app//EN"));
    }

    /**
     * Verifies that the calendar name includes the owner's username,
     * allowing calendar clients to display a meaningful feed title.
     */
    @Test
    void render_emptyMeetings_containsOwnerName() {
        String result = iCalService.render(owner, List.of());
        assertTrue(result.contains("alice's meetings"));
    }

    /**
     * Verifies that line endings use CRLF (\\r\\n) as required by RFC 5545.
     * Non-compliant line endings can cause parsing failures in calendar clients.
     */
    @Test
    void render_usesCRLFLineEndings() {
        String result = iCalService.render(owner, List.of());
        assertTrue(result.contains("\r\n"));
        assertFalse(result.contains("\r\n\n"));
    }

    // -------------------------------------------------------------------------
    // VEVENT structure
    // -------------------------------------------------------------------------

    /**
     * Verifies that each meeting produces a VEVENT block with begin/end tags.
     */
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

    /**
     * Verifies that the meeting title is rendered as the SUMMARY property.
     */
    @Test
    void render_singleMeeting_containsTitle() {
        Instant start = Instant.parse("2025-06-10T10:00:00Z");
        Instant end   = Instant.parse("2025-06-10T11:00:00Z");
        Meeting m = new Meeting("Standup", "desc", start, end, owner);
        m.addParticipant(new MeetingParticipant(m, owner, InviteStatus.ACCEPTED));

        String result = iCalService.render(owner, List.of(m));
        assertTrue(result.contains("SUMMARY:Standup"));
    }

    /**
     * Verifies that the meeting description is rendered as the DESCRIPTION property.
     */
    @Test
    void render_singleMeeting_containsDescription() {
        Instant start = Instant.parse("2025-06-10T10:00:00Z");
        Instant end   = Instant.parse("2025-06-10T11:00:00Z");
        Meeting m = new Meeting("Standup", "Daily sync", start, end, owner);
        m.addParticipant(new MeetingParticipant(m, owner, InviteStatus.ACCEPTED));

        String result = iCalService.render(owner, List.of(m));
        assertTrue(result.contains("DESCRIPTION:Daily sync"));
    }

    /**
     * Verifies that a blank description does not produce a DESCRIPTION property.
     * Omitting empty properties keeps the feed clean and avoids client parsing issues.
     */
    @Test
    void render_blankDescription_noDescriptionLine() {
        Instant start = Instant.parse("2025-06-10T10:00:00Z");
        Instant end   = Instant.parse("2025-06-10T11:00:00Z");
        Meeting m = new Meeting("Standup", "   ", start, end, owner);
        m.addParticipant(new MeetingParticipant(m, owner, InviteStatus.ACCEPTED));

        String result = iCalService.render(owner, List.of(m));
        assertFalse(result.contains("DESCRIPTION:"));
    }

    /**
     * Verifies that start and end times are rendered in UTC format (yyyyMMdd'T'HHmmss'Z')
     * as required by RFC 5545 for UTC timestamps.
     */
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

    // -------------------------------------------------------------------------
    // Meeting.isConfirmed() — STATUS property
    // -------------------------------------------------------------------------

    /**
     * Verifies that a meeting where all participants have ACCEPTED is rendered
     * with STATUS:CONFIRMED, indicating the meeting is definite.
     */
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

    /**
     * Verifies that a meeting with at least one PENDING participant is rendered
     * with STATUS:TENTATIVE, indicating the meeting is not yet confirmed.
     */
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

    // -------------------------------------------------------------------------
    // RFC 5545 compliance
    // -------------------------------------------------------------------------

    /**
     * Verifies that special characters (semicolons and commas) in the meeting title
     * are escaped as required by RFC 5545 to prevent iCal parsing errors.
     */
    @Test
    void render_specialCharsInTitle_escaped() {
        Instant start = Instant.parse("2025-06-10T10:00:00Z");
        Instant end   = Instant.parse("2025-06-10T11:00:00Z");
        Meeting m = new Meeting("Meet; Alice, Bob", "desc", start, end, owner);
        m.addParticipant(new MeetingParticipant(m, owner, InviteStatus.ACCEPTED));

        String result = iCalService.render(owner, List.of(m));
        assertTrue(result.contains("SUMMARY:Meet\\; Alice\\, Bob"));
    }

    // -------------------------------------------------------------------------
    // ATTENDEE PARTSTAT
    // -------------------------------------------------------------------------

    /**
     * Verifies that an ACCEPTED participant is rendered with PARTSTAT=ACCEPTED,
     * allowing calendar clients to show attendance confirmation.
     */
    @Test
    void render_attendeePartStatAccepted() {
        Instant start = Instant.parse("2025-06-10T10:00:00Z");
        Instant end   = Instant.parse("2025-06-10T11:00:00Z");
        Meeting m = new Meeting("Standup", "desc", start, end, owner);
        m.addParticipant(new MeetingParticipant(m, owner, InviteStatus.ACCEPTED));

        String result = iCalService.render(owner, List.of(m));
        assertTrue(result.contains("PARTSTAT=ACCEPTED"));
    }

    /**
     * Verifies that a PENDING participant is rendered with PARTSTAT=NEEDS-ACTION,
     * which is the RFC 5545 standard value for unanswered invites.
     */
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

    // -------------------------------------------------------------------------
    // Multiple meetings
    // -------------------------------------------------------------------------

    /**
     * Verifies that multiple meetings all appear in the rendered output,
     * each with their correct SUMMARY.
     */
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