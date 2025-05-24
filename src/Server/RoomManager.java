package Server;

import java.util.*;

public class RoomManager {
    // 假设有 3 个房间 GymA/B/C
    private List<String> roomList = Arrays.asList("GymA", "GymB", "GymC");

    // 使用 Map 存储已预约房间 key: "2025-05-25_14:00" → 已分配的房间名列表
    private Map<String, Set<String>> reserved = new HashMap<>();

    public synchronized boolean isRoomAvailable(String date, String time) {
        String key = date + "_" + time;
        Set<String> reservedRooms = reserved.getOrDefault(key, new HashSet<>());
        return reservedRooms.size() < roomList.size();
    }

    public synchronized String assignRoom(String date, String time) {
        String key = date + "_" + time;
        Set<String> reservedRooms = reserved.getOrDefault(key, new HashSet<>());

        for (String room : roomList) {
            if (!reservedRooms.contains(room)) {
                reservedRooms.add(room);
                reserved.put(key, reservedRooms);
                System.out.println(room);
                return room;
            }
        }

        return null; // 没有空房间（理论上不应该到这里）
    }

    public synchronized void reserveRoom(String date, String time) {
        assignRoom(date, time); // 简单调用
    }
}
