package com.x2bee.api.common.app.service.xpay;

import com.x2bee.api.common.app.dto.request.xpay.XpayCancelPricePerProvider;
import com.x2bee.api.common.app.dto.request.xpay.XpayMakeDivideSlipRequest;
import com.x2bee.api.common.app.dto.response.xpay.ClaimXpayCancelInfoResponse;
import com.x2bee.api.common.app.dto.response.xpay.OrderInfoByXpayResponse;
import com.x2bee.api.common.app.dto.response.xpay.OrderItemPriceResponse;
import com.x2bee.api.common.app.dto.response.xpay.XpayMakeDivideSlipResponse;
import com.x2bee.api.common.app.entity.EtEntrAcctLinkInfo;
import com.x2bee.api.common.app.entity.EtEntrBase;
import com.x2bee.api.common.app.entity.xpay.XpayBase;
import com.x2bee.api.common.app.entity.xpay.XpayProductionInfo;
import com.x2bee.api.common.app.enums.OM007;
import com.x2bee.api.common.app.repository.orderrodb.popbill.ProviderMapper;
import com.x2bee.api.common.app.repository.orderrwdb.xpay.XpayMapper;
import com.x2bee.api.common.base.XpayUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * packageName    : com.x2bee.api.common.app.service.xpay
 * fileName       : XpayBulkInsertServiceImpl
 * author         : wonpyo
 * date           : 2025-02-06
 * description    :
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 2025-02-06        wonpyo       최초 생성
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class XpayBulkInsertServiceImpl {
    private final XpayMapper xpayMapper;
    private final XpayService xpayService;

    public void insertFailXpayData(){
        /** 매출전표 테이블에 누락된 주문 번호 조회 */
        List<String> failOrdNos = xpayMapper.failOrdNos();
//        List<String> failOrdNos = Arrays.asList("20250210O088182","20250210O088191", "20250221O215869", "20250217O127211", "20250210O088221");
        int insertCnt = 1;
        for(String failOrdNo : failOrdNos){
            log.error("{}번째 저장, 주문번호 : {}", insertCnt, failOrdNo);
            XpayMakeDivideSlipRequest request = new XpayMakeDivideSlipRequest();
            request.setOrdNo("20250210O088182");
            request.setSpecificMID("");
            this.makeDivideSlip(request,insertCnt);
            insertCnt++;
        }
    }

    private void makeDivideSlip(XpayMakeDivideSlipRequest request, int insertCnt){
        try {
            XpayMakeDivideSlipResponse response = xpayService.makeDivideSlip(request);
            log.error("매출전표 수기발행 성공- 주문번호:  {}, res {}", request.getOrdNo(), response);
        } catch (Exception e) {
            log.error("주문번호 : {}, {} 번째 저장 중 매출전표 데이터 적재 실패 사유 : {}",request.getOrdNo(),insertCnt, e.getMessage());
        }
    }

}
