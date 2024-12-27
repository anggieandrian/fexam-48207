package com.hand.demo.infra.repository.impl;

import com.hand.demo.api.dto.InvCountHeaderDTO;
import org.apache.commons.collections.CollectionUtils;
import org.apache.ibatis.session.SqlSession;
import org.hzero.mybatis.base.impl.BaseRepositoryImpl;
import org.springframework.stereotype.Component;
import com.hand.demo.domain.entity.InvCountHeader;
import com.hand.demo.domain.repository.InvCountHeaderRepository;
import com.hand.demo.infra.mapper.InvCountHeaderMapper;

import javax.annotation.Resource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * (InvCountHeader)资源库
 *
 * @author Anggie A
 * @since 2024-12-19 16:56:44
 */
@Component
public class InvCountHeaderRepositoryImpl extends BaseRepositoryImpl<InvCountHeader> implements InvCountHeaderRepository {
    @Resource
    private InvCountHeaderMapper invCountHeaderMapper;
    @Resource
    private InvCountHeaderRepository invCountHeaderRepository;
    @Resource
    private SqlSession sqlSession;

    @Override
    public List<InvCountHeader> selectList(InvCountHeader invCountHeader) {
        if (invCountHeader == null) {
            throw new IllegalArgumentException("Search criteria cannot be null.");
        }

        // Gunakan MyBatis Mapper untuk query dinamis
        return invCountHeaderMapper.selectList(invCountHeader);
    }

    @Override
    public InvCountHeader selectByPrimary(Long countHeaderId) {
        InvCountHeader invCountHeader = new InvCountHeader();
        invCountHeader.setCountHeaderId(countHeaderId);
        List<InvCountHeader> invCountHeaders = invCountHeaderMapper.selectList(invCountHeader);
        if (invCountHeaders.size() == 0) {
            return null;
        }
        return invCountHeaders.get(0);
    }


    @Override
    public Optional<InvCountHeader> findById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("ID tidak boleh null.");
        }

        // Ambil data dari database berdasarkan ID
        Optional<InvCountHeader> header = invCountHeaderRepository.findById(id);
        if (!header.isPresent()) {
            System.err.println("Header dengan ID " + id + " tidak ditemukan.");
        }
        return header;
    }


    @Override
    public void deleteById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("ID tidak boleh null.");
        }

        // Periksa apakah data ada sebelum menghapus
        Optional<InvCountHeader> header = invCountHeaderRepository.findById(id);
        if (!header.isPresent()) {
            throw new IllegalArgumentException("Header dengan ID " + id + " tidak ditemukan.");
        }

        // Hapus data dari database
        invCountHeaderRepository.deleteById(id);

        System.out.println("Header dengan ID " + id + " berhasil dihapus.");
    }

    @Override
    public InvCountHeader selectCountNumber(String countNumber) {
        InvCountHeader invCountHeader = new InvCountHeader();
        invCountHeader.setCountNumber(countNumber);
        List<InvCountHeader> invCountHeaders = invCountHeaderMapper.selectList(invCountHeader);
        if (CollectionUtils.isEmpty(invCountHeaders)) {
            return null;
        }
        return invCountHeaders.get(0);
    }


    @Override
    public List<InvCountHeader> selectByMap(Map<String, Object> queryFilters) {
        // Ensure queryFilters is not null or empty
        if (queryFilters == null || queryFilters.isEmpty()) {
            throw new IllegalArgumentException("Query filters cannot be null or empty.");
        }

        // Build SQL query dynamically
        StringBuilder query = new StringBuilder("SELECT * FROM inv_count_header WHERE 1=1");

        // List to hold parameters for the query
        Map<String, Object> parameters = new HashMap<>();

        // Iterate through the filters and build the WHERE clause
        queryFilters.forEach((key, value) -> {
            query.append(" AND ").append(key).append(" = #{").append(key).append("}");
            parameters.put(key, value); // Add the parameter value
        });

        // Execute query using MyBatis
        try {
            return sqlSession.selectList("com.hand.demo.domain.repository.InvCountHeaderRepository.customSelectByMap", parameters);
        } catch (Exception e) {
            throw new RuntimeException("Error executing selectByMap query: " + e.getMessage(), e);
        }
    }



    @Override
    public List<InvCountHeader> selectByCriteria(InvCountHeaderDTO filterDTO) {
        // Validate input DTO
        if (filterDTO == null) {
            throw new IllegalArgumentException("Filter DTO cannot be null.");
        }

        // Use the HZero repository to dynamically build query filters
        List<InvCountHeader> resultList;

        // Build dynamic query filters using DTO fields
        Map<String, Object> queryFilters = new HashMap<>();
        if (filterDTO.getCountNumber() != null) {
            queryFilters.put("countNumber", filterDTO.getCountNumber());
        }
        if (filterDTO.getCountStatus() != null) {
            queryFilters.put("countStatus", filterDTO.getCountStatus());
        }
        if (filterDTO.getDepartmentId() != null) {
            queryFilters.put("departmentId", filterDTO.getDepartmentId());
        }
        if (filterDTO.getWarehouseId() != null) {
            queryFilters.put("warehouseId", filterDTO.getWarehouseId());
        }
        if (filterDTO.getCreatedDate() != null) {
            queryFilters.put("createdDate", filterDTO.getCreatedDate());
        }

        try {
            // Fetch results using HZero repository and criteria map
            resultList = invCountHeaderRepository.selectByMap(queryFilters);
        } catch (Exception e) {
            throw new RuntimeException("Error executing query: " + e.getMessage(), e);
        }

        return resultList;
    }

    @Override
    public void deleteByIds(List<Long> removableIds) {
        // 1. Validasi input
        if (removableIds == null || removableIds.isEmpty()) {
            throw new IllegalArgumentException("The list of IDs to delete cannot be null or empty.");
            // Jika daftar ID null atau kosong, lemparkan IllegalArgumentException.
        }

        // 2. Menggabungkan ID untuk digunakan dalam SQL query
        String joinedIds = removableIds.stream() // Gunakan stream untuk memproses daftar ID
                .map(String::valueOf) // Konversi setiap ID menjadi String
                .collect(Collectors.joining(",")); // Gabungkan ID dengan pemisah koma

        // untuk ngambil data dari header
        List<InvCountHeader> header = this.selectByIds(joinedIds);
        this.batchDeleteByPrimaryKey(header);

    }

}
