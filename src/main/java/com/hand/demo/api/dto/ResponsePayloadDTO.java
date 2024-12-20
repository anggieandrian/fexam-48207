package com.hand.demo.api.dto;

import lombok.Data;

import java.util.Map;

@Data
public class ResponsePayloadDTO {
    private String returnStatus; // Jika tersedia langsung di root
    private String code;         // Jika tersedia langsung di root
    private String returnMsg;    // Jika tersedia langsung di root
    private Map<String, Object> body; // Body response sebagai map

    public String getCode() {
        if (body != null && body.containsKey("code")) {
            return body.get("code").toString();
        }
        return code; // Fallback jika code ada di root
    }

//
//    public String getReturnMsg() {
//        if (body != null && body.containsKey("returnMsg")) {
//            return body.get("returnMsg").toString();
//        }
//        return returnMsg; // Fallback jika returnMsg ada di root
//    }
//
//    public String getReturnStatus() {
//        if (body != null && body.containsKey("returnStatus")) {
//            return body.get("returnStatus").toString();
//        }
//        return returnStatus; // Fallback jika returnStatus ada di root
//    }
}

