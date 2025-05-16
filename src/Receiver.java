import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

public class Receiver {

    private DatagramSocket serverSocket;
    private boolean isRunning = false;

    public Receiver(DatagramSocket serverSocket) throws SocketException {
        this.serverSocket = serverSocket;
        System.out.println("Receiver initialized");
    }

    public void start() {
        isRunning = true;
        System.out.println("Receiver has started");
    }

    public boolean isRunning() {
        return isRunning;
    }

    public void stopRunning() {
        isRunning = false;
        if (!serverSocket.isClosed()) {
            serverSocket.close();
        }
    }

    /**
     * Receives a single message if available and prints it to the terminal
     * @return The received message as a string, or null if no message was available
     */
    public String receiveMessage() {
        if (!isRunning || serverSocket.isClosed()) {
            return null;
        }

        try {
            byte[] receiveData = new byte[1024];
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);

            // Set a short timeout to make this non-blocking
            serverSocket.setSoTimeout(10);
            serverSocket.receive(receivePacket);

            // Extract the message as a string
            String message = new String(receivePacket.getData(), 0, receivePacket.getLength());

            // Print the message to the terminal
            System.out.println("Message received from " + receivePacket.getSocketAddress() + ": " + message);

            return message;
        } catch (IOException e) {
            // Timeout or socket closed - this is expected behavior for non-blocking
            return null;
        }
    }

    /**
     * Blocking call that continuously receives messages and prints them to the terminal
     * until stopRunning() is called
     */
    public void receiveLoop() {
        start();

        while (isRunning && !serverSocket.isClosed()) {
            try {
                byte[] receiveData = new byte[1024];
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);

                // This will block until a packet is received
                serverSocket.receive(receivePacket);

                // Extract the message as a string
                String message = new String(receivePacket.getData(), 0, receivePacket.getLength());

                // Print the message to the terminal
                System.out.println("Message received from " + receivePacket.getSocketAddress() + ": " + message);
            } catch (IOException e) {
                if (isRunning) {
                    e.printStackTrace();
                }
                // If socket is closed or not running, break out of the loop
                if (serverSocket.isClosed() || !isRunning) {
                    break;
                }
            }
        }

        System.out.println("Receiver stopped");
    }
}
