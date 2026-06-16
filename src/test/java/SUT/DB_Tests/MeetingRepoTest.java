package SUT.DB_Tests;

import com.example.meetings.MeetingsApplication;
import com.example.meetings.model.InviteStatus;
import com.example.meetings.model.Meeting;
import com.example.meetings.model.MeetingParticipant;
import com.example.meetings.model.User;
import com.example.meetings.repository.MeetingRepository;
import com.example.meetings.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link MeetingRepository}'s custom JPQL queries against a real
 * (embedded, in-memory) H2 database. These queries embed important business rules — declined
 * invites free up the calendar, and overlap detection is time-window based — that are easy to
 * get subtly wrong and aren't meaningfully covered by mocked unit tests.
 */
@DataJpaTest
@ContextConfiguration(classes = MeetingsApplication.class)
class MeetingRepoTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MeetingRepository meetingRepository;

    private User testuser1;
    private User testuser2;
    private User carol;

    @BeforeEach
    void setUp() {
        testuser1 = userRepository.save(new User("testuser1", "testuser1@example.com", "hash"));
        testuser2 = userRepository.save(new User("testuser2", "testuser2@example.com", "hash"));
        carol = userRepository.save(new User("carol", "carol@example.com", "hash"));
        userRepository.flush();
    }

    @Test
    @DisplayName("findCalendarMeetings - should include meetings the user organizes")
    void findCalendarMeetingsIncludesOrganizedMeetings() {
        Meeting meeting = new Meeting("Standup", "Daily sync",
                Instant.parse("2026-07-01T09:00:00Z"),
                Instant.parse("2026-07-01T09:30:00Z"), testuser1);
        meeting.addParticipant(new MeetingParticipant(meeting, testuser1, InviteStatus.ACCEPTED));
        meetingRepository.saveAndFlush(meeting);

        List<Meeting> calendar = meetingRepository.findCalendarMeetings(testuser1);

        assertThat(calendar).extracting(Meeting::getTitle).containsExactly("Standup");
    }

    @Test
    @DisplayName("findCalendarMeetings - should include meetings where the user has a pending invite")
    void findCalendarMeetingsIncludesPendingInvites() {
        Meeting meeting = new Meeting("Planning", "Sprint planning",
                Instant.parse("2026-07-02T10:00:00Z"),
                Instant.parse("2026-07-02T11:00:00Z"), testuser2);
        meeting.addParticipant(new MeetingParticipant(meeting, testuser2, InviteStatus.ACCEPTED));
        meeting.addParticipant(new MeetingParticipant(meeting, testuser1, InviteStatus.PENDING));
        meetingRepository.saveAndFlush(meeting);

        List<Meeting> calendar = meetingRepository.findCalendarMeetings(testuser1);

        assertThat(calendar).extracting(Meeting::getTitle).containsExactly("Planning");
    }

    @Test
    @DisplayName("findCalendarMeetings - should exclude meetings the user has declined")
    void findCalendarMeetingsExcludesDeclinedInvites() {
        Meeting meeting = new Meeting("Retro", "Sprint retro",
                Instant.parse("2026-07-03T15:00:00Z"),
                Instant.parse("2026-07-03T16:00:00Z"), testuser2);
        meeting.addParticipant(new MeetingParticipant(meeting, testuser2, InviteStatus.ACCEPTED));
        meeting.addParticipant(new MeetingParticipant(meeting, testuser1, InviteStatus.DECLINED));
        meetingRepository.saveAndFlush(meeting);

        assertThat(meetingRepository.findCalendarMeetings(testuser1)).isEmpty();
    }

    @Test
    @DisplayName("findCalendarMeetings - should exclude meetings the user has no relation to")
    void findCalendarMeetingsExcludesUnrelatedMeetings() {
        Meeting meeting = new Meeting("Carol's 1:1", "Catch up",
                Instant.parse("2026-07-04T12:00:00Z"),
                Instant.parse("2026-07-04T12:30:00Z"), carol);
        meeting.addParticipant(new MeetingParticipant(meeting, carol, InviteStatus.ACCEPTED));
        meetingRepository.saveAndFlush(meeting);

        assertThat(meetingRepository.findCalendarMeetings(testuser1)).isEmpty();
        assertThat(meetingRepository.findCalendarMeetings(testuser2)).isEmpty();
    }

    @Test
    @DisplayName("findCalendarMeetings - should order results by start time")
    void findCalendarMeetingsOrdersByStartTime() {
        Meeting later = new Meeting("Later meeting", null,
                Instant.parse("2026-07-10T09:00:00Z"),
                Instant.parse("2026-07-10T09:30:00Z"), testuser1);
        later.addParticipant(new MeetingParticipant(later, testuser1, InviteStatus.ACCEPTED));

        Meeting earlier = new Meeting("Earlier meeting", null,
                Instant.parse("2026-07-05T09:00:00Z"),
                Instant.parse("2026-07-05T09:30:00Z"), testuser1);
        earlier.addParticipant(new MeetingParticipant(earlier, testuser1, InviteStatus.ACCEPTED));

        // Save in reverse chronological order so the result order is driven by the query, not insertion order.
        meetingRepository.saveAndFlush(later);
        meetingRepository.saveAndFlush(earlier);

        List<Meeting> calendar = meetingRepository.findCalendarMeetings(testuser1);

        assertThat(calendar).extracting(Meeting::getTitle)
                .containsExactly("Earlier meeting", "Later meeting");
    }

    @Test
    @DisplayName("findOverlapping - should return meetings whose time range intersects the given window")
    void findOverlappingReturnsIntersectingMeetings() {
        Meeting meeting = new Meeting("Workshop", null,
                Instant.parse("2026-07-15T10:00:00Z"),
                Instant.parse("2026-07-15T12:00:00Z"), testuser1);
        meeting.addParticipant(new MeetingParticipant(meeting, testuser1, InviteStatus.ACCEPTED));
        meetingRepository.saveAndFlush(meeting);

        List<Meeting> overlapping = meetingRepository.findOverlapping(testuser1,
                Instant.parse("2026-07-15T11:00:00Z"),
                Instant.parse("2026-07-15T13:00:00Z"));

        assertThat(overlapping).extracting(Meeting::getTitle).containsExactly("Workshop");
    }

    @Test
    @DisplayName("findOverlapping - should not return meetings adjacent to but outside the given window")
    void findOverlappingExcludesNonIntersectingMeetings() {
        Meeting meeting = new Meeting("Workshop", null,
                Instant.parse("2026-07-15T10:00:00Z"),
                Instant.parse("2026-07-15T12:00:00Z"), testuser1);
        meeting.addParticipant(new MeetingParticipant(meeting, testuser1, InviteStatus.ACCEPTED));
        meetingRepository.saveAndFlush(meeting);

        // Window starts exactly when the meeting ends — touching, not overlapping.
        List<Meeting> overlapping = meetingRepository.findOverlapping(testuser1,
                Instant.parse("2026-07-15T12:00:00Z"),
                Instant.parse("2026-07-15T13:00:00Z"));

        assertThat(overlapping).isEmpty();
    }

    @Test
    @DisplayName("findOverlapping - should exclude meetings the user has declined even if the time ranges intersect")
    void findOverlappingExcludesDeclinedMeetings() {
        Meeting meeting = new Meeting("Workshop", null,
                Instant.parse("2026-07-15T10:00:00Z"),
                Instant.parse("2026-07-15T12:00:00Z"), testuser2);
        meeting.addParticipant(new MeetingParticipant(meeting, testuser2, InviteStatus.ACCEPTED));
        meeting.addParticipant(new MeetingParticipant(meeting, testuser1, InviteStatus.DECLINED));
        meetingRepository.saveAndFlush(meeting);

        List<Meeting> overlapping = meetingRepository.findOverlapping(testuser1,
                Instant.parse("2026-07-15T10:00:00Z"),
                Instant.parse("2026-07-15T12:00:00Z"));

        assertThat(overlapping).isEmpty();
    }
}