package Server;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MeetingStatus {
    public String meetingId;
    public GYMBookingServer.BookingRequest request;
    public Set<String> accepted = new HashSet<>();
    public Set<String> rejected = new HashSet<>();
    public Map<String, Integer> retryCount = new HashMap<>();
    public long createTime;

    public MeetingStatus(String meetingId, GYMBookingServer.BookingRequest request) {
        this.meetingId = meetingId;
        this.request = request;
        this.createTime = System.currentTimeMillis();
        for (String ip : request.participantIPs) {
            retryCount.put(ip, 0); // 初始化重传次数
        }
    }
}
