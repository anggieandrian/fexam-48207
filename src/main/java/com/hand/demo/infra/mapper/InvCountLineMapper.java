package com.hand.demo.infra.mapper;

import com.hand.demo.api.dto.InvCountLineDTO;
import io.choerodon.mybatis.common.BaseMapper;
import com.hand.demo.domain.entity.InvCountLine;

import java.util.List;
import java.util.Map;

/**
 * (InvCountLine)应用服务
 *
 * @author Anggie A
 * @since 2024-12-19 16:57:11
 */
public interface InvCountLineMapper extends BaseMapper<InvCountLine> {
    /**
     * 基础查询
     *
     * @param invCountLine 查询条件
     * @return 返回值
     */
    List<InvCountLine> selectList(InvCountLine invCountLine);


}

