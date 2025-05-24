package Server;

import java.util.*;

public class MeetingStatus {

    // Indicates whether the meeting has been finalized (confirmed or canceled)
    public volatile boolean finalized = false;

    public String meetingId;
    public BookingRequest request;

    public Set<String> accepted = new HashSet<>();
    public Set<String> rejected = new HashSet<>();
    public Map<String, Integer> retryCount = new HashMap<>();
    public Set<String> responded = new HashSet<>();

    public long createTime;

    /**
     * Constructs a MeetingStatus object for tracking responses and retries.
     */
    public MeetingStatus(String meetingId, BookingRequest request) {
        this.meetingId = meetingId;
        this.request = request;
        this.createTime = System.currentTimeMillis();

        // Initialize retry count for each participant
        for (String ip : request.participantIPs) {
            retryCount.put(ip, 0);
        }
    }

    /**
     * Checks if all participants have responded.
     */
    public boolean allResponded() {
        return responded.size() >= request.participantIPs.size();
    }

    /**
     * Records a participant's response.
     * @param ip participant IP address
     * @param accept true if ACCEPT, false if REJECT
     */
    public void markResponse(String ip, boolean accept) {
        responded.add(ip);
        if (accept) accepted.add(ip);
        else rejected.add(ip);
    }
}
