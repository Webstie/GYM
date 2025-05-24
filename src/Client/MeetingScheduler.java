package Client;

import java.net.*;
import java.util.Arrays;
import java.util.List;

public class MeetingScheduler {

    public static void main(String[] args) throws Exception {
        int clientPort = 9877;
        int serverPort = 9876;
        InetAddress serverIP = InetAddress.getByName("127.0.0.1");

        // 创建共享 socket
        DatagramSocket socket = new DatagramSocket(clientPort);

        // 初始化 Sender 和 Receiver
        Sender sender = new Sender(socket, serverPort, serverIP);
        Receiver receiver = new Receiver(socket);

        sender.start();
        receiver.start();

        // 构造请求消息
        String requestID = "RQ#100";
        List<String> ips = Arrays.asList("127.0.0.1");
        String bookRequest = String.format(
                "BOOK %s DATE:2025-05-26 TIME:10:00 ACTIVITY:PingPong IPS:%s MIN:1",
                requestID, String.join(",", ips)
        );

        sender.sendMessage(bookRequest);

        // 启动接收线程（自动处理 INVITE，自动回复 ACCEPT）
        new Thread(() -> {
            while (receiver.isRunning()) {
                String message = receiver.receiveMessage();
                if (message == null) continue;

                if (message.startsWith("INVITE")) {
                    String meetingId = message.split(" ")[1];  // 正确提取 MT#2、MT#3……
                    String acceptMsg = "ACCEPT " + meetingId;
                    sender.sendMessage(acceptMsg);

                }

                if (message.startsWith("CONFIRM") || message.startsWith("CANCEL")) {
                    System.out.println("Meeting final result: " + message);
                    sender.stopRunning();
                    receiver.stopRunning();
                    break;
                }
            }
        }).start();
    }
}
