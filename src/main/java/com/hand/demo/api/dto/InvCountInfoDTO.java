package com.hand.demo.api.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter

public class  InvCountInfoDTO {

    private String totalErrorMsg;
    private String errorMsg;
    private String getsuccessList;

    @ApiModelProperty(value = "Verification passed list")
    private List<InvCountHeaderDTO> successList;

    @ApiModelProperty(value = "Verification failed list")
    private List<InvCountHeaderDTO> errorList;


}

