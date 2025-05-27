package Server;

import java.net.*;
import java.util.*;

public class GYMBookingServer {

    private static final int MAX_RETRY = 3;
    private static final int RETRY_INTERVAL_MS = 3000;

    private DatagramSocket socket;
    private Sender sender;
    private RoomManager roomManager;
    private Receiver receiver;
    private Map<String, MeetingStatus> meetingMap = new HashMap<>();
    public Map<String, String> participantStatus = new HashMap<>();
    private int meetingCounter = 1;

    public static void main(String[] args) throws Exception {
        int port = 9876;
        DatagramSocket socket = new DatagramSocket(port);

        GYMBookingServer server = new GYMBookingServer(socket);
        System.out.println("GBMS started, listening on port: " + port);
        server.start();
    }

    public GYMBookingServer(DatagramSocket socket) {
        this.socket = socket;
        this.sender = new Sender(socket);
        this.roomManager = new RoomManager();
    }

    public void start() {
        try {
            this.receiver = new Receiver(socket, this::handleMessage);
            new Thread(receiver::receiveLoop).start();
        } catch (SocketException e) {
            System.err.println("Failed to start receiver: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void handleMessage(String message, SocketAddress senderAddress) {
        if (senderAddress.toString().contains("127.0.0.1:9876")) return;

        if (message.startsWith("BOOK")) {
            BookingRequest request = parseBookingRequest(message, senderAddress);
            if (request != null) {
                processBookingRequest(request);
            }
        } else if (message.startsWith("CANCEL")) {
            processCancelRequest(message);
        } else if (message.startsWith("ADD")) {
            processAddRequest(message, senderAddress);
        } else if (message.startsWith("ACCEPT") || message.startsWith("REJECT")) {
            processInviteResponse(message, senderAddress);
        } else {
            System.out.println("Received unknown message format: " + message);
        }
    }

    public void processAddRequest(String message, SocketAddress senderAddress) {
        String ip = senderAddress.toString();
        if (ip.startsWith("/")) {
            ip = ip.substring(1); // 去掉前导斜杠
        }
        String[] parts = message.trim().split(" ");
        String meetingId = parts[1];
        String room = RoomManager.getRoom(meetingId);
        MeetingStatus status = meetingMap.get(meetingId);
        if (status.getRejected().contains(ip)){
            String msg = String.format("CONFIRM %s %s",
                    meetingId, room);
            String hostMsg = String.format("ADDED %s %s", meetingId, senderAddress);
            sender.sendMessage(msg, senderAddress);
            sender.sendMessage(hostMsg, status.host);
        }
    }

    public BookingRequest parseBookingRequest(String message, SocketAddress senderAddress) {
        try {
            String[] parts = message.split(" ");
            String requestId = parts[1].substring(3);
            String date = parts[2].split(":")[1];
            String time = parts[3].split(":")[1];
            String activity = parts[4].split(":")[1];
            String ip = parts[5].substring(parts[5].indexOf(':') + 1);
            String[] ips = ip.split(",");
            int min = Integer.parseInt(parts[6].split(":")[1]);
            List<String> participantIPs = Arrays.asList(ips);

            String requesterIP = senderAddress.toString();
            if (requesterIP.startsWith("/")) requesterIP = requesterIP.substring(1);
            return new BookingRequest(requestId, date, time, activity, participantIPs, min, requesterIP);
        } catch (Exception e) {
            System.out.println("Failed to parse BOOK request: " + message);
            e.printStackTrace();
            return null;
        }
    }

    public void processBookingRequest(BookingRequest request) {
        if (!roomManager.isRoomAvailable(request.date, request.time)) {
            String msg = "UNAVAILABLE RQ#" + request.requestId;
            sender.sendMessage(msg, parseAddress(request.requesterIP));
            return;
        }

        String meetingId = "MT#" + (meetingCounter++);
        MeetingStatus status = new MeetingStatus(meetingId, request);
        status.nameHost(request.requesterIP);
        meetingMap.put(meetingId, status);

        for (String ip : request.participantIPs) {
            String msg = String.format("INVITE %s DATE:%s TIME:%s TYPE:%s REQUESTER:%s",
                    meetingId, request.date, request.time, request.activityType, request.requesterIP);
            sender.sendMessage(msg, parseAddress(ip));
        }

        startInviteResponseTimer(meetingId);
    }

    public void processCancelRequest(String message) {
        try {
            String[] parts = message.trim().split(" ");
            if (parts.length < 2 || !parts[0].equals("CANCEL")) return;

            String meetingId = parts[1];
            MeetingStatus status = meetingMap.get(meetingId);
            if (status == null || !status.finalized) return;

            System.out.println("Organizer requested cancellation for " + meetingId);

            // 通知所有已确认的参与者
            String cancelMsg = "CANCEL " + meetingId + " REASON:Cancelled by organizer";
            for (String ip : status.accepted) {
                sender.sendMessage(cancelMsg, parseAddress(ip));
            }

            // 移除会议室占用
            System.out.println("removing");
            RoomManager.removeRoom(meetingId);

            // 清除会议记录
            meetingMap.remove(meetingId);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public void processInviteResponse(String message, SocketAddress senderAddress) {
        String[] parts = message.split(" ");
        if (parts.length < 2) return;
        String responseType = parts[0];
        String meetingId = parts[1];

        MeetingStatus status = meetingMap.get(meetingId);
        if (status == null) return;

        String ip = senderAddress.toString();
        if (status.responded.contains(ip)) return;

        status.markResponse(ip, responseType.equals("ACCEPT"));

        if (status.allResponded()) {
            finishMeetingDecision(status);
        }
    }

    private void finishMeetingDecision(MeetingStatus status) {
        if (status.finalized) return;
        status.finalized = true;

        BookingRequest req = status.request;
        String roomName = null;

        if (status.accepted.size() >= req.minParticipants) {
            roomName = roomManager.assignRoom(req.date, req.time, status.meetingId);
            String msg = String.format("CONFIRM %s ROOM:%s PARTICIPANTS:%s",
                    status.meetingId, roomName, String.join(",", status.accepted));
            for (String ip : status.accepted) {
                sender.sendMessage(msg, parseAddress(ip));
            }
        } else {
            String msg = String.format("CANCEL %s REASON:Number of participants is lower than minimum required PARTICIPANTS:%s",
                    status.meetingId, String.join(",", status.accepted));
            for (String ip : status.accepted) {
                sender.sendMessage(msg, parseAddress(ip));
            }
            meetingMap.remove(status.meetingId);
        }
    }

    public void startInviteResponseTimer(String meetingId) {
        MeetingStatus status = meetingMap.get(meetingId);
        if (status == null) return;

        new Thread(() -> {
            try {
                while (true) {
                    // 等待下一轮前延迟一下（比如 300~500ms，给 finalize 完成时间）
                    Thread.sleep(500);

                    if (status.finalized) {
                        System.out.println("⛔ Meeting " + meetingId + " already finalized. Stopping retry loop.");
                        break;
                    }

                    if (status.allResponded()) {
                        System.out.println("✅ All responded for " + meetingId + ". Finishing decision.");
                        finishMeetingDecision(status);
                        break;
                    }

                    // 再真正等待重发间隔
                    Thread.sleep(RETRY_INTERVAL_MS);

                    for (String ip : status.request.participantIPs) {
                        if (status.responded.contains(ip)) continue;

                        int retry = status.retryCount.getOrDefault(ip, 0);
                        if (retry >= MAX_RETRY) {
                            System.out.println("⚠️ Max retries for " + ip + ". Marking as rejected.");
                            status.markResponse(ip, false);
                            continue;
                        }

                        String msg = String.format("INVITE %s DATE:%s TIME:%s TYPE:%s REQUESTER:%s",
                                status.meetingId, status.request.date, status.request.time,
                                status.request.activityType, status.request.requesterIP);
                        sender.sendMessage(msg, GYMBookingServer.parseAddress(ip));
                        status.retryCount.put(ip, retry + 1);
                    }
                }
            } catch (InterruptedException e) {
                System.err.println("❌ Retry thread interrupted for " + meetingId);
            }
        }).start();
    }

        public static InetSocketAddress parseAddress(String ip) {
        if (ip.startsWith("/")) {
                ip = ip.substring(1); // 去掉前导斜杠
        }
        String[] parts = ip.split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid IP:Port format: " + ip);
        }
        return new InetSocketAddress(parts[0], Integer.parseInt(parts[1]));
    }
}
