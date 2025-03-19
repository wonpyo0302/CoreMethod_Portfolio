package com.x2bee.api.common.app.service.popbill.cashbill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.x2bee.api.common.app.dto.request.popbill.cashbill.PopbillSingleRegistRequest;
import com.x2bee.api.common.app.dto.response.popbill.cashbill.CashbillRevokeReponse;
import com.x2bee.api.common.app.dto.response.popbill.cashbill.PopbillSingleRegistResponse;
import com.x2bee.api.common.app.entity.popbill.OpOrdMgrLog;
import com.x2bee.api.common.app.enums.OM058;
import com.x2bee.api.common.app.repository.orderrwdb.popbill.OpOrdMgrLogTrxMapper;
import com.x2bee.api.common.app.util.user.CustomUserDetailUtil;
import com.x2bee.common.base.context.CookieContextHolder;
import com.x2bee.common.base.exception.AppException;
import com.x2bee.common.base.messageconverter.CustomObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * @author shguddnr2@coremethod.co.kr
 * @version 1.0
 * @description
 * @since 25. 1. 20.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CashBillLogServiceImpl implements CashBillLogService {

    private final OpOrdMgrLogTrxMapper opOrdMgrLogTrxMapper;
    public static final String NON_MEMBER_NO = "999999999";

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = false, value = "orderRwdbTxManager")
    public String cashBillSingleRegistLogInsert(PopbillSingleRegistRequest request, PopbillSingleRegistResponse response, boolean isSuccess) {
        String ordMgrLogSeq = null;
        String ordNo = request.getOrdNo();
        try {
            ObjectMapper objectMapper = CustomObjectMapper.getInstance().get();
            OpOrdMgrLog opOrdMgrLog = new OpOrdMgrLog();
            opOrdMgrLog.setLogDt(LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE));
            opOrdMgrLog.setSiteNo(CookieContextHolder.getSiteNo());
            opOrdMgrLog.setLogGbCd(OM058.CR_LOG.getCd());
            opOrdMgrLog.setOrdNo(ordNo);
            opOrdMgrLog.setClaimNo("");
            opOrdMgrLog.setProcStatCd(isSuccess ? "0000" : String.valueOf(response.getCode()));
            opOrdMgrLog.setMbrNo(CustomUserDetailUtil.isNonMember() ? NON_MEMBER_NO : CustomUserDetailUtil.getCustomUserDetail().getMbrNo());
            opOrdMgrLog.setReqPrmtJson(objectMapper.writeValueAsString(request));
            if (isSuccess) {
                opOrdMgrLog.setRplyConts(objectMapper.writeValueAsString(response));
            } else {
                opOrdMgrLog.setErrorConts(objectMapper.writeValueAsString(response));
            }
            ordMgrLogSeq = opOrdMgrLogTrxMapper.insertOrderHistory(opOrdMgrLog);
        } catch (Exception e) {
            log.error("tax invoice log insert error. ordNo: {} ", ordNo, e);
        }
        return ordMgrLogSeq;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = {AppException.class, Exception.class, RuntimeException.class}, readOnly = false, value = "orderRwdbTxManager")
    public String cashBillRevokeLogInsertForClaim(Object request, CashbillRevokeReponse response, boolean isSuccess) {
        String ordMgrLogSeq = null;
        try {
            ObjectMapper objectMapper = CustomObjectMapper.getInstance().get();
            OpOrdMgrLog opOrdMgrLog = new OpOrdMgrLog();
            opOrdMgrLog.setLogDt(LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE));
            opOrdMgrLog.setSiteNo(CookieContextHolder.getSiteNo());
            opOrdMgrLog.setLogGbCd(OM058.CR_LOG.getCd());
            opOrdMgrLog.setOrdNo("");
            opOrdMgrLog.setClaimNo("");
            opOrdMgrLog.setProcStatCd(isSuccess ? "0000" : String.valueOf(response.getCode()));
            opOrdMgrLog.setMbrNo(CustomUserDetailUtil.isNonMember() ? NON_MEMBER_NO : CustomUserDetailUtil.getCustomUserDetail().getMbrNo());
            opOrdMgrLog.setReqPrmtJson(objectMapper.writeValueAsString(request));
            if (isSuccess) {
                opOrdMgrLog.setRplyConts(objectMapper.writeValueAsString(response));
            } else {
                opOrdMgrLog.setErrorConts(objectMapper.writeValueAsString(response));
            }
            ordMgrLogSeq = opOrdMgrLogTrxMapper.insertOrderHistory(opOrdMgrLog);
        } catch (Exception e) {
            log.error("tax invoice log insert error. ordNo: {} ", "", e);
        }
        return ordMgrLogSeq;
    }
}
