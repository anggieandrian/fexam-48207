package com.hand.demo.api.controller.v1;

import com.hand.demo.api.dto.InvCountHeaderDTO;
import com.hand.demo.api.dto.InvCountInfoDTO;
import io.choerodon.core.domain.Page;
import io.choerodon.core.iam.ResourceLevel;
import io.choerodon.mybatis.pagehelper.annotation.SortDefault;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import io.choerodon.mybatis.pagehelper.domain.Sort;
import io.choerodon.swagger.annotation.Permission;
import io.swagger.annotations.ApiOperation;
import org.hzero.core.base.BaseController;
import org.hzero.core.util.Results;
import org.hzero.mybatis.helper.SecurityTokenHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.hand.demo.app.service.InvCountHeaderService;
import com.hand.demo.domain.entity.InvCountHeader;
import com.hand.demo.domain.repository.InvCountHeaderRepository;
import springfox.documentation.annotations.ApiIgnore;

import java.util.List;

/**
 * (InvCountHeader)表控制层
 *
 * @author Anggie A
 * @since 2024-12-19 16:56:45
 */

@RestController("invCountHeaderController.v1")
@RequestMapping("/v1/{organizationId}/inv-count-headers")
public class InvCountHeaderController extends BaseController {

    @Autowired
    private InvCountHeaderRepository invCountHeaderRepository;

    @Autowired
    private InvCountHeaderService invCountHeaderService;

    @ApiOperation(value = "列表")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @GetMapping
    public ResponseEntity<Page<InvCountHeader>> list(InvCountHeader invCountHeader, @PathVariable Long organizationId,
                                                     @ApiIgnore @SortDefault(value = InvCountHeader.FIELD_COUNT_HEADER_ID,
                                                             direction = Sort.Direction.DESC) PageRequest pageRequest) {
        Page<InvCountHeader> list = invCountHeaderService.selectList(pageRequest, invCountHeader);
        return Results.success(list);
    }

    @ApiOperation(value = "明细")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @GetMapping("/{countHeaderId}/detail")
    public ResponseEntity<InvCountHeader> detail(@PathVariable Long countHeaderId) {
        InvCountHeader invCountHeader = invCountHeaderRepository.selectByPrimary(countHeaderId);
        return Results.success(invCountHeader);
    }

    @ApiOperation(value = "创建或更新")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @PostMapping
    public ResponseEntity<List<InvCountHeader>> save(@PathVariable Long organizationId, @RequestBody List<InvCountHeader> invCountHeaders) {
        validObject(invCountHeaders);
        SecurityTokenHelper.validTokenIgnoreInsert(invCountHeaders);
        invCountHeaders.forEach(item -> item.setTenantId(organizationId));
        invCountHeaderService.saveData(invCountHeaders);
        return Results.success(invCountHeaders);
    }

    @ApiOperation(value = "Save Counting Order")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @PostMapping ("/order-save")
    public ResponseEntity<InvCountInfoDTO> orderSave(@PathVariable Long organizationId, @RequestBody List<InvCountHeaderDTO> orderSaveHeaders) {
        validObject(orderSaveHeaders);
        orderSaveHeaders.forEach(item -> item.setTenantId(organizationId));
        return Results.success(invCountHeaderService.orderSave(orderSaveHeaders));
    }

    @ApiOperation(value = "删除")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @DeleteMapping
    public ResponseEntity<?> remove(@RequestBody List<InvCountHeader> invCountHeaders) {
        SecurityTokenHelper.validToken(invCountHeaders);
        invCountHeaderRepository.batchDeleteByPrimaryKey(invCountHeaders);
        return Results.success();
    }

    @ApiOperation(value = "checkAndRemove")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @DeleteMapping("/checkAndRemove")
    public ResponseEntity<?> checkAndRemove(@PathVariable Long organizationId, @RequestBody List<InvCountHeaderDTO > invCountHeaders) {
        SecurityTokenHelper.validToken(invCountHeaders);
        invCountHeaders.forEach(header -> header.setTenantId(organizationId));
        invCountHeaderService.checkAndRemove(invCountHeaders);
        return Results.success();
    }

    @ApiOperation(value = "Execute Counting Order Verification")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @PostMapping("/order-execution")
    public ResponseEntity<InvCountInfoDTO> orderExecution(@PathVariable Long organizationId, @RequestBody List<InvCountHeaderDTO> invCountHeaders) {
        validObject(invCountHeaders); // Validasi input wajib
        SecurityTokenHelper.validTokenIgnoreInsert(invCountHeaders);
        InvCountInfoDTO executeResult = invCountHeaderService.orderExecution(invCountHeaders); // Panggil metode executeCheck dari service
        return Results.success(executeResult); // Kembalikan hasil validasi
    }
}

