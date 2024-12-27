package com.hand.demo.app.service.impl;

import com.hand.demo.api.dto.*;
import com.hand.demo.domain.entity.*;
import com.hand.demo.domain.repository.*;
import io.choerodon.core.domain.Page;
import io.choerodon.core.exception.CommonException;
import io.choerodon.core.oauth.DetailsHelper;
import io.choerodon.mybatis.pagehelper.PageHelper;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import io.micrometer.core.instrument.util.StringUtils;
import org.hzero.boot.interfaces.sdk.dto.RequestPayloadDTO;
import org.hzero.boot.interfaces.sdk.dto.ResponsePayloadDTO;
import org.hzero.boot.interfaces.sdk.invoke.InterfaceInvokeSdk;
import org.hzero.boot.platform.code.builder.CodeRuleBuilder;
import org.hzero.boot.platform.profile.ProfileClient;
import org.hzero.boot.workflow.WorkflowClient;
import org.hzero.boot.workflow.dto.RunInstance;
import org.hzero.core.base.BaseConstants;
import org.hzero.core.util.TokenUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import com.hand.demo.app.service.InvCountHeaderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private IamDepartmentRepository iamDepartmentRepository;
    @Autowired
    private InterfaceInvokeSdk invokeSdk;
    @Autowired
    private CodeRuleBuilder codeRuleBuilder;
    @Autowired
    private ProfileClient profileClient;
    @Autowired
    private WorkflowClient workflowClient;


    /**
     * Mengambil daftar header penghitungan berdasarkan kriteria yang diberikan,
     * mendukung pagination dan sorting.
     */
    @Override
    public Page<InvCountHeaderDTO> list(PageRequest pageRequest, InvCountHeaderDTO invCountHeaders) {
        return PageHelper.doPageAndSort(pageRequest, () -> invCountHeaderRepository.selectList(invCountHeaders));
    }


    /**
     * Menyimpan daftar header penghitungan, membedakan antara insert dan update.
     */
    @Override
    public void saveData(List<InvCountHeader> invCountHeaders) {
        List<InvCountHeader> inserSave = invCountHeaders.stream().filter(line -> line.getCountHeaderId() == null).collect(Collectors.toList());
        List<InvCountHeader> updateSave = invCountHeaders.stream().filter(line -> line.getCountHeaderId() != null).collect(Collectors.toList());
        invCountHeaderRepository.batchInsertSelective(inserSave);
        invCountHeaderRepository.batchUpdateByPrimaryKeySelective(updateSave);
    }

    /**
     * Memvalidasi dan menyimpan header penghitungan. Hanya header yang lolos validasi yang disimpan.
     */
    @Override
    public InvCountInfoDTO orderSave(List<InvCountHeaderDTO> orderSaveHeaders) {
        InvCountInfoDTO validationResult = manualSaveCheck(orderSaveHeaders); // validasi data menggunakan manualsavecheck
        // Todo HARUSNYA UBAH KE ERROR
        // done
        if (CollectionUtils.isEmpty(validationResult.getErrorList())) {// simpan data hanya bila validasi berhasil
            this.manualSave(validationResult.getSuccessList());
        }
        return validationResult;
    }

    @Override
    public InvCountInfoDTO manualSaveCheck(List<InvCountHeaderDTO> invCountHeaders) {
        // membuat objek hasil u/ menyipan data error / berhasil
        InvCountInfoDTO resultManualSave = new InvCountInfoDTO();
        List<InvCountHeaderDTO> errorList = new ArrayList<>();
        List<InvCountHeaderDTO> successList = new ArrayList<>();

//        InvCountHeader existingHeader = invCountHeaderRepository.selectByPrimary(invCountHeaders.getCountHeaderId());

        // Mendapatkan ID pengguna dan tenant saat ini
        Long userId = DetailsHelper.getUserDetails().getUserId(); // Untuk validasi pembuat dokumen
        Long tenantId = DetailsHelper.getUserDetails().getTenantId(); // Untuk validasi tenant

        for (InvCountHeaderDTO header : invCountHeaders) {
            StringBuilder errorMessages = new StringBuilder(); // Tempat menyimpan pesan error untuk setiap header

            // Validasi tenantId
            if (header.getTenantId() == null) {
                header.setTenantId(tenantId); // Jika kosong, isi dengan tenant ID saat ini
            } else if (!tenantId.equals(header.getTenantId())) {
                errorMessages.append("Invalid Tenant ID. "); // Tambahkan pesan error jika tenant tidak cocok
            }

// Todo nanti di hapus karna datanya sudaha da di bwh dan tidak perlu ada karna untuk di save ||DONE||

            // TOdo tambahkan untuk sat insert list berhasil baru lanjut ke tahap berikutnya
            // Validasi countStatu
            String status = header.getCountStatus();
            if (status == null || (!status.equalsIgnoreCase("DRAFT")
                    && !status.equalsIgnoreCase("INCOUNTING")
                    && !status.equalsIgnoreCase("PROCESSING")
                    && !status.equalsIgnoreCase("WITHDRAWN"))) {
                errorMessages.append("Only DRAFT,INCOUNTING, PROCESSING, or WITHDRAWN statuses are allowed. ");
            }

            // Validasi khusus untuk status DRAFT
            // Validasi untuk status DRAFT
            if ("DRAFT".equalsIgnoreCase(status)) {
                validateDraftFields(header, errorMessages);
            } else if ("IN COUNTING".equalsIgnoreCase(status)) {
                validateInCountingFields(header, errorMessages);
            } else if ("REJECTED".equalsIgnoreCase(status) || "WITHDRAWN".equalsIgnoreCase(status)) {
                validateRejectedOrWithdrawnFields(header, errorMessages);
            }

            // Tambahkan ke daftar sukses atau error
            if (errorMessages.length() > 0) {
                header.setErrorMsg(errorMessages.toString()); // Tambahkan pesan error ke header
                errorList.add(header); // Masukkan ke daftar error
            } else {
                successList.add(header); // Masukkan ke daftar sukses
            }
        }

        resultManualSave.setErrorList(errorList); // Simpan daftar error
        resultManualSave.setSuccessList(successList); // Simpan daftar sukses
        return resultManualSave; // Kembalikan hasil validasi
    }


