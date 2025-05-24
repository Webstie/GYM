package Server;

import java.io.IOException;
import java.net.*;

public class Sender {
    private DatagramSocket socket;

    public Sender(DatagramSocket socket) {
        this.socket = socket;
        System.out.println("Sender initialized");
    }

    /**
     * Send a message using a SocketAddress object (recommended method)
     */
    public boolean sendMessage(String message, SocketAddress address) {
        try {
            byte[] data = message.getBytes();
            DatagramPacket packet = new DatagramPacket(data, data.length, address);
            socket.send(packet);
            System.out.println("Message sent to " + address + ": " + message);
            return true;
        } catch (IOException e) {
            System.err.println("Failed to send message: " + e.getMessage());
            return false;
        }
    }

    /**
     * Optional: send a message using IP and port directly
     */
    public boolean sendMessage(String message, String ip, int port) {
        try {
            byte[] data = message.getBytes();
            InetAddress inetAddress = InetAddress.getByName(ip);
            DatagramPacket packet = new DatagramPacket(data, data.length, inetAddress, port);
            socket.send(packet);
            System.out.println("Message sent to " + ip + ":" + port + " -> " + message);
            return true;
        } catch (IOException e) {
            System.err.println("Failed to send message: " + e.getMessage());
            return false;
        }
    }
}
