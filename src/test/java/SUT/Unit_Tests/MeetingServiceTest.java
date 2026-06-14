package SUT.Unit_Tests;

import com.example.meetings.discover.DiscoveredEvent;
import com.example.meetings.model.InviteStatus;
import com.example.meetings.model.Meeting;
import com.example.meetings.model.MeetingParticipant;
import com.example.meetings.model.User;
import com.example.meetings.repository.MeetingParticipantRepository;
import com.example.meetings.repository.MeetingRepository;
import com.example.meetings.repository.UserRepository;
import com.example.meetings.service.MeetingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MeetingService Unit Tests")
class MeetingServiceTest {

    @Mock
    private MeetingRepository meetingRepository;

    @Mock
    private MeetingParticipantRepository participantRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private MeetingService meetingService;

    private User organizer;
    private User invitee1;
    private User invitee2;
    private Instant meetingStart;
    private Instant meetingEnd;
    private String meetingTitle;
    private String meetingDescription;

    @BeforeEach
    void setUp() {
        organizer = new User("organizer", "org@example.com", "hash1");

        invitee1 = new User("invitee1", "inv1@example.com", "hash2");

        invitee2 = new User("invitee2", "inv2@example.com", "hash3");

        meetingStart = Instant.now().plus(Duration.ofHours(1));
        meetingEnd = meetingStart.plus(Duration.ofHours(1));
        meetingTitle = "Team Meeting";
        meetingDescription = "Quarterly sync";
    }

