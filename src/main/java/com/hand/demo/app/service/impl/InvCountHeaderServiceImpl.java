package com.hand.demo.app.service.impl;

import com.hand.demo.api.dto.InvCountHeaderDTO;
import com.hand.demo.api.dto.InvCountInfoDTO;
import com.hand.demo.api.dto.InvCountLineDTO;
import com.hand.demo.app.service.InvCountExtraService;
import com.hand.demo.domain.entity.*;
import com.hand.demo.domain.repository.*;
import com.hand.demo.infra.mapper.InvCountHeaderMapper;
import com.hand.demo.infra.mapper.InvCountLineMapper;
import com.hand.demo.infra.mapper.InvWarehouseMapper;
import com.hand.demo.infra.util.Utils;
import io.choerodon.core.domain.Page;
import io.choerodon.core.oauth.DetailsHelper;
import io.choerodon.mybatis.pagehelper.PageHelper;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import io.micrometer.core.instrument.util.StringUtils;
import org.hzero.boot.interfaces.sdk.dto.RequestPayloadDTO;
import org.hzero.boot.interfaces.sdk.dto.ResponsePayloadDTO;
import org.hzero.boot.interfaces.sdk.invoke.InterfaceInvokeSdk;
import org.hzero.boot.platform.code.builder.CodeRuleBuilder;
import org.hzero.core.base.BaseConstants;
import org.hzero.core.util.TokenUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import com.hand.demo.app.service.InvCountHeaderService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.springframework.security.oauth2.common.AuthenticationScheme.header;

/**
 * (InvCountHeader)应用服务
 *
 * @author Anggie A
 * @since 2024-12-19 16:56:45
 */
@Service
public class InvCountHeaderServiceImpl implements InvCountHeaderService {
    @Autowired
    private InvCountHeaderRepository invCountHeaderRepository;
    @Autowired
    private InvWarehouseRepository invWarehouseRepository;
    @Autowired
    private InvCountLineRepository invCountLineRepository;
    @Autowired
    private InvStockRepository invStockRepository;
    @Autowired
    private InvCountExtraRepository invCountExtraRepository;
    @Autowired
    private InvCountHeaderMapper invCountHeaderMapper;
    @Autowired
    private InvCountLineMapper invCountLineMapper;
    @Autowired
    private InvWarehouseMapper invWarehouseMapper;
    @Autowired
    private InvCountExtraService invCountExtraService;
    @Autowired
    private InterfaceInvokeSdk invokeSdk;
    @Autowired
    private CodeRuleBuilder codeRuleBuilder;


    @Override
    public Page<InvCountHeader> selectList(PageRequest pageRequest, InvCountHeader invCountHeader) {
        return PageHelper.doPageAndSort(pageRequest, () -> invCountHeaderRepository.selectList(invCountHeader));
    }

    @Override
    public void saveData(List<InvCountHeader> invCountHeaders) {
        List<InvCountHeader> insertList = invCountHeaders.stream().filter(line -> line.getCountHeaderId() == null).collect(Collectors.toList());
        List<InvCountHeader> updateList = invCountHeaders.stream().filter(line -> line.getCountHeaderId() != null).collect(Collectors.toList());
        invCountHeaderRepository.batchInsertSelective(insertList);
        invCountHeaderRepository.batchUpdateByPrimaryKeySelective(updateList);
    }

    @Override
    public InvCountInfoDTO orderSave(List<InvCountHeaderDTO> orderSaveHeaders) {
        // validasi data menggunakan manualsavecheck
        InvCountInfoDTO validationResult = manualSaveCheck(orderSaveHeaders);
        // simpan data hanya bila validasi berhasil
        if (validationResult.getSuccessList() != null && !validationResult.getSuccessList().isEmpty()) {
            this.manualSave(validationResult.getSuccessList());
        }

        return validationResult;
    }

    @Override
    public List<InvCountHeaderDTO> manualSave(List<InvCountHeaderDTO> invCountHeaders) {
        Long tenantId = DetailsHelper.getUserDetails().getTenantId();
        Long userId = DetailsHelper.getUserDetails().getUserId();

        for (InvCountHeaderDTO headerManualSave : invCountHeaders) {
            if (headerManualSave.getCountHeaderId() == null) {
                // Insert new header
                codeRuleBuilder.generateCode("INV.COUNTING07.COUNT_NUMBER", new HashMap<>());
//                Utils.generateCountNumber(headerManualSave, "INV.COUNTING07.COUNT_NUMBER", codeRuleBuilder);
                headerManualSave.setTenantId(tenantId);
                headerManualSave.setCountStatus("DRAFT"); // Default status
                headerManualSave.setCreatedBy(userId);
                headerManualSave.setLastUpdatedBy(userId);
                invCountHeaderRepository.insertSelective(headerManualSave);
            } else {
                // Update existing header
                InvCountHeader existingHeader = invCountHeaderRepository.selectByPrimary(headerManualSave.getCountHeaderId());
                if (existingHeader == null) {
                    throw new IllegalArgumentException("Header with ID " + headerManualSave.getCountHeaderId() + " does not exist.");
                }

                headerManualSave.setObjectVersionNumber(existingHeader.getObjectVersionNumber());
                headerManualSave.set_token(existingHeader.get_token());
                headerManualSave.setLastUpdatedBy(userId);
                invCountHeaderRepository.updateByPrimaryKeySelective(headerManualSave);
            }

            // Save associated lines
            saveLines(headerManualSave);
        }

        return invCountHeaders;
    }

