// =============================
// MeetingScheduler.java
// =============================
package Client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;

import java.io.File;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class MeetingScheduler {

    private BiConsumer<String, Consumer<Boolean>> popupHandler;
    private Sender sender;
    private Receiver receiver;
    private DatagramSocket socket;

    private Consumer<String> messageListener;  // Callback for GUI log display

    public MeetingScheduler(int clientPort, int serverPort, InetAddress serverIP) throws Exception {
        this.socket = new DatagramSocket(clientPort);
        this.sender = new Sender(socket, serverPort, serverIP);
        this.receiver = new Receiver(socket);
    }

    public void start() {
        sender.start();
        receiver.start();

        new Thread(() -> {
            while (receiver.isRunning()) {
                String message = receiver.receiveMessage();
                if (message == null) continue;

                if (messageListener != null) {
                    messageListener.accept("📩 Received: " + message);
                }

                if (message.startsWith("INVITE")) {
                    String[] parts = message.split(" ");
                    if (parts.length >= 6) {
                        String meetingId = parts[1];
                        String date = extractValue(message, "DATE");
                        String timeRange = extractValue(message, "TIME");
                        String[] timeParts = timeRange.split("-");
                        int startTime = Integer.parseInt(timeParts[0]);
                        int endTime = Integer.parseInt(timeParts[1]);
                        String participantsRaw = extractValue(message, "PARTICIPANT");
                        System.out.println("SMMFMFMMFOBVIOUS");
                        System.out.println(participantsRaw);
                        if (participantsRaw.startsWith("[") && participantsRaw.endsWith("]")) {
                            participantsRaw = participantsRaw.substring(1, participantsRaw.length() - 1);
                        }
                        System.out.println(participantsRaw);

                        List<String> participantIPs = Arrays.asList(participantsRaw.split(","))
                                .stream()
                                .map(String::trim)
                                .toList();
                        System.out.println(participantsRaw);
                        ObjectMapper mapper = new ObjectMapper();
                        File scheduleFile = new File("src/Client/Schedule.Json");
                        List<TimeSlot> schedule = new ArrayList<>();
                        System.out.println(scheduleFile.exists());
                        System.out.println(scheduleFile.getAbsolutePath());
                        if (scheduleFile.exists()) {
                            try {
                                schedule = mapper.readValue(scheduleFile, new TypeReference<List<TimeSlot>>() {});
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }
                        System.out.println(schedule);

                        boolean hasConflict = false;
                        System.out.println(schedule.isEmpty());
                        for (TimeSlot slot : schedule) {
                            if (slot.overlapsWith(date, startTime, endTime)) {
                                hasConflict = true;
                                break;
                            }
                        }
                        System.out.println(hasConflict);

                        final List<TimeSlot> finalSchedule = schedule;

                        if (hasConflict) {
                            String rejectMsg = "REJECT " + meetingId;
                            sender.sendMessage(rejectMsg);
                            if (messageListener != null)
                                messageListener.accept("🔴 Auto-rejected (conflict): " + rejectMsg);
                        } else {
                            String info = "Date: " + date + "\nTime: " + startTime + "-" + endTime + "\nMeeting ID: " + meetingId;
                            if (popupHandler != null) {
                                List<String> finalParticipantIPs = participantIPs;
                                popupHandler.accept(info, (userAccepted) -> {
                                    if (userAccepted) {
                                        String acceptMsg = "ACCEPT " + meetingId;
                                        sender.sendMessage(acceptMsg);
                                        if (messageListener != null)
                                            messageListener.accept("🟢 User accepted: " + acceptMsg);

                                        finalSchedule.add(new TimeSlot(date, startTime, endTime, meetingId, finalParticipantIPs));
                                        System.out.println("Written into Json");
                                        try {
                                            mapper.writeValue(scheduleFile, finalSchedule);
                                        } catch (IOException e) {
                                            throw new RuntimeException("❌ Failed to write schedule.json", e);
                                        }
                                    } else {
                                        String rejectMsg = "REJECT " + meetingId;
                                        sender.sendMessage(rejectMsg);
                                        if (messageListener != null)
                                            messageListener.accept("🔴 User rejected: " + rejectMsg);
                                    }
                                });
                            }
                        }
                    }
                }
                if (message.startsWith("CANCEL")) {
                    if (messageListener != null)
                        messageListener.accept("🏁 Meeting cancelled: " + message);

                    // 👇 自动从 Schedule.Json 中移除该会议时间段
                    String meetingId = message.split(" ")[1];  // e.g. CANCEL MT#2

                    try {
                        File scheduleFile = new File("src/Client/Schedule.Json");
                        ObjectMapper mapper = new ObjectMapper();
                        List<TimeSlot> schedule = new ArrayList<>();

                        if (scheduleFile.exists()) {
                            schedule = mapper.readValue(scheduleFile, new TypeReference<List<TimeSlot>>() {});
                        }

                        // 过滤掉被取消的会议
                        List<TimeSlot> updated = new ArrayList<>();
                        for (TimeSlot slot : schedule) {
                            if (!slot.meetingId.equals(meetingId)) {
                                updated.add(slot);
                            }
                        }

                        mapper.writeValue(scheduleFile, updated);
                        if (messageListener != null)
                            messageListener.accept("🗑 Removed from schedule.json: " + meetingId);

                    } catch (Exception e) {
                        if (messageListener != null)
                            messageListener.accept("❌ Failed to update schedule.json: " + e.getMessage());
                    }
                }
                if (message.startsWith("ADDED")) {
                    String[] parts = message.split(" ");
                    String meetingId = parts[1];
                    String ip = extractValue(message, "IP");
                    if (ip.startsWith("/")) {
                        ip = ip.substring(1); // remove leading slash
                    }

                    try {
                        File scheduleFile = new File("src/Client/Schedule.Json");
                        ObjectMapper mapper = new ObjectMapper();
                        List<TimeSlot> schedule = new ArrayList<>();

                        if (scheduleFile.exists()) {
                            schedule = mapper.readValue(scheduleFile, new TypeReference<List<TimeSlot>>() {});
                        }

                        boolean updated = false;
                        for (TimeSlot slot : schedule) {
                            if (slot.meetingId.equals(meetingId)) {
                                if (!slot.participantIPs.contains(ip)) {
                                    slot.participantIPs.add(ip);
                                    updated = true;
                                }
                                break;
                            }
                        }

                        if (updated) {
                            mapper.writeValue(scheduleFile, schedule);
                            if (messageListener != null)
                                messageListener.accept("➕ ADDED updated participantIPs for " + meetingId);
                        }

                    } catch (Exception e) {
                        if (messageListener != null)
                            messageListener.accept("❌ Failed to update participantIPs on ADDED: " + e.getMessage());
                    }
                }
                if (message.startsWith("WITHDRAWN")) {
                    String[] parts = message.split(" ");
                    String meetingId = parts[1];
                    String ip = extractValue(message, "IP");
                    if (ip.startsWith("/")) {
                        ip = ip.substring(1); // remove leading slash
                    }


                    try {
                        File scheduleFile = new File("src/Client/Schedule.Json");
                        ObjectMapper mapper = new ObjectMapper();
                        List<TimeSlot> schedule = new ArrayList<>();

                        if (scheduleFile.exists()) {
                            schedule = mapper.readValue(scheduleFile, new TypeReference<List<TimeSlot>>() {});
                        }
                        boolean updated = false;
                        for (TimeSlot slot : schedule) {
                            System.out.println(slot.participantIPs);
                            System.out.println(ip);
                            if (slot.meetingId.equals(meetingId)) {
                                if (slot.participantIPs.remove(ip)) {

                                    updated = true;
                                }
                                break;
                            }
                        }

                        if (updated) {
                            mapper.writeValue(scheduleFile, schedule);
                            if (messageListener != null)
                                messageListener.accept("🗑 WITHDRAWN removed IP from " + meetingId);
                        }

                    } catch (Exception e) {
                        if (messageListener != null)
                            messageListener.accept("❌ Failed to update participantIPs on WITHDRAWN: " + e.getMessage());
                    }
                }
                if (message.startsWith("CONFIRM")) {
                    if (messageListener != null)
                        messageListener.accept("🏁 Meeting finalized: " + message);
                }
            }
        }).start();
    }

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

    public void sendCancelRequest(String meetingId) {
        String cancelMsg = "CANCEL " + meetingId;
        sender.sendMessage(cancelMsg);
        if (messageListener != null) {
            messageListener.accept("📤 Sent: " + cancelMsg);
        }
    }

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

    public void addMessageListener(Consumer<String> listener) {
        this.messageListener = listener;
    }

    public void stop() {
        sender.stopRunning();
        receiver.stopRunning();
        socket.close();
    }

    public static String extractValue(String message, String key) {
        String pattern = key + ":(\\[[^\\]]*\\]|[^ ]+)";
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(pattern).matcher(message);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    public void setPopupHandler(BiConsumer<String, Consumer<Boolean>> handler) {
        this.popupHandler = handler;
    }
}