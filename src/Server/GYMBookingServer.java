package Server;

import java.net.*;
import java.util.*;

public class GYMBookingServer {

    private static final int MAX_RETRY = 3;
    private static final int RETRY_INTERVAL_MS = 10000;

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
//        Thread.sleep(10000);
//        server.makeRoomUnavailable("GymA");
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

        } else if (message.startsWith("WITHDRAW")) {
            processWithdrawRequest(message, senderAddress);
        } else {
            System.out.println("Received unknown message format: " + message);
        }
    }

    public void processWithdrawRequest(String message, SocketAddress senderAddress) {
        String[] parts = message.split(" ");
        String meetingId = parts[1];

        MeetingStatus status = meetingMap.get(meetingId);
        if (status == null || !status.finalized) return;

        String ip = senderAddress.toString().replace("/","");

        // 1. Remove from accepted/responded
        status.accepted.remove(ip);
        status.responded.remove(ip);
        status.participantStatus.replace(ip, "WITHDRAWN");
        String msgUser = String.format("WITHDRAWN %s IP:%s", meetingId, senderAddress);
        sender.sendMessage(msgUser, senderAddress);

        // 2. Notify host
        String notifyMsg = String.format("WITHDRAW_NOTIFY %s FROM:%s", meetingId, ip);
        sender.sendMessage(notifyMsg, status.host);

        // 3. Check if still valid
        if (status.accepted.size() >= status.request.minParticipants) return;

        System.out.println("⚠️ Not enough participants after withdrawal. Trying reinvite...");
        // 4. Re-invite all who never accepted or already rejected
        for (String candidate : status.request.participantIPs) {
            if (!status.accepted.contains(candidate) && !"WITHDRAWN".equals(status.participantStatus.get(candidate))) {
                // Reset retry & responded
                status.retryCount.put(candidate, 0);
                status.responded.remove(candidate);

                String msg = String.format("INVITE %s DATE:%s TIME:%s TYPE:%s REQUESTER:%s PARTICIPANT:%s",
                        meetingId, status.request.date, status.request.time,
                        status.request.activityType, status.request.requesterIP, status.request.participantIPs);
                sender.sendMessage(msg, parseAddress(candidate));
            }
        }
        status.finalized = false;
        // Let retry loop run again — don’t finalize here
    }

    public void processAddRequest(String message, SocketAddress senderAddress) {
        String ip = senderAddress.toString();
        if (ip.startsWith("/")) {
            ip = ip.substring(1); // remove leading slash
        }
        String[] parts = message.trim().split(" ");
        String meetingId = parts[1];
        String room = RoomManager.getRoom(meetingId);
        MeetingStatus status = meetingMap.get(meetingId);
        if (status.getRejected().contains(ip)) {
            String msg = String.format("CONFIRM %s %s", meetingId, room);
            String msgAdd = String.format("ADDED %s IP:%s", meetingId, senderAddress);
            String hostMsg = String.format("ADDED %s %s", meetingId, senderAddress);
            sender.sendMessage(msgAdd, senderAddress);
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
            String msg = String.format("INVITE %s DATE:%s TIME:%s TYPE:%s REQUESTER:%s PARTICIPANT:%s",
                    meetingId, request.date, request.time, request.activityType, request.requesterIP, request.participantIPs);
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

            // notify all accepted participants
            String cancelMsg = "CANCEL " + meetingId + " REASON:Cancelled by organizer";
            for (String ip : status.accepted) {
                sender.sendMessage(cancelMsg, parseAddress(ip));
            }

            // release assigned room
            System.out.println("Releasing room for " + meetingId);
            RoomManager.removeRoom(meetingId);

            // clear meeting record
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

        String ip = senderAddress.toString().replace("/","");
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
        String roomName;

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
                    // delay briefly before next round (e.g. 300~500ms to allow finalize)
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

                    // actual wait before resending INVITE
                    Thread.sleep(RETRY_INTERVAL_MS);

                    for (String ip : status.request.participantIPs) {
                        if (status.responded.contains(ip)) continue;

                        int retry = status.retryCount.getOrDefault(ip, 0);
                        if (retry >= MAX_RETRY) {
                            System.out.println("⚠️ Max retries for " + ip + ". Marking as rejected.");
                            status.markResponse(ip, false);
                            continue;
                        }

                        String msg = String.format("INVITE %s DATE:%s TIME:%s TYPE:%s REQUESTER:%s PARTICIPANTS:%s",
                                status.meetingId, status.request.date, status.request.time,
                                status.request.activityType, status.request.requesterIP, status.request.participantIPs);
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
            ip = ip.substring(1); // remove leading slash
        }
        String[] parts = ip.split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid IP:Port format: " + ip);
        }
        return new InetSocketAddress(parts[0], Integer.parseInt(parts[1]));
    }

    public void makeRoomUnavailable(String roomName) {
        String meetingId = RoomManager.makeUnavailableByRoomName(roomName);

        if (meetingId == null) {
            System.out.println("⚠️ No meeting is currently using room " + roomName);
            return;
        }

        MeetingStatus status = meetingMap.get(meetingId);
        if (status == null || !status.finalized) {
            System.out.println("⚠️ Meeting " + meetingId + " not found or not finalized");
            return;
        }

        String newRoom = roomManager.assignRoom(status.request.date, status.request.time, meetingId);

        if (newRoom != null) {
            String msg = String.format("ROOM_CHANGE %s NEW_ROOM#%s", meetingId, newRoom);
            for (String ip : status.accepted) {
                sender.sendMessage(msg, parseAddress(ip));
            }
            System.out.println("✅ ROOM_CHANGE sent for " + meetingId);
        } else {
            String cancelMsg = String.format("CANCEL %s REASON:Room unavailable and no alternatives", meetingId);
            for (String ip : status.accepted) {
                sender.sendMessage(cancelMsg, parseAddress(ip));
            }
            meetingMap.remove(meetingId);
            System.out.println("❌ Meeting " + meetingId + " canceled due to no replacement rooms");
        }
    }

}