    private void saveLines(InvCountHeaderDTO header) {
        List<InvCountLineDTO> linesSave = header.getInvCountLineDTOList();
        if (linesSave == null || linesSave.isEmpty()) {
            return;
        }

        for (InvCountLineDTO line : linesSave) {
            line.setCountHeaderId(header.getCountHeaderId());
            if (line.getCountLineId() == null) {
                invCountLineRepository.insertSelective(line);
            } else {
                InvCountLine existingLine = invCountLineRepository.selectByPrimary(line.getCountLineId());
                if (existingLine == null) {
                    throw new IllegalArgumentException("Line with ID " + line.getCountLineId() + " does not exist.");
                }
                invCountLineRepository.updateByPrimaryKeySelective(line);
            }
        }
    }

    @Override
    public InvCountInfoDTO manualSaveCheck(List<InvCountHeaderDTO> invCountHeaders) {
        InvCountInfoDTO resultManualSave = new InvCountInfoDTO();
        List<InvCountHeaderDTO> errorList = new ArrayList<>();
        List<InvCountHeaderDTO> successList = new ArrayList<>();

        Long userId = DetailsHelper.getUserDetails().getUserId();
        int errorCount = 0;

        for (InvCountHeaderDTO header : invCountHeaders) {
            StringBuilder errorMessages = new StringBuilder();

            if (header.getCountHeaderId() == null) {
                successList.add(header);
                continue;
            }

            validateHeader(header, userId, errorMessages);

            if (errorMessages.length() > 0) {
                header.setErrorMsg(errorMessages.toString());
                errorList.add(header);
                errorCount++;
            } else {
                successList.add(header);
            }
        }

        resultManualSave.setErrorList(errorList);
        resultManualSave.setSuccessList(successList);
        resultManualSave.setTotalErrorMsg("Total error message count: " + errorCount);
        return resultManualSave;
    }

    private void validateHeader(InvCountHeaderDTO headerValidate, Long userId, StringBuilder errorMessages) {
        if (isCountNumberModified(headerValidate)) {
            errorMessages.append("Count number updates are not allowed. ");
        }

        String status = headerValidate.getCountStatus();
        if (status == null || (!"DRAFT".equals(status) && !"IN COUNTING".equals(status) &&
                !"REJECTED".equals(status) && !"WITHDRAWN".equals(status))) {
            errorMessages.append("Invalid status. Only DRAFT, IN COUNTING, REJECTED, or WITHDRAWN are allowed. ");
        }

        if ("DRAFT".equals(status) && !userId.equals(headerValidate.getCreatedBy())) {
            errorMessages.append("Documents in DRAFT status can only be modified by the creator. ");
        }

        if ("DRAFT".equals(status)) {
            validateDraftFields(headerValidate, errorMessages);
        } else if ("IN COUNTING".equals(status)) {
            validateInCountingFields(headerValidate, errorMessages);
        } else if ("REJECTED".equals(status) || "WITHDRAWN".equals(status)) {
            validateRejectedOrWithdrawnFields(headerValidate, errorMessages);
        }
    }

    private boolean isCountNumberModified(InvCountHeaderDTO headerCoutNumber) {
        if (headerCoutNumber.getCountNumber() == null || headerCoutNumber.getCountHeaderId() == null) {
            return false;
        }

        InvCountHeader existingHeader = invCountHeaderRepository.selectByPrimary(headerCoutNumber.getCountHeaderId());
        return existingHeader != null && !headerCoutNumber.getCountNumber().equals(existingHeader.getCountNumber());
    }

    private void validateDraftFields(InvCountHeaderDTO headerValidateDraf, StringBuilder errorMessages) {
        if (headerValidateDraf.getCompanyId() == null) {
            errorMessages.append("Company ID is required for DRAFT status. ");
        }
        if (headerValidateDraf.getWarehouseId() == null) {
            errorMessages.append("Warehouse ID is required for DRAFT status. ");
        }
        if (headerValidateDraf.getCounterIds() == null) {
            errorMessages.append("Counter IDs are required for DRAFT status. ");
        }
        if (headerValidateDraf.getSupervisorIds() == null) {
            errorMessages.append("Supervisor IDs are required for DRAFT status. ");
        }
    }

    private void validateInCountingFields(InvCountHeaderDTO headerValidateCount, StringBuilder errorMessages) {
        if (headerValidateCount.getRemark() == null || headerValidateCount.getRemark().isEmpty()) {
            errorMessages.append("Remark is required for IN COUNTING status. ");
        }
        if (headerValidateCount.getReason() == null || headerValidateCount.getReason().isEmpty()) {
            errorMessages.append("Reason is required for IN COUNTING status. ");
        }
    }

    private void validateRejectedOrWithdrawnFields(InvCountHeaderDTO headerValidateReject, StringBuilder errorMessages) {
        if (isCountNumberModified(headerValidateReject)) {
            errorMessages.append("Count number updates are not allowed in REJECTED/WITHDRAWN status. ");
        }
        if (headerValidateReject.getReason() == null) {
            errorMessages.append("Reason is required for REJECTED/WITHDRAWN status. ");
        }
    }


