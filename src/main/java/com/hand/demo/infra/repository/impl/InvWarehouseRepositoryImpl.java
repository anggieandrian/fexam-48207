package com.hand.demo.infra.repository.impl;

import org.apache.commons.collections.CollectionUtils;
import org.hzero.mybatis.base.impl.BaseRepositoryImpl;
import org.springframework.stereotype.Component;
import com.hand.demo.domain.entity.InvWarehouse;
import com.hand.demo.domain.repository.InvWarehouseRepository;
import com.hand.demo.infra.mapper.InvWarehouseMapper;

import javax.annotation.Resource;
import java.util.List;

/**
 * (InvWarehouse)资源库
 *
 * @author Anggie A
 * @since 2024-12-19 16:57:27
 */
@Component
public class InvWarehouseRepositoryImpl extends BaseRepositoryImpl<InvWarehouse> implements InvWarehouseRepository {
    @Resource
    private InvWarehouseMapper invWarehouseMapper;

    @Override
    public List<InvWarehouse> selectList(InvWarehouse invWarehouse) {
        // Menggunakan `invWarehouseMapper` untuk mengambil daftar warehouse dari database
        return invWarehouseMapper.selectList(invWarehouse);
        // Mengembalikan semua warehouse yang sesuai dengan kriteria `invWarehouse`
    }

    @Override
    public InvWarehouse selectByPrimary(Long warehouseId) {
        // Membuat instance objek `InvWarehouse` untuk kriteria pencarian
        InvWarehouse invWarehouse = new InvWarehouse();
        invWarehouse.setWarehouseId(warehouseId); // Set `warehouseId` sebagai filter pencarian

        // Gunakan mapper untuk mendapatkan daftar warehouse yang cocok berdasarkan ID
        List<InvWarehouse> invWarehouses = invWarehouseMapper.selectList(invWarehouse);

        if (invWarehouses.size() == 0) {// Jika tidak ada hasil, kembalikan null (tidak ditemukan)
            return null; // Tidak ada warehouse yang ditemukan
        }
        return invWarehouses.get(0);// Kembalikan warehouse pertama dari daftar hasil
    }


}

