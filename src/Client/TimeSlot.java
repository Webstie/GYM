package Client;

import java.util.ArrayList;
import java.util.List;

public class TimeSlot {
    public String date;
    public int start;
    public int end;
    public String meetingId;
    public List<String> participantIPs;

    public TimeSlot() {
        this.participantIPs = new ArrayList<>();
    }

    public TimeSlot(String date, int start, int end, String meetingId, List<String> participantIPs) {
        this.date = date;
        this.start = start;
        this.end = end;
        this.meetingId = meetingId;
        this.participantIPs = participantIPs;
    }

    public boolean overlapsWith(String otherDate, int otherStart, int otherEnd) {
        if (!this.date.equals(otherDate)) return false;
        return !(otherEnd <= this.start || otherStart >= this.end);
    }
}
