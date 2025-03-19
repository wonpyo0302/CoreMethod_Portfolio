package com.x2bee.api.common.app.service.popbill.cashbill;

import com.google.gson.JsonObject;
import com.popbill.api.CBIssueResponse;
import com.popbill.api.CashbillService;
import com.popbill.api.PopbillException;
import com.popbill.api.Response;
import com.popbill.api.cashbill.Cashbill;
import com.x2bee.api.common.app.dto.request.order.OrderGetItemRequest;
import com.x2bee.api.common.app.dto.request.popbill.ClaimProcessResultResponse;
import com.x2bee.api.common.app.dto.request.popbill.cashbill.*;
import com.x2bee.api.common.app.dto.response.popbill.PopbillCheckDownStateResponse;
import com.x2bee.api.common.app.dto.response.popbill.PopbillProviderBaseResponse;
import com.x2bee.api.common.app.dto.response.popbill.cashbill.*;
import com.x2bee.api.common.app.dto.response.popbill.tax.OrderGetItemResponse;
import com.x2bee.api.common.app.entity.EtEntrBase;
import com.x2bee.api.common.app.entity.OpPpblCrSngOrdInfo;
import com.x2bee.api.common.app.entity.OpShareInfo;
import com.x2bee.api.common.app.entity.popbill.PopbillCashbillInfo;
import com.x2bee.api.common.app.entity.popbill.PopbillLogInfo;
import com.x2bee.api.common.app.entity.popbill.SalesFailInfo;
import com.x2bee.api.common.app.enums.CashbillState;
import com.x2bee.api.common.app.enums.OM064;
import com.x2bee.api.common.app.enums.OM065;
import com.x2bee.api.common.app.enums.PR007;
import com.x2bee.api.common.app.repository.orderrodb.popbill.ProviderMapper;
import com.x2bee.api.common.app.repository.orderrwdb.OrderMapper;
import com.x2bee.api.common.app.repository.orderrwdb.popbill.PopbillTrxMapper;
import com.x2bee.api.common.app.service.popbill.PopbillCommonService;
import com.x2bee.common.base.exception.CommonException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * packageName    : com.x2bee.api.common.app.service.popbill
 * fileName       : PopbillCashbillCommonServiceImpl
 * author         : wonpyo
 * date           : 2024-11-05
 * description    :
 * 메소드 대체 항목
 * - GetIssueTargetOrder -> PopbillTrxMapper.checkSingleOrder
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 2024-11-05        wonpyo       최초 생성
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PopbillCashbillServiceImpl implements PopbillCashbillService {
    private final PopbillCommonService popbillCommonService;
    private final PopbillTrxMapper popbillTrxMapper;
    private final OrderMapper orderMapper;
    private final CashbillService cashbillService;
    private final ProviderMapper providerMapper;
    private final CashBillLogService cashBillLogService;

    @Value("${popbill.connectApiKey.cashbill}")
    private String connectKey;

    @Value("${popbill.hqCorpNum}")
    private String HQ_CORP_NUM;
    /**************************************/
    /********고객대상 현금영수증 - 공통영역 ********/
    /**************************************/
    /**************************************/

    /**
     * 회계 TFT 프로젝트 - 현금영수증 : 발행 목적으로 부여된 주문서의 발행 대상자 데이터 반환
     * date : 2024-12-16
     *
     * @ param ordNo : 주문 번호
     * @ return : null or OP_CR_ISU_TGT_INFO row (배열 타입으로)
     */
    public List<CashbillShareResponse> getShareInfo(String ordNo) {
        return popbillTrxMapper.getShareInfo(ordNo);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = {Exception.class, RuntimeException.class}, readOnly = false, value = "orderRwdbTxManager")
    public void setShareInfo(OpShareInfo opShareInfo) {
        opShareInfo = this.trimFields(opShareInfo);
        String isuGb = opShareInfo.getCrIsuGb();
        if ("10".equals(isuGb) || "30".equals(isuGb)) {
            opShareInfo.setIdefNo(opShareInfo.getOrdmnCellNo());
        } else if (isuGb.equals("20")) {
            opShareInfo.setIdefNo(opShareInfo.getBmanNo());
        } else {
            //todo 무증빙에 대한 코드 추가 예정
        }
        opShareInfo.setVaildYn("Y");

        popbillTrxMapper.insertShareInfo(opShareInfo); //신규 주문이면 insert, 기존 주문관련이면 update를 해줌
    }

    /**
     * 회계 TFT 프로젝트 - 현금영수증 : 발행 취소 요청
     * date : 2024-12-16
     *
     * @ param crSeq		: 발행 취소할 내부 DB 현금영수증 번호
     * @ return : Array(
     * CashbillCancelIssueResponse
     * )
     * @ Code 리스트
     * @	0 미만 : 팝빌 API 이슈이며, 이 때의 정확한 사유는 메시지를 확인
     * @	// 아래의 코드에 대한 메시지는 프론트 영역에 노출하지 말 것
     * @	0 : 예외 발생. 이 때의 정확한 사유는 메시지를 확인.
     * @	1 : 정상 처리 완료
     * @	2 : 유효하지 않은 현금영수증 정보
     * @	3 : 유효하지 않은 입점사
     * @	4 : 이미 취소 또는 삭제 처리된 영수증
     * @	5 : 이미 국세청 전송이 완료된 형수증 - 취소 현금영수증 발행 요망
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = {Exception.class, RuntimeException.class}, readOnly = false, value = "orderRwdbTxManager")
    public CashbillCancelIssueResponse cancelIssue(String crSeq) {
        log.debug("CancelIssue 호출");
        CashbillCancelIssueResponse cancelIssueResponse = new CashbillCancelIssueResponse(false, "핸들링되지 않은 에러", 0, null);
        CashbillInfoRequest request = new CashbillInfoRequest();
        request.setCrSeq(crSeq);
        PopbillCashbillInfo cashbillInfo = popbillTrxMapper.getCashbillInfo(request);
        if (cashbillInfo == null) {
            log.debug("Fail : 유효하지 않은 현금영수증 정보 : {}", crSeq);
            cancelIssueResponse.setCode(2);
            cancelIssueResponse.setMessage("유효하지 않은 현금영수증 정보");
            return cancelIssueResponse;
        }
        /** 대상 계산서의 취소 처리 요청 전 상태를 반환 */
        cancelIssueResponse.setStateBeforeCancel(cashbillInfo.getPpblIsuStat());
        /** 영수증을 발행한 가맹점에 대한 사업자번호 확인을 진행 */
        List<PopbillProviderBaseResponse> providerList = popbillTrxMapper.getProviderList(cashbillInfo.getEntrNo(), "");
        if (providerList == null || providerList.size() < 1) {
            log.debug("Fail : 유효하지 않은 입점사 정보 : {}", crSeq);
            cancelIssueResponse.setCode(3);
            cancelIssueResponse.setMessage("유효하지 않은 입점사 정보");
            return cancelIssueResponse;
        }
        PopbillProviderBaseResponse providerInfo = providerList.get(0);
        if (cashbillInfo.getPpblIsuStat().equals(OM065.CANCELISSUE) || cashbillInfo.getPpblIsuStat().equals(OM065.DELETE)) {
            log.debug("Fail : 이미 취소 또는 삭제 처리된 영수증 : {}", crSeq);
            cancelIssueResponse.setCode(4);
            cancelIssueResponse.setMessage("이미 취소 또는 삭제 처리된 영수증");
            return cancelIssueResponse;
        }
        if (cashbillInfo.getPpblIsuStat().equals(OM065.PROGRESSISSUE)) {
            log.debug("Fail : 이미 국세청 전송이 진행중인 영수증 - 취소 현금계산서 발행 요망 : {}", crSeq);
            cancelIssueResponse.setCode(5);
            cancelIssueResponse.setMessage("이미 국세청 전송이 진행중인 영수증 - 취소 현금계산서 발행 요망");
            return cancelIssueResponse;
        }
        if (cashbillInfo.getPpblIsuStat().equals(OM065.ISSUE)) {
            log.debug("Fail : 이미 국세청 전송이 완료된 영수증 - 취소 현금영수증 발행 요망 : {}", crSeq);
            cancelIssueResponse.setCode(5);
            cancelIssueResponse.setMessage("이미 국세청 전송이 완료된 영수증 - 취소 현금영수증 발행 요망");
            return cancelIssueResponse;
        }
        try {
            /** Standby 상태 : 국세청 전송 전 상태의 원발행 현금영수증에 대한 발행을 예약한 취소 현금영수증 건이라면 팝빌에 넘길 필요가 없음. */
            if (cashbillInfo.getPpblIsuStat().equals(OM065.STANDBY)) {
                log.debug("Success : 팝빌 발행예약 취소 성공 -> seq : {}", crSeq);
                //로그처리
                PopbillLogInfo popbillLogInfo = new PopbillLogInfo();
                popbillLogInfo.setCrSeq(crSeq);
                popbillLogInfo.setMessage("현금영수증 발행 예약 발행취소");

                cancelIssueResponse.setCode(1);
                cancelIssueResponse.setMessage("발행예약 취소 성공");
                cancelIssueResponse.setStateBeforeCancel(OM065.STANDBY.getCode());
                cancelIssueResponse.setSuccess(true);

                return cancelIssueResponse;
            }
            /**
             *  로컬 관리 상, 문제가 없어보인다면 실제로 처리 진행 (popbillcashbill_model.php -> line.560)
             *  $cancelResult = $this->PopbillCashbill->RevokeRegistIssue(str_replace("-", "", $providerInfo->info_num), $cashbillInfo->mgtKey, "TB0000023", "20230318", false, null, null, false, 1, null, null, null, null, null, null, null, null);
             */
            CBIssueResponse cbIssueResponse = cashbillService.revokeRegistIssue(
                    providerInfo.getBmanNo().replaceAll("-", ""), cashbillInfo.getMgrNo(), "TB0000023", "20230318"
                    , false, null, false, 1, null, null, null, null, null, null);
            cancelIssueResponse.setCode(cbIssueResponse.getCode());
            cancelIssueResponse.setMessage(cbIssueResponse.getMessage());

            // $returnResult["code"]가 1이 아니라면 발행 실패.
            if (cancelIssueResponse.getCode() != 1) {
                log.debug("Fail : 팝빌 발행 취소 API 실패 -> {} :: {}", cbIssueResponse.getCode(), cbIssueResponse.getMessage());
                return cancelIssueResponse;
            } else {
                /** 현금영수증 상태변경 */
                PopbillCashbillInfo updatePopbillCashbillInfo = new PopbillCashbillInfo();
                updatePopbillCashbillInfo.setCrSeq(crSeq);
                updatePopbillCashbillInfo.setPpblIsuStat(OM065.CANCELISSUE.getCode());
                updatePopbillCashbillInfo.setStatCd("400");
                updatePopbillCashbillInfo.setCnclIsuDt(LocalDateTime.now());
                popbillTrxMapper.updateCashbillInfo(updatePopbillCashbillInfo);

                log.debug("Success : 팝빌 발행 취소 API 성공 -> seq : {}", crSeq);
                //성공 로그처리
                PopbillLogInfo popbillLogInfo = new PopbillLogInfo();
                popbillLogInfo.setCrSeq(crSeq);
                popbillLogInfo.setMessage("현금영수증 발행취소");
                popbillTrxMapper.insertPopbillLog(popbillLogInfo);
            }

        } catch (PopbillException e) {
            log.error("팝빌 예외 발생 -> code: {}, message: {} ", e.getCode(), e.getMessage());
            throw new CommonException(e.getMessage());
        } catch (Exception e) {
            log.error("예외 발생 -> message: {} ", e.getMessage());
            throw new CommonException(e.getMessage());
        }

        cancelIssueResponse.setSuccess(true);
        cancelIssueResponse.setStateBeforeCancel(cashbillInfo.getPpblIsuStat());
        return cancelIssueResponse;
    }

    /**
     * 회계 TFT 프로젝트 - 현금영수증 : 해당 현금영수증이 포함되어 있는 최종 취소 현금영수증 번호를 반환
     *
     * @ param oriCrSeq					    : 현금영수증 번호
     * @ return : false or Array(				: false일 경우, 유효하지 않은 현금영수증을 대상으로 시도한 것으로 간주
     * "cashbillSeq" : int				: 최종 취소 현금영수증 번호. 관련된 취소 현금영수증이 없다면 oriCrSeq = return val
     * "canRevokeissue" : true / false	: 최종 취소 현금영수증에서 추가로 취소 현금영수증을 발행 가능한 상태인가?
     * )
     */
    private Map<String, Object> getLastInCancelGroup(String oriCrSeq) {
        GetLastInCancelGroupResponse response = popbillTrxMapper.getLastInCancelGroup(oriCrSeq);
        String cashbillSeq = "-1";
        boolean canRevokeissue = false;

        /** if ($getMaxCashbillQuery == false || $getMaxCashbillQuery->num_rows() != 1 || $maxCashbillInfo == null) */
        if (response == null) {
            return null;
        }
        String rootState = "";
        String rootStaeCode = "-1";

        if (response.getUprCrSeq() == null) {
            cashbillSeq = response.getCrSeq();
            rootState = response.getPpblIsuStat();
            rootStaeCode = response.getStatCd();
        } else {
            cashbillSeq = response.getUprCrSeq();
            rootState = response.getRootState();
            rootStaeCode = response.getRootStatecode();
        }
        /** 최종 세금계산서에서 수정 세금계산서를 추가로 발행할 수 있는지 여부 */
        if (rootState.equals(OM065.ISSUE.getCode()) && rootStaeCode.equals("304")) {
            canRevokeissue = true;
        }
        if (rootState.equals(OM065.REGISTISSUE.getCode()) && response.getTrnsNo() != null) {
            canRevokeissue = true;
        }
        Map<String, Object> map = new HashMap<>();
        map.put("cashbillSeq", cashbillSeq);
        map.put("canRevokeissue", canRevokeissue);

        return map;
    }

    /**
     * 회계 TFT 프로젝트 - 현금영수증 : 취소 현금영수증 발행
     * date : 2024-12-06
     *
     * @ param CashbillRevokeIssueRequest	: 취소 현금영수증 발행 DTO
     * - originCashbillSeq	: 취소 현금영수증의 원본이 되는 현금영수증 번호
     * - isPartCancel			: 부분취소 여부 (계약의 해지일 경우는 false로 처리)
     * - cancelSupplyVar	    : 취소 발행할 공급가액 기입 (0보다 커야 함, isPartCancel = false라면 무시됨)
     * - cancelTaxVar		: 취소 발행할 세액 기입 (0보다 커야 함, isPartCancel = false라면 무시됨)
     * - refundCode			: 환불일 경우 기입하여 취소 현금영수증과 환불내역을 연결 (> isPartCancel = true일 때 같이 기입)
     * - isForceStandbyIfNotIssuable : 원본 현금영수증이 국세청 전송 전일 경우, 해당 취소 현금영수증을 대기 상태로 임시 저장 후, 원본이 전송 통보될 경우 자동 발행 처리
     * @ return : Array(
     * CashbillRevokeReponse
     * )
     * @ Code 리스트
     * @	0 미만 : 팝빌 API 이슈이며, 이 때의 정확한 사유는 메시지를 확인
     * @	// 아래의 코드에 대한 메시지는 프론트 영역에 노출하지 말 것
     * @	0 : 예외 발생. 이 때의 정확한 사유는 메시지를 확인.
     * @	1 : 정상 처리 완료
     * @	2 : 취소 발행 불가 - 유효하지 않은 현금영수증을 대상으로 발행 시도
     * @	3 : 취소 발행 불가 - 원발행에 대한 최초 취소 현금영수증 또는 해당 현금영수증으로부터 생성된 취소 현금영수증 중, 최근의 영수증에서만 추가 취소 발행이 가능
     * @	4 : 취소 발행 불가 - 국세청 전송이 진행되지 않았거나, 유효한 상태가 아님
     * @	100 ~ : 부분취소일 경우의 금액 검증을 통과하지 못함. 메세지를 확인할 것
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = {Exception.class, RuntimeException.class}, readOnly = false, value = "orderRwdbTxManager")
    public CashbillRevokeReponse revokeIssue(CashbillRevokeIssueRequest request) {
        log.debug("RevokeIssue 호출");
        CashbillRevokeReponse revokeReponse = new CashbillRevokeReponse(
                false, 0, "핸들링되지 않은 에러", null
        );
        boolean success = false;
        try {
            boolean castedIsPartCancel = request.isPartCancel();
            int cancelSupply = request.getCancelSupplyVar();
            int cancelTax = request.getCancelTaxVar();

            if (castedIsPartCancel && cancelSupply <= 0 && cancelTax <= 0) {
                throw new CommonException("100", "부분취소 금액 제약 확인 : 취소할 공급가액 및 세액이 유효하지 않음 - supply : %d, tax : %d".formatted(cancelSupply, cancelTax));
            }
            Map<String, Object> getLastSeqResult = this.getLastInCancelGroup(request.getOriginCashbillSeq());
            String cashbillSeq = getLastSeqResult.get("cashbillSeq").toString();
            boolean canRevokeissue = (boolean) getLastSeqResult.get("canRevokeissue");

            if (getLastSeqResult == null || cashbillSeq.equals("-1")) {
                throw new CommonException("2", "Fail : 유효하지 않은 현금영수증을 대상으로 발행 시도");
            }

            if (!cashbillSeq.equals(request.getOriginCashbillSeq())) {
                throw new CommonException("3", "Fail : 원발행에 대한 최초 취소 현금영수증 또는 해당 현금영수증으로부터 생성된 취소 현금영수증 중, 최근의 영수증에서만 추가 취소 발행이 가능");
            }

            if (canRevokeissue == false && request.isForceStandbyIfNotIssuable() == false) {
                throw new CommonException("4", "Fail : 취소 발행 불가 - 국세청 전송이 진행되지 않았거나, 유효한 상태가 아님");
            }
            String singleOrdNo = popbillTrxMapper.checkSingleOrder(request.getOriginCashbillSeq());
            if (singleOrdNo == null && singleOrdNo.isEmpty()) {
                throw new CommonException("2", "Fail : 취소 발행 불가 - 유효하지 않은 현금영수증을 대상으로 발행 시도");
            }
            /**
             * $targetOrderSeq = -1;
             * 	if ($IssueTypeCheckResult["single_order_seq"] != null)
             * 	    $targetOrderSeq = $IssueTypeCheckResult["single_order_seq"];
             * 	else if (is_array($IssueTypeCheckResult["multiple_order_seq"]) && count($IssueTypeCheckResult["multiple_order_seq"]) > 0)
             * 		$targetOrderSeq = $IssueTypeCheckResult["multiple_order_seq"];
             * 	쓰이지도 않는	$targetOrderSeq 변수가 왜 있는건지 모르겠음
             */
            // String targetOrderSeq = singleOrdNo; php에서도 쓰지 않는 변수인데,,, 일단은 선언해놓고 주석처리함

            /** 원본 현금영수증 정보 획득하여 상태를 체크한다. */
            CashbillInfoRequest cashbillInfoRequest = new CashbillInfoRequest();
            cashbillInfoRequest.setCrSeq(request.getOriginCashbillSeq());
            PopbillCashbillInfo originCashbillDoc = popbillTrxMapper.getCashbillInfo(cashbillInfoRequest);
            if (originCashbillDoc == null) {
                throw new CommonException("2", "Fail : 취소 발행 불가 - 유효하지 않은 현금영수증을 대상으로 발행 시도");
            }
            /**
             * 원본 현금영수증이 전송 완료된 상태가 아니라면 아래에서 예약을 하도록 되어있음
             * 팝빌 발행상태 or 국세청 전송 상태 이외일 때만 제한한다.
             */
            List<String> stateList = Arrays.asList(OM065.REGISTISSUE.getCode(), OM065.PROGRESSISSUE.getCode(), OM065.ISSUE.getCode());
            if (!stateList.contains(originCashbillDoc.getPpblIsuStat())) {
                throw new CommonException("4", "Fail : 취소 발행 불가 - 원본 현금영수증이 팝빌 발행되어 예약이 가능하거나, 국세청 전송이 완료되어 즉시 취소 발행이 가능한 상태가 ");
            }

            if (!originCashbillDoc.getTaxTyp().equals("과세") && request.getCancelTaxVar() != 0) { //PR007.TAX.getCodeName() = 비과세 ,request.getCancelTaxVar() = 10000 ,
                throw new CommonException("101", "Fail : 부분취소 금액 제약 확인 : 과세 현금영수증이 아니었다면, 세액을 지정할 수 없음");
            }

            /** 공급자에 대한 휴폐업 조회를 진행하여 발행요청 거부를 판단 */
            PopbillCheckDownStateResponse CheckDownResult = popbillCommonService.checkDownState(originCashbillDoc.getIsuBmanNo());
            String[] stateVaild = {"1", "3"};
            if (!"Y".equals(CheckDownResult.getIsSuccess()) || !Arrays.asList(stateVaild).contains(CheckDownResult.getState())) {
                // 현재 Unix Timestamp
                long exeDtmTimStmp = Instant.now().getEpochSecond();
                // 현재 날짜 및 시간
                LocalDateTime exeDtm = LocalDateTime.ofInstant(Instant.ofEpochSecond(exeDtmTimStmp), ZoneId.systemDefault());
                String reason = CheckDownResult.getIsSuccess().equals("Y") ? "폐업된 입점사를 대상으로 발행 시도" : "해당 입점사의 휴폐업 조회 실패";
                SalesFailInfo salesFailInfo = SalesFailInfo.builder()
                        .entrNo(originCashbillDoc.getEntrNo())
                        .ordNo(originCashbillDoc.getOrdNo())
                        .refundCode(request.getRefundCode())
                        .exeDtm(exeDtm)
                        .exeDtmTimeStmp(exeDtmTimStmp)
                        .docType(OM064.CASHBILL_REVOKE.getCode())
                        .supplyPrice(request.getCancelSupplyVar())
                        .taxPrice(request.getCancelTaxVar())
                        .reason(reason).build();
                popbillTrxMapper.insertSalesFailproof(salesFailInfo);
                revokeReponse.setCode(6);
                revokeReponse.setErrorMessage(reason);
                return revokeReponse;
            }

            int maxInCashbillVar = Integer.parseInt(popbillTrxMapper.getMaxVar());
            String providerSeq = originCashbillDoc.getEntrNo();
            String mgtKey = popbillCommonService.createMgtKey("cashbill", maxInCashbillVar, providerSeq);
            /**
             * functions 모델 내에서 발행가능 금액 계산 시, 취소 현금영수증 한정으로 offset을 -1 곱 처리
             * 정상적으로 동작하게 하려면 취소금액을 그대로 양수로 입력할 것
             */
            PopbillCashbillInfo insertCashbillInfo = new PopbillCashbillInfo();
            insertCashbillInfo.setCrSeq(Integer.toString(maxInCashbillVar));
            insertCashbillInfo.setOrdNo(originCashbillDoc.getOrdNo());
            insertCashbillInfo.setEntrNo(originCashbillDoc.getEntrNo());
            insertCashbillInfo.setUprCrSeq(originCashbillDoc.getUprCrSeq() == null ? originCashbillDoc.getCrSeq() : originCashbillDoc.getUprCrSeq());
            insertCashbillInfo.setMgrNo(mgtKey);
            insertCashbillInfo.setUprMgrNo(originCashbillDoc.getMgrNo());
            insertCashbillInfo.setUprTrnsNo(originCashbillDoc.getTrnsNo());
            insertCashbillInfo.setIsuDtm(LocalDateTime.now());
            insertCashbillInfo.setIsuDt(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")));
            insertCashbillInfo.setUprIsuDt(originCashbillDoc.getIsuDt());
            insertCashbillInfo.setCnclIsuDt(LocalDateTime.now());
            insertCashbillInfo.setCnclIsuYn("Y");
            insertCashbillInfo.setSctnIsuYn(castedIsPartCancel == true ? "Y" : "N");
            insertCashbillInfo.setTaxTyp(originCashbillDoc.getTaxTyp());
            insertCashbillInfo.setOrdTotAmt(request.getCancelSupplyVar() + request.getCancelTaxVar());
            insertCashbillInfo.setOrdSupPrc(request.getCancelSupplyVar());
            insertCashbillInfo.setOrdTaxPrc(request.getCancelTaxVar());
            insertCashbillInfo.setClaimNo(request.getClaimNo());

            /** 검색기능을 위한 성능 개선용 플래그를 세팅. 원발행에서는 환불코드를 입력하지 않음 */
            insertCashbillInfo.setAdminIsuYn(request.getAdminIsuYn()); //TODO 관리자 권한에 따라 값 세팅 로직 추가필요
            insertCashbillInfo.setSngIsuYn(originCashbillDoc.getSngIsuYn());
            insertCashbillInfo.setSctnIsuYn(originCashbillDoc.getSctnIsuYn());
            insertCashbillInfo.setManualIsuYn(originCashbillDoc.getManualIsuYn());
            insertCashbillInfo.setRfdCd(request.getRefundCode());
            insertCashbillInfo.setIsuBmanNm(originCashbillDoc.getIsuBmanNm());
            insertCashbillInfo.setIsuBmanNo(originCashbillDoc.getIsuBmanNo());
            insertCashbillInfo.setPrchsrIdefNo(originCashbillDoc.getPrchsrIdefNo());
            insertCashbillInfo.setPrchsrName(originCashbillDoc.getPrchsrName());
            insertCashbillInfo.setPrsntPurps(originCashbillDoc.getPrsntPurps());
            insertCashbillInfo.setPpblIsuStat("");
            insertCashbillInfo.setStatCd("");

            /** 원본 현금영수증의 처리 상태가 국세청 전송 전일 때는 취소분을 발행 대기로 등록 */
            if (!canRevokeissue && request.isForceStandbyIfNotIssuable()) {
                insertCashbillInfo.setPpblIsuStat(OM065.STANDBY.getCode());
            }
            /** 영수증 데이터 및  맵핑 구조를 먼저 DB에 추가 */
            int insertedRows = popbillTrxMapper.insertCashbillInfo(insertCashbillInfo);
            String insertedId = insertCashbillInfo.getCrSeq();
            /** 영수증 데이터가 생성되지 않았다면 작업을 중단... */
            if (insertedRows < 1 || insertedId == null || insertedId.isEmpty()) {
                throw new CommonException("5", "Fail : 현금영수증 정보 생성 실패");
            }
            if (singleOrdNo != null && !singleOrdNo.isEmpty()) {
                /** 단일 주문 내역 매핑 */
                OpPpblCrSngOrdInfo opPpblCrSngOrdInfo = new OpPpblCrSngOrdInfo();
                opPpblCrSngOrdInfo.setCrSeq(insertedId);
                opPpblCrSngOrdInfo.setOrdNo(singleOrdNo);
                opPpblCrSngOrdInfo.setEntrNo(insertCashbillInfo.getEntrNo());
                opPpblCrSngOrdInfo.setSupPrce(request.getCancelSupplyVar());
                opPpblCrSngOrdInfo.setTaxPrce(request.getCancelTaxVar());
                int cashbillMapInsertQuery = popbillTrxMapper.insertOpPpblCrSngOrdInfo(opPpblCrSngOrdInfo);
                // 단일 주문 내역 맵핑이 진행되지 못했다면 작업을 중단
                if (cashbillMapInsertQuery < 1) {
                    throw new CommonException("5", "Fail : 현금영수증 맵핑 정보 생성 실패");
                }
            }

            /** DB 맵핑까지 성공했다면 현금영수증 발행 시도 */
            CBIssueResponse issueResult = cashbillService.revokeRegistIssue(
                    originCashbillDoc.getIsuBmanNo() //CorpNum
                    , mgtKey //MgtKey
                    , originCashbillDoc.getTrnsNo() // OrgConfirmNum
                    , originCashbillDoc.getIsuDt() //OrgTradeDate
                    , false // SMSSendYN
                    , null // Memo
                    , castedIsPartCancel // IsPartCancel
                    , 1 // CancelType
                    , Integer.toString(request.getCancelSupplyVar()) // SupplyCost
                    , Integer.toString(request.getCancelTaxVar()) //Tax
                    , null // ServiceFee
                    , Integer.toString(request.getCancelSupplyVar() + request.getCancelTaxVar()) //TotalAmount
                    , null // UserID
            );

            revokeReponse.setCode(issueResult.getCode());
            revokeReponse.setErrorMessage(issueResult.getMessage());

            // $returnResult["code"]가 1이 아니라면 발행 실패. 트랜잭션 롤백 진행
            if (issueResult.getCode() != 1) {
                throw new CommonException("Fail : 팝빌 취소발행 API 실패 -> %d :: %s".formatted(issueResult.getCode(), issueResult.getMessage()));
            }
            log.debug("Success : 팝빌 취소발행 API 성공 -> seq : %s , NTS 코드 : %s".formatted(insertedId, issueResult.getConfirmNum()));
            /** 국세청 번호를 업데이트 - 이 부분에서 실패하면 발행된 상태에서 데이터 손실이 발생하므로, 따로 진행해야 한다. 이건 실패해도 성공처리해야 함 */
            try {
                popbillTrxMapper.updateConfirmNum(insertedId, issueResult.getConfirmNum(), OM065.REGISTISSUE.getCode());
            } catch (Exception e) {
                log.error("국세청 번호 업데이트 에러 : {}", e.getMessage());
            }

            /** 로그 처리 */
            PopbillLogInfo popbillLogInfo = new PopbillLogInfo();
            popbillLogInfo.setCrSeq(insertedId);
            popbillLogInfo.setMessage("현금영수증 취소발행 : %s".formatted(castedIsPartCancel ? "전체취소" : "부분취소"));
            popbillTrxMapper.insertPopbillLog(popbillLogInfo);

            /** 확보한 영수증 데이터를 반환 */
            revokeReponse.setSuccess(true);
            CashbillInfoRequest infoRequest = new CashbillInfoRequest();
            infoRequest.setCrSeq(insertedId);
            PopbillCashbillInfo popbillCashbillInfo = popbillTrxMapper.getCashbillInfo(infoRequest);
            revokeReponse.setDocument(popbillCashbillInfo);
            success = true;
        } catch (PopbillException e) {
            revokeReponse.setErrorMessage(e.getMessage());
            throw new CommonException(String.valueOf(e.getCode()), e.getMessage());
        } catch (Exception e) {

            throw new CommonException(e.getMessage());
        } finally {
            cashBillLogService.cashBillRevokeLogInsertForClaim(request, revokeReponse, success);
        }
        return revokeReponse;
    }

    @Override
    public CashbillSendEmailResponse resendMail(CashbillSendEmailRequest request) {
        CashbillSendEmailResponse response = CashbillSendEmailResponse.init();
        String email = request.getEmail();
        String crSeq = request.getCrSeq();

        String regex = "^[_\\.0-9a-zA-Z-]+@([0-9a-zA-Z][0-9a-zA-Z-]+\\.)+[a-zA-Z]{2,6}$";
        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(email);

        if (!matcher.matches()) {
            response.setMessage("이메일 주소를 다시 확인해 주세요.");
            return response;
        }

        /** 영수증 유효성 체크 php:402 */
        PopbillCashbillInfo cashbillDoc = popbillTrxMapper.getCashbillInfoByCrSeq(crSeq);
        if (cashbillDoc == null) {
            response.setMessage("해당하는 현금영수증 정보가 없습니다.");
            return response;
        }
        /** 영수증을 발행한 가맹점에 대한 사업자번호 확인을 진행 php:412 */
        EtEntrBase providerInfoByEntrNo = providerMapper.getProviderInfoByEntrNo(cashbillDoc.getEntrNo());
        if (providerInfoByEntrNo == null) {
            response.setMessage("가맹점 정보가 없습니다.");
            return response;
        }

        try {
            Response sendResult = cashbillService.sendEmail(providerInfoByEntrNo.getBmanNo().replaceAll("-", ""), cashbillDoc.getMgrNo(), email);
            if (sendResult.getCode() == 1) {
                response.setCode(200);
                response.setMessage(String.format("정상 요청 완료 -> %s, email : %s로 재전송 완료", crSeq, email));
                return response;
            } else {
                response.setCode(200);
                response.setMessage("이메일 발송 요청 실패");
                return response;
            }
        } catch (PopbillException e) {
            response.setMessage(
                    String.format(
                            "Exception : 팝빌 예외 발생 -> code : %s, 메세지 : %s (%s %s)",
                            e.getCode(),
                            e.getMessage(),
                            cashbillDoc.getPrsntPurps(),
                            cashbillDoc.getMgrNo()
                    )
            );
            return response;
        } catch (Exception e) {
            response.setMessage(
                    String.format(
                            "Exception : 이메일 발송 예외 발생 -> code : %s, 메세지 : %s (%s %s)",
                            response.getCode(),
                            e.getMessage(),
                            cashbillDoc.getPrsntPurps(),
                            cashbillDoc.getMgrNo()
                    )
            );
            return response;
        }

    }

    @Override
    public List<CashbillRevokeIssueRequest> createRequestForRevokeIssueAfterClaim(ClaimProcessResultResponse response) {
        List<CashbillRevokeIssueRequest> returnReqList = new ArrayList<>();
        List<OrderPriceAfterDoClaimResponse> orderPriceAfterClaim = orderMapper.getOrderPriceAfterClaim(response.getOrdNo(), response.getClaimNo());
        for (OrderPriceAfterDoClaimResponse item : orderPriceAfterClaim) {
            CashbillRevokeIssueRequest request = new CashbillRevokeIssueRequest();
            request.setClaimNo(response.getClaimNo());
            request.setDateAfterClaim(item);
            returnReqList.add(request);
        }
        return returnReqList;
    }

    /**
     * 회계 TFT 프로젝트 - 현금영수증 : 팝빌 커넥트 서비스에 따른 상태 전환
     * ※ 무단변경에 따른 데이터 오염을 방지하기 위해, API KEY 방식의 헤더 검증을 통과한 요청만 처리
     * date : 2024-12-16
     *
     * @ param connectData			: 팝빌 커넥트 수신 본문
     * @ return : Array(
     * "result" 		=> "OK" or "FAIL",
     * "message" 	=> String (단, result가 OK일 때는 아에 없어지는 데이터)
     * )
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = {Exception.class, RuntimeException.class}, readOnly = false, value = "orderRwdbTxManager")
    public String setToIssueState(JsonObject jsonObject, HttpServletRequest request) {
        log.debug("현금영수증 - setToIssueState 호출");
        String result = "{'result':'OK'}";
        /** jsonObject 값
         * {
         *     "franchiseCorpNum": "1208633565",
         *     "mgtKey": "C20241202-00252-00000162",
         *     "stateDT": "20241202173358",
         *     "eventType": "NTS",
         *     "tradeDate": "20241202",
         *     "itemKey": "024120217335800001",
         *     "corpNum": "1208633565",
         *     "confirmNum": "TB0000030",
         *     "interOPYN": true,
         *     "ntssendDT": "20241203000000",
         *     "stateCode": 304,
         *     "ntsresultDT": "20241203091512",
         *     "ntsresultCode": "0000",
         *     "eventDT": "20241203091512",
         *     "issueDT": "20241202173358"
         * }
         */
        Map<String, Object> headersMap = popbillCommonService.HeaderUtil(request);
        String requestApiKey = headersMap.get("x-api-key").toString();
        if (!requestApiKey.equals(connectKey)) {
            log.debug("Fail : API Key가 일치하지 않음 : {}으로 요청이 확인됨", requestApiKey);
            log.debug("Header : {}", headersMap);
            return result;
        }

        if (jsonObject.get("mgtKey").isJsonNull()) {
            log.debug("Fail : mgtKey 값이 확인되지 않음");
            return result;
        }

        if (jsonObject.get("eventDT").isJsonNull()) {
            log.debug("Fail : eventDT 값이 확인되지 않음");
            return result;
        }

        String stateCode = jsonObject.get("stateCode").isJsonNull() ? "0" : jsonObject.get("stateCode").getAsString();

        switch (jsonObject.get("eventType").getAsString()) {
            case "Issue": // 발행
                return result;
            case "Cancel": // 발행취소
                // 발행 및 취소 동작에서 미리 처리하는 건이므로, 별 동작없이 패스 처리
                log.debug("PASS : 서비스 내에서 처리할 이벤트가 아님 : {} 타입 요청이 확인됨", jsonObject.get("eventType").getAsString());
                return result;
            case "NTS":
                if (stateCode.equals(CashbillState.SUCCESS.getCode()) && (jsonObject.get("ntssendDT").isJsonNull() || jsonObject.get("ntssendDT").isJsonNull())) {
                    log.debug("Fail : ntssendDT / ntsresultDT 값이 확인되지 않음");
                    return result;
                }
                /** if (in_array($stateCode, Array(300, 301, 302, 303, 304, 305)) == false && empty($connectData["ntsresultCode"]))
                 * 팝빌-현금영수증에서 뱉어주는 stateCode는 300,304,305가 전부인데,,, 위 as-is 분기의 존재 이유가 없어보임..
                 * 따라서 아래처럼 수정하였으나 문제가 생길시 걷어내야함
                 */
                if (jsonObject.get("ntsresultCode").isJsonNull()) {
                    log.debug("Fail : ntsresultCode 값이 확인되지 않음");
                    return result;
                }

                //데이터 생성
                PopbillCashbillInfo updatePopbillCashbillInfo = new PopbillCashbillInfo();
                PopbillLogInfo popbillLogInfo = new PopbillLogInfo();
                updatePopbillCashbillInfo.setStatCd(stateCode);

                if (stateCode.equals(CashbillState.SUCCESS.getCode())) {
                    // 국세청 전송 성공 : stateCode 반영, state를 Issue로 변경
                    updatePopbillCashbillInfo.setPpblIsuStat(OM065.ISSUE.getCode());
                    updatePopbillCashbillInfo.setTrnsDt(LocalDateTime.now());
                    String logMsg = String.format("[%s] 국세청 전송 완료 - 전송요청 시간 : %s, 전송완료 시간 : %s"
                            , jsonObject.get("eventDT").getAsString()
                            , jsonObject.get("ntssendDT").getAsString()
                            , jsonObject.get("ntsresultDT").getAsString()
                    );
                    popbillLogInfo.setMessage(logMsg);
                } else if (stateCode.equals(CashbillState.REGIST.getCode())) {
                    // 국세청 전송 절차 : stateCode만 반영, state는 RegistIssue를 유지
                    updatePopbillCashbillInfo.setPpblIsuStat(OM065.REGISTISSUE.getCode());
                    String logMsg = String.format("[%s] 국세청 전송 절차 진행 전 - 상태값 : %s으로 전환"
                            , jsonObject.get("eventDT").getAsString()
                            , stateCode
                    );
                    popbillLogInfo.setMessage(logMsg);
                } else if (stateCode.equals(CashbillState.FAIL.getCode())) {
                    // 국세청 전송 실패 : stateCode 반영, state를 FailIssue로 변경
                    updatePopbillCashbillInfo.setPpblIsuStat(OM065.REGISTISSUE.getCode());
                    String logMsg = String.format("[%s] 국세청 전송 실패 - 사유 코드 : %s"
                            , jsonObject.get("eventDT").getAsString()
                            , jsonObject.get("ntsresultCode").getAsString()
                    );
                    popbillLogInfo.setMessage(logMsg);
                } else {
                    /**  php-> (in_array($stateCode, Array(301, 302, 303)) == true) 부분
                     현금영수증 stateCode에는 301,302,303이 없는데,, 이 분기가 왜 있는지 모르겠음..
                     국세청 전송 절차 : stateCode만 반영, state는 RegistIssue를 유지
                     */
                    updatePopbillCashbillInfo.setPpblIsuStat(OM065.REGISTISSUE.getCode());
                    String logMsg = String.format("[%s] 국세청 전송 절차 진행 중 - 상태값 : %s으로 전환"
                            , jsonObject.get("eventDT").getAsString()
                            , stateCode
                    );
                    popbillLogInfo.setMessage(logMsg);
                }

                try {
                    CashbillInfoRequest cashbillInfoRequest = new CashbillInfoRequest();
                    cashbillInfoRequest.setMgrNo(jsonObject.get("mgtKey").getAsString());
                    PopbillCashbillInfo popbillCashbillInfo = popbillTrxMapper.getCashbillInfo(cashbillInfoRequest);
                    if (popbillCashbillInfo == null) {
                        throw new CommonException("Fail : mgtKey를 기반으로 하여 상태 갱신 대상을 찾지 못함 -1 : %s, 수기발행 기능을 개발할 때까지, 일단 응답으로는 OK 수용한다.".formatted(jsonObject.get("mgtKey").getAsString()));
                    }

                    if (popbillCashbillInfo.getPpblIsuStat().equals(OM065.ISSUE)) {
                        log.debug("PASS : 이미 처리된 건 : {}", jsonObject.get("mgtKey").getAsString());
                        return result;
                    }
                    /**
                     * $this->db->where("mgtKey", $connectData["mgtKey"]);
                     * $updateQuery = $this->db->update("fm_popbill_cashbill", $updateData);
                     */
                    updatePopbillCashbillInfo.setMgrNo(jsonObject.get("mgtKey").getAsString());
                    popbillTrxMapper.updateCashbillInfo(updatePopbillCashbillInfo);

                    popbillLogInfo.setCrSeq(popbillCashbillInfo.getCrSeq());
                    popbillTrxMapper.insertPopbillLog(popbillLogInfo);

                    /** 해당 현금영수증 관련으로 취소 현금영수증 발행이 예약되어 있다면, 자동 발행 진행 */
                    if (stateCode.equals(CashbillState.SUCCESS.getCode())) {

                    }
                } catch (CommonException e) {
                    log.debug(e.getMessage());
                    return result;
                } catch (Exception e) {
                    throw new CommonException(e.getMessage());
                }
            default:
                log.debug("PASS : 서비스 내에서 처리할 이벤트가 아님 : {}타입 요청이 확인됨", jsonObject.get("eventType").getAsString());
                return result;
        }

    }

    /**************************************/
    /********고객대상 현금영수증 - 공통영역 끝 ******/
    /**************************************/
    /**************************************/


    /**************************************/
    /********고객대상 현금영수증 - 단일발행 ********/
    /**************************************/
    /**************************************/

    /**
     * 회계 TFT 프로젝트 - 고객대상 단일 현금영수증 : 팝빌로 발행을 요청하고 해당 영수증에 대한 맵핑 구조 반영
     * 금액 일치 여부에 대해서는 검증하지 않으므로 주의할 것
     * date : 2024-12-16
     *
     * @ param PopbillSingleRegistRequest
     * - ordNo					: 주문 번호
     * - entrNo				: 입점사 (위탁자)
     * - supplyVar				: 발행 공급가
     * - taxVar				: 발행 세액
     * - taxation				: 과세 여부 (기본값 true)
     * @ return : null or Array(
     * PopbillSingleRegistResponse
     * )
     * @ ※ false : IsValidSingleOrder 확인 결과, 단일발행이 가능한 상태가 아님
     * @ Code 리스트
     * @	0 미만 : 팝빌 API 이슈이며, 이 때의 정확한 사유는 메시지를 확인
     * @	// 아래의 코드에 대한 메시지는 프론트 영역에 노출하지 말 것
     * @	0 : 예외 발생. 이 때의 정확한 사유는 메시지를 확인.
     * @	1 : 정상 처리 완료
     * @	2 : 금액 검증 실패
     * @	3 : 유효하지 않은 입점사 정보를 확인
     * @ 4 : 올바르지 않은 품목 처리를 확인
     * @	5 : 현금영수증 or 맵핑 정보 생성에 실패
     * @ 6 : 수기발행의 경우, 사유를 반드시 입력해야 함
     **/
    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = {Exception.class, RuntimeException.class}, readOnly = false, value = "orderRwdbTxManager")
    public PopbillSingleRegistResponse singleRegistIssue(PopbillSingleRegistRequest request) throws Exception {
        PopbillSingleRegistResponse response = new PopbillSingleRegistResponse();
        boolean success = false;
        /** 이하 로직에서는 과세여부를 PR007코드로 다루지 않음 */
        if (request.getTaxation().equals(PR007.TAX.getCode())) {
            request.setTaxation("과세");
        } else {
            request.setTaxation("비과세");
        }
        int supply = Integer.parseInt(request.getSupplyVar());
        int tax = Integer.parseInt(request.getTaxVar());

        /** 1. 단일발행 가능여부 및 발행가능 금액을 확인 -> MakeSingleIssuablePrice 메소드 실행(popbillfunctions_model) */
        PopbillSinglePriceRequest singlePriceRequest = new PopbillSinglePriceRequest();
        singlePriceRequest.setOrdNo(request.getOrdNo());
        singlePriceRequest.setEntrNo(request.getEntrNo());
        PopbillSinglePriceResponse checkPrice = popbillCommonService.makeSingleIssuablePrice(singlePriceRequest);

        String isPartial = "N"; // 부분발행 플래그에 사용될 변수
        String isManually = request.getManuallyYn();

        if (checkPrice == null) {
            return null;
        }
        if (isManually.equals("Y") && request.getManuallyMemo().isBlank()) {
            response.setCode("6");
            response.setErrorMessage("수기발행의 경우, 사유를 반드시 입력해야 함");
            return response;
        }
        /** 2. 과세,면세,수기발행에 따른 로직처리 */
        int taxationSupply = Integer.parseInt(checkPrice.getTaxationSupply());
        int taxfreeSipply = Integer.parseInt(checkPrice.getTaxfreeSupply());
        /** 현금영수증은 1회 통합으로 진행한다. 세금이 있기만 하면 과세, 없으면 비과세로 처리 */
        int totalSupply = taxationSupply + taxfreeSipply;

        /** 이하 과세 로직 수행 */
        if (isManually.equals("N") && request.getTaxation().equals("과세")) {
            int taxationTax = Integer.parseInt(checkPrice.getTaxationTax()); /** taxationTax : $checkPrice["taxation_tax"] */
            if (totalSupply <= 0 || taxationTax <= 0) {
                throw new CommonException("2", "발행가능 금액이 없음 (과세 - supply : %d, tax : %d)".formatted(totalSupply, taxationTax));
            }

            supply = Math.min(supply, totalSupply);
            tax = Math.min(supply, taxationTax);

            if (supply <= 0 || tax <= 0) {
                throw new CommonException("2", "금액 입력에 문제 확인 (과세 - supply : %d, tax : %d)".formatted(supply, tax));
            }

            // 부분발행 플래그를 넣기 위한 작업
            if (supply != totalSupply || tax != taxationTax)
                isPartial = "Y";
        } else if (isManually.equals("N")) {
            /** 이하 면세 로직 수행 */
            if (totalSupply <= 0) {
                throw new CommonException("2", "발행 가능 금액이 없음 (면세 - supply : %d)".formatted(totalSupply));
            }

            supply = Math.min(supply, totalSupply);
            tax = 0;

            if (supply <= 0) {
                throw new CommonException("2", "금액 입력에 문제 확인 (면세 - supply : %d)".formatted(supply));
            }

            if (supply != totalSupply) // 부분발행 플래그를 넣기 위한 작업
                isPartial = "Y";
        } else {
            /** 이하 수기발행 로직 수행 */
            if (supply == 0 && tax == 0) {
                response.setIsSuccess("success");
                response.setCode("1");
                response.setErrorMessage("수기발행 처리 패스 (수기발행 - supply : %d, tax : %d)".formatted(supply, tax));
                return response;
            } else if (supply < 0 || tax < 0) {
                throw new CommonException("2", "금액 입력에 문제 확인 (수기발행 - supply : %d, tax : %d)".formatted(supply, tax));
            }
        }
        /**
         * 3. 유효성검증할 입점사 데이터 조회
         * 현금영수증은 위수탁 개념이 없으므로 단일 입점사만 확보해서 가맹점 정보로 사용하면 됨
         * 다만, 본사 정보 일부가 기입되어야 하므로, 세금계산서와 동일하게, 252도 같이 뽑아야 함
         */
        List<PopbillProviderBaseResponse> providerList = popbillTrxMapper.getProviderList("252", request.getEntrNo());
        if (providerList == null || providerList.size() < 1 || (!request.getEntrNo().equals("252") && providerList.size() < 2)) {
            throw new CommonException("3", "유효하지 않은 입점사 정보를 확인");
        }
        /** 4. 입점사 정보 세팅 */
        PopbillProviderBaseResponse franchiseInfo = new PopbillProviderBaseResponse(); // 실제 현금영수증에 기입될 입점사
        PopbillProviderBaseResponse mallInfo = new PopbillProviderBaseResponse(); // 아이스크림몰 정보용 입점사
        for (PopbillProviderBaseResponse providerInfo : providerList) {
            if (mallInfo.getEntrNo() == null && "252".equals(providerInfo.getEntrNo())) {
                mallInfo = providerInfo;
            }
            if (franchiseInfo.getEntrNo() == null || (franchiseInfo.getEntrNo() != null && !"252".equals(providerInfo.getEntrNo()))) {
                franchiseInfo = providerInfo;
            }
        }
        /** 5. 휴폐업 조회 (발행 요청 거부 판단) 운영에서 확인해봐야함...*/
        PopbillCheckDownStateResponse popbillCheckDownStateResponse = popbillCommonService.checkDownState(franchiseInfo.getBmanNo());
        String[] stateVaild = {"1", "3"};
        if (!"Y".equals(popbillCheckDownStateResponse.getIsSuccess()) || !Arrays.asList(stateVaild).contains(popbillCheckDownStateResponse.getState())) {
            // 현재 Unix Timestamp
            long exeDtmTimStmp = Instant.now().getEpochSecond();
            // 현재 날짜 및 시간
            LocalDateTime exeDtm = LocalDateTime.ofInstant(Instant.ofEpochSecond(exeDtmTimStmp), ZoneId.systemDefault());
            String reason = popbillCheckDownStateResponse.getIsSuccess().equals("Y") ? "폐업된 입점사를 대상으로 발행 시도" : "해당 입점사의 휴폐업 조회 실패";
            SalesFailInfo salesFailInfo = SalesFailInfo.builder()
                    .entrNo(franchiseInfo.getEntrNo())
                    .ordNo(request.getOrdNo())
                    .refundCode(null)
                    .exeDtm(exeDtm)
                    .exeDtmTimeStmp(exeDtmTimStmp)
                    .docType("20")
                    .supplyPrice(Integer.parseInt(request.getSupplyVar()))
                    .taxPrice(Integer.parseInt(request.getTaxVar()))
                    .reason(reason).build();
            popbillTrxMapper.insertSalesFailproof(salesFailInfo);
            response.setCode("6");
            response.setErrorMessage(reason);
            return response;
        }
        /** 7. 발행대상 정보 확보 */
        CashbillShareResponse shareInfo = popbillTrxMapper.getShareInfoByOrdNo(request.getOrdNo());
        /** 8. 품목명 및 상품명 획득 */
        OrderGetItemRequest orderGetItemRequest = OrderGetItemRequest.from(String.valueOf(request.getOrdNo()));
        List<OrderGetItemResponse> itemList = orderMapper.findByOrderGetItemResponseByRequest(orderGetItemRequest);
        String itemName = "";
        int itemCount = 0;
        /** 9. 가져온 품목 및 상품 유효성 검증 */
        // 초기 상품명 획득하고 품목 개수 카운트
        for (OrderGetItemResponse itemInfo : itemList) {
            String taxGbCd = itemInfo.getTaxGbCd();
            // 입점사가 일치하지 않으면 필터
            if (!itemInfo.getEntrNo().equals(request.getEntrNo())) continue;
            // 과세 요청인데 과세 상품이 아니면 필터
//            if (request.getTaxation().equals("과세") && PR007.lookup(taxGbCd) != PR007.TAX) continue;
            // 비과세 요청인데 과세 상품이라면 필터
            if (!request.getTaxation().equals("과세") && itemInfo.getTaxGbCd().equals("01")) continue;
            // itemName이 비어 있으면 현재 상품명으로 초기화
            if (itemName.isEmpty()) itemName = itemInfo.getGoodsNm();

            itemCount++;
        }
        // 품목명에 카운트 반영
        if (itemCount > 1)
            itemName = itemName + " 외 " + itemCount + "건";

        // 과세 요청에 상품이 없을 경우는 배송비만 진행하는 것일 수 있으므로 따로 조치 (100% 신뢰는 못함)
        if (itemName.isEmpty() && request.getTaxation().equals("과세"))
            itemName = "배송비";

        // 품목명이 세팅되지 않았다면 처리 거부
        if (request.getManuallyYn().equals("N") && itemName.isEmpty()) {
            response.setCode("4");
            response.setErrorMessage("올바르지 않은 품목 처리를 확인");
            return response;
        }


        /** 10. 팝빌 발행을 위한 Cashbill 객체 생성 */
        Cashbill cashbill = new Cashbill();
        int totalAmount = supply + tax;
        cashbill.setTradeType("승인거래"); // 발행 단계이므로 승인거래 고정
        if ("30".equals(shareInfo.getPrsntPurps())) {
            cashbill.setTradeUsage("소득공제용"); // 미신청이면 소득공제처리
        } else {
            cashbill.setTradeUsage(shareInfo.getPrsntPurpsNm()); //거래구분 소득공제용,지출증빙용
        }
        cashbill.setTaxationType(request.getTaxation()); // "과세","비과세"
        cashbill.setTotalAmount(Integer.toString(totalAmount)); //공급가격 + 세금
        cashbill.setSupplyCost(Integer.toString(supply)); // 공급가격
        cashbill.setTax(Integer.toString(tax)); // 세금
        cashbill.setServiceFee("0"); // 고정
        cashbill.setIdentityNum(shareInfo.getIdefNo()); // 식별번호
        cashbill.setCustomerName(shareInfo.getPrchsrNm()); // $shareInfo->customerName;

        if (isManually.equals("Y")) {
            // 수기발행 o
            cashbill.setItemName(request.getManuallyMemo().substring(0, 65));
        } else {
            // 수기발행 x
            cashbill.setItemName(itemName.substring(0, Math.min(itemName.length(), 65)));
        }
        cashbill.setOrderNumber("[" + mallInfo.getEntrNm() + "] " + request.getOrdNo()); // 입점사명+주문번호
        cashbill.setEmail(shareInfo.getPrchsrEmail()); // $shareInfo->email
        cashbill.setHp(shareInfo.getPrchsrCell()); // $shareInfo->hp

        if (mallInfo.getBmanNo().equals(franchiseInfo.getBmanNo())) {
            cashbill.setFranchiseCorpName(franchiseInfo.getEntrNm());
        } else if (mallInfo.getBmanNo().equals(franchiseInfo.getBmanNo())) {
            cashbill.setFranchiseCorpName(mallInfo.getEntrNm());
        } else {
            cashbill.setFranchiseCorpName("[" + mallInfo.getEntrNm() + "] " + franchiseInfo.getEntrNm());
        }

        cashbill.setFranchiseCorpNum(franchiseInfo.getBmanNo().replaceAll("-", ""));
        cashbill.setFranchiseCEOName(franchiseInfo.getRpstmnNm());
        String franchiseAddr = franchiseInfo.getZipAddr();
        franchiseAddr += franchiseInfo.getDtlAddr().isEmpty() ? "" : " " + franchiseInfo.getDtlAddr();
        cashbill.setFranchiseAddr(franchiseAddr);
        cashbill.setFranchiseTEL(franchiseInfo.getAempTelRgnNo() + "-" + franchiseInfo.getAempTelTxnoNo() + "-" + franchiseInfo.getAempTelEndNo());
        /**
         GetAll류 함수에서 반복처리로 요청하므로, 유닛 단위 처리가 되어야 함
         $this->db->trans_status로 결과 판단하지 말 것
         최대한 성능을 높이기 위해, 트랜잭션 실행 위치를 조정
         */
        SingleRegistLoadRequest singleRegistLoadRequest = new SingleRegistLoadRequest();
        singleRegistLoadRequest.setCashbill(cashbill);
        singleRegistLoadRequest.setPopbillSingleRegistRequest(request);
        singleRegistLoadRequest.setSupply(supply);
        singleRegistLoadRequest.setTax(tax);
        singleRegistLoadRequest.setIsPartial(isPartial);
        singleRegistLoadRequest.setIsManually(isManually);

        boolean isActiveTransaction = TransactionSynchronizationManager.isActualTransactionActive();
        log.error("트랜잭션 실행확인 : {}", isActiveTransaction);
        String isInsertSuccess = "N";
        String providerSeq = singleRegistLoadRequest.getPopbillSingleRegistRequest().getEntrNo();
        int currentTryCount;
        /** 11. 영수증 정보 세팅 후 DB에 저장 */
        String insertedID = "";
        for (currentTryCount = 0; currentTryCount < 5; currentTryCount++) {
            String maxVar = popbillTrxMapper.getMaxVar();
            int maxInCashbillVar = maxVar == null ? 1 : Integer.parseInt(maxVar);
            String mgtKey = popbillCommonService.createMgtKey("cashbill", maxInCashbillVar, providerSeq);
            singleRegistLoadRequest.getCashbill().setMgtKey(mgtKey);
            PopbillCashbillInfo insertCashbillInfo = this.setCashbillInsertData(singleRegistLoadRequest, maxInCashbillVar);
            int insertCashbillResult = popbillTrxMapper.insertCashbillInfo(insertCashbillInfo);
            insertedID = insertCashbillInfo.getCrSeq();
            if (insertCashbillResult < 1) {
                log.error("Fail : 현금영수증 정보 생성 {}번째 시도 실패", currentTryCount);
                isInsertSuccess = "N";
            } else {
                isInsertSuccess = "Y";
            }

            // 재시도 횟수 내 성공했을 경우 더 이상 진행하지 않음
            // 재시도 횟수 내 실패했을 경우, 1초의 지연 후, 다시 시도
            if (isInsertSuccess.equals("Y")) {
                break;
            } else {
                try {
                    Thread.sleep(1000);  // 1초 대기
                } catch (InterruptedException e) {
                    log.error("Fail : 현금영수증 재시도 대기 중 예외 발생 ({})", e.getMessage());  // 예외 처리
                }
            }

        }
        // 모든 재시도가 실패했을 경우, 최종 실패 판정 처리
        if (isInsertSuccess.equals("N")) {
            throw new CommonException("5", "현금영수증 정보 생성 실패");
        }
        /** 12. 단일 주문 내역 정보 세팅 후 DB 저장 */
        OpPpblCrSngOrdInfo opPpblCrSngOrdInfo = new OpPpblCrSngOrdInfo();
        opPpblCrSngOrdInfo.setCrSeq(insertedID);
        opPpblCrSngOrdInfo.setOrdNo(singleRegistLoadRequest.getPopbillSingleRegistRequest().getOrdNo());
        opPpblCrSngOrdInfo.setEntrNo(singleRegistLoadRequest.getPopbillSingleRegistRequest().getEntrNo());
        opPpblCrSngOrdInfo.setSupPrce(singleRegistLoadRequest.getSupply());
        opPpblCrSngOrdInfo.setTaxPrce(singleRegistLoadRequest.getTax());
        opPpblCrSngOrdInfo.setSysRegId(request.getSysRegId());
        opPpblCrSngOrdInfo.setSysModId(request.getSysModId());
        int insertOpPpblCrSngOrdInfoResult = popbillTrxMapper.insertOpPpblCrSngOrdInfo(opPpblCrSngOrdInfo);
        // 단일 주문 내역 맵핑이 진행되지 못했다면 작업을 중단
        if (insertOpPpblCrSngOrdInfoResult < 1) {
            throw new CommonException("5", "현금영수증 맵핑 정보 생성 실패");
        }

        try {
            /** 13. "RegistIssue" 현금영수증 발행 API 통신 */
            CBIssueResponse cbIssueResponse = cashbillService.registIssue(singleRegistLoadRequest.getCashbill().getFranchiseCorpNum(), singleRegistLoadRequest.getCashbill());
            response.setCode(Long.toString(cbIssueResponse.getCode()));
            response.setErrorMessage(cbIssueResponse.getMessage());
            if (!response.getCode().equals("1")) {
                throw new CommonException(response.getCode(), response.getErrorMessage());
            }
            /** 14. 발행 성공시 국세청 번호를 업데이트 */
            //실패하더라도 아래 로직이 실행되어야하여 별도 분리
            try {
                popbillTrxMapper.updateConfirmNum(insertedID, cbIssueResponse.getConfirmNum(), null);
            } catch (Exception e) {
                log.error("국세청 번호 업데이트 에러 : {}", e.getMessage());
            }


            /** 15. 로그처리 */
            PopbillLogInfo popbillLogInfo = new PopbillLogInfo();
            popbillLogInfo.setMessage("현금영수증 원발행 (단일발행)");
            popbillLogInfo.setCrSeq(insertedID);
            popbillLogInfo.setSysRegId(request.getSysRegId());
            popbillLogInfo.setSysModId(request.getSysModId());
            popbillTrxMapper.insertPopbillLog(popbillLogInfo);

            /** 16. 영수증 데이터를 반환 */
            response.setIsSuccess("Y");
            CashbillInfoRequest cashbillInfoRequest = new CashbillInfoRequest();
            cashbillInfoRequest.setCrSeq(insertedID);
            PopbillCashbillInfo popbillCashbillInfo = popbillTrxMapper.getCashbillInfo(cashbillInfoRequest);
            response.setDocument(popbillCashbillInfo);
            success = true;
            return response;
        } catch (PopbillException e) {
            response.setErrorMessage(e.getMessage());
            throw new RuntimeException(e.getMessage());
        } catch (Exception e) {
            response.setErrorMessage(e.getMessage());
            throw new RuntimeException(e.getMessage());
        } finally {
            cashBillLogService.cashBillSingleRegistLogInsert(request, response, success);
        }
    }

    /**
     * 주문 내 모든 현금영수증 발행 취소 요청
     * 간단한 상태 체크만 진행
     * date : 2024-12-06
     *
     * @ param ordNo	: 전체 발행 취소할 주문번호
     * @ return : false or Array(
     * [crSeq] => CancelIssue 결과,
     * [crSeq] => CancelIssue 결과, ...
     * )
     */
    @Override
    public List<CashbillCancelIssueResponse> singleCancelIssue(String ordNo) {
        log.debug("SingleCancelIssue 호출");
        List<CashbillCancelIssueResponse> cancelIssueResponseList = new ArrayList<>();
        List<String> cashbillSeqList = popbillTrxMapper.getCrSeqByOrdNo(ordNo);
        for (String cashbillSeq : cashbillSeqList) {
            cancelIssueResponseList.add(this.cancelIssue(cashbillSeq));
        }
        popbillTrxMapper.updateCashbillValidStatusToN(ordNo);

        return cancelIssueResponseList;
    }


    /**************************************/
    /********고객대상 현금영수증 - 단일발행 끝 ******/
    /**************************************/
    /**************************************/

    private PopbillCashbillInfo setCashbillInsertData(SingleRegistLoadRequest request, int maxInCashbillVar) {
        PopbillCashbillInfo insertCashbillInfo = new PopbillCashbillInfo();
        insertCashbillInfo.setCrSeq(String.valueOf(maxInCashbillVar));
        insertCashbillInfo.setOrdNo(request.getPopbillSingleRegistRequest().getOrdNo());
        insertCashbillInfo.setEntrNo(request.getPopbillSingleRegistRequest().getEntrNo());
        insertCashbillInfo.setMgrNo(request.getCashbill().getMgtKey());
        insertCashbillInfo.setIsuDt(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")));
        insertCashbillInfo.setIsuDtm(LocalDateTime.now());
        insertCashbillInfo.setSgtDtm(LocalDateTime.now());
        insertCashbillInfo.setTaxTyp(request.getCashbill().getTaxationType());
        insertCashbillInfo.setOrdTotAmt(request.getSupply() + request.getTax());
        insertCashbillInfo.setOrdSupPrc(request.getSupply());
        insertCashbillInfo.setOrdTaxPrc(request.getTax());
        insertCashbillInfo.setRmk(request.getCashbill().getItemName());
        insertCashbillInfo.setPpblIsuStat(OM065.REGISTISSUE.getCode());
        insertCashbillInfo.setStatCd("0");
        insertCashbillInfo.setSysRegId(request.getPopbillSingleRegistRequest().getSysRegId());
        insertCashbillInfo.setSysModId(request.getPopbillSingleRegistRequest().getSysModId());

        // 검색기능을 위한 성능 개선용 플래그를 세팅. 원발행에서는 환불코드를 입력하지 않음
        /** "isIssuedByAdmin" => !empty($this->uri) && !empty($this->uri->uri_string) && preg_match("/(^admin|selleradmin)/i", $this->uri->uri_string) */
        insertCashbillInfo.setAdminIsuYn("N"); //TODO 관리자 권한에 따라 값 세팅 로직 추가필요
        insertCashbillInfo.setSngIsuYn("Y");
        insertCashbillInfo.setSctnIsuYn(request.getIsPartial());
        insertCashbillInfo.setManualIsuYn(request.getIsManually());
        insertCashbillInfo.setIsuBmanNm(request.getCashbill().getFranchiseCorpName());
        insertCashbillInfo.setIsuBmanNo(request.getCashbill().getFranchiseCorpNum());
        insertCashbillInfo.setPrchsrIdefNo(request.getCashbill().getIdentityNum());
        insertCashbillInfo.setPrchsrName(request.getCashbill().getCustomerName());
        insertCashbillInfo.setPrsntPurps(request.getCashbill().getTradeUsage());
        return insertCashbillInfo;
    }

    private OpShareInfo trimFields(OpShareInfo opShareInfo) {
        Field[] fields = opShareInfo.getClass().getDeclaredFields();

        for (Field field : fields) {
            // String 타입 필드에 대해서만 처리
            if (field.getType().equals(String.class)) {
                field.setAccessible(true);
                try {
                    String value = (String) field.get(opShareInfo);  // 필드 값 가져오기
                    if (value != null) {
                        String trimmedValue = value.trim();
                        if (!trimmedValue.equals(value)) {
                            field.set(opShareInfo, trimmedValue);  // 공백이 제거된 값으로 설정
                        }
                    }
                } catch (IllegalAccessException e) {
                    e.printStackTrace();  // 접근 불가능한 경우 예외 처리
                }
            }
        }
        return opShareInfo;
    }
}
