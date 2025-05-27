package Server;

import java.util.*;

public class RoomManager {
    // Static room list
    private static final List<String> roomList = Arrays.asList("GymA", "GymB", "GymC");

    // key = meetingId, value = assigned room
    private static final Map<String, String> meetingToRoom = new HashMap<>();

    /**
     * Checks if at least one room is available
     */
    public synchronized boolean isRoomAvailable(String date, String time) {
        return meetingToRoom.size() < roomList.size();
    }

    /**
     * Assigns and reserves an available room. Ignores date/time, keeps signature for compatibility.
     */
    public synchronized String assignRoom(String date, String time, String meetingId) {
        for (String room : roomList) {
            if (!meetingToRoom.containsValue(room)) {
                meetingToRoom.put(meetingId, room);
                System.out.println("Assigned room " + room + " to " + meetingId);
                return room;
            }
        }
        return null;
    }

    /**
     * Releases the room assigned to the meeting
     */
    public static synchronized void removeRoom(String meetingId) {
        if (!meetingToRoom.containsKey(meetingId)) return;
        String room = meetingToRoom.remove(meetingId);
        System.out.println("Room " + room + " released from meeting " + meetingId);
    }

    /**
     * Optional: get room by meeting ID
     */
    public static synchronized String getRoom(String meetingId) {
        return meetingToRoom.get(meetingId);
    }
}
