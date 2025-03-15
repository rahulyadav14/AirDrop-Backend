package com.example.airdropclone.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebSocketMessage {

  private String type;

  private String from;

  private String to;

  private Object data;

  private String roomId;

  private String peerId;
}
