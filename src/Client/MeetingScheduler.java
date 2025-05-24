package Client;

import java.net.*;
import java.util.Arrays;
import java.util.List;

public class MeetingScheduler {

    public static void main(String[] args) throws Exception {
        int clientPort = 9877;
        int serverPort = 9876;
        InetAddress serverIP = InetAddress.getByName("127.0.0.1");

        // Create shared UDP socket
        DatagramSocket socket = new DatagramSocket(clientPort);

        // Initialize Sender and Receiver
        Sender sender = new Sender(socket, serverPort, serverIP);
        Receiver receiver = new Receiver(socket);

        sender.start();
        receiver.start();

        // Build the BOOK request message
        String requestID = "RQ#100";
        List<String> ips = Arrays.asList("127.0.0.1");  // Participant IPs
        String bookRequest = String.format(
                "BOOK %s DATE:2025-05-26 TIME:10:00 ACTIVITY:PingPong IPS:%s MIN:1",
                requestID, String.join(",", ips)
        );

        // Send booking request to server
        sender.sendMessage(bookRequest);

        // Start a background thread to listen and respond automatically
        new Thread(() -> {
            while (receiver.isRunning()) {
                String message = receiver.receiveMessage();
                if (message == null) continue;

                if (message.startsWith("INVITE")) {
                    // Extract meeting ID and send ACCEPT
                    String meetingId = message.split(" ")[1];
                    String acceptMsg = "ACCEPT " + meetingId;
                    sender.sendMessage(acceptMsg);
                }

                if (message.startsWith("CONFIRM") || message.startsWith("CANCEL")) {
                    // Print final result and terminate client
                    System.out.println("Meeting final result: " + message);
                    sender.stopRunning();
                    receiver.stopRunning();
                    break;
                }
            }
        }).start();
    }
}
