package Server;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.*;

public class GYMBookingServer {

    public static void main(String[] args) throws Exception {
        int port = 9876;
        DatagramSocket socket = new DatagramSocket(port);

        GymBookingServer server = new GymBookingServer(socket);
        System.out.println("GBMS 启动，监听端口: " + port);

        // 启动接收线程
        Receiver receiver = new Receiver(socket, server::handleMessage);
        new Thread(receiver::receiveLoop).start();
    }
    public GymBookingServer(DatagramSocket socket) {
        this.socket = socket;
        this.sender = new Sender(socket);
        this.roomManager = new RoomManager();
    }


    private Map<String, MeetingStatus> meetingMap = new HashMap<>();
    private int meetingCounter = 1;

    public BookingRequest parseBookingRequest(String message, SocketAddress senderAddress) {
        try {
            // 示例格式：BOOK RQ#123 DATE:2025-05-25 TIME:14:00 TYPE:Basketball MIN:3 PARTICIPANTS:192.168.1.2,192.168.1.3,...
            String[] parts = message.split(" ");

            String requestId = parts[1].substring(3); // 去掉 "RQ#"
            String date = parts[2].split(":")[1];
            String time = parts[3].split(":")[1];
            String activity = parts[4].split(":")[1];
            int min = Integer.parseInt(parts[5].split(":")[1]);
            String[] ips = parts[6].split(":")[1].split(",");

            List<String> participantIPs = Arrays.asList(ips);
            String requesterIP = senderAddress.toString(); // 格式可能是 /192.168.1.2:xxxx，需要截取

            // 去掉斜杠和端口号，只取 IP
            if (requesterIP.startsWith("/")) {
                requesterIP = requesterIP.substring(1);
            }
            requesterIP = requesterIP.split(":")[0];

            return new BookingRequest(requestId, date, time, activity, participantIPs, min, requesterIP);
        } catch (Exception e) {
            System.out.println("解析 BOOK 请求失败: " + message);
            e.printStackTrace();
            return null;
        }
    }
    public void processBookingRequest(BookingRequest request) {
        if (!RoomManager.isRoomAvailable(request.date, request.time)) {
            // 无可用房间，发送 UNAVAILABLE
            String msg = "UNAVAILABLE RQ#" + request.requestId;
            Sender.sendMessage(msg, request.requesterIP);
            return;
        }

        // 分配会议编号
        String meetingId = "MT#" + (meetingCounter++);
        MeetingStatus status = new MeetingStatus(meetingId, request);
        meetingMap.put(meetingId, status);

        // 给所有参与者发 INVITE
        for (String ip : request.participantIPs) {
            String inviteMsg = String.format("INVITE %s DATE:%s TIME:%s TYPE:%s REQUESTER:%s",
                    meetingId, request.date, request.time, request.activityType, request.requesterIP);
            sender.sendMessage(inviteMsg, ip);
        }

        // 可选：启动一个等待响应的计时器线程（用于超时重传）
        startInviteResponseTimer(meetingId);
    }
    public void processInviteResponse(String message, SocketAddress senderAddress) {
        String[] parts = message.split(" ");
        if (parts.length < 2) return;

        String responseType = parts[0]; // ACCEPT or REJECT
        String meetingId = parts[1];    // MT#xxx

        MeetingStatus status = meetingMap.get(meetingId);
        if (status == null) {
            System.out.println("未知会议编号: " + meetingId);
            return;
        }

        // 提取参与者 IP
        String ip = senderAddress.toString().replace("/", "").split(":")[0];

        // 记录响应
        if (responseType.equals("ACCEPT")) {
            status.accepted.add(ip);
        } else if (responseType.equals("REJECT")) {
            status.rejected.add(ip);
        } else {
            System.out.println("无效响应类型: " + responseType);
            return;
        }
        private void finishMeetingDecision(MeetingStatus status) {
            BookingRequest req = status.request;
            String roomName = null;

            if (status.accepted.size() >= req.minParticipants) {
                // 预约成功
                roomManager.reserveRoom(req.date, req.time);
                roomName = roomManager.assignRoom();

                String confirmMsg = String.format(
                        "CONFIRM %s ROOM:%s PARTICIPANTS:%s",
                        status.meetingId,
                        roomName,
                        String.join(",", status.accepted)
                );

                for (String ip : status.accepted) {
                    sender.sendMessage(confirmMsg, ip);
                }
                sender.sendMessage(confirmMsg, req.requesterIP); // 发给请求者

            } else {
                // 预约失败，人数不足
                String cancelMsg = String.format(
                        "CANCEL %s REASON:Number of participants is lower than minimum required PARTICIPANTS:%s",
                        status.meetingId,
                        String.join(",", status.accepted)
                );

                for (String ip : status.accepted) {
                    sender.sendMessage(cancelMsg, ip);
                }
                sender.sendMessage(cancelMsg, req.requesterIP); // 发给请求者
            }

            // 清理状态
            meetingMap.remove(status.meetingId);
        }


        // 检查是否所有人都已回复
        int totalReplied = status.accepted.size() + status.rejected.size();
        int totalExpected = status.request.participantIPs.size();

        if (totalReplied >= totalExpected) {
            finishMeetingDecision(status);
        }
    }
    private void finishMeetingDecision(MeetingStatus status) {
        BookingRequest req = status.request;
        String roomName = null;

        if (status.accepted.size() >= req.minParticipants) {
            // 预约成功
            roomManager.reserveRoom(req.date, req.time);
            roomName = roomManager.assignRoom();

            String confirmMsg = String.format(
                    "CONFIRM %s ROOM:%s PARTICIPANTS:%s",
                    status.meetingId,
                    roomName,
                    String.join(",", status.accepted)
            );

            for (String ip : status.accepted) {
                sender.sendMessage(confirmMsg, ip);
            }
            sender.sendMessage(confirmMsg, req.requesterIP); // 发给请求者

        } else {
            // 预约失败，人数不足
            String cancelMsg = String.format(
                    "CANCEL %s REASON:Number of participants is lower than minimum required PARTICIPANTS:%s",
                    status.meetingId,
                    String.join(",", status.accepted)
            );

            for (String ip : status.accepted) {
                sender.sendMessage(cancelMsg, ip);
            }
            sender.sendMessage(cancelMsg, req.requesterIP); // 发给请求者
        }

        // 清理状态
        meetingMap.remove(status.meetingId);
    }
}
