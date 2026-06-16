package SUT.DB_Tests;

import com.example.meetings.MeetingsApplication;
import com.example.meetings.model.InviteStatus;
import com.example.meetings.model.Meeting;
import com.example.meetings.model.MeetingParticipant;
import com.example.meetings.model.User;
import com.example.meetings.repository.MeetingParticipantRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link MeetingParticipantRepository} against a real (embedded,
 * in-memory) H2 database.
 */
@DataJpaTest
@ContextConfiguration(classes = MeetingsApplication.class)
class MeetingParticipantsRepoTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MeetingRepository meetingRepository;

    @Autowired
    private MeetingParticipantRepository participantRepository;

    private User testuser1;
    private User testuser2;
    private Meeting meeting;

    @BeforeEach
    void setUp() {
        testuser1 = userRepository.save(new User("testuser1", "testuser1@example.com", "hash"));
        testuser2 = userRepository.save(new User("testuser2", "testuser2@example.com", "hash"));

        meeting = new Meeting("Planning", "Sprint planning",
                Instant.parse("2026-07-02T10:00:00Z"),
                Instant.parse("2026-07-02T11:00:00Z"), testuser1);
        meeting.addParticipant(new MeetingParticipant(meeting, testuser1, InviteStatus.ACCEPTED));
        meeting.addParticipant(new MeetingParticipant(meeting, testuser2, InviteStatus.PENDING));
        meeting = meetingRepository.saveAndFlush(meeting);
    }

    @Test
    @DisplayName("findByUserAndStatus - should return only the participations matching the given status")
    void findByUserAndStatusFiltersByStatus() {
        List<MeetingParticipant> pending = participantRepository.findByUserAndStatus(testuser2, InviteStatus.PENDING);
        List<MeetingParticipant> accepted = participantRepository.findByUserAndStatus(testuser2, InviteStatus.ACCEPTED);

        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).getMeeting().getTitle()).isEqualTo("Planning");
        assertThat(accepted).isEmpty();
    }

    @Test
    @DisplayName("findByMeetingIdAndUserId - should return the participant for the given meeting and user")
    void findByMeetingIdAndUserIdReturnsParticipant() {
        Optional<MeetingParticipant> participant =
                participantRepository.findByMeetingIdAndUserId(meeting.getId(), testuser2.getId());

        assertThat(participant).isPresent();
        assertThat(participant.get().getStatus()).isEqualTo(InviteStatus.PENDING);
    }

    @Test
    @DisplayName("findByMeetingIdAndUserId - should return empty when the user is not a participant of the meeting")
    void findByMeetingIdAndUserIdReturnsEmptyForNonParticipant() {
        User carol = userRepository.save(new User("carol", "carol@example.com", "hash"));
        userRepository.flush();

        Optional<MeetingParticipant> participant =
                participantRepository.findByMeetingIdAndUserId(meeting.getId(), carol.getId());

        assertThat(participant).isEmpty();
    }

    @Test
    @DisplayName("setStatus + save - should persist an updated invite status")
    void updatingParticipantStatusPersists() {
        MeetingParticipant participant = participantRepository
                .findByMeetingIdAndUserId(meeting.getId(), testuser2.getId())
                .orElseThrow();

        participant.setStatus(InviteStatus.ACCEPTED);
        participantRepository.saveAndFlush(participant);

        MeetingParticipant reloaded = participantRepository
                .findByMeetingIdAndUserId(meeting.getId(), testuser2.getId())
                .orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(InviteStatus.ACCEPTED);
    }
}