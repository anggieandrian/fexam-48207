package com.hand.demo.api.dto;

import com.hand.demo.domain.entity.InvCountHeader;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Transient;
import java.util.Date;
import java.util.List;
import java.util.Map;


@Setter
@Getter
public class InvCountHeaderDTO extends InvCountHeader {
    @ApiModelProperty(value = "Error Message")
    private String errorMsg;
    private String status;
    private List<UserDTO> counterList;
    private String supervisorList;
    private String snapshotMaterialList;
    private String snapshotBatchList;
    private Boolean isWmsWarehouse;
    private Date createdDate;


   @Transient
    private List<InvCountLineDTO> invCountLineDTOList;

    private String countStatusMeaning;
    private String countDimensionMeaning;
    private String countTypeMeaning;
    private String countModeMeaning;



}