    @Override
    public InvCountInfoDTO checkAndRemove(List<InvCountHeaderDTO> invCountHeaders) {
        InvCountInfoDTO result = new InvCountInfoDTO();
        List<InvCountHeaderDTO> errorList = new ArrayList<>();
        List<InvCountHeaderDTO> successList = new ArrayList<>();
        Long currentUserId = DetailsHelper.getUserDetails().getUserId();

        // Fetch all existing headers by IDs from the database
        List<Long> headerIds = invCountHeaders.stream()
                .map(InvCountHeaderDTO::getCountHeaderId)
                .collect(Collectors.toList());
        List<InvCountHeader> existingHeaders = invCountHeaderRepository.findAllById(headerIds);
        Map<Long, InvCountHeader> headerMap = existingHeaders.stream()
                .collect(Collectors.toMap(InvCountHeader::getCountHeaderId, Function.identity()));

        // 1. Pre-verification
        for (InvCountHeaderDTO headerDTO : invCountHeaders) {
            InvCountHeader existingHeader = headerMap.get(headerDTO.getCountHeaderId());

            // Check if the header exists
            if (existingHeader == null) {
                headerDTO.setErrorMsg("Header with ID " + headerDTO.getCountHeaderId() + " not found.");
                errorList.add(headerDTO);
                continue;
            }

            // Verify status: Only DRAFT status is allowed for deletion
            if (!"DRAFT".equalsIgnoreCase(existingHeader.getCountStatus())) {
                headerDTO.setErrorMsg("Document with ID " + headerDTO.getCountHeaderId() + " cannot be deleted because its status is not DRAFT.");
                errorList.add(headerDTO);
                continue;
            }

            // Verify the creator of the document
            if (!currentUserId.equals(existingHeader.getCreatedBy())) {
                headerDTO.setErrorMsg("Document with ID " + headerDTO.getCountHeaderId() + " can only be deleted by its creator.");
                errorList.add(headerDTO);
                continue;
            }

            // If all verifications pass, add to successList
            successList.add(headerDTO);
        }

        // 2. Delete documents
        if (!successList.isEmpty()) {
            List<Long> removableIds = successList.stream()
                    .map(InvCountHeaderDTO::getCountHeaderId)
                    .collect(Collectors.toList());
            invCountHeaderRepository.deleteAllById(removableIds);
        }

        // 3. Prepare results
        result.setErrorList(errorList);
        result.setSuccessList(successList);
        result.setTotalErrorMsg(errorList.isEmpty() ? "All documents have been successfully deleted." : "Some documents could not be deleted.");
        return result;
    }

    @Override
    public InvCountHeaderDTO detail(Long countHeaderId) {
        // Fetch header data
        InvCountHeader header = invCountHeaderRepository.selectByPrimary(countHeaderId);
        if (header == null) {
            throw new IllegalArgumentException("Count Header not found for id: " + countHeaderId);
        }

        // Convert to DTO
        InvCountHeaderDTO headerDTO = new InvCountHeaderDTO();
        BeanUtils.copyProperties(header, headerDTO);

        // Fetch isWMSwarehouse
        headerDTO.setIsWmsWarehouse(fetchIsWmsWarehouse(header.getWarehouseId()));

        // Fetch invCountLineDTOList
        List<InvCountLine> countLines = invCountLineRepository.selectList(new InvCountLine() {{
            setCountHeaderId(countHeaderId);
        }});
        List<InvCountLineDTO> countLineDTOList = countLines.stream()
                .map(line -> {
                    InvCountLineDTO lineDTO = new InvCountLineDTO();
                    BeanUtils.copyProperties(line, lineDTO);
                    return lineDTO;
                })
                .collect(Collectors.toList());
        headerDTO.setInvCountLineDTOList(countLineDTOList);

        // Parse additional fields
        headerDTO.setCounterList(parseCounterList((String) header.getCounterIds()).toString());
        headerDTO.setSupervisorList(parseSupervisorList((String) header.getSupervisorIds()).toString());
        headerDTO.setSnapshotMaterialList(parseSnapshotMaterialList((String) header.getSnapshotMaterialIds()).toString());
        headerDTO.setSnapshotBatchList(parseSnapshotBatchList((String) header.getSnapshotBatchIds()).toString());

        return headerDTO;
    }

    private Boolean fetchIsWmsWarehouse(Long warehouseId) {
        if (warehouseId == null) return null;
        InvWarehouse warehouse = invWarehouseRepository.selectByPrimary(warehouseId);
        return warehouse != null && warehouse.getIsWmsWarehouse() == 1;
    }

    private List<Map<String, String>> parseCounterList(String counterIds) {
        if (counterIds == null || counterIds.isEmpty()) return Collections.emptyList();
        return Arrays.stream(counterIds.split(","))
                .map(id -> Collections.singletonMap("id", id))
                .map(map -> {
                    map.put("realName", "ZH" + map.get("id"));
                    return map;
                })
                .collect(Collectors.toList());
    }

    private List<Map<String, String>> parseSupervisorList(String supervisorIds) {
        if (supervisorIds == null || supervisorIds.isEmpty()) return Collections.emptyList();

        return Arrays.stream(supervisorIds.split(","))
                .map(id -> {
                    Map<String, String> map = new HashMap<>();
                    map.put("id", id);
                    map.put("realName", "ZH" + id);
                    return map;
                })
                .collect(Collectors.toList());
    }


    private List<Map<String, String>> parseSnapshotMaterialList(String snapshotMaterialIds) {
        if (snapshotMaterialIds == null || snapshotMaterialIds.isEmpty()) {
            return Collections.emptyList();
        }

        return Arrays.stream(snapshotMaterialIds.split(","))
                .map(entry -> {
                    String[] parts = entry.split("_");
                    if (parts.length == 2) {
                        Map<String, String> map = new HashMap<>();
                        map.put("id", parts[0]);
                        map.put("code", parts[1]);
                        return map;
                    }
                    throw new IllegalArgumentException("Invalid snapshot material format: " + entry);
                })
                .collect(Collectors.toList());
    }


