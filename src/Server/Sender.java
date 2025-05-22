package Server;

import java.io.IOException;
import java.net.*;

public class Sender {
    private DatagramSocket socket;

    public Sender(DatagramSocket socket) {
        this.socket = socket;
        System.out.println("Server.Sender initialized");
    }

    // 使用 SocketAddress 发送（推荐方式）
    public boolean sendMessage(String message, SocketAddress address) {
        try {
            byte[] data = message.getBytes();
            DatagramPacket packet = new DatagramPacket(data, data.length, address);
            socket.send(packet);
            System.out.println("发送消息到 " + address + ": " + message);
            return true;
        } catch (IOException e) {
            System.err.println("发送失败: " + e.getMessage());
            return false;
        }
    }

    // 可选：使用 IP + 端口发送
    public boolean sendMessage(String message, String ip, int port) {
        try {
            byte[] data = message.getBytes();
            InetAddress inetAddress = InetAddress.getByName(ip);
            DatagramPacket packet = new DatagramPacket(data, data.length, inetAddress, port);
            socket.send(packet);
            System.out.println("发送消息到 " + ip + ":" + port + " -> " + message);
            return true;
        } catch (IOException e) {
            System.err.println("发送失败: " + e.getMessage());
            return false;
        }
    }
}
