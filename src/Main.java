import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

public class Main {
    private static DatagramSocket clientSocket = null;
    private static final InetAddress IP = InetAddress.getLoopbackAddress();
    private static final int serverPort = 8888;
    public static void main(String[] args) throws SocketException {
        Sender sender = new Sender(clientSocket, serverPort, IP);
        Receiver receiver = new Receiver(clientSocket);
        sender.start();
        receiver.start();
    }
}
