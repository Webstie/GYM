package Server;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import java.util.*;

public class MeetingStatus {
    public volatile boolean finalized = false;
    public String meetingId;
    public BookingRequest request;
    public Set<String> accepted = new HashSet<>();
    public Set<String> rejected = new HashSet<>();
    public Map<String, Integer> retryCount = new HashMap<>();
    public Set<String> responded = new HashSet<>();

    public long createTime;

    public MeetingStatus(String meetingId, BookingRequest request) {
        this.meetingId = meetingId;
        this.request = request;
        this.createTime = System.currentTimeMillis();

        for (String ip : request.participantIPs) {
            retryCount.put(ip, 0); // 每个参与者的重试次数初始化为 0
        }
    }

    public boolean allResponded() {
        return responded.size() >= request.participantIPs.size();
    }

    public void markResponse(String ip, boolean accept) {
        responded.add(ip);
        if (accept) accepted.add(ip);
        else rejected.add(ip);
    }
}

