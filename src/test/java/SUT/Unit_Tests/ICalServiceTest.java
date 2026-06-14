package SUT.Unit_Tests;

import com.example.meetings.model.InviteStatus;
import com.example.meetings.model.Meeting;
import com.example.meetings.model.MeetingParticipant;
import com.example.meetings.model.User;
import com.example.meetings.service.ICalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ICalService Unit Tests")
class ICalServiceTest {

    private ICalService icalService;
    private User organizer;
    private User participant1;
    private Instant meetingStart;
    private Instant meetingEnd;

    @BeforeEach
    void setUp() {
        icalService = new ICalService();
        organizer = new User("organizer", "org@example.com", "hash1");
        organizer = new User("organizer", "org@example.com", "hash1");

        participant1 = new User("participant1", "part1@example.com", "hash2");
        participant1 = new User("participant1", "part1@example.com", "hash2");

        meetingStart = Instant.parse("2024-06-15T10:00:00Z");
        meetingEnd = meetingStart.plus(Duration.ofHours(1));
    }

    @Test
    @DisplayName("render - should include iCalendar header and footer")
    void testRenderICalendarStructure() {
        List<Meeting> meetings = new ArrayList<>();

        String result = icalService.render(organizer, meetings);

        assertTrue(result.contains("BEGIN:VCALENDAR"));
        assertTrue(result.contains("END:VCALENDAR"));
        assertTrue(result.contains("VERSION:2.0"));
        assertTrue(result.contains("PRODID:-//meetings-app//EN"));
    }

    @Test
    @DisplayName("render - should include owner calendar name")
    void testRenderCalendarName() {
        List<Meeting> meetings = new ArrayList<>();

        String result = icalService.render(organizer, meetings);

        assertTrue(result.contains("X-WR-CALNAME:organizer's meetings"));
    }

    @Test
    @DisplayName("render - should include meeting event")
    void testRenderMeetingEvent() {
        Meeting meeting = new Meeting("Team Meeting", "Quarterly sync", meetingStart, meetingEnd, organizer);
        meeting.addParticipant(new MeetingParticipant(meeting, organizer, InviteStatus.ACCEPTED));
        List<Meeting> meetings = Arrays.asList(meeting);

        String result = icalService.render(organizer, meetings);

        assertTrue(result.contains("BEGIN:VEVENT"));
        assertTrue(result.contains("END:VEVENT"));
        assertTrue(result.contains("SUMMARY:Team Meeting"));
        assertTrue(result.contains("DESCRIPTION:Quarterly sync"));
    }

    @Test
    @DisplayName("render - should include meeting start and end times in UTC format")
    void testRenderMeetingTimes() {
        Meeting meeting = new Meeting("Team Meeting", null, meetingStart, meetingEnd, organizer);
        meeting.addParticipant(new MeetingParticipant(meeting, organizer, InviteStatus.ACCEPTED));
        List<Meeting> meetings = Arrays.asList(meeting);

        String result = icalService.render(organizer, meetings);

        assertTrue(result.contains("DTSTART:20240615T100000Z"));
        assertTrue(result.contains("DTEND:20240615T110000Z"));
    }

    @Test
    @DisplayName("render - should include organizer information")
    void testRenderOrganizerInfo() {
        Meeting meeting = new Meeting("Team Meeting", null, meetingStart, meetingEnd, organizer);
        meeting.addParticipant(new MeetingParticipant(meeting, organizer, InviteStatus.ACCEPTED));
        List<Meeting> meetings = Arrays.asList(meeting);

        String result = icalService.render(organizer, meetings);

        assertTrue(result.contains("ORGANIZER;CN=organizer:mailto:org@example.com"));
    }

    @Test
    @DisplayName("render - should include attendees with status")
    void testRenderAttendees() {
        Meeting meeting = new Meeting("Team Meeting", null, meetingStart, meetingEnd, organizer);
        meeting.addParticipant(new MeetingParticipant(meeting, organizer, InviteStatus.ACCEPTED));
        meeting.addParticipant(new MeetingParticipant(meeting, participant1, InviteStatus.PENDING));
        List<Meeting> meetings = Arrays.asList(meeting);

        String result = icalService.render(organizer, meetings);

        assertTrue(result.contains("ATTENDEE;CN=organizer;PARTSTAT=ACCEPTED:mailto:org@example.com"));
        assertTrue(result.contains("ATTENDEE;CN=participant1;PARTSTAT=NEEDS-ACTION:mailto:part1@example.com"));
    }

