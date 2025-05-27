package Client;

import java.net.*;
import java.util.List;
import java.util.function.Consumer;

public class MeetingScheduler {

    private Sender sender;
    private Receiver receiver;
    private DatagramSocket socket;

    private Consumer<String> messageListener;  // Callback for GUI log display

    /**
     * Initialize the scheduler
     * @param clientPort Client port (e.g., 9877)
     * @param serverPort Server port (e.g., 9876)
     * @param serverIP   Server IP address
     */
    public MeetingScheduler(int clientPort, int serverPort, InetAddress serverIP) throws Exception {
        this.socket = new DatagramSocket(clientPort);
        this.sender = new Sender(socket, serverPort, serverIP);
        this.receiver = new Receiver(socket);
    }

    /**
     * Start sender and receiver, and begin listening for responses
     */
    public void start() {
        sender.start();
        receiver.start();

        // Background thread to handle incoming messages
        new Thread(() -> {
            while (receiver.isRunning()) {
                String message = receiver.receiveMessage();
                if (message == null) continue;

                // Notify GUI or logging system
                if (messageListener != null) {
                    messageListener.accept("📩 Received: " + message);
                }

                // Auto-respond to INVITE
                if (message.startsWith("INVITE")) {
                    String[] parts = message.split(" ");
                    if (parts.length >= 2) {
                        String meetingId = parts[1];
                        String acceptMsg = "ACCEPT " + meetingId;
                        sender.sendMessage(acceptMsg);
                        if (messageListener != null)
                            messageListener.accept("🟢 Auto-sent: " + acceptMsg);
                    }
                }

                // If final message received
                if (message.startsWith("CONFIRM") || message.startsWith("CANCEL")) {
                    if (messageListener != null)
                        messageListener.accept("🏁 Meeting finalized: " + message);
//                    sender.stopRunning();
//                    receiver.stopRunning();
                }
            }
        }).start();
    }

    /**
     * Send BOOK request
     */
    public void sendBookRequest(String requestID, String date, String time,
                                String activity, List<String> ips, int min) {
        String bookRequest = String.format(
                "BOOK %s DATE:%s TIME:%s ACTIVITY:%s IPS:%s MIN:%d",
                requestID, date, time, activity, String.join(",", ips), min
        );
        sender.sendMessage(bookRequest);
        if (messageListener != null)
            messageListener.accept("📤 Sent: " + bookRequest);
    }

    /**
     * Send CANCEL request
     */
    public void sendCancelRequest(String meetingId) {
        String cancelMsg = "CANCEL " + meetingId;
        sender.sendMessage(cancelMsg);
        if (messageListener != null) {
            messageListener.accept("📤 Sent: " + cancelMsg);
        }
    }

    /**
     * Send ADD request
     */
    public void sendAddRequest(String meetingId) {
        String addMsg = "ADD " + meetingId;
        sender.sendMessage(addMsg);
        if (messageListener != null) {
            messageListener.accept("📤 Sent: " + addMsg);
        }
    }

    public void sendWithdrawRequest(String meetingId) {
        String withdrawMsg = "WITHDRAW " + meetingId;
        sender.sendMessage(withdrawMsg);
        if (messageListener != null) {
            messageListener.accept("📤 Sent: " + withdrawMsg);
        }
    }

    /**
     * Set listener for passing messages back to GUI console or external output
     */
    public void addMessageListener(Consumer<String> listener) {
        this.messageListener = listener;
    }

    /**
     * Stop sender and receiver and close the socket
     */
    public void stop() {
        sender.stopRunning();
        receiver.stopRunning();
        socket.close();
    }
}
