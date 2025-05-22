package Server;

import java.util.List;

public class BookingRequest {
    public String requestId;
    public String date;
    public String time;
    public String activityType;
    public List<String> participantIPs;
    public int minParticipants;
    public String requesterIP;

    public BookingRequest(String requestId, String date, String time, String activityType,
                          List<String> participantIPs, int minParticipants, String requesterIP) {
        this.requestId = requestId;
        this.date = date;
        this.time = time;
        this.activityType = activityType;
        this.participantIPs = participantIPs;
        this.minParticipants = minParticipants;
        this.requesterIP = requesterIP;
    }
}