    @Test
    @DisplayName("render - should set STATUS to CONFIRMED when all participants accept")
    void testRenderConfirmedStatus() {
        Meeting meeting = new Meeting("Team Meeting", null, meetingStart, meetingEnd, organizer);
        meeting.addParticipant(new MeetingParticipant(meeting, organizer, InviteStatus.ACCEPTED));
        List<Meeting> meetings = Arrays.asList(meeting);

        String result = icalService.render(organizer, meetings);

        assertTrue(result.contains("STATUS:CONFIRMED"));
    }

    @Test
    @DisplayName("render - should set STATUS to TENTATIVE when not all participants accept")
    void testRenderTentativeStatus() {
        Meeting meeting = new Meeting("Team Meeting", null, meetingStart, meetingEnd, organizer);
        meeting.addParticipant(new MeetingParticipant(meeting, organizer, InviteStatus.ACCEPTED));
        meeting.addParticipant(new MeetingParticipant(meeting, participant1, InviteStatus.PENDING));
        List<Meeting> meetings = Arrays.asList(meeting);

        String result = icalService.render(organizer, meetings);

        assertTrue(result.contains("STATUS:TENTATIVE"));
    }

    @Test
    @DisplayName("render - should include UID for each meeting")
    void testRenderMeetingUID() {
        Meeting meeting = new Meeting("Team Meeting", null, meetingStart, meetingEnd, organizer);
        meeting.addParticipant(new MeetingParticipant(meeting, organizer, InviteStatus.ACCEPTED));
        List<Meeting> meetings = Arrays.asList(meeting);

        String result = icalService.render(organizer, meetings);

        assertTrue(result.contains("UID:meeting-"));
        assertTrue(result.contains("@meetings-app"));
    }

    @Test
    @DisplayName("render - should use CRLF line endings")
    void testRenderCRLFLineEndings() {
        List<Meeting> meetings = new ArrayList<>();

        String result = icalService.render(organizer, meetings);

        assertTrue(result.contains("\r\n"));
        assertFalse(result.matches(".*[^\r]\n.*"));  // Check no LF without CR
    }

    @Test
    @DisplayName("render - should escape special characters in title")
    void testRenderEscapesSpecialCharactersInTitle() {
        Meeting meeting = new Meeting("Team;Meeting,Test", null, meetingStart, meetingEnd, organizer);
        meeting.addParticipant(new MeetingParticipant(meeting, organizer, InviteStatus.ACCEPTED));
        List<Meeting> meetings = Arrays.asList(meeting);

        String result = icalService.render(organizer, meetings);

        assertTrue(result.contains("SUMMARY:Team\\;Meeting\\,Test"));
    }

    @Test
    @DisplayName("render - should escape special characters in description")
    void testRenderEscapesSpecialCharactersInDescription() {
        Meeting meeting = new Meeting("Team Meeting", "Description;with,special\\chars\nand newlines",
                                     meetingStart, meetingEnd, organizer);
        meeting.addParticipant(new MeetingParticipant(meeting, organizer, InviteStatus.ACCEPTED));
        List<Meeting> meetings = Arrays.asList(meeting);

        String result = icalService.render(organizer, meetings);

        assertTrue(result.contains("DESCRIPTION:"));
        assertTrue(result.contains("\\;"));
        assertTrue(result.contains("\\,"));
        assertTrue(result.contains("\\\\"));
        assertTrue(result.contains("\\n"));
    }

    @Test
    @DisplayName("render - should handle empty description")
    void testRenderEmptyDescription() {
        Meeting meeting = new Meeting("Team Meeting", "", meetingStart, meetingEnd, organizer);
        meeting.addParticipant(new MeetingParticipant(meeting, organizer, InviteStatus.ACCEPTED));
        List<Meeting> meetings = Arrays.asList(meeting);

        String result = icalService.render(organizer, meetings);

        int descriptionIndex = result.indexOf("SUMMARY:Team Meeting");
        int nextEventMarker = result.indexOf("ORGANIZER", descriptionIndex);
        String eventSection = result.substring(descriptionIndex, nextEventMarker);
        assertFalse(eventSection.contains("DESCRIPTION:"));
    }

    @Test
    @DisplayName("render - should handle null description")
    void testRenderNullDescription() {
        Meeting meeting = new Meeting("Team Meeting", null, meetingStart, meetingEnd, organizer);
        meeting.addParticipant(new MeetingParticipant(meeting, organizer, InviteStatus.ACCEPTED));
        List<Meeting> meetings = Arrays.asList(meeting);

        String result = icalService.render(organizer, meetings);

        assertTrue(result.contains("SUMMARY:Team Meeting"));
        assertTrue(result.contains("ORGANIZER"));
    }

