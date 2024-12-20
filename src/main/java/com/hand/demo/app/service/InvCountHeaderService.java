package com.hand.demo.app.service;

import com.hand.demo.api.dto.InvCountHeaderDTO;
import com.hand.demo.api.dto.InvCountInfoDTO;
import io.choerodon.core.domain.Page;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import com.hand.demo.domain.entity.InvCountHeader;
import org.hzero.boot.interfaces.sdk.dto.ResponsePayloadDTO;

import java.util.List;

/**
 * (InvCountHeader)应用服务
 *
 * @author Anggie A
 * @since 2024-12-19 16:56:44
 */
public interface InvCountHeaderService {

    /**
     * 查询数据
     *
     * @param pageRequest     分页参数
     * @param invCountHeaders 查询条件
     * @return 返回值
     */
    Page<InvCountHeader> selectList(PageRequest pageRequest, InvCountHeader invCountHeaders);

    /**
     * 保存数据
     *
     * @param invCountHeaders 数据
     */
    void saveData(List<InvCountHeader> invCountHeaders);

    InvCountInfoDTO orderSave(List<InvCountHeaderDTO> orderSaveHeaders);

    List<InvCountHeaderDTO> manualSave(List<InvCountHeaderDTO> headers);

    InvCountInfoDTO manualSaveCheck(List<InvCountHeaderDTO> invCountHeaders);

    InvCountInfoDTO checkAndRemove(List<InvCountHeaderDTO> invCountHeaders);

    InvCountHeaderDTO detail(Long countHeaderId);

    InvCountInfoDTO orderExecution(List<InvCountHeaderDTO> orderExecutionHeaders);

    InvCountInfoDTO executeCheck(List<InvCountHeaderDTO> invCountHeaders);

    List<InvCountHeaderDTO> execute(List<InvCountHeaderDTO> invCountHeaders);

    InvCountInfoDTO countSyncWms(List<InvCountHeaderDTO> invCountHeaderList);

    ResponsePayloadDTO invokeInterface(String payload, String namespace, String serverCode,
                                       String interfaceCode, String accessToken);
}

