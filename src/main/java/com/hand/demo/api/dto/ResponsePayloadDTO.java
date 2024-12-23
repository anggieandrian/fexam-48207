package com.hand.demo.api.dto;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class ResponsePayloadDTO {
    private String returnStatus; // Jika tersedia langsung di root
    private String code;         // Jika tersedia langsung di root
    private String returnMsg;    // Jika tersedia langsung di root
    private Map<String, Object> body; // Body response sebagai map
    private String payload;

}

