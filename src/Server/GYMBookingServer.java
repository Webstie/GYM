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

    private Map<String, MeetingStatus> meetingMap = new HashMap<>();
    private int meetingCounter = 1;

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





    public void handleMessage(String message, SocketAddress senderAddress) {
        if (message.startsWith("BOOK")) {
            BookingRequest request = parseBookingRequest(message, senderAddress);
            processBookingRequest(request);
        } else if (message.startsWith("ACCEPT") || message.startsWith("REJECT")) {
            processInviteResponse(message, senderAddress);
        } else {
            System.out.println("未知指令: " + message);
        }
    }
}
