package com.example.airdropclone.service;

import com.example.airdropclone.model.Room;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RoomsService {
  private final Map<String, Room> rooms = new ConcurrentHashMap<>();
  private final Map<String, String> sessionToRoom = new ConcurrentHashMap<>();

  public Room createRoom(String roomId) {
    Room room = new Room(roomId);
    rooms.put(roomId, room);
    return room;
  }

  public Room getRoom(String roomId) {
    return rooms.get(roomId);
  }

  public void addParticipant(String roomId, String sessionId) {
    Room room = rooms.get(roomId);
    if (room != null) {
      room.addParticipant(sessionId);
      sessionToRoom.put(sessionId, roomId);
    }
  }

  public void removeParticipant(String sessionId) {
    String roomId = sessionToRoom.remove(sessionId);
    if (roomId != null) {
      Room room = rooms.get(roomId);
      if (room != null) {
        room.removeParticipant(sessionId);

        // Remove room if empty
        if (room.isEmpty()) {
          rooms.remove(roomId);
        }
      }
    }
  }

  public Room getRoomBySessionId(String sessionId) {
    String roomId = sessionToRoom.get(sessionId);
    if (roomId != null) {
      return rooms.get(roomId);
    }
    return null;
  }

  public String generatePeerId() {
    return UUID.randomUUID().toString();
  }
}
