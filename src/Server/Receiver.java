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
        System.out.println("Receiver initialized");
    }

    /**
     * Starts the receiving loop
     */
    public void start() {
        isRunning = true;
        System.out.println("Receiver has started");
    }

    /**
     * Stops the receiver and closes the socket
     */
    public void stop() {
        isRunning = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
            System.out.println("Receiver stopped");
        }
    }

    /**
     * Continuously listens for incoming messages and passes them to the handler
     */
    public void receiveLoop() {
        start();

        byte[] buffer = new byte[1024];
        while (isRunning && !socket.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String message = new String(packet.getData(), 0, packet.getLength());
                SocketAddress sender = packet.getSocketAddress();

                System.out.println("Received message: " + message + " from " + sender);

                // Pass message to the handler (e.g., GYMBookingServer.handleMessage)
                if (messageHandler != null) {
                    messageHandler.accept(message, sender);
                }
            } catch (SocketException e) {
                if (isRunning) {
                    System.err.println("Socket error: " + e.getMessage());
                }
                break;
            } catch (IOException e) {
                System.err.println("Failed to receive message: " + e.getMessage());
            }
        }
    }
}
