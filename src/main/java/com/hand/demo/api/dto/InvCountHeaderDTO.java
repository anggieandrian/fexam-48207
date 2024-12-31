package com.hand.demo.api.dto;

import com.hand.demo.domain.entity.InvCountHeader;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;
import javax.persistence.Transient;
import java.util.Date;
import java.util.List;


@Setter
@Getter
public class InvCountHeaderDTO extends InvCountHeader {
    @ApiModelProperty(value = "Error Message")
    private String errorMsg;
    private String status;
    private List<UserDTO> counterList;
    private List<UserDTO> supervisorList;
    private Boolean isWmsWarehouse;
    private Date createdDate;

   @Transient
    private List<InvCountLineDTO> invCountLineDTOList;

    private List<SnapshotMaterialDTO> snapshotMaterialList; // Update type
    private List<SnapshotBatchDTO> snapshotBatchList;


    private String countStatusMeaning;
    private String countDimensionMeaning;
    private String countTypeMeaning;
    private String countModeMeaning;
    private String supervisor;



}
