package com.example.airdropclone.handler;

import com.example.airdropclone.model.Room;
import com.example.airdropclone.model.WebSocketMessage;
import com.example.airdropclone.service.RoomsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class WebSocketHandler extends TextWebSocketHandler {

  private final ObjectMapper objectMapper;
  private final RoomsService roomsService;
  private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

  public WebSocketHandler(ObjectMapper objectMapper, RoomsService roomsService) {
    this.objectMapper = objectMapper;
    this.roomsService = roomsService;
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) {
    log.info("WebSocket connection established: {}", session.getId());
    sessions.put(session.getId(), session);
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
    log.debug("Received message: {}", message.getPayload());
    WebSocketMessage webSocketMessage = objectMapper.readValue(message.getPayload(), WebSocketMessage.class);

    log.info("Received message type: {}", webSocketMessage.getType());

    switch (webSocketMessage.getType()) {
      case "create-room":
        handleCreateRoom(session, webSocketMessage);
        break;
      case "join-room":
        handleJoinRoom(session, webSocketMessage);
        break;
      case "offer":
        handleOffer(session, webSocketMessage);
        break;
      case "answer":
        handleAnswer(session, webSocketMessage);
        break;
      case "ice-candidate":
        handleIceCandidate(session, webSocketMessage);
        break;
      default:
        log.warn("Unknown message type: {}", webSocketMessage.getType());
    }
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    log.info("WebSocket connection closed: {}", session.getId());

    // Get the room for this session
    Room room = roomsService.getRoomBySessionId(session.getId());

    if (room != null) {
      // Notify other participants that this peer has left
      for (String participantId : room.getParticipants()) {
        if (!participantId.equals(session.getId())) {
          WebSocketSession participantSession = sessions.get(participantId);
          if (participantSession != null && participantSession.isOpen()) {
            try {
              WebSocketMessage leaveMessage = WebSocketMessage.builder()
                      .type("peer-left")
                      .from(session.getId())
                      .roomId(room.getId())
                      .build();

              sendMessage(participantSession, leaveMessage);
            } catch (IOException e) {
              log.error("Error sending peer-left message", e);
            }
          }
        }
      }
    }

    roomsService.removeParticipant(session.getId());
    sessions.remove(session.getId());
  }

  private void handleCreateRoom(WebSocketSession session, WebSocketMessage message) throws IOException {
    String roomId = message.getRoomId();
    String peerId = roomsService.generatePeerId();

    // Create room
    Room room = roomsService.createRoom(roomId);

    // Add participant to room
    roomsService.addParticipant(roomId, session.getId());

    // Send response
    WebSocketMessage response = WebSocketMessage.builder()
            .type("room-created")
            .roomId(roomId)
            .peerId(peerId)
            .build();

    sendMessage(session, response);

    log.info("Room created: {}", roomId);
  }

  private void handleJoinRoom(WebSocketSession session, WebSocketMessage message) throws IOException {
    String roomId = message.getRoomId();
    String peerId = roomsService.generatePeerId();

    // Get room
    Room room = roomsService.getRoom(roomId);

    if (room == null) {
      // Room doesn't exist
      WebSocketMessage response = WebSocketMessage.builder()
              .type("error")
              .data("Room doesn't exist")
              .build();

      sendMessage(session, response);
      return;
    }

    // Add participant to room
    roomsService.addParticipant(roomId, session.getId());

    // Send response to joining peer
    WebSocketMessage joinResponse = WebSocketMessage.builder()
            .type("room-joined")
            .roomId(roomId)
            .peerId(peerId)
            .build();

    sendMessage(session, joinResponse);

    // Notify existing participants about the new peer
    for (String participantId : room.getParticipants()) {
      if (!participantId.equals(session.getId())) {
        WebSocketSession participantSession = sessions.get(participantId);

        if (participantSession != null && participantSession.isOpen()) {
          // Notify existing participant about new peer
          WebSocketMessage newPeerMessage = WebSocketMessage.builder()
                  .type("new-peer")
                  .from(session.getId())
                  .roomId(roomId)
                  .build();

          sendMessage(participantSession, newPeerMessage);

          // Notify new peer about existing participant
          WebSocketMessage existingPeerMessage = WebSocketMessage.builder()
                  .type("new-peer")
                  .from(participantId)
                  .roomId(roomId)
                  .build();

          sendMessage(session, existingPeerMessage);
        }
      }
    }

    log.info("Peer joined room: {}", roomId);
  }

  private void handleOffer(WebSocketSession session, WebSocketMessage message) throws IOException {
    String to = message.getTo();
    WebSocketSession recipient = sessions.get(to);

    if (recipient != null && recipient.isOpen()) {
      WebSocketMessage offerMessage = WebSocketMessage.builder()
              .type("offer")
              .from(session.getId())
              .to(to)
              .data(message.getData())
              .roomId(message.getRoomId())
              .build();

      sendMessage(recipient, offerMessage);
    }
  }

  private void handleAnswer(WebSocketSession session, WebSocketMessage message) throws IOException {
    String to = message.getTo();
    WebSocketSession recipient = sessions.get(to);

    if (recipient != null && recipient.isOpen()) {
      WebSocketMessage answerMessage = WebSocketMessage.builder()
              .type("answer")
              .from(session.getId())
              .to(to)
              .data(message.getData())
              .roomId(message.getRoomId())
              .build();

      sendMessage(recipient, answerMessage);
    }
  }

  private void handleIceCandidate(WebSocketSession session, WebSocketMessage message) throws IOException {
    String to = message.getTo();
    WebSocketSession recipient = sessions.get(to);

    if (recipient != null && recipient.isOpen()) {
      WebSocketMessage iceCandidateMessage = WebSocketMessage.builder()
              .type("ice-candidate")
              .from(session.getId())
              .to(to)
              .data(message.getData())
              .roomId(message.getRoomId())
              .build();

      sendMessage(recipient, iceCandidateMessage);
    }
  }

  private void sendMessage(WebSocketSession session, WebSocketMessage message) throws IOException {
    try {
      String jsonMessage = objectMapper.writeValueAsString(message);
      session.sendMessage(new TextMessage(jsonMessage));
    } catch (IOException e) {
      log.error("Error sending message: {}", e.getMessage());
      throw e;
    }
  }

}