    private List<Map<String, String>> parseSnapshotBatchList(String snapshotBatchIds) {
        if (snapshotBatchIds == null || snapshotBatchIds.isEmpty()) return Collections.emptyList();
        return Arrays.stream(snapshotBatchIds.split(","))
                .map(batchId -> {
                    Map<String, String> map = new HashMap<>();
                    map.put("batchId", batchId);
                    map.put("batchCode", "b" + batchId);
                    return map;
                })
                .collect(Collectors.toList());
    }
//    Todo mencoba fix
@Override
public InvCountInfoDTO orderExecution(List<InvCountHeaderDTO> orderExecutionHeaders) {
    InvCountInfoDTO result = new InvCountInfoDTO();

    // Step 1: Counting order save verification
    result = manualSaveCheck(orderExecutionHeaders);

    // Step 2: Counting order save method
    if (result.getSuccessList() != null && !result.getSuccessList().isEmpty()) {
        this.manualSave(result.getSuccessList());
    } else {
        return result; // Jika validasi gagal, hentikan proses
    }

    // Step 3: Counting order execute verification
    InvCountInfoDTO executeValidationResult = executeCheck(result.getSuccessList());
    if (executeValidationResult.getErrorList() != null && !executeValidationResult.getErrorList().isEmpty()) {
        result.setErrorList(executeValidationResult.getErrorList());
        result.setTotalErrorMsg("Execution validation failed for some records.");
        return result; // Hentikan proses jika validasi eksekusi gagal
    }

    // Step 4: Counting order execute method
    execute(executeValidationResult.getSuccessList());

    // Step 5: Counting order synchronization WMS
    InvCountInfoDTO syncResult = countSyncWms(executeValidationResult.getSuccessList());
    if (syncResult.getErrorList() != null && !syncResult.getErrorList().isEmpty()) {
        result.setErrorList(syncResult.getErrorList());
        result.setTotalErrorMsg("WMS synchronization failed for some records.");
    }

    // Gabungkan hasil sinkronisasi ke hasil akhir
    result.setSuccessList(syncResult.getSuccessList());
    result.setErrorList(syncResult.getErrorList());
    result.setTotalErrorMsg(syncResult.getTotalErrorMsg());

    return result;
}


    @Override
    public InvCountInfoDTO executeCheck(List<InvCountHeaderDTO> invCountHeaders) {
        InvCountInfoDTO executeCheckResult = new InvCountInfoDTO();
        List<InvCountHeaderDTO> errorList = new ArrayList<>();
        List<InvCountHeaderDTO> successList = new ArrayList<>();

        Long userId = DetailsHelper.getUserDetails().getUserId();
        Long tenantId = DetailsHelper.getUserDetails().getTenantId();

        for (InvCountHeaderDTO header : invCountHeaders) {
            StringBuilder errorMessages = new StringBuilder();

            // a. Document status validation
            if (!"DRAFT".equals(header.getCountStatus())) {
                errorMessages.append("Only documents in DRAFT status can be executed. ");
            }

            // b. Current login user validation
            if (!userId.equals(header.getCreatedBy())) {
                errorMessages.append("Only the document creator can execute the order. ");
            }

            // c. Value set validation
            if (header.getCountDimension() == null || header.getCountType() == null || header.getCountMode() == null) {
                errorMessages.append("Count dimension, type, and mode must be specified. ");
            }

            // d. Company, department, warehouse validation
            if (header.getCompanyId() == null) {
                errorMessages.append("Company ID is required. ");
            }
            if (header.getWarehouseId() == null) {
                errorMessages.append("Warehouse ID is required. ");
            }

            // e. On-hand quantity validation
            if (!validateOnHandQuantity(header, tenantId)) {
                errorMessages.append("Unable to query on-hand quantity data. ");
            }

            if (errorMessages.length() > 0) {
                header.setErrorMsg(errorMessages.toString());
                errorList.add(header);
            } else {
                successList.add(header);
            }
        }

        executeCheckResult.setErrorList(errorList);
        executeCheckResult.setSuccessList(successList);
        executeCheckResult.setTotalErrorMsg("Total errors: " + errorList.size());

        return executeCheckResult;
    }

    private boolean validateOnHandQuantity(InvCountHeaderDTO header, Long tenantId) {
        InvStock stockCriteria = new InvStock();
        stockCriteria.setTenantId(tenantId);
        stockCriteria.setCompanyId(header.getCompanyId());
        stockCriteria.setDepartmentId(header.getDepartmentId());
        stockCriteria.setWarehouseId(header.getWarehouseId());
        stockCriteria.setBatchId(parseBatchId((String) header.getSnapshotBatchIds()));
        stockCriteria.setMaterialId(parseMaterialId((String) header.getSnapshotMaterialIds()));


        List<InvStock> stocks = invStockRepository.selectList(stockCriteria);
        return stocks.stream().anyMatch(stock -> stock.getAvailableQuantity().compareTo(BigDecimal.ZERO) > 0);
    }

    private Long parseBatchId(String snapshotBatchIds) {
        if (snapshotBatchIds == null || snapshotBatchIds.isEmpty() || "null".equals(snapshotBatchIds)) {
            return null;
        }
        return Long.parseLong(snapshotBatchIds.split(",")[0]);
    }

    private Long parseMaterialId(String snapshotMaterialIds) {
        if (snapshotMaterialIds == null || snapshotMaterialIds.isEmpty() || "null".equals(snapshotMaterialIds)) {
            return null;
        }
        String[] parts = snapshotMaterialIds.split(",")[0].split("_");
        if (parts.length == 2) {
            return Long.parseLong(parts[0]);
        }
        throw new IllegalArgumentException("Invalid material ID format: " + snapshotMaterialIds);
    }

