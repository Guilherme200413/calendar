package com.example.meetings.unit;

import com.example.meetings.model.InviteStatus;
import com.example.meetings.model.Meeting;
import com.example.meetings.model.MeetingParticipant;
import com.example.meetings.model.User;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;


class MeetingTest {

    private final User organizer = new User("alice", "alice@example.com", "hash");

    private Meeting newMeeting() {
        return new Meeting("Standup", null,
                Instant.parse("2099-06-10T10:00:00Z"),
                Instant.parse("2099-06-10T11:00:00Z"),
                organizer);
    }

    private void addParticipant(Meeting meeting, String username, InviteStatus status) {
        User user = new User(username, username + "@example.com", "hash");
        meeting.addParticipant(new MeetingParticipant(meeting, user, status));
    }

    @Test
    void noParticipants_isNotConfirmed() {
        assertThat(newMeeting().isConfirmed()).isFalse();
    }

    @Test
    void singleAcceptedParticipant_isConfirmed() {
        Meeting meeting = newMeeting();
        addParticipant(meeting, "alice", InviteStatus.ACCEPTED);

        assertThat(meeting.isConfirmed()).isTrue();
    }

    @Test
    void allParticipantsAccepted_isConfirmed() {
        Meeting meeting = newMeeting();
        addParticipant(meeting, "alice", InviteStatus.ACCEPTED);
        addParticipant(meeting, "bob", InviteStatus.ACCEPTED);
        addParticipant(meeting, "carol", InviteStatus.ACCEPTED);

        assertThat(meeting.isConfirmed()).isTrue();
    }

    @Test
    void onePending_isNotConfirmed() {
        Meeting meeting = newMeeting();
        addParticipant(meeting, "alice", InviteStatus.ACCEPTED);
        addParticipant(meeting, "bob", InviteStatus.PENDING);

        assertThat(meeting.isConfirmed()).isFalse();
    }

    @Test
    void oneDeclined_isNotConfirmed() {
        Meeting meeting = newMeeting();
        addParticipant(meeting, "alice", InviteStatus.ACCEPTED);
        addParticipant(meeting, "bob", InviteStatus.DECLINED);

        assertThat(meeting.isConfirmed()).isFalse();
    }

    @Test
    void allDeclined_isNotConfirmed() {
        Meeting meeting = newMeeting();
        addParticipant(meeting, "alice", InviteStatus.DECLINED);
        addParticipant(meeting, "bob", InviteStatus.DECLINED);

        assertThat(meeting.isConfirmed()).isFalse();
    }
}