package com.hand.demo.domain.repository;

import com.hand.demo.api.dto.InvCountHeaderDTO;
import org.hzero.mybatis.base.BaseRepository;
import com.hand.demo.domain.entity.InvCountHeader;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * (InvCountHeader)资源库
 *
 * @author Anggie A
 * @since 2024-12-19 16:56:44
 */
public interface InvCountHeaderRepository extends BaseRepository<InvCountHeader> {
    /**
     * 查询
     *
     * @param invCountHeader 查询条件
     * @return 返回值
     */
    List<InvCountHeader> selectList(InvCountHeader invCountHeader);

    /**
     * 根据主键查询（可关联表）
     *
     * @param countHeaderId 主键
     * @return 返回值
     */
    InvCountHeader selectByPrimary(Long countHeaderId);

    Optional<InvCountHeader> findById(Long id);

    void deleteById(Long id);

    InvCountHeader selectCountNumber(String businessKey);

    List<InvCountHeader> selectByMap(Map<String, Object> queryFilters);

    List<InvCountHeader> selectByCriteria(InvCountHeaderDTO filterDTO);

    void deleteByIds(List<Long> removableIds);
}
