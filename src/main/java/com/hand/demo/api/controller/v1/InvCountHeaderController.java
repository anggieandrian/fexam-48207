package com.hand.demo.api.controller.v1;

import com.hand.demo.api.dto.InvCountHeaderDTO;
import com.hand.demo.api.dto.InvCountInfoDTO;
import com.hand.demo.api.dto.WorkFlowEventDTO;
import io.choerodon.core.domain.Page;
import io.choerodon.core.iam.ResourceLevel;
import io.choerodon.mybatis.pagehelper.annotation.SortDefault;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import io.choerodon.mybatis.pagehelper.domain.Sort;
import io.choerodon.swagger.annotation.Permission;
import io.swagger.annotations.ApiOperation;
import org.hzero.boot.platform.lov.annotation.ProcessLovValue;
import org.hzero.core.base.BaseConstants;
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

import javax.validation.Valid;
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


    /**
     * Mendapatkan daftar header penghitungan dengan kemampuan paging dan sorting.
     */
    @ApiOperation(value = "LIST")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @ProcessLovValue(targetField = BaseConstants.FIELD_BODY)
    @GetMapping
    public ResponseEntity<Page<InvCountHeaderDTO>> list(InvCountHeaderDTO invCountHeaderDTO, @PathVariable Long organizationId,
                                                     @ApiIgnore @SortDefault(value = InvCountHeader.FIELD_COUNT_HEADER_ID, // TODO update sortD
                                                             direction = Sort.Direction.DESC) PageRequest pageRequest) {
        Page<InvCountHeaderDTO> list = invCountHeaderService.list(pageRequest, invCountHeaderDTO);
        return Results.success(list);
    }

    /**
     * Mendapatkan detail spesifik header penghitungan berdasarkan ID.
     */
    @ApiOperation(value = "Details")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @ProcessLovValue(targetField = BaseConstants.FIELD_BODY)
    @GetMapping("/{countHeaderId}/detail")
    public ResponseEntity<InvCountHeader> detail(@PathVariable Long countHeaderId) {
        InvCountHeader invCountHeader = invCountHeaderService.detail(countHeaderId);
        return Results.success(invCountHeader);
    }

    /**
     * Membuat atau memperbarui header penghitungan dalam batch.
     */
    @ApiOperation(value = "创建或更新")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @PostMapping
    public ResponseEntity<List<InvCountHeader>> save(@PathVariable Long organizationId, @RequestBody List<InvCountHeader> invCountHeaders) {validObject(invCountHeaders);
        SecurityTokenHelper.validTokenIgnoreInsert(invCountHeaders);
        invCountHeaders.forEach(item -> item.setTenantId(organizationId));
        invCountHeaderService.saveData(invCountHeaders);
        return Results.success(invCountHeaders);
    }

    /**
     * Menyimpan urutan penghitungan dengan validasi dan logika bisnis yang diperlukan.
     */
    @ApiOperation(value = "Save Counting Order")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @PostMapping ("/order-save")
    public ResponseEntity<InvCountInfoDTO> orderSave(@PathVariable Long organizationId, @RequestBody List<InvCountHeaderDTO> orderSaveHeaders) {
        SecurityTokenHelper.validTokenIgnoreInsert(orderSaveHeaders);
        validList(orderSaveHeaders, InvCountHeader.validateCreate.class); // seharusnya pakai ini hubungkana tabel header dengan line agar bisa terbaca untuk keduanya
        orderSaveHeaders.forEach(item -> item.setTenantId(organizationId));
        return Results.success(invCountHeaderService.orderSave(orderSaveHeaders));
    }

    /**
     * Menghapus header penghitungan berdasarkan token keamanan.
     */
    @ApiOperation(value = "删除")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @DeleteMapping
    public ResponseEntity<?> remove(@RequestBody List<InvCountHeader> invCountHeaders) {
        SecurityTokenHelper.validToken(invCountHeaders);
        invCountHeaderRepository.batchDeleteByPrimaryKey(invCountHeaders);
        return Results.success();
    }

    /**
     * Memeriksa kondisi tertentu sebelum menghapus header penghitungan.
     */
    @ApiOperation(value = "Check and Remove InvCount")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @DeleteMapping("/checkAndRemove")
    public ResponseEntity<?> checkAndRemove(@PathVariable Long organizationId, @RequestBody List<InvCountHeaderDTO > invCountHeaders) {
        SecurityTokenHelper.validToken(invCountHeaders); // Validasi token keamanan pada daftar header
        // todo ga perlua da forEach
        invCountHeaders.forEach(header -> header.setTenantId(organizationId)); // Set tenantId untuk setiap header berdasarkan organizationId
        invCountHeaderService.checkAndRemove(invCountHeaders); // Panggil service untuk memproses penghapusan header
        return Results.success();// Kembalikan respons berhasil
    }

    /**
     * Melakukan verifikasi dan eksekusi atas urutan penghitungan.
     */
    @ApiOperation(value = "Execute Counting Order Verification")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @PostMapping("/order-execution")
    public ResponseEntity<InvCountInfoDTO> orderExecution(@PathVariable Long organizationId, @RequestBody List<InvCountHeaderDTO> invCountHeaders) {
        validList(invCountHeaders); // Validasi input wajib
        SecurityTokenHelper.validTokenIgnoreInsert(invCountHeaders);
        InvCountInfoDTO execute = invCountHeaderService.orderExecution(invCountHeaders); // Panggil metode executeCheck dari service
        return Results.success(execute); // Kembalikan hasil validasi
    }

    /**
     * Menyinkronkan hasil penghitungan dengan sistem atau database eksternal.
     */
    @ApiOperation(value = "Register External Interface")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @PostMapping("/count-result-sync")
    public ResponseEntity<InvCountHeaderDTO> countResultSync(@PathVariable Long organizationId, @RequestBody InvCountHeaderDTO invCountHeaders) {
        validObject(invCountHeaders);// Validasi input wajib
        InvCountHeaderDTO resultSync = invCountHeaderService.countResultSync(invCountHeaders);// Panggil service untuk proses sinkronisasi hasil counting
        return Results.success(resultSync);// Kembalikan hasil dalam format REST standar
    }

    /**
     * Mengajukan hasil penghitungan untuk persetujuan.
     */
    @ApiOperation(value = "Submit counting results for approval")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @PostMapping("/order-submit")
    public ResponseEntity<InvCountInfoDTO> orderSubmit(@PathVariable Long organizationId, @RequestBody List<InvCountHeaderDTO> invCountHeaders) {
        validObject(invCountHeaders); // Validasi input wajib
        SecurityTokenHelper.validTokenIgnoreInsert(invCountHeaders);
        InvCountInfoDTO orderSubmit = invCountHeaderService.orderSubmit(invCountHeaders); // Panggil metode executeCheck dari service
        return Results.success(orderSubmit); // Kembalikan hasil validasi
    }

    /**
     * Memulai atau menanggapi callback dari proses workflow.
     */
    @ApiOperation("Start workflow")
    @PostMapping("/start-workflow")
    @Permission(level = ResourceLevel.ORGANIZATION)
    public ResponseEntity<InvCountHeaderDTO> approvalCallback(@PathVariable Long organizationId, @RequestBody WorkFlowEventDTO workFlowEventDTO){
        return Results.success(invCountHeaderService.approvalCallback(organizationId, workFlowEventDTO));
    }

    /**
     * Mengambil laporan dataset perintah penghitungan berdasarkan kriteria pencarian.
     */
    @ApiOperation("Counting order report dataset method")
    @GetMapping("/counting-order-report")
    @Permission(level = ResourceLevel.ORGANIZATION)
    public ResponseEntity<List<InvCountHeaderDTO>> countingOrderReportDs(@PathVariable Long organizationId, @RequestBody InvCountHeaderDTO searchCriteria) {
        return Results.success(invCountHeaderService.countingOrderReportDs(searchCriteria));
    }

}