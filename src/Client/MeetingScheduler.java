package Client;

import java.net.*;
import java.util.List;
import java.util.function.Consumer;

public class MeetingScheduler {

    private Sender sender;
    private Receiver receiver;
    private DatagramSocket socket;

    private Consumer<String> messageListener;  // 回调接口，用于 GUI 显示日志

    /**
     * 初始化调度器
     * @param clientPort 客户端端口（如 9877）
     * @param serverPort 服务器端口（如 9876）
     * @param serverIP   服务器 IP 地址
     */
    public MeetingScheduler(int clientPort, int serverPort, InetAddress serverIP) throws Exception {
        this.socket = new DatagramSocket(clientPort);
        this.sender = new Sender(socket, serverPort, serverIP);
        this.receiver = new Receiver(socket);
    }

    /**
     * 启动发送器和接收器，并自动监听处理响应
     */
    public void start() {
        sender.start();
        receiver.start();

        // 启动后台线程监听来自服务器的消息
        new Thread(() -> {
            while (receiver.isRunning()) {
                String message = receiver.receiveMessage();
                if (message == null) continue;

                // 通知 GUI（或日志系统）
                if (messageListener != null) {
                    messageListener.accept("📩 收到: " + message);
                }

                // 自动响应 INVITE
                if (message.startsWith("INVITE")) {
                    String[] parts = message.split(" ");
                    if (parts.length >= 2) {
                        String meetingId = parts[1];
                        String acceptMsg = "ACCEPT " + meetingId;
                        sender.sendMessage(acceptMsg);
                        if (messageListener != null)
                            messageListener.accept("🟢 已自动发送: " + acceptMsg);
                    }
                }

                // 如果是最终消息
                if (message.startsWith("CONFIRM") || message.startsWith("CANCEL")) {
                    if (messageListener != null)
                        messageListener.accept("🏁 会议完成: " + message);
                    sender.stopRunning();
                    receiver.stopRunning();
                    break;
                }
            }
        }).start();
    }

    /**
     * 发送 BOOK 预定请求
     */
    public void sendBookRequest(String requestID, String date, String time,
                                String activity, List<String> ips, int min) {
        String bookRequest = String.format(
                "BOOK %s DATE:%s TIME:%s ACTIVITY:%s IPS:%s MIN:%d",
                requestID, date, time, activity, String.join(",", ips), min
        );
        sender.sendMessage(bookRequest);
        if (messageListener != null)
            messageListener.accept("📤 已发送: " + bookRequest);
    }

    /**
     * 设置监听器，将内部消息回传给 GUI 控制台或其他输出逻辑
     */
    public void addMessageListener(Consumer<String> listener) {
        this.messageListener = listener;
    }

    public void stop() {
        sender.stopRunning();
        receiver.stopRunning();
        socket.close();
    }
}
