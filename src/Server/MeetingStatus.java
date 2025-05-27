package Server;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.*;

public class MeetingStatus {

    public volatile boolean finalized = false;

    public String meetingId;
    public BookingRequest request;

    public SocketAddress host;
    public Set<String> accepted = new HashSet<>();
    public Set<String> rejected = new HashSet<>();
    public Map<String, Integer> retryCount = new HashMap<>();
    public Set<String> responded = new HashSet<>();

    public Map<String, String> participantStatus = new HashMap<>();

    public long createTime;

    public MeetingStatus(String meetingId, BookingRequest request) {
        this.meetingId = meetingId;
        this.request = request;
        this.createTime = System.currentTimeMillis();

        for (String ip : request.participantIPs) {
            retryCount.put(ip, 0);
            participantStatus.put(ip, "PENDING");  // initialize everyone as PENDING
        }
    }

    public boolean allResponded() {
        return responded.size() >= request.participantIPs.size();
    }

    public void markResponse(String ip, boolean accept) {
        responded.add(ip);
        participantStatus.put(ip, accept ? "ACCEPTED" : "REJECTED");

        if (accept) accepted.add(ip);
        else rejected.add(ip);
    }
    public void nameHost(String host) {
        this.host = GYMBookingServer.parseAddress(host);
    }
    public ArrayList<String> getRejected() {
        return new ArrayList<>(rejected);
    }
}