    @Test
    @DisplayName("propose - should create meeting with organizer and invitees")
    void testProposeSuccess() {
        List<String> invitees = Arrays.asList("invitee1", "invitee2");

        when(userRepository.findByUsername("invitee1")).thenReturn(Optional.of(invitee1));
        when(userRepository.findByUsername("invitee2")).thenReturn(Optional.of(invitee2));
        when(meetingRepository.save(any(Meeting.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Meeting result = meetingService.propose(organizer, meetingTitle, meetingDescription,
                                                 meetingStart, meetingEnd, invitees);

        assertNotNull(result);
        assertEquals(meetingTitle, result.getTitle());
        assertEquals(meetingDescription, result.getDescription());
        assertEquals(meetingStart, result.getStartTime());
        assertEquals(meetingEnd, result.getEndTime());
        assertEquals(organizer, result.getOrganizer());
        assertEquals(3, result.getParticipants().size());

        verify(meetingRepository).save(any(Meeting.class));
    }

    // Line coverage: Line 1
    // Branch Coverage: !end.isAfter(start) == True
    @Test
    @DisplayName("propose - should throw exception when end time is not after start time")
    void testProposeInvalidTimeRange() {
        List<String> invitees = new ArrayList<>();

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> meetingService.propose(organizer, meetingTitle, meetingDescription,
                                        meetingStart, meetingStart, invitees)
        );

        assertEquals("End time must be after start time", exception.getMessage());
        verify(meetingRepository, never()).save(any());
    }

    // Line coverage: Line 1, 3, 4, 5, 6, 7, 8, 9, 10
    // Branch Coverage: !end.isAfter(start) == False and orElseThrow
    @Test
    @DisplayName("propose - should throw exception for invalid invitee")
    void testProposeUnknownInvitee() {
        List<String> invitees = List.of("unknown_user");
        when(userRepository.findByUsername("unknown_user")).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> meetingService.propose(organizer, meetingTitle, meetingDescription,
                                        meetingStart, meetingEnd, invitees)
        );

        assertTrue(exception.getMessage().contains("Unknown invitee"));
        verify(meetingRepository, never()).save(any());
    }

    // Line coverage: Line 1, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12
    // Branch Coverage: !end.isAfter(start) == True AND
    //                  userRepository.findByUsername(normalized) is not null AND
    //                  (normalized.isEmpty() || !seen.add(normalized)) == False
    @Test
    @DisplayName("propose - should auto-accept for organizer")
    void testProposeOrganizerAutoAccepts() {
        List<String> invitees = List.of("invitee1");
        when(userRepository.findByUsername("invitee1")).thenReturn(Optional.of(invitee1));
        when(meetingRepository.save(any(Meeting.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Meeting result = meetingService.propose(organizer, meetingTitle, meetingDescription,
                                                meetingStart, meetingEnd, invitees);

        MeetingParticipant organizerParticipant = result.getParticipants().stream()
            .filter(p -> p.getUser().equals(organizer))
            .findFirst()
            .orElse(null);

        assertNotNull(organizerParticipant);
        assertEquals(InviteStatus.ACCEPTED, organizerParticipant.getStatus());
    }

    // Line coverage: Line 1, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12
    // Branch Coverage: !end.isAfter(start) == True AND
    //                  userRepository.findByUsername(normalized) is not null AND
    //                  (normalized.isEmpty() || !seen.add(normalized)) == False
    @Test
    @DisplayName("propose - should set invitees to PENDING status")
    void testProposeInviteesPending() {
        List<String> invitees = List.of("invitee1");
        when(userRepository.findByUsername("invitee1")).thenReturn(Optional.of(invitee1));
        when(meetingRepository.save(any(Meeting.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Meeting result = meetingService.propose(organizer, meetingTitle, meetingDescription,
                                                meetingStart, meetingEnd, invitees);

        MeetingParticipant inviteeParticipant = result.getParticipants().stream()
            .filter(p -> p.getUser().equals(invitee1))
            .findFirst()
            .orElse(null);

        assertNotNull(inviteeParticipant);
        assertEquals(InviteStatus.PENDING, inviteeParticipant.getStatus());
    }

    // Line coverage: Line 1, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12
    // Branch Coverage: !end.isAfter(start) == True AND
    //                  userRepository.findByUsername(normalized) is not null AND
    //                  (normalized.isEmpty() || !seen.add(normalized)) == True
    @Test
    @DisplayName("propose - should ignore duplicate invitees")
    void testProposeDuplicateInvitees() {
        List<String> invitees = Arrays.asList("invitee1", "invitee1", "invitee1");
        when(userRepository.findByUsername("invitee1")).thenReturn(Optional.of(invitee1));
        when(meetingRepository.save(any(Meeting.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Meeting result = meetingService.propose(organizer, meetingTitle, meetingDescription,
                                                meetingStart, meetingEnd, invitees);

        long invitee1Count = result.getParticipants().stream()
            .filter(p -> p.getUser().equals(invitee1))
            .count();

        assertEquals(1, invitee1Count);
        assertEquals(2, result.getParticipants().size()); // organizer + invitee1
    }

    // Line coverage: Line 1, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12
    // Branch Coverage: !end.isAfter(start) == True AND
    //                  userRepository.findByUsername(normalized) is not null AND
    //                  (normalized.isEmpty() || !seen.add(normalized)) == True
    @Test
    @DisplayName("propose - should handle empty and whitespace invitees")
    void testProposeEmptyAndWhitespaceInvitees() {
        List<String> invitees = Arrays.asList("", "  ", null, "invitee1");
        when(userRepository.findByUsername("invitee1")).thenReturn(Optional.of(invitee1));
        when(meetingRepository.save(any(Meeting.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Meeting result = meetingService.propose(organizer, meetingTitle, meetingDescription,
                                                meetingStart, meetingEnd, invitees);

        assertEquals(2, result.getParticipants().size()); // organizer + invitee1
    }

    // Line coverage: Line 1
    @Test
    @DisplayName("calendarFor - should return meetings for user")
    void testCalendarForSuccess() {
        List<Meeting> expectedMeetings = new ArrayList<>();
        when(meetingRepository.findCalendarMeetings(organizer)).thenReturn(expectedMeetings);

        List<Meeting> result = meetingService.calendarFor(organizer);

        assertEquals(expectedMeetings, result);
        verify(meetingRepository).findCalendarMeetings(organizer);
    }

    // Line coverage: Line 1
    @Test
    @DisplayName("pendingInvitesFor - should return pending invites for user")
    void testPendingInvitesForSuccess() {
        List<MeetingParticipant> expectedInvites = new ArrayList<>();
        when(participantRepository.findByUserAndStatus(organizer, InviteStatus.PENDING))
            .thenReturn(expectedInvites);

        List<MeetingParticipant> result = meetingService.pendingInvitesFor(organizer);

        assertEquals(expectedInvites, result);
        verify(participantRepository).findByUserAndStatus(organizer, InviteStatus.PENDING);
    }

    // Line coverage: Line 1, 3, 4, 5, 6
    // Branch Coverage: (status != InviteStatus.ACCEPTED && status != InviteStatus.DECLINED) == True AND
    //                  participantRepository.findByMeetingIdAndUserId(meetingId, invitee1.getId()) is not null
    @Test
    @DisplayName("respond - should accept invite")
    void testRespondAccept() {
        Long meetingId = 1L;
        Meeting meeting = new Meeting(meetingTitle, meetingDescription, meetingStart, meetingEnd, organizer);
        MeetingParticipant participant = new MeetingParticipant(meeting, invitee1, InviteStatus.PENDING);

        when(participantRepository.findByMeetingIdAndUserId(meetingId, invitee1.getId()))
            .thenReturn(Optional.of(participant));

        meetingService.respond(meetingId, invitee1, InviteStatus.ACCEPTED);

        assertEquals(InviteStatus.ACCEPTED, participant.getStatus());
    }

    // Line coverage: Line 1, 3, 4, 5, 6
    // Branch Coverage: (status != InviteStatus.ACCEPTED && status != InviteStatus.DECLINED) == True AND
    //                  participantRepository.findByMeetingIdAndUserId(meetingId, invitee1.getId()) is not null
    @Test
    @DisplayName("respond - should decline invite")
    void testRespondDecline() {
        Long meetingId = 1L;
        Meeting meeting = new Meeting(meetingTitle, meetingDescription, meetingStart, meetingEnd, organizer);
        MeetingParticipant participant = new MeetingParticipant(meeting, invitee1, InviteStatus.PENDING);

        when(participantRepository.findByMeetingIdAndUserId(meetingId, invitee1.getId()))
            .thenReturn(Optional.of(participant));

        meetingService.respond(meetingId, invitee1, InviteStatus.DECLINED);

        assertEquals(InviteStatus.DECLINED, participant.getStatus());
    }

    // Line coverage: Line 1, 2
    // Branch Coverage: (status != InviteStatus.ACCEPTED && status != InviteStatus.DECLINED) == False
    @Test
    @DisplayName("respond - should throw exception for PENDING status")
    void testRespondInvalidStatus() {
        Long meetingId = 1L;

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> meetingService.respond(meetingId, invitee1, InviteStatus.PENDING)
        );

        assertEquals("Response must be ACCEPTED or DECLINED", exception.getMessage());
        verify(participantRepository, never()).findByMeetingIdAndUserId(any(), any());
    }

    // Line coverage: Line 1, 3, 4, 5
    // Branch Coverage: (status != InviteStatus.ACCEPTED && status != InviteStatus.DECLINED) == True AND
    //                  participantRepository.findByMeetingIdAndUserId(meetingId, invitee1.getId()) is null
    @Test
    @DisplayName("respond - should throw exception when invite not found")
    void testRespondInviteNotFound() {
        Long meetingId = 1L;
        when(participantRepository.findByMeetingIdAndUserId(meetingId, invitee1.getId()))
            .thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> meetingService.respond(meetingId, invitee1, InviteStatus.ACCEPTED)
        );

        assertEquals("No invite found for this user", exception.getMessage());
    }

    // Line coverage: Line 1, 2, 3, 4, 5
    // Branch Coverage: event.end() != null == True
    @Test
    @DisplayName("copyFromDiscovered - should create meeting from discovered event with end time")
    void testCopyFromDiscoveredWithEndTime() {
        Instant eventStart = Instant.now();
        Instant eventEnd = eventStart.plus(Duration.ofHours(3));
        DiscoveredEvent event = new DiscoveredEvent(
            "ticketmaster", "external123", "Concert", "Great concert", eventStart, eventEnd, "http://url", "Venue XYZ"
        );

        when(meetingRepository.save(any(Meeting.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Meeting result = meetingService.copyFromDiscovered(organizer, event);

        assertEquals("Concert", result.getTitle());
        assertEquals(eventStart, result.getStartTime());
        assertEquals(eventEnd, result.getEndTime());
        assertEquals(organizer, result.getOrganizer());
        assertEquals(1, result.getParticipants().size());
    }

    // Line coverage: Line 1, 2, 3, 4, 5
    // Branch Coverage: event.end() != null == False
    @Test
    @DisplayName("copyFromDiscovered - should default to 2 hour duration when no end time")
    void testCopyFromDiscoveredNoEndTime() {
        Instant eventStart = Instant.now();
        DiscoveredEvent event = new DiscoveredEvent(
            "ticketmaster", "external456", "Concert", "Great concert", eventStart, null, "http://url", "Venue XYZ"
        );

        when(meetingRepository.save(any(Meeting.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Meeting result = meetingService.copyFromDiscovered(organizer, event);

        assertEquals(eventStart.plus(Duration.ofHours(2)), result.getEndTime());
    }

    // Line coverage: Line 1, 2, 3, 4, 5
    // Branch Coverage: event.end() != null == True
    @Test
    @DisplayName("copyFromDiscovered - should set user as sole auto-accepted participant")
    void testCopyFromDiscoveredUserAutoAccepts() {
        Instant eventStart = Instant.now();
        DiscoveredEvent event = new DiscoveredEvent(
            "ticketmaster", "external789", "Concert", null, eventStart, null, null, "Venue XYZ"
        );

        when(meetingRepository.save(any(Meeting.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Meeting result = meetingService.copyFromDiscovered(organizer, event);

        assertEquals(1, result.getParticipants().size());
        MeetingParticipant participant = result.getParticipants().iterator().next();
        assertEquals(organizer, participant.getUser());
        assertEquals(InviteStatus.ACCEPTED, participant.getStatus());
    }

    @Test
    @DisplayName("copyFromDiscovered - should build description from event details")
    void testCopyFromDiscoveredDescriptionBuilding() {
        Instant eventStart = Instant.now();
        DiscoveredEvent event = new DiscoveredEvent(
            "ticketmaster", "external999", "Concert", "Amazing concert", eventStart, null, "http://example.com", "Royal Arena"
        );

        when(meetingRepository.save(any(Meeting.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Meeting result = meetingService.copyFromDiscovered(organizer, event);

        String description = result.getDescription();
        assertTrue(description.contains("Amazing concert"));
        assertTrue(description.contains("Royal Arena"));
        assertTrue(description.contains("ticketmaster"));
    }

    // Line coverage: Line 1, 2
    // Branch Coverage: userRepository.findByIcalToken(token) is not null
    @Test
    @DisplayName("calendarForIcalToken - should return calendar for valid token")
    void testCalendarForIcalTokenSuccess() {
        // Arrange
        String token = "valid-ical-token";
        List<Meeting> expectedMeetings = new ArrayList<>();

        when(userRepository.findByIcalToken(token)).thenReturn(Optional.of(organizer));
        when(meetingRepository.findCalendarMeetings(organizer)).thenReturn(expectedMeetings);

        // Act
        List<Meeting> result = meetingService.calendarForIcalToken(token);

        // Assert
        assertEquals(expectedMeetings, result);
        verify(userRepository).findByIcalToken(token);
        verify(meetingRepository).findCalendarMeetings(organizer);
    }

    // Line coverage: Line 1
    // Branch Coverage: userRepository.findByIcalToken(token) is null (orElseThrow)
    @Test
    @DisplayName("calendarForIcalToken - should throw exception for invalid token")
    void testCalendarForIcalTokenInvalid() {
        // Arrange
        String invalidToken = "invalid-token";
        when(userRepository.findByIcalToken(invalidToken)).thenReturn(Optional.empty());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> meetingService.calendarForIcalToken(invalidToken)
        );

        assertEquals("Invalid iCal token", exception.getMessage());
        verify(userRepository).findByIcalToken(invalidToken);
        verify(meetingRepository, never()).findCalendarMeetings(any());
    }
}