// todo data sudah di sesuikan dengan soalnya
    @Override
    public List<InvCountHeaderDTO> manualSave(List<InvCountHeaderDTO> invCountHeaders) {
        Long tenantId = DetailsHelper.getUserDetails().getTenantId(); // tenant id sekarang
        Long userId = DetailsHelper.getUserDetails().getUserId(); // user id sekarang

        List<InvCountHeader> insertList = invCountHeaders.stream().filter(headerDTO -> headerDTO.getCountHeaderId() == null).collect(Collectors.toList());
        List<InvCountHeader> updateList = invCountHeaders.stream().filter(headerDTO -> headerDTO.getCountHeaderId() != null).collect(Collectors.toList());

        insertList.forEach(headerDTO -> {
            Map<String,String > args = new HashMap<>();
            args.put("customSegment", headerDTO.getTenantId().toString());
            String codegenenerate = codeRuleBuilder.generateCode("codename",args);
            headerDTO.setCountNumber(codegenenerate);
            headerDTO.setCountStatus("DRAFT");
            headerDTO.setDelFlag(0);
        });
        if(!CollectionUtils.isEmpty(updateList)){
            updateList.forEach(headerDTO -> {
                InvCountHeader existingHeader = invCountHeaderRepository.selectByPrimary(headerDTO.getCountHeaderId());
                if (existingHeader == null){
                   throw new IllegalStateException("Header wiht ID" + headerDTO.getCountHeaderId() + "does not exist." );
                }
                 // validasi status dan perbarui hanya field yang diizikan
                String currentStatus = existingHeader.getCountStatus();
                if("DRAFT".equalsIgnoreCase(currentStatus)) {
                    headerDTO.setCompanyId(headerDTO.getCompanyId() != null ? headerDTO.getCompanyId() : existingHeader.getCompanyId());
                    headerDTO.setDepartmentId(headerDTO.getDepartmentId() != null ? headerDTO.getDepartmentId() : existingHeader.getDepartmentId());
                    headerDTO.setWarehouseId(headerDTO.getWarehouseId() != null ? headerDTO.getWarehouseId() : existingHeader.getWarehouseId());
                    headerDTO.setCountDimension(headerDTO.getCountDimension() != null ? headerDTO.getCountDimension() : existingHeader.getCountDimension());
                    headerDTO.setCountType(headerDTO.getCountType() != null ? headerDTO.getCountType() : existingHeader.getCountType());
                    headerDTO.setCountMode(headerDTO.getCountMode() != null ? headerDTO.getCountMode() : existingHeader.getCountMode());
                    headerDTO.setCountTimeStr(headerDTO.getCountTimeStr() != null ? headerDTO.getCountTimeStr() : existingHeader.getCountTimeStr());
                    headerDTO.setCounterIds(headerDTO.getCounterIds() != null ? headerDTO.getCounterIds() : existingHeader.getCounterIds());
                    headerDTO.setSupervisorIds(headerDTO.getSupervisorIds() != null ? headerDTO.getSupervisorIds() : existingHeader.getSupervisorIds());
                    headerDTO.setSnapshotMaterialIds(headerDTO.getSnapshotMaterialIds() != null ? headerDTO.getSnapshotMaterialIds() : existingHeader.getSnapshotMaterialIds());
                    headerDTO.setSnapshotBatchIds(headerDTO.getSnapshotBatchIds() != null ? headerDTO.getSnapshotBatchIds() : existingHeader.getSnapshotBatchIds());
                }

                // Field yang dapat diperbarui pada status tertentu
                if ("PROCESSING".equalsIgnoreCase(currentStatus)) {
                    headerDTO.setRemark(headerDTO.getRemark() != null ? headerDTO.getRemark() : existingHeader.getRemark());
                    headerDTO.setReason(headerDTO.getReason() != null ? headerDTO.getReason() : existingHeader.getReason());
                } else if ("REJECTED".equalsIgnoreCase(currentStatus)) {
                    headerDTO.setReason(headerDTO.getReason() != null ? headerDTO.getReason() : existingHeader.getReason());
                }

                // Salin metadata dari header lama
                headerDTO.setObjectVersionNumber(existingHeader.getObjectVersionNumber());
                headerDTO.set_token(existingHeader.get_token());
                headerDTO.setLastUpdatedBy(userId); // Set pengguna terakhir yang memperbarui
            });

            // Simpan semua header yang diperbarui ke database
            invCountHeaderRepository.batchUpdateOptional(updateList);
        }

        // Simpan semua header baru ke database
        if (!CollectionUtils.isEmpty(insertList)) {
            invCountHeaderRepository.batchInsert(insertList);
        }

        // Proses line terkait
        invCountHeaders.forEach(this::saveLines);

        // Gabungkan semua header yang berhasil disimpan
        List<InvCountHeader> result = new ArrayList<>();
        result.addAll(insertList);
        result.addAll(updateList);
        return invCountHeaders; // Kembalikan daftar dokumen yang disimpan
    }


    private void saveLines(InvCountHeaderDTO header) {
        List<InvCountLineDTO> linesSave = header.getInvCountLineDTOList(); // Ambil daftar line terkait
        if (linesSave == null || linesSave.isEmpty()) {
            return; // Jika tidak ada line, keluar dari fungsi
        }

        Long tenantId = DetailsHelper.getUserDetails().getTenantId(); // Tenant ID saat ini
        Long currentUserId = DetailsHelper.getUserDetails().getUserId(); // User ID saat ini

        int lineNumber = 1; // Nomor baris awal
        for (InvCountLineDTO line : linesSave) {
            line.setTenantId(tenantId); // Set tenant ID
            line.setCountHeaderId(header.getCountHeaderId()); // Asosiasikan dengan header
            line.setLineNumber(lineNumber++); // Nomor baris increment

            if (line.getCountLineId() == null) {
                // Line baru
                InvStock stock = invStockRepository.selectOne(new InvStock() {{
                    setTenantId(tenantId);
                    setWarehouseId(header.getWarehouseId());
                    setMaterialId(line.getMaterialId());
                    setBatchId(line.getBatchId());
                }});
                line.setSnapshotUnitQty(stock != null ? stock.getAvailableQuantity() : BigDecimal.ZERO); // Ambil stok awal
                line.setCounterIds(header.getCounterIds()); // Inisialisasi counter IDs
                invCountLineRepository.insertSelective(line); // Simpan line baru
            } else {
                // Update line lama
                InvCountLine existingLine = invCountLineRepository.selectByPrimary(line.getCountLineId());
                if (existingLine == null) {
                    throw new IllegalArgumentException("Line with ID " + line.getCountLineId() + " does not exist.");
                }

                if ("INCOUNTING".equalsIgnoreCase(header.getCountStatus())) {
                    // Perbarui `unitQty` jika counter diizinkan
                    if (line.getUnitQty() != null && !line.getUnitQty().equals(existingLine.getUnitQty())) {
                        if (!isCounterAuthorized(existingLine.getCounterIds(), currentUserId)) { // pindahkan masukan ke validasi
                            throw new IllegalArgumentException("Only authorized counters can modify unitQty.");
                        }
                        existingLine.setUnitQty(line.getUnitQty()); // Update jumlah unit
                        existingLine.setCounterIds(String.valueOf(currentUserId)); // Set counter ID
                    }

                    existingLine.setUnitDiffQty(calculateUnitDiffQty(existingLine.getSnapshotUnitQty(), line.getUnitQty())); // Hitung selisih
                }

                invCountLineRepository.updateByPrimaryKeySelective(existingLine); // Simpan perubahan line
            }
        }
    }

    private BigDecimal calculateUnitDiffQty(BigDecimal snapshotUnitQty, BigDecimal unitQty) {
        if (snapshotUnitQty == null || unitQty == null) {
            return BigDecimal.ZERO; // Jika salah satu nilai null, hasil perbedaan diatur menjadi 0
        }
        return unitQty.subtract(snapshotUnitQty); // Mengembalikan selisih antara jumlah yang dihitung (unitQty) dan stok awal (snapshotUnitQty)
    }

    private boolean isCounterAuthorized(String counterIds, Long currentUserId) {
        if (counterIds == null || currentUserId == null) {
            return false; // Jika daftar counterIds atau currentUserId kosong, kembalikan false
        }
        List<Long> counters = Arrays.stream(counterIds.split(",")) // Pecah daftar counterIds yang dipisahkan koma
                .map(String::trim) // Hilangkan spasi berlebih
                .map(Long::valueOf) // Konversi setiap elemen menjadi Long
                .collect(Collectors.toList()); // Kumpulkan hasil ke dalam list
        return counters.contains(currentUserId); // Periksa apakah currentUserId ada dalam daftar counter
    }

    private String generateCountNumber(Long tenantId) {
        return String.format("INV-Counting-%d-%s", tenantId, LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")));
        // buat Format: INV-Counting-{tenantId}-{yyyyMMdd}
    }

    private boolean isCountNumberModified(InvCountHeaderDTO headerCoutNumber) {
        // Jika countNumber atau countHeaderId null, langsung kembalikan false
        if (headerCoutNumber.getCountNumber() == null || headerCoutNumber.getCountHeaderId() == null) {
            return false; // Tidak bisa memeriksa perubahan jika salah satu nilai tidak ada
        }

        // Ambil header yang ada di database berdasarkan countHeaderId
        InvCountHeader existingHeader = invCountHeaderRepository.selectByPrimary(headerCoutNumber.getCountHeaderId());

        // Periksa apakah header ada di database dan apakah countNumber berbeda
        return existingHeader != null && !headerCoutNumber.getCountNumber().equals(existingHeader.getCountNumber());
        // Return true jika countNumber berbeda, false jika sama atau tidak ditemukan
    }


    private void validateDraftFields(InvCountHeaderDTO headerValidateDraf, StringBuilder errorMessages) {
        // Periksa apakah companyId null
        if (headerValidateDraf.getCompanyId() == null) {
            errorMessages.append("Company ID is required for DRAFT status. ");
            // Tambahkan pesan error jika companyId tidak diisi
        }

        // Periksa apakah warehouseId null
        if (headerValidateDraf.getWarehouseId() == null) {
            errorMessages.append("Warehouse ID is required for DRAFT status. ");
            // Tambahkan pesan error jika warehouseId tidak diisi
        }

        // Periksa apakah counterIds null
        if (headerValidateDraf.getCounterIds() == null) {
            errorMessages.append("Counter IDs are required for DRAFT status. ");
            // Tambahkan pesan error jika counterIds tidak diisi
        }

        // Periksa apakah supervisorIds null
        if (headerValidateDraf.getSupervisorIds() == null) {
            errorMessages.append("Supervisor IDs are required for DRAFT status. ");
            // Tambahkan pesan error jika supervisorIds tidak diisi
        }
    }


    private void validateInCountingFields(InvCountHeaderDTO headerValidateCount, StringBuilder errorMessages) {
        // Periksa apakah reason kosong atau null
        if (headerValidateCount.getReason() == null || headerValidateCount.getReason().isEmpty()) {
            errorMessages.append("Reason is required for PROCESSING  status. ");
            // Tambahkan pesan error jika reason tidak diisi
        }
    }


    private void validateRejectedOrWithdrawnFields(InvCountHeaderDTO headerValidateReject, StringBuilder errorMessages) {
        // Periksa apakah countNumber diubah
        if (isCountNumberModified(headerValidateReject)) {
            errorMessages.append("Count number updates are not allowed in WITHDRAWN status. ");
            // Tambahkan pesan error jika countNumber diubah
        }

        // Periksa apakah reason null
        if (headerValidateReject.getReason() == null) {
            errorMessages.append("Reason is required for WITHDRAWN status. ");
            // Tambahkan pesan error jika reason tidak diisi
        }
    }


    @Override
    public InvCountInfoDTO checkAndRemove(List<InvCountHeaderDTO> invCountHeaders) {
        // Inisialisasi hasil
        InvCountInfoDTO result = new InvCountInfoDTO(); // Objek hasil yang akan menyimpan daftar sukses dan error
        List<InvCountHeaderDTO> errorList = new ArrayList<>(); // List untuk menyimpan header yang gagal validasi
        List<Long> headerIds = invCountHeaders.stream() // Ambil semua ID header dari input
                .map(InvCountHeaderDTO::getCountHeaderId) // Ekstrak countHeaderId dari setiap DTO
                .filter(Objects::nonNull) // Hanya ambil ID yang tidak null
                .collect(Collectors.toList()); // Kumpulkan ke dalam daftar

        // Jika tidak ada ID valid, langsung kembalikan semua sebagai error
        if (headerIds.isEmpty()) {
            result.setErrorList(invCountHeaders); // Semua header dianggap error karena tidak ada ID
            return result; // Kembalikan hasil proses
        }

        // Ambil header yang valid dari database dalam satu query menggunakan MyBatis
        List<InvCountHeader> existingHeaders = invCountHeaderRepository.selectByIds(headerIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","))); // di ubah menjadi string

        // Peta untuk akses cepat header berdasarkan ID
        Map<Long, InvCountHeader> headerMap = existingHeaders.stream() // Stream dari daftar hasil query
                .collect(Collectors.toMap(InvCountHeader::getCountHeaderId, h -> h)); // Konversi ke Map dengan ID sebagai kunci

        // List untuk menyimpan ID dokumen yang bisa dihapus
        List<Long> removableIds = new ArrayList<>();
        Long currentUserId = DetailsHelper.getUserDetails().getUserId(); // Ambil ID pengguna saat ini

        // Iterasi header input untuk validasi
        for (InvCountHeaderDTO headerDTO : invCountHeaders) {
            InvCountHeader existingHeader = headerMap.get(headerDTO.getCountHeaderId()); // Cari header di database berdasarkan ID
            if (existingHeader == null) { // Jika header tidak ditemukan
                headerDTO.setErrorMsg("Header not found for ID: " + headerDTO.getCountHeaderId()); // Set pesan error
                errorList.add(headerDTO); // Tambahkan ke daftar error
            } else if (!"DRAFT".equalsIgnoreCase(existingHeader.getCountStatus())) { // Jika status bukan DRAFT
                headerDTO.setErrorMsg("Only DRAFT status can be deleted."); // Set pesan error
                errorList.add(headerDTO); // Tambahkan ke daftar error
            } else if (!currentUserId.equals(existingHeader.getCreatedBy())) { // Jika pengguna bukan pencipta dokumen
                headerDTO.setErrorMsg("Only the creator can delete the document."); // Set pesan error
                errorList.add(headerDTO); // Tambahkan ke daftar error
            } else {
                // Jika semua validasi lolos, tambahkan ID ke daftar removable
                removableIds.add(headerDTO.getCountHeaderId());
            }
        }

        // Jika ada dokumen yang valid untuk dihapus
        // TODO tambahkaan bila code sudah salah mau di apakah
        if (!removableIds.isEmpty()) {
            invCountHeaderRepository.deleteByIds(removableIds); // Hapus dokumen dengan batch query
        }

        // Set hasil proses
        result.setSuccessList(invCountHeaders.stream() // Filter header yang berhasil dihapus
                .filter(header -> removableIds.contains(header.getCountHeaderId())) // Hanya header dengan ID yang valid
                .collect(Collectors.toList())); // Kumpulkan ke dalam daftar sukses
        result.setErrorList(errorList); // Set daftar error
        return result; // Kembalikan hasil proses
    }


    @Override
    public InvCountHeaderDTO detail(Long countHeaderId) {
        // ga udh tambahin output di tambahin
        // Validate the input parameter to ensure it's not null
        if (countHeaderId == null) {
            throw new IllegalArgumentException("Count Header ID cannot be null.");
        }

        // Step 1: Fetch header data
        // Fetch the InvCountHeader entity from the repository using the provided ID
        InvCountHeader header = invCountHeaderRepository.selectByPrimary(countHeaderId);
        if (header == null) {
            // ganti IllegalArgumentException = com
            throw new IllegalArgumentException("Count Header not found for ID: " + countHeaderId);
        }

        // Step 2: Convert entity to DTO
        // Create a DTO object and copy properties from the entity
        InvCountHeaderDTO headerDTO = new InvCountHeaderDTO();
        BeanUtils.copyProperties(header, headerDTO);

        // Step 3: Fetch the isWMSWarehouse status
        // Determine if the associated warehouse is a WMS warehouse
        headerDTO.setIsWmsWarehouse(fetchIsWmsWarehouse(header.getWarehouseId()));

        // Step 4: Fetch the list of related InvCountLineDTO objects
        // Retrieve lines associated with the header ID and map them to DTOs
        List<InvCountLineDTO> countLineDTOList = invCountLineRepository.selectList(new InvCountLine() {{
                    setCountHeaderId(countHeaderId); // Filter lines by countHeaderId
                }}).stream()
                .map(line -> {
                    // Convert each InvCountLine entity to InvCountLineDTO
                    InvCountLineDTO lineDTO = new InvCountLineDTO();
                    BeanUtils.copyProperties(line, lineDTO);
                    return lineDTO;
                })
                .collect(Collectors.toList());
        headerDTO.setInvCountLineDTOList(countLineDTOList);

        // Step 5: Parse additional fields and set them in the DTO
        // Convert string fields to lists of UserDTO or specialized DTOs as needed
        headerDTO.setCounterList(parseCounterList(header.getCounterIds())); // Parse counter IDs
        headerDTO.setSupervisorList(parseSupervisorList(header.getSupervisorIds())); // Parse supervisor IDs
        headerDTO.setSnapshotMaterialList(parseSnapshotMaterialList(header.getSnapshotMaterialIds())); // Parse material snapshot IDs
        headerDTO.setSnapshotBatchList(parseSnapshotBatchList(header.getSnapshotBatchIds())); // Parse batch snapshot IDs

        return headerDTO; // Return the fully constructed DTO
    }

    /**
     * Fetch the isWMSWarehouse status for the given warehouse ID.
     */
    private Boolean fetchIsWmsWarehouse(Long warehouseId) {
        // Return null if warehouseId is not provided
        if (warehouseId == null) return null;

        // Retrieve warehouse entity using the repository
        InvWarehouse warehouse = invWarehouseRepository.selectByPrimary(warehouseId);

        // Return true if the warehouse has the isWMSWarehouse flag set to 1
        return warehouse != null && warehouse.getIsWmsWarehouse() == 1;
    }

    /**
     * Parse a comma-separated list of counter IDs into a list of UserDTO objects.
     */
    private List<UserDTO> parseCounterList(String counterIds) {
        // Return an empty list if the input is null or empty
        if (counterIds == null || counterIds.isEmpty()) {
            return Collections.emptyList();
        }

        // Split the string by commas and map each ID to a UserDTO
        return Arrays.stream(counterIds.split(","))
                .map(id -> {
                    UserDTO user = new UserDTO();
                    user.setId(Long.valueOf(id)); // Set the ID for each UserDTO
                    return user;
                })
                .collect(Collectors.toList());
    }

    /**
     * Parse a comma-separated list of supervisor IDs into a list of UserDTO objects.
     */
    private List<UserDTO> parseSupervisorList(String supervisorIds) {
        // Return an empty list if the input is null or empty
        if (supervisorIds == null || supervisorIds.isEmpty()) {
            return Collections.emptyList();
        }

        // Split the string by commas and map each ID to a UserDTO
        return Arrays.stream(supervisorIds.split(","))
                .map(id -> {
                    UserDTO user = new UserDTO();
                    user.setId(Long.valueOf(id)); // Set the ID for each UserDTO
                    return user;
                })
                .collect(Collectors.toList());
    }

    /**
     * Parse a string of snapshot material IDs into a list of SnapshotMaterialDTO objects.
     * Example Input: "1_item1,2_item2"
     * Example Output: [SnapshotMaterialDTO(id="1", code="item1"), SnapshotMaterialDTO(id="2", code="item2")]
     */
    private List<SnapshotMaterialDTO> parseSnapshotMaterialList(String snapshotMaterialIds) {
        // Return an empty list if the input is null or empty
        if (snapshotMaterialIds == null || snapshotMaterialIds.isEmpty()) {
            return Collections.emptyList();
        }

        // Split the string by commas and map each entry to a SnapshotMaterialDTO
        return Arrays.stream(snapshotMaterialIds.split(","))
                .map(entry -> {
                    String[] parts = entry.split("_"); // Split each entry into ID and Code
                    if (parts.length == 2) {
                        SnapshotMaterialDTO dto = new SnapshotMaterialDTO();
                        dto.setId(parts[0].trim()); // Set ID
                        dto.setCode(parts[1].trim()); // Set Code
                        return dto;
                    }
                    throw new IllegalArgumentException("Invalid snapshot material format: " + entry);
                })
                .collect(Collectors.toList());
    }

    /**
     * Parse a string of snapshot batch IDs into a list of SnapshotBatchDTO objects.
     * Example Input: "1,2,3"
     * Example Output: [SnapshotBatchDTO(batchId="1", batchCode="b1"), ...]
     */
    private List<SnapshotBatchDTO> parseSnapshotBatchList(String snapshotBatchIds) {
        // Return an empty list if the input is null or empty
        if (snapshotBatchIds == null || snapshotBatchIds.isEmpty()) {
            return Collections.emptyList();
        }

        // Split the string by commas and map each ID to a SnapshotBatchDTO
        return Arrays.stream(snapshotBatchIds.split(","))
                .map(batchId -> {
                    SnapshotBatchDTO dto = new SnapshotBatchDTO();
                    dto.setBatchId(batchId.trim()); // Set Batch ID
                    dto.setBatchCode("b" + batchId.trim()); // Create Batch Code with prefix "b"
                    return dto;
                })
                .collect(Collectors.toList());
    }



    @Override
    public InvCountInfoDTO orderExecution(List<InvCountHeaderDTO> orderExecutionHeaders) {
        InvCountInfoDTO result = new InvCountInfoDTO();

        // Step 1: Validasi penyimpanan counting order
        InvCountInfoDTO validationResult = manualSaveCheck(orderExecutionHeaders);

        // Jika ada error pada validasi penyimpanan, kembalikan hanya daftar error
        if (validationResult.getErrorList() != null && !validationResult.getErrorList().isEmpty()) {
            result.setErrorList(validationResult.getErrorList());
            result.setTotalErrorMsg("Validation failed for some records.");
            return result;
        }

        // Step 2: Simpan counting order yang valid
        this.manualSave(validationResult.getSuccessList());

        // Step 3: Validasi eksekusi counting order
        InvCountInfoDTO executionValidationResult = executeCheck(validationResult.getSuccessList());

        // Jika ada error pada validasi eksekusi, kembalikan hanya daftar error
        if (executionValidationResult.getErrorList() != null && !executionValidationResult.getErrorList().isEmpty()) {
            result.setErrorList(executionValidationResult.getErrorList());
            result.setTotalErrorMsg("Execution validation failed for some records.");
            return result;
        }

        // Step 4: Eksekusi counting order
        execute(executionValidationResult.getSuccessList());

        // Step 5: Sinkronisasi dengan WMS
        InvCountInfoDTO syncResult = countSyncWms(executionValidationResult.getSuccessList());

        // Jika ada error pada sinkronisasi WMS, kembalikan hanya daftar error
        if (syncResult.getErrorList() != null && !syncResult.getErrorList().isEmpty()) {
            result.setErrorList(syncResult.getErrorList());
            result.setTotalErrorMsg("WMS synchronization failed for some records.");
            return result;
        }

        // Jika semua langkah berhasil, kembalikan daftar sukses
        result.setSuccessList(syncResult.getSuccessList());
        result.setTotalErrorMsg("All records processed successfully.");
        return result;
    }


    @Override
    public InvCountInfoDTO executeCheck(List<InvCountHeaderDTO> invCountHeaders) {
        InvCountInfoDTO executeCheckResult = new InvCountInfoDTO();
        List<InvCountHeaderDTO> errorList = new ArrayList<>();
        List<InvCountHeaderDTO> successList = new ArrayList<>();

        Long userId = DetailsHelper.getUserDetails().getUserId();
        Long tenantId = DetailsHelper.getUserDetails().getTenantId();

        for (InvCountHeaderDTO headerExecuteCheck : invCountHeaders) {
            StringBuilder errorMessages = new StringBuilder();

            // a. Document status validation: Only draft status can execute
            if (!"DRAFT".equals(headerExecuteCheck.getCountStatus())) {
                errorMessages.append("Only documents in DRAFT status can be executed. ");
            }

            // b. Current login user validation: Only the document creator can execute
            if (!userId.equals(headerExecuteCheck.getCreatedBy())) {
                errorMessages.append("Only the document creator can execute the order. ");
            }

            // c. Value set validation
            if (headerExecuteCheck.getCountDimension() == null || headerExecuteCheck.getCountType() == null || headerExecuteCheck.getCountMode() == null) {
                errorMessages.append("Count dimension, type, and mode must be specified. ");
            }

            // d. Company, department, warehouse validation
            if (headerExecuteCheck.getCompanyId() == null) {
                errorMessages.append("Company ID is required. ");
            }
            if (headerExecuteCheck.getWarehouseId() == null) {
                errorMessages.append("Warehouse ID is required. ");
            }

            // e. On-hand quantity validation
            if (!validateOnHandQuantity(headerExecuteCheck, tenantId)) {
                errorMessages.append("Unable to query on-hand quantity data. ");
            }

            // Collect errors or add to success list
            if (errorMessages.length() > 0) {
                headerExecuteCheck.setErrorMsg(errorMessages.toString());
                errorList.add(headerExecuteCheck);
            } else {
                successList.add(headerExecuteCheck);
            }
        }

        // Set results
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

        // Check stock with available quantity > 0
        List<InvStock> stocks = invStockRepository.selectList(stockCriteria);
        return stocks.stream().anyMatch(stock -> stock.getAvailableQuantity().compareTo(BigDecimal.ZERO) > 0);
    }

    private Long parseBatchId(String snapshotBatchIds) {
        if (snapshotBatchIds == null || snapshotBatchIds.isEmpty()) {
            return null;
        }
        // Assuming the first batchId is sufficient for validation
        return Long.parseLong(snapshotBatchIds.split(",")[0]);
    }

    private Long parseMaterialId(String snapshotMaterialIds) {
        if (snapshotMaterialIds == null || snapshotMaterialIds.isEmpty()) {
            return null;
        }
        // Assuming the first materialId is sufficient for validation
        String[] parts = snapshotMaterialIds.split(",")[0].split("_");
        if (parts.length == 2) {
            return Long.parseLong(parts[0]);
        }
        throw new IllegalArgumentException("Invalid material ID format: " + snapshotMaterialIds);
    }


    /**
     * Melakukan operasi penghitungan, memperbarui status, dan mengelola data terkait.
     * Memastikan hanya draf yang dapat dieksekusi.
     */
    @Override
    public List<InvCountHeaderDTO> execute(List<InvCountHeaderDTO> invCountHeaders) {
        Long tenantId = DetailsHelper.getUserDetails().getTenantId();
        Long userId = DetailsHelper.getUserDetails().getUserId();

        for (InvCountHeaderDTO headerExecute : invCountHeaders) {
            // 1. Update the counting order status to "In counting"
            headerExecute.setCountStatus("PROCESSING");
            headerExecute.setLastUpdatedBy(userId);
            invCountHeaderRepository.updateByPrimaryKeySelective(headerExecute);

            // 2. Get stock data to generate row data
            List<InvCountLine> countLines = generateCountLines(headerExecute, tenantId);

            // Save generated count lines
            for (int i = 0; i < countLines.size(); i++) {
                InvCountLine countLine = countLines.get(i);
                countLine.setLineNumber(i + 1); // Set line number
                invCountLineRepository.insertSelective(countLine);
            }
        }

        return invCountHeaders;
    }



    private List<InvCountLine> generateCountLines(InvCountHeaderDTO header, Long tenantId) {
        List<InvCountLine> countLines = new ArrayList<>();

        // Create stock query criteria
        InvStock stockCriteria = new InvStock();
        stockCriteria.setTenantId(tenantId);
        stockCriteria.setCompanyId(header.getCompanyId());
        stockCriteria.setDepartmentId(header.getDepartmentId());
        stockCriteria.setWarehouseId(header.getWarehouseId());
        stockCriteria.setBatchId(parseBatchId((String) header.getSnapshotBatchIds()));
        stockCriteria.setMaterialId(parseMaterialId((String) header.getSnapshotMaterialIds()));

        // Fetch stock data where available quantity > 0
        List<InvStock> stocks = invStockRepository.selectList(stockCriteria).stream()
                .filter(stock -> stock.getAvailableQuantity().compareTo(BigDecimal.ZERO) > 0)
                .collect(Collectors.toList());

        // Generate count lines
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
            countLine.setUnitQty(null); // Default value
            countLine.setUnitDiffQty(null); // Default value
            countLine.setCounterIds(header.getCounterIds());
            countLines.add(countLine);
        }

        return countLines;
    }

    /**
     * Mensinkronkan data penghitungan dengan WMS jika berlaku. Memeriksa jenis gudang
     * dan memanggil API eksternal jika diperlukan.
     */
    @Override
    public InvCountInfoDTO countSyncWms(List<InvCountHeaderDTO> invCountHeaderList) {
        InvCountInfoDTO result = new InvCountInfoDTO();
        List<InvCountHeaderDTO> successList = new ArrayList<>();
        List<InvCountHeaderDTO> errorList = new ArrayList<>();

        Long tenantId = DetailsHelper.getUserDetails().getTenantId();

        for (InvCountHeaderDTO header : invCountHeaderList) {
            try {
                // a. Validasi warehouseId pada header
                if (header.getWarehouseId() == null) {
                    throw new IllegalArgumentException("Warehouse ID is missing for Count Header ID: " + header.getCountHeaderId());
                }

                // Query warehouse data
                InvWarehouse warehouse = invWarehouseRepository.selectOne(new InvWarehouse() {{
                    setWarehouseId(header.getWarehouseId());
                    setTenantId(tenantId);
                }});
                if (warehouse == null) {
                    throw new IllegalArgumentException("Warehouse not found for ID: " + header.getWarehouseId());
                }

                // b. Validasi atau inisialisasi data tambahan
                InvCountExtra syncStatusExtra = getOrCreateExtra(tenantId, header.getCountHeaderId(), "wms_sync_status", "SKIP");
                InvCountExtra syncMsgExtra = getOrCreateExtra(tenantId, header.getCountHeaderId(), "wms_sync_error_message", "");

                // c. Sinkronisasi jika warehouse adalah tipe WMS
                if (Boolean.TRUE.equals(warehouse.getIsWmsWarehouse())) {
                    // Buat payload API
                    StringBuilder payload = new StringBuilder();
                    payload.append("countHeaderId=").append(header.getCountHeaderId())
                            .append("&employeeNumber=21995");

                    if (header.getInvCountLineDTOList() != null) {
                        for (InvCountLineDTO line : header.getInvCountLineDTOList()) {
                            payload.append("&countOrderLineList=")
                                    .append("countLineId:").append(line.getCountLineId())
                                    .append(",materialId:").append(line.getMaterialId())
                                    .append(",unitCode:").append(line.getUnitCode())
                                    .append(",snapshotUnitQty:").append(line.getSnapshotUnitQty());
                        }
                    }

                    // Panggil API WMS melalui InterfaceInvokeSdk
                    ResponsePayloadDTO response = invokeInterface(
                            payload.toString(), // Parameter payload
                            "HZERO",            // Namespace API
                            "FEXAM_WMS",        // Server Code API
                            "fexam-wms-api.thirdAddCounting", // Interface Code
                            null                // Token
                    );

                    // d. Proses respon API
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
                    // Tandai dengan status SKIP jika bukan tipe WMS
                    syncStatusExtra.setProgramValue("SKIP");
                }

                // Simpan data tambahan
                invCountExtraRepository.insert(syncStatusExtra);
                invCountExtraRepository.insert(syncMsgExtra);
                successList.add(header);
            } catch (Exception e) {
                // Tangani error dan tambahkan ke daftar error
                header.setErrorMsg(e.getMessage());
                errorList.add(header);
            }
        }

        // Buat hasil sinkronisasi
        result.setSuccessList(successList);
        result.setErrorList(errorList);
        result.setTotalErrorMsg("Synchronization completed with " + errorList.size() + " errors.");

        return result;
    }


    private InvCountExtra getOrCreateExtra(Long tenantId, Long sourceId, String programKey, String defaultValue) {
        // Buat kriteria pencarian untuk data tambahan (extended table)
        InvCountExtra extraCriteria = new InvCountExtra();
        extraCriteria.setTenantId(tenantId);
        extraCriteria.setSourceId(sourceId);
        extraCriteria.setProgramKey(programKey);

        // Cari data tambahan yang sudah ada
        InvCountExtra existingExtra = invCountExtraRepository.selectOne(extraCriteria);
        if (existingExtra == null) {
            // Jika data tidak ditemukan, inisialisasi data baru
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


    public ResponsePayloadDTO invokeInterface(String payload, String namespace, String serverCode,
                                              String interfaceCode, String accessToken) {
        RequestPayloadDTO requestPayloadDTO = new RequestPayloadDTO();
        requestPayloadDTO.setPayload(payload);

        // Menambahkan header parameter
        Map<String, String> headerParams = new HashMap<>();
        headerParams.put("Authorization", "bearer " +
                (StringUtils.isBlank(accessToken) ? TokenUtils.getToken() : accessToken));
        requestPayloadDTO.setHeaderParamMap(headerParams);

        // Menambahkan path variable
        Map<String, String> pathVariableMap = new HashMap<>();
        pathVariableMap.put("organizationId", BaseConstants.DEFAULT_TENANT_ID.toString());
        requestPayloadDTO.setPathVariableMap(pathVariableMap);

        // Memanggil InterfaceInvokeSdk
        return invokeSdk.invoke(namespace, serverCode, interfaceCode, requestPayloadDTO);
    }


    @Override
    public InvCountHeaderDTO countResultSync(InvCountHeaderDTO countHeaderDTO) {
        Long tenantId = DetailsHelper.getUserDetails().getTenantId();

        // 1. Mandatory input verification
        if (countHeaderDTO.getCountHeaderId() == null) {
            throw new IllegalArgumentException("Count Header ID is required.");
        }
        if (countHeaderDTO.getInvCountLineDTOList() == null || countHeaderDTO.getInvCountLineDTOList().isEmpty()) {
            throw new IllegalArgumentException("Count Order Line List is required.");
        }

        // 2. Query the database based on counting order header data
        InvCountHeader existingHeader = invCountHeaderRepository.selectByPrimary(countHeaderDTO.getCountHeaderId());
        if (existingHeader == null) {
            throw new IllegalArgumentException("Count Header not found for ID: " + countHeaderDTO.getCountHeaderId());
        }

        // 3. Determine whether the warehouse is a WMS warehouse
        InvWarehouse warehouse = invWarehouseRepository.selectByPrimary(existingHeader.getWarehouseId());
        if (warehouse == null || Boolean.FALSE.equals(warehouse.getIsWmsWarehouse())) {
            throw new IllegalStateException("The current warehouse is not a WMS warehouse, operations are not allowed.");
        }

        // 4. Check data consistency
        List<InvCountLine> existingLines = invCountLineRepository.selectList(new InvCountLine() {{
            setCountHeaderId(countHeaderDTO.getCountHeaderId());
        }});

        Map<Long, InvCountLine> existingLineMap = existingLines.stream()
                .collect(Collectors.toMap(InvCountLine::getCountLineId, Function.identity()));

        for (InvCountLineDTO inputLine : countHeaderDTO.getInvCountLineDTOList()) {
            if (!existingLineMap.containsKey(inputLine.getCountLineId())) {
                throw new IllegalStateException("The counting order line data is inconsistent with the INV system, please check the data.");
            }
        }

        if (existingLines.size() != countHeaderDTO.getInvCountLineDTOList().size()) {
            throw new IllegalStateException("The number of rows is inconsistent with the INV system, please check the data.");
        }

        // 5. Update the line data
        for (InvCountLineDTO inputLine : countHeaderDTO.getInvCountLineDTOList()) {
            InvCountLine existingLine = existingLineMap.get(inputLine.getCountLineId());

            // Update fields
            existingLine.setUnitQty(inputLine.getUnitQty());
            existingLine.setUnitDiffQty(inputLine.getUnitDiffQty());
            existingLine.setRemark(inputLine.getRemark());
            existingLine.setLastUpdatedBy(DetailsHelper.getUserDetails().getUserId());
            existingLine.setLastUpdateDate(new Date());

            invCountLineRepository.updateByPrimaryKeySelective(existingLine);
        }

        // 6. Return success response
        countHeaderDTO.setStatus("S");
        countHeaderDTO.setErrorMsg(null);

        return countHeaderDTO;
    }


    @Override
    public InvCountInfoDTO orderSubmit(List<InvCountHeaderDTO> orderSubmitHeaders) {
        // Langkah 1: Validasi data menggunakan counting order save verification
        InvCountInfoDTO saveValidationResult = manualSaveCheck(orderSubmitHeaders);

        // Langkah 2: Simpan data hanya bila validasi berhasil
        if (saveValidationResult.getSuccessList() != null && !saveValidationResult.getSuccessList().isEmpty()) {
            this.manualSave(saveValidationResult.getSuccessList());
        } else {
            // Jika validasi gagal, kembalikan error
            return saveValidationResult;
        }

        // Langkah 3: Validasi submit counting order
        InvCountInfoDTO submitValidationResult = submitCheck(saveValidationResult.getSuccessList());

        // Jika validasi submit gagal, kembalikan error
        if (submitValidationResult.getErrorList() != null && !submitValidationResult.getErrorList().isEmpty()) {
            return submitValidationResult;
        }

        // Langkah 4: Submit counting order
        this.submit(submitValidationResult.getSuccessList());

        // Kembalikan hasil akhir
        InvCountInfoDTO finalResult = new InvCountInfoDTO();
        finalResult.setSuccessList(submitValidationResult.getSuccessList());
        finalResult.setErrorList(new ArrayList<>()); // Tidak ada error
        finalResult.setTotalErrorMsg("All counting orders have been successfully submitted.");
        return finalResult;
    }


    @Override
    public InvCountInfoDTO submitCheck(List<InvCountHeaderDTO> invCountHeaders) {
        InvCountInfoDTO result = new InvCountInfoDTO();
        List<InvCountHeaderDTO> successList = new ArrayList<>();
        List<InvCountHeaderDTO> errorList = new ArrayList<>();
        Long currentUserId = DetailsHelper.getUserDetails().getUserId();

        for (InvCountHeaderDTO header : invCountHeaders) {
            StringBuilder errorMessages = new StringBuilder();

            // Requery the database
            InvCountHeader existingHeader = invCountHeaderRepository.selectByPrimary(header.getCountHeaderId());
            if (existingHeader == null) {
                errorMessages.append("Document with ID ").append(header.getCountHeaderId()).append(" does not exist. ");
            }

            // 1. Check document status
            String status = existingHeader.getCountStatus();
            if (!"PROCESSING".equalsIgnoreCase(status) &&
                    !"WITHDRAWN".equalsIgnoreCase(status)) {
                errorMessages.append("Operation is only allowed for documents in PROCESSING, or WITHDRAWN status. ");
            }

            // 2. Current login user validation
            if (!currentUserId.equals(existingHeader.getSupervisorIds())) {
                errorMessages.append("Only the supervisor can submit the document. ");
            }

            // 3. Data integrity check
            List<InvCountLine> lines = invCountLineRepository.selectList(new InvCountLine() {{
                setCountHeaderId(existingHeader.getCountHeaderId());
            }});

            boolean hasEmptyUnitQty = lines.stream().anyMatch(line -> line.getUnitQty() == null);
            if (hasEmptyUnitQty) {
                errorMessages.append("There are data rows with empty count quantity. Please check the data. ");
            }

            boolean hasDiffWithoutReason = lines.stream().anyMatch(line -> line.getUnitDiffQty() != null && line.getReason() == null);
            if (hasDiffWithoutReason) {
                errorMessages.append("For rows with differences in counting, the reason field must be entered. ");
            }

            // Collect errors or add to success list
            if (errorMessages.length() > 0) {
                header.setErrorMsg(errorMessages.toString());
                errorList.add(header);
            } else {
                successList.add(header);
            }
        }

        // Set results
        result.setErrorList(errorList);
        result.setSuccessList(successList);
        result.setTotalErrorMsg("Total errors: " + errorList.size());
        return result;
    }

    /**
     * Mengirimkan perintah penghitungan untuk persetujuan, memulai proses alur kerja di mana dikonfigurasi.
     * Hanya header yang valid dan dapat disetujui yang dikirim.
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<InvCountHeaderDTO> submit(List<InvCountHeaderDTO> invCountHeaders) {
        if (invCountHeaders == null || invCountHeaders.isEmpty()) {
            throw new IllegalArgumentException("The list of count headers cannot be null or empty.");
        }

        List<InvCountHeader> invheaders = new ArrayList<>();
        Long tenantId = DetailsHelper.getUserDetails().getTenantId();
        Long userId = DetailsHelper.getUserDetails().getUserId();

        // Fetch workflow configuration
        String workflowFlag = profileClient.getProfileValueByOptions(
                tenantId,
                null,
                null,
                "FEXAM07.INV.COUNTING.ISWORKFLOW"
        );

        // Collect department IDs for mapping
        Set<Long> departmentIds = invCountHeaders.stream()
                .map(InvCountHeaderDTO::getDepartmentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, String> departmentCodeMap = fetchDepartmentCodes(departmentIds);

        Date currentTime = new Date();

        for (InvCountHeaderDTO header : invCountHeaders) {
            validateHeaderForSubmission(header);

            if ("1".equals(workflowFlag)) {
                // Start workflow process
                startWorkflowProcess(header, tenantId, userId, departmentCodeMap);
                invheaders.add(header);
            } else {
                // Update document status directly
                confirmDocumentStatus(header, currentTime, userId);
            }
        }

        // Batch update headers in the database
        invCountHeaderRepository.batchUpdateOptional(
                invheaders,
                InvCountHeader.FIELD_COUNT_STATUS,
                InvCountHeader.FIELD_WORKFLOW_ID,
                InvCountHeader.FIELD_APPROVED_TIME
        );

        return invCountHeaders;
    }

    private void validateHeaderForSubmission(InvCountHeaderDTO header) {
        if (header.getCountHeaderId() == null) {
            throw new IllegalArgumentException("Count Header ID cannot be null.");
        }

        InvCountHeader existingHeader = invCountHeaderRepository.selectByPrimary(header.getCountHeaderId());
        if (existingHeader == null) {
            throw new IllegalArgumentException("Document with ID " + header.getCountHeaderId() + " does not exist.");
        }

        String status = existingHeader.getCountStatus();
        if (!Arrays.asList( "PROCESSING", "WITHDRAWN").contains(status.toUpperCase())) {
            throw new IllegalStateException("Operation is only allowed for documents in PROCESSING, or WITHDRAWN status.");
        }

        header.setTenantId(existingHeader.getTenantId());
        header.setCountNumber(existingHeader.getCountNumber());
    }

    private Map<Long, String> fetchDepartmentCodes(Set<Long> departmentIds) {
        if (departmentIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<IamDepartment> departments = iamDepartmentRepository.selectByIds(
                departmentIds.stream().map(String::valueOf).collect(Collectors.joining(","))
        );

        return departments.stream()
                .collect(Collectors.toMap(IamDepartment::getDepartmentId, IamDepartment::getDepartmentCode));
    }

    private void startWorkflowProcess(InvCountHeaderDTO header, Long tenantId, Long userId, Map<Long, String> departmentCodeMap) {
        String departmentCode = departmentCodeMap.get(header.getDepartmentId());
        if (departmentCode == null) {
            throw new CommonException("error.department.not.exist");
        }

        RunInstance instance = workflowClient.startInstanceByFlowKey(
                tenantId,
                "COUNTING_SUBMIT_FLOW",
                header.getCountNumber(),
                "EMPLOYEE",
                String.valueOf(userId),
                Collections.singletonMap("departmentCode", departmentCode)
        );

        header.setWorkflowId(instance.getInstanceId());
        header.setCountStatus("PROCESSING");
    }

    private void confirmDocumentStatus(InvCountHeaderDTO header, Date currentTime, Long userId) {
        header.setCountStatus("CONFIRMED");
        header.setApprovedTime(currentTime);
        header.setLastUpdatedBy(userId);
    }

    @Override
    public InvCountHeaderDTO approvalCallback(Long organizationId, WorkFlowEventDTO workFlowEventDTO) {
        // Validasi input
        if (workFlowEventDTO == null || workFlowEventDTO.getBusinessKey() == null) {
            throw new IllegalArgumentException("WorkFlowEventDTO or BusinessKey cannot be null.");
        }

        // Mengambil data dokumen berdasarkan Business Key
        InvCountHeader invCountHeader = invCountHeaderRepository.selectCountNumber(workFlowEventDTO.getBusinessKey());
        if (invCountHeader == null) {
            throw new CommonException(String.format("The document [%s] does not exist.", workFlowEventDTO.getBusinessKey()));
        }

        // Menginisialisasi DTO untuk hasil pengembalian
        InvCountHeaderDTO invCountHeaderDTO = new InvCountHeaderDTO();

        // Mengupdate dokumen berdasarkan input dari workflow
        invCountHeader.setWorkflowId(workFlowEventDTO.getWorkflowId());
        invCountHeader.setCountStatus(workFlowEventDTO.getDocStatus());

        // Update status dan waktu berdasarkan kondisi dokumen
        switch (workFlowEventDTO.getDocStatus().toUpperCase()) {
            case "PROCESSING":
                // Status dimulai (PROCESSING), Supervisor diatur ke operator saat ini
                invCountHeader.setSupervisorIds(String.valueOf(DetailsHelper.getUserDetails().getUserId()));
                break;

            case "APPROVED":


            case "WITHDRAWN":
                // Tidak ada tambahan logika untuk WITHDRAWN
                invCountHeader.setApprovedTime(workFlowEventDTO.getApprovedTime() != null ?
                        workFlowEventDTO.getApprovedTime() : new Date());
                break;

            default:
                throw new IllegalStateException(String.format("Invalid document status [%s]", workFlowEventDTO.getDocStatus()));
        }

        // Update data di repository
        invCountHeaderRepository.updateOptional(
                invCountHeader,
                InvCountHeader.FIELD_WORKFLOW_ID,
                InvCountHeader.FIELD_COUNT_STATUS,
                InvCountHeader.FIELD_APPROVED_TIME,
                InvCountHeader.FIELD_SUPERVISOR_IDS
        );

        // Salin data kembali ke DTO untuk hasil pengembalian
        BeanUtils.copyProperties(invCountHeader, invCountHeaderDTO);

        return invCountHeaderDTO;
    }

    @Override
    public List<InvCountHeaderDTO> countingOrderReportDs(InvCountHeaderDTO searchCriteria) {
        // Validasi parameter input
        if (searchCriteria == null) {
            throw new IllegalArgumentException("Search criteria cannot be null.");
        }

        // Inisialisasi filter untuk query
        Map<String, Object> filters = new HashMap<>();
        if (searchCriteria.getCountNumber() != null) {
            filters.put("countNumber", searchCriteria.getCountNumber());
        }
        if (searchCriteria.getCountStatus() != null) {
            filters.put("countStatus", searchCriteria.getCountStatus());
        }
        if (searchCriteria.getDepartmentId() != null) {
            filters.put("departmentId", searchCriteria.getDepartmentId());
        }
        if (searchCriteria.getWarehouseId() != null) {
            filters.put("warehouseId", searchCriteria.getWarehouseId());
        }
        if (searchCriteria.getCreatedDate() != null) {
            filters.put("createdDate", searchCriteria.getCreatedDate());
        }

        // Query database untuk mendapatkan data counting order
        List<InvCountHeader> countHeaders = invCountHeaderRepository.selectByCriteria((InvCountHeaderDTO) filters);

        // Konversi hasil query ke DTO
        List<InvCountHeaderDTO> countHeaderDTOs = countHeaders.stream()
                .map(header -> {
                    InvCountHeaderDTO dto = new InvCountHeaderDTO();
                    BeanUtils.copyProperties(header, dto);

                    // Fetch dan set tambahan informasi terkait Counter dan Supervisor
                    dto.setCounterList(fetchCounterList(header.getCounterIds()));
//                    dto.setSupervisorList(header.getSupervisorIds()); // SupervisorList disimpan langsung sebagai String

                    return dto;
                })
                .collect(Collectors.toList());

        return countHeaderDTOs;
    }

    private List<UserDTO> fetchCounterList(String counterIds) {
        if (counterIds == null || counterIds.isEmpty()) {
            return Collections.emptyList();
        }

        return Arrays.stream(counterIds.split(","))
                .map(counterId -> {
                    UserDTO userDTO = new UserDTO();
                    userDTO.setId(Long.valueOf(counterId));
                    return userDTO;
                })
                .collect(Collectors.toList());
    }

}
