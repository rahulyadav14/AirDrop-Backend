package com.example.airdropclone.model;

import java.util.HashSet;
import java.util.Set;
import lombok.Data;

@Data
public class Room {
    private String id;
    private Set<String> participants = new HashSet<>();
    
    public Room(String id) {
        this.id = id;
    }
    
    public void addParticipant(String sessionId) {
        participants.add(sessionId);
    }
    
    public void removeParticipant(String sessionId) {
        participants.remove(sessionId);
    }
    
    public boolean isEmpty() {
        return participants.isEmpty();
    }
}