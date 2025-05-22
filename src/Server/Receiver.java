package Server;

import java.io.IOException;
import java.net.*;
import java.util.function.BiConsumer;

public class Receiver {

    private DatagramSocket socket;
    private BiConsumer<String, SocketAddress> messageHandler;
    private boolean isRunning = false;

    public Receiver(DatagramSocket socket, BiConsumer<String, SocketAddress> messageHandler) throws SocketException {
        this.socket = socket;
        this.messageHandler = messageHandler;
        System.out.println("Server.Receiver initialized");
    }

    public void start() {
        isRunning = true;
        System.out.println("Server.Receiver has started");
    }

    public void stop() {
        isRunning = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
            System.out.println("Server.Receiver stopped");
        }
    }

    public void receiveLoop() {
        start(); // 标记 isRunning = true

        byte[] buffer = new byte[1024];
        while (isRunning && !socket.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String message = new String(packet.getData(), 0, packet.getLength());
                SocketAddress sender = packet.getSocketAddress();

                System.out.println("收到消息: " + message + " 来自 " + sender);

                // 将消息交给 GYMBookingServer 的 handleMessage 处理
                if (messageHandler != null) {
                    messageHandler.accept(message, sender);
                }
            } catch (SocketException e) {
                // Socket 被关闭时跳出循环
                if (isRunning) {
                    System.err.println("Socket 异常: " + e.getMessage());
                }
                break;
            } catch (IOException e) {
                System.err.println("接收数据失败: " + e.getMessage());
            }
        }
    }
}