    @Test
    @DisplayName("render - should map ACCEPTED status to ACCEPTED")
    void testRenderAcceptedPartStatus() {
        Meeting meeting = new Meeting("Team Meeting", null, meetingStart, meetingEnd, organizer);
        meeting.addParticipant(new MeetingParticipant(meeting, organizer, InviteStatus.ACCEPTED));
        List<Meeting> meetings = Arrays.asList(meeting);

        String result = icalService.render(organizer, meetings);

        assertTrue(result.contains("PARTSTAT=ACCEPTED"));
    }

    @Test
    @DisplayName("render - should map DECLINED status to DECLINED")
    void testRenderDeclinedPartStatus() {
        Meeting meeting = new Meeting("Team Meeting", null, meetingStart, meetingEnd, organizer);
        meeting.addParticipant(new MeetingParticipant(meeting, organizer, InviteStatus.DECLINED));
        List<Meeting> meetings = Arrays.asList(meeting);

        String result = icalService.render(organizer, meetings);

        assertTrue(result.contains("PARTSTAT=DECLINED"));
    }

    @Test
    @DisplayName("render - should map PENDING status to NEEDS-ACTION")
    void testRenderPendingPartStatus() {
        Meeting meeting = new Meeting("Team Meeting", null, meetingStart, meetingEnd, organizer);
        meeting.addParticipant(new MeetingParticipant(meeting, organizer, InviteStatus.PENDING));
        List<Meeting> meetings = Arrays.asList(meeting);

        String result = icalService.render(organizer, meetings);

        assertTrue(result.contains("PARTSTAT=NEEDS-ACTION"));
    }

    @Test
    @DisplayName("render - should include multiple meetings")
    void testRenderMultipleMeetings() {

        Meeting meeting1 = new Meeting("Meeting 1", null, meetingStart, meetingEnd, organizer);
        meeting1.addParticipant(new MeetingParticipant(meeting1, organizer, InviteStatus.ACCEPTED));

        Instant start2 = meetingStart.plus(Duration.ofHours(2));
        Instant end2 = start2.plus(Duration.ofHours(1));
        Meeting meeting2 = new Meeting("Meeting 2", null, start2, end2, organizer);
        meeting2.addParticipant(new MeetingParticipant(meeting2, organizer, InviteStatus.ACCEPTED));

        List<Meeting> meetings = Arrays.asList(meeting1, meeting2);

        String result = icalService.render(organizer, meetings);

        int count = 0;
        int index = 0;
        while ((index = result.indexOf("BEGIN:VEVENT", index)) != -1) {
            count++;
            index += 1;
        }
        assertEquals(2, count);
        assertTrue(result.contains("SUMMARY:Meeting 1"));
        assertTrue(result.contains("SUMMARY:Meeting 2"));
    }

    @Test
    @DisplayName("render - should handle empty meeting list")
    void testRenderEmptyMeetingList() {
        List<Meeting> meetings = new ArrayList<>();

        String result = icalService.render(organizer, meetings);

        assertTrue(result.contains("BEGIN:VCALENDAR"));
        assertTrue(result.contains("END:VCALENDAR"));
        assertFalse(result.contains("BEGIN:VEVENT"));
    }

    @Test
    @DisplayName("render - should escape special characters in organizer name and email")
    void testRenderEscapesOrganizerInfo() {
        User specialOrganic = new User("org;test", "org;@example.com", "hash");
        Meeting meeting = new Meeting("Team Meeting", null, meetingStart, meetingEnd, specialOrganic);
        meeting.addParticipant(new MeetingParticipant(meeting, specialOrganic, InviteStatus.ACCEPTED));
        List<Meeting> meetings = Arrays.asList(meeting);

        String result = icalService.render(specialOrganic, meetings);

        assertTrue(result.contains("ORGANIZER;CN=org\\;test:mailto:org\\;@example.com"));
    }

    @Test
    @DisplayName("render - should include DTSTAMP in UTC format")
    void testRenderIncludesDTStamp() {
        Meeting meeting = new Meeting("Team Meeting", null, meetingStart, meetingEnd, organizer);
        meeting.addParticipant(new MeetingParticipant(meeting, organizer, InviteStatus.ACCEPTED));
        List<Meeting> meetings = Arrays.asList(meeting);

        String result = icalService.render(organizer, meetings);

        assertTrue(result.contains("DTSTAMP:"));
        // DTSTAMP should be in format like yyyyMMddTHHmmssZ (the current time)
        assertTrue(result.matches("(?s).*DTSTAMP:\\d{8}T\\d{6}Z.*"));
    }
}

