package com.hand.demo.infra.repository.impl;

import org.apache.commons.collections.CollectionUtils;
import org.hzero.mybatis.base.impl.BaseRepositoryImpl;
import org.springframework.stereotype.Component;
import com.hand.demo.domain.entity.InvCountLine;
import com.hand.demo.domain.repository.InvCountLineRepository;
import com.hand.demo.infra.mapper.InvCountLineMapper;

import javax.annotation.Resource;
import java.util.List;

/**
 * (InvCountLine)资源库
 *
 * @author Anggie A
 * @since 2024-12-19 16:57:11
 */
@Component
public class InvCountLineRepositoryImpl extends BaseRepositoryImpl<InvCountLine> implements InvCountLineRepository {
    @Resource
    private InvCountLineMapper invCountLineMapper;

    @Override
    public List<InvCountLine> selectList(InvCountLine invCountLine) {
        // Memanggil mapper untuk mengambil daftar line berdasarkan kriteria yang diberikan
        return invCountLineMapper.selectList(invCountLine);
    }

    @Override
    public InvCountLine selectByPrimary(Long countLineId) {
        InvCountLine invCountLine = new InvCountLine();
        invCountLine.setCountLineId(countLineId);
        List<InvCountLine> invCountLines = invCountLineMapper.selectList(invCountLine);
        if (invCountLines.size() == 0) {
            return null;
        }
        return invCountLines.get(0);
    }

}

