package Server;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class Sender {

    private DatagramSocket clientSocket;
    private int serverPort;
    private InetAddress serverIP;
    private boolean isRunning = false;

    public Sender(DatagramSocket clientSocket, int serverPort, InetAddress serverIP) {
        this.clientSocket = clientSocket;
        this.serverPort = serverPort;
        this.serverIP = serverIP;
        System.out.println("Server.Sender initialized on port " + serverIP + " " + serverPort);
    }

    public void start() {
        isRunning = true;
    }

    public boolean isRunning() {
        return isRunning;
    }

    public void stopRunning() {
        isRunning = false;
    }

    /**
     * Sends a string message to the server
     * @param message The string to send
     * @return true if the message was sent successfully, false otherwise
     */
    public boolean sendMessage(String message) {
        if (!isRunning) {
            return false;
        }

        try {
            byte[] buffer = message.getBytes();
            DatagramPacket sendPacket = new DatagramPacket(buffer, buffer.length, serverIP, serverPort);
            clientSocket.send(sendPacket);
            System.out.println("Message sent: " + message);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
}
 