    @Override
    public List<InvCountHeaderDTO> execute(List<InvCountHeaderDTO> invCountHeaders) {
        Long tenantId = DetailsHelper.getUserDetails().getTenantId();
        Long userId = DetailsHelper.getUserDetails().getUserId();

        for (InvCountHeaderDTO header : invCountHeaders) {
            header.setCountStatus("IN COUNTING");
            header.setLastUpdatedBy(userId);
            invCountHeaderRepository.updateByPrimaryKeySelective(header);

            List<InvCountLine> countLines = generateCountLines(header, tenantId);

            for (int i = 0; i < countLines.size(); i++) {
                InvCountLine countLine = countLines.get(i);
                countLine.setLineNumber(i + 1);
                invCountLineRepository.insertSelective(countLine);
            }
        }

        return invCountHeaders;
    }

    private List<InvCountLine> generateCountLines(InvCountHeaderDTO header, Long tenantId) {
        List<InvCountLine> countLines = new ArrayList<>();

        InvStock stockCriteria = new InvStock();
        stockCriteria.setTenantId(tenantId);
        stockCriteria.setCompanyId(header.getCompanyId());
        stockCriteria.setDepartmentId(header.getDepartmentId());
        stockCriteria.setWarehouseId(header.getWarehouseId());
        stockCriteria.setBatchId(parseBatchId((String) header.getSnapshotBatchIds()));
        stockCriteria.setMaterialId(parseMaterialId((String) header.getSnapshotMaterialIds()));

        List<InvStock> stocks = invStockRepository.selectList(stockCriteria).stream()
                .filter(stock -> stock.getAvailableQuantity().compareTo(BigDecimal.ZERO) > 0)
                .collect(Collectors.toList());

        for (InvStock stock : stocks) {
            InvCountLine countLine = new InvCountLine();
            countLine.setTenantId(tenantId);
            countLine.setCountHeaderId(header.getCountHeaderId());
            countLine.setWarehouseId(stock.getWarehouseId());
            countLine.setMaterialId(stock.getMaterialId());
            countLine.setMaterialCode(stock.getMaterialCode());
            countLine.setUnitCode(stock.getUnitCode());
            countLine.setBatchId(stock.getBatchId());
            countLine.setSnapshotUnitQty(stock.getAvailableQuantity());
            countLine.setUnitQty(null);
            countLine.setUnitDiffQty(null);
            countLine.setCounterIds(header.getCounterIds());
            countLines.add(countLine);
        }

        return countLines;
    }

    @Override
    public InvCountInfoDTO countSyncWms(List<InvCountHeaderDTO> invCountHeaderList) {
        InvCountInfoDTO result = new InvCountInfoDTO();
        List<InvCountHeaderDTO> successList = new ArrayList<>();
        List<InvCountHeaderDTO> errorList = new ArrayList<>();

        Long tenantId = DetailsHelper.getUserDetails().getTenantId();

        for (InvCountHeaderDTO header : invCountHeaderList) {
            try {
                if (header.getWarehouseId() == null) {
                    throw new IllegalArgumentException("Warehouse ID is missing for Count Header ID: " + header.getCountHeaderId());
                }

                InvWarehouse warehouse = invWarehouseRepository.selectOne(new InvWarehouse() {{
                    setWarehouseId(header.getWarehouseId());
                    setTenantId(tenantId);
                }});
                if (warehouse == null) {
                    throw new IllegalArgumentException("Warehouse not found for ID: " + header.getWarehouseId());
                }

                InvCountExtra syncStatusExtra = getOrCreateExtra(tenantId, header.getCountHeaderId(), "wms_sync_status", "SKIP");
                InvCountExtra syncMsgExtra = getOrCreateExtra(tenantId, header.getCountHeaderId(), "wms_sync_error_message", "");

                if (Boolean.TRUE.equals(warehouse.getIsWmsWarehouse())) {
                    ResponsePayloadDTO response = invokeInterface(
                            "payload", // Simulasi payload
                            "HZERO",
                            "FEXAM_WMS",
                            "fexam-wms-api.thirdAddCounting",
                            null
                    );

                    if (response != null && "S".equals(response.getStatus())) {
                        syncStatusExtra.setProgramValue("SUCCESS");
                        syncMsgExtra.setProgramValue("");
                        header.setRelatedWmsOrderCode(String.valueOf(response.getStatusCode()));
                    } else if (response != null) {
                        syncStatusExtra.setProgramValue("ERROR");
                        syncMsgExtra.setProgramValue(response.getMessage());
                        throw new IllegalStateException("WMS synchronization failed: " + response.getMessage());
                    } else {
                        throw new IllegalStateException("WMS synchronization returned null response.");
                    }
                } else {
                    syncStatusExtra.setProgramValue("SKIP");
                }

                invCountExtraRepository.insert(syncStatusExtra);
                invCountExtraRepository.insert(syncMsgExtra);
                successList.add(header);
            } catch (Exception e) {
                header.setErrorMsg(e.getMessage());
                errorList.add(header);
            }
        }

        result.setSuccessList(successList);
        result.setErrorList(errorList);
        result.setTotalErrorMsg("Synchronization completed with " + errorList.size() + " errors.");
        return result;
    }

