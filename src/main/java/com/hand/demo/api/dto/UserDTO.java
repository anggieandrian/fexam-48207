package com.hand.demo.api.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;
import org.hzero.common.HZeroCacheKey;
import org.hzero.core.cache.CacheValue;
import org.hzero.core.cache.Cacheable;

@Getter
@Setter
public class UserDTO implements Cacheable {
    @ApiModelProperty("Unique User ID")
    private Long id;

    @ApiModelProperty("Full Name of the User")
    @CacheValue(
            key = HZeroCacheKey.USER,
            primaryKey = "id",
            searchKey = "realName",
            structure = CacheValue.DataStructure.MAP_OBJECT
        )
    private String realName;

    @ApiModelProperty("Employee Number")
    @CacheValue(
            key = HZeroCacheKey.USER,
            primaryKey = "id",
            searchKey = "loginName",
            structure = CacheValue.DataStructure.MAP_OBJECT
    )
    private String employeeNumber;

    @ApiModelProperty("Tenant Identifier")
    @CacheValue(
            key = HZeroCacheKey.USER,
            primaryKey = "id",
            searchKey = "tenantNum",
            structure = CacheValue.DataStructure.MAP_OBJECT
    )
    private String tenantNumber;
}