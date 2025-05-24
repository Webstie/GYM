package Server;

import java.util.*;

public class RoomManager {
    // Example room list: GymA, GymB, GymC
    private List<String> roomList = Arrays.asList("GymA", "GymB", "GymC");

    // Stores reserved rooms for each time slot: key = "YYYY-MM-DD_HH:MM" → Set of room names
    private Map<String, Set<String>> reserved = new HashMap<>();

    /**
     * Checks if at least one room is available for the given date and time
     */
    public synchronized boolean isRoomAvailable(String date, String time) {
        String key = date + "_" + time;
        Set<String> reservedRooms = reserved.getOrDefault(key, new HashSet<>());
        return reservedRooms.size() < roomList.size();
    }

    /**
     * Assigns and reserves an available room for the given date and time.
     * Returns the room name, or null if no room is available.
     */
    public synchronized String assignRoom(String date, String time) {
        String key = date + "_" + time;
        Set<String> reservedRooms = reserved.getOrDefault(key, new HashSet<>());

        for (String room : roomList) {
            if (!reservedRooms.contains(room)) {
                reservedRooms.add(room);
                reserved.put(key, reservedRooms);
                return room;
            }
        }

        return null; // No available room (should not happen under normal conditions)
    }
}