    private InvCountExtra getOrCreateExtra(Long tenantId, Long sourceId, String programKey, String defaultValue) {
        InvCountExtra extraCriteria = new InvCountExtra();
        extraCriteria.setTenantId(tenantId);
        extraCriteria.setSourceId(sourceId);
        extraCriteria.setProgramKey(programKey);

        InvCountExtra existingExtra = invCountExtraRepository.selectOne(extraCriteria);
        if (existingExtra == null) {
            InvCountExtra newExtra = new InvCountExtra();
            newExtra.setTenantId(tenantId);
            newExtra.setSourceId(sourceId);
            newExtra.setProgramKey(programKey);
            newExtra.setProgramValue(defaultValue);
            newExtra.setEnabledFlag(1);
            return newExtra;
        }
        return existingExtra;
    }

    @Override
    public ResponsePayloadDTO invokeInterface(String payload, String namespace, String serverCode, String interfaceCode, String accessToken) {
        RequestPayloadDTO requestPayloadDTO = new RequestPayloadDTO();
        requestPayloadDTO.setPayload(payload);

        Map<String, String> headerParams = new HashMap<>();
        headerParams.put("Authorization", "bearer " + (StringUtils.isBlank(accessToken) ? TokenUtils.getToken() : accessToken));
        requestPayloadDTO.setHeaderParamMap(headerParams);

        Map<String, String> pathVariableMap = new HashMap<>();
        pathVariableMap.put("organizationId", BaseConstants.DEFAULT_TENANT_ID.toString());
        requestPayloadDTO.setPathVariableMap(pathVariableMap);

        return invokeSdk.invoke(namespace, serverCode, interfaceCode, requestPayloadDTO);
    }

//    Todo execute

//
//    @Override
//    public InvCountInfoDTO orderExecution(List<InvCountHeaderDTO> orderExecutionHeaders) {
//        InvCountInfoDTO result;
//
//        // Step 1: Counting order save verification method (validasi penyimpanan counting order)
//        result = manualSaveCheck(orderExecutionHeaders);
//
//        // Step 2: Counting order save method (simpan counting order jika validasi berhasil)
//        if (result.getSuccessList() != null && !result.getSuccessList().isEmpty()) {
//            this.manualSave(result.getSuccessList());
//        } else {
//            return result; // Jika validasi gagal, kembalikan hasil tanpa melanjutkan ke langkah berikutnya
//        }
//
//        // Step 3: Counting order execute verification method (validasi eksekusi counting order)
//        InvCountInfoDTO executeValidationResult = executeCheck(result.getSuccessList());
//        if (executeValidationResult.getErrorList() != null && !executeValidationResult.getErrorList().isEmpty()) {
//            result.setErrorList(executeValidationResult.getErrorList());
//            result.setTotalErrorMsg("Execution validation failed for some records.");
//            return result; // Hentikan proses jika validasi eksekusi gagal
//        }
//
//        // Step 4: Counting order execute method (eksekusi counting order)
//        execute(executeValidationResult.getSuccessList());
//
//        // Step 5: Counting order synchronization WMS method (sinkronisasi dengan WMS)
//        InvCountInfoDTO syncResult = countSyncWms(executeValidationResult.getSuccessList());
//        if (syncResult.getErrorList() != null && !syncResult.getErrorList().isEmpty()) {
//            result.setErrorList(syncResult.getErrorList());
//            result.setTotalErrorMsg("WMS synchronization failed for some records.");
//        }
//
//        // Gabungkan hasil eksekusi dan sinkronisasi ke dalam hasil akhir
//        result.setSuccessList(syncResult.getSuccessList());
//        result.setErrorList(syncResult.getErrorList());
//        result.setTotalErrorMsg(syncResult.getTotalErrorMsg());
//
//        return result;
//    }
//
//
//    @Override
//    public InvCountInfoDTO executeCheck(List<InvCountHeaderDTO> invCountHeaders) {
//        InvCountInfoDTO executeCheckResult = new InvCountInfoDTO();
//        List<InvCountHeaderDTO> errorList = new ArrayList<>();
//        List<InvCountHeaderDTO> successList = new ArrayList<>();
//
//        Long userId = DetailsHelper.getUserDetails().getUserId();
//        Long tenantId = DetailsHelper.getUserDetails().getTenantId();
//
//        for (InvCountHeaderDTO header : invCountHeaders) {
//            StringBuilder errorMessages = new StringBuilder();
//
//            // a. Document status validation: Only draft status can execute
//            if (!"DRAFT".equals(header.getCountStatus())) {
//                errorMessages.append("Only documents in DRAFT status can be executed. ");
//            }
//
//            // b. Current login user validation: Only the document creator can execute
//            if (!userId.equals(header.getCreatedBy())) {
//                errorMessages.append("Only the document creator can execute the order. ");
//            }
//
//            // c. Value set validation
//            if (header.getCountDimension() == null || header.getCountType() == null || header.getCountMode() == null) {
//                errorMessages.append("Count dimension, type, and mode must be specified. ");
//            }
//
//            // d. Company, department, warehouse validation
//            if (header.getCompanyId() == null) {
//                errorMessages.append("Company ID is required. ");
//            }
//            if (header.getWarehouseId() == null) {
//                errorMessages.append("Warehouse ID is required. ");
//            }
//
//            // e. On-hand quantity validation
//            if (!validateOnHandQuantity(header, tenantId)) {
//                errorMessages.append("Unable to query on-hand quantity data. ");
//            }
//
//            // Collect errors or add to success list
//            if (errorMessages.length() > 0) {
//                header.setErrorMsg(errorMessages.toString());
//                errorList.add(header);
//            } else {
//                successList.add(header);
//            }
//        }
//
//        // Set results
//        executeCheckResult.setErrorList(errorList);
//        executeCheckResult.setSuccessList(successList);
//        executeCheckResult.setTotalErrorMsg("Total errors: " + errorList.size());
//
//        return executeCheckResult;
//    }
//
//    private boolean validateOnHandQuantity(InvCountHeaderDTO header, Long tenantId) {
//        InvStock stockCriteria = new InvStock();
//        stockCriteria.setTenantId(tenantId);
//        stockCriteria.setCompanyId(header.getCompanyId());
//        stockCriteria.setDepartmentId(header.getDepartmentId());
//        stockCriteria.setWarehouseId(header.getWarehouseId());
//        stockCriteria.setBatchId(parseBatchId((String) header.getSnapshotBatchIds()));
//        stockCriteria.setMaterialId(parseMaterialId((String) header.getSnapshotMaterialIds()));
//
//        // Check stock with available quantity > 0
//        List<InvStock> stocks = invStockRepository.selectList(stockCriteria);
//        return stocks.stream().anyMatch(stock -> stock.getAvailableQuantity().compareTo(BigDecimal.ZERO) > 0);
//    }
//
//    private Long parseBatchId(String snapshotBatchIds) {
//        if (snapshotBatchIds == null || snapshotBatchIds.isEmpty()) {
//            return null;
//        }
//        // Assuming the first batchId is sufficient for validation
//        return Long.parseLong(snapshotBatchIds.split(",")[0]);
//    }
//
//    private Long parseMaterialId(String snapshotMaterialIds) {
//        if (snapshotMaterialIds == null || snapshotMaterialIds.isEmpty()) {
//            return null;
//        }
//        // Assuming the first materialId is sufficient for validation
//        String[] parts = snapshotMaterialIds.split(",")[0].split("_");
//        if (parts.length == 2) {
//            return Long.parseLong(parts[0]);
//        }
//        throw new IllegalArgumentException("Invalid material ID format: " + snapshotMaterialIds);
//    }
//
//
//    @Override
//    public List<InvCountHeaderDTO> execute(List<InvCountHeaderDTO> invCountHeaders) {
//        Long tenantId = DetailsHelper.getUserDetails().getTenantId();
//        Long userId = DetailsHelper.getUserDetails().getUserId();
//
//        for (InvCountHeaderDTO header : invCountHeaders) {
//            // 1. Update the counting order status to "In counting"
//            header.setCountStatus("IN COUNTING");
//            header.setLastUpdatedBy(userId);
//            invCountHeaderRepository.updateByPrimaryKeySelective(header);
//
//            // 2. Get stock data to generate row data
//            List<InvCountLine> countLines = generateCountLines(header, tenantId);
//
//            // Save generated count lines
//            for (int i = 0; i < countLines.size(); i++) {
//                InvCountLine countLine = countLines.get(i);
//                countLine.setLineNumber(i + 1); // Set line number
//                invCountLineRepository.insertSelective(countLine);
//            }
//        }
//
//        return invCountHeaders;
//    }
//
//
//
//    private List<InvCountLine> generateCountLines(InvCountHeaderDTO header, Long tenantId) {
//        List<InvCountLine> countLines = new ArrayList<>();
//
//        // Create stock query criteria
//        InvStock stockCriteria = new InvStock();
//        stockCriteria.setTenantId(tenantId);
//        stockCriteria.setCompanyId(header.getCompanyId());
//        stockCriteria.setDepartmentId(header.getDepartmentId());
//        stockCriteria.setWarehouseId(header.getWarehouseId());
//        stockCriteria.setBatchId(parseBatchId((String) header.getSnapshotBatchIds()));
//        stockCriteria.setMaterialId(parseMaterialId((String) header.getSnapshotMaterialIds()));
//
//        // Fetch stock data where available quantity > 0
//        List<InvStock> stocks = invStockRepository.selectList(stockCriteria).stream()
//                .filter(stock -> stock.getAvailableQuantity().compareTo(BigDecimal.ZERO) > 0)
//                .collect(Collectors.toList());
//
//        // Generate count lines
//        for (InvStock stock : stocks) {
//            InvCountLine countLine = new InvCountLine();
//            countLine.setTenantId(tenantId);
//            countLine.setCountHeaderId(header.getCountHeaderId());
//            countLine.setWarehouseId(stock.getWarehouseId());
//            countLine.setMaterialId(stock.getMaterialId());
//            countLine.setMaterialCode(stock.getMaterialCode());
//            countLine.setUnitCode(stock.getUnitCode());
//            countLine.setBatchId(stock.getBatchId());
//            countLine.setSnapshotUnitQty(stock.getAvailableQuantity());
//            countLine.setUnitQty(null); // Default value
//            countLine.setUnitDiffQty(null); // Default value
//            countLine.setCounterIds(header.getCounterIds());
//            countLines.add(countLine);
//        }
//
//        return countLines;
//    }
//
//    @Override
//    public InvCountInfoDTO countSyncWms(List<InvCountHeaderDTO> invCountHeaderList) {
//        InvCountInfoDTO result = new InvCountInfoDTO();
//        List<InvCountHeaderDTO> successList = new ArrayList<>();
//        List<InvCountHeaderDTO> errorList = new ArrayList<>();
//
//        Long tenantId = DetailsHelper.getUserDetails().getTenantId();
//
//        for (InvCountHeaderDTO header : invCountHeaderList) {
//            try {
//                // a. Validasi warehouseId pada header
//                if (header.getWarehouseId() == null) {
//                    throw new IllegalArgumentException("Warehouse ID is missing for Count Header ID: " + header.getCountHeaderId());
//                }
//
//                // Query warehouse data
//                InvWarehouse warehouse = invWarehouseRepository.selectOne(new InvWarehouse() {{
//                    setWarehouseId(header.getWarehouseId());
//                    setTenantId(tenantId);
//                }});
//                if (warehouse == null) {
//                    throw new IllegalArgumentException("Warehouse not found for ID: " + header.getWarehouseId());
//                }
//
//                // b. Validasi atau inisialisasi data tambahan
//                InvCountExtra syncStatusExtra = getOrCreateExtra(tenantId, header.getCountHeaderId(), "wms_sync_status", "SKIP");
//                InvCountExtra syncMsgExtra = getOrCreateExtra(tenantId, header.getCountHeaderId(), "wms_sync_error_message", "");
//
//                // c. Sinkronisasi jika warehouse adalah tipe WMS
//                if (Boolean.TRUE.equals(warehouse.getIsWmsWarehouse())) {
//                    // Buat payload API
//                    StringBuilder payload = new StringBuilder();
//                    payload.append("countHeaderId=").append(header.getCountHeaderId())
//                            .append("&employeeNumber=21995");
//
//                    if (header.getInvCountLineDTOList() != null) {
//                        for (InvCountLineDTO line : header.getInvCountLineDTOList()) {
//                            payload.append("&countOrderLineList=")
//                                    .append("countLineId:").append(line.getCountLineId())
//                                    .append(",materialId:").append(line.getMaterialId())
//                                    .append(",unitCode:").append(line.getUnitCode())
//                                    .append(",snapshotUnitQty:").append(line.getSnapshotUnitQty());
//                        }
//                    }
//
//                    // Panggil API WMS melalui InterfaceInvokeSdk
//                    ResponsePayloadDTO response = invokeInterface(
//                            payload.toString(), // Parameter payload
//                            "HZERO",            // Namespace API
//                            "FEXAM_WMS",        // Server Code API
//                            "fexam-wms-api.thirdAddCounting", // Interface Code
//                            null                // Token
//                    );
//
//                    // d. Proses respon API
//                    if (response != null && "S".equals(response.getStatus())) {
//                        syncStatusExtra.setProgramValue("SUCCESS");
//                        syncMsgExtra.setProgramValue("");
//                        header.setRelatedWmsOrderCode(String.valueOf(response.getStatusCode()));
//                    } else if (response != null) {
//                        syncStatusExtra.setProgramValue("ERROR");
//                        syncMsgExtra.setProgramValue(response.getMessage());
//                        throw new IllegalStateException("WMS synchronization failed: " + response.getMessage());
//                    } else {
//                        throw new IllegalStateException("WMS synchronization returned null response.");
//                    }
//                } else {
//                    // Tandai dengan status SKIP jika bukan tipe WMS
//                    syncStatusExtra.setProgramValue("SKIP");
//                }
//
//                // Simpan data tambahan
//                invCountExtraRepository.insert(syncStatusExtra);
//                invCountExtraRepository.insert(syncMsgExtra);
//                successList.add(header);
//            } catch (Exception e) {
//                // Tangani error dan tambahkan ke daftar error
//                header.setErrorMsg(e.getMessage());
//                errorList.add(header);
//            }
//        }
//
//        // Buat hasil sinkronisasi
//        result.setSuccessList(successList);
//        result.setErrorList(errorList);
//        result.setTotalErrorMsg("Synchronization completed with " + errorList.size() + " errors.");
//
//        return result;
//    }
//
//    private InvCountExtra getOrCreateExtra(Long tenantId, Long sourceId, String programKey, String defaultValue) {
//        // Buat kriteria pencarian untuk data tambahan (extended table)
//        InvCountExtra extraCriteria = new InvCountExtra();
//        extraCriteria.setTenantId(tenantId);
//        extraCriteria.setSourceId(sourceId);
//        extraCriteria.setProgramKey(programKey);
//
//        // Cari data tambahan yang sudah ada
//        InvCountExtra existingExtra = invCountExtraRepository.selectOne(extraCriteria);
//        if (existingExtra == null) {
//            // Jika data tidak ditemukan, inisialisasi data baru
//            InvCountExtra newExtra = new InvCountExtra();
//            newExtra.setTenantId(tenantId);
//            newExtra.setSourceId(sourceId);
//            newExtra.setProgramKey(programKey);
//            newExtra.setProgramValue(defaultValue);
//            newExtra.setEnabledFlag(1);
//            return newExtra;
//        }
//        return existingExtra;
//    }
//
//    @Override
//    public ResponsePayloadDTO invokeInterface(String payload, String namespace, String serverCode,
//                                              String interfaceCode, String accessToken) {
//        RequestPayloadDTO requestPayloadDTO = new RequestPayloadDTO();
//        requestPayloadDTO.setPayload(payload);
//
//        // Menambahkan header parameter
//        Map<String, String> headerParams = new HashMap<>();
//        headerParams.put("Authorization", "bearer " +
//                (StringUtils.isBlank(accessToken) ? TokenUtils.getToken() : accessToken));
//        requestPayloadDTO.setHeaderParamMap(headerParams);
//
//        // Menambahkan path variable
//        Map<String, String> pathVariableMap = new HashMap<>();
//        pathVariableMap.put("organizationId", BaseConstants.DEFAULT_TENANT_ID.toString());
//        requestPayloadDTO.setPathVariableMap(pathVariableMap);
//
//        // Memanggil InterfaceInvokeSdk
//        return invokeSdk.invoke(namespace, serverCode, interfaceCode, requestPayloadDTO);
//    }



}






