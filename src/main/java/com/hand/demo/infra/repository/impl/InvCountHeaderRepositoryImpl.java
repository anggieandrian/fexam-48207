package com.hand.demo.infra.repository.impl;

import org.apache.commons.collections.CollectionUtils;
import org.hzero.mybatis.base.impl.BaseRepositoryImpl;
import org.springframework.stereotype.Component;
import com.hand.demo.domain.entity.InvCountHeader;
import com.hand.demo.domain.repository.InvCountHeaderRepository;
import com.hand.demo.infra.mapper.InvCountHeaderMapper;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

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

    @Override
    public List<InvCountHeader> selectList(InvCountHeader invCountHeader) {
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
    public void deleteAllById(List<Long> removableIds) {
        // Validasi: Pastikan removableIds tidak null atau kosong
        if (removableIds == null || removableIds.isEmpty()) {
            throw new IllegalArgumentException("Daftar ID yang dapat dihapus tidak boleh kosong.");
        }

        // Iterasi setiap ID dan hapus dari database
        for (Long id : removableIds) {
            try {
                // Validasi: Pastikan ID tidak null
                if (id == null) {
                    throw new IllegalArgumentException("ID tidak boleh null.");
                }

                // Ambil data berdasarkan ID untuk validasi keberadaan
                Optional<InvCountHeader> existingHeaderOpt = invCountHeaderRepository.findById(id);
                if (!existingHeaderOpt.isPresent()) {
                    throw new IllegalArgumentException("Header dengan ID " + id + " tidak ditemukan.");
                }

                // Hapus data menggunakan repository
                invCountHeaderRepository.deleteById(id);

            } catch (Exception e) {
                // Tangani error dan lanjutkan ke ID berikutnya
                System.err.println("Gagal menghapus ID " + id + ": " + e.getMessage());
            }
        }

        // Log hasil penghapusan
        System.out.println("Proses penghapusan selesai untuk ID: " + removableIds);
    }


    @Override
    public List<InvCountHeader> findAllById(List<Long> headerIds) {
        return Collections.emptyList();
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


}

