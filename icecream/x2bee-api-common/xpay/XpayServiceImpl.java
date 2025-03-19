package com.x2bee.api.common.app.service.xpay;

import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.x2bee.api.common.app.dto.request.popbill.ClaimProcessResultResponse;
import com.x2bee.api.common.app.dto.request.xpay.*;
import com.x2bee.api.common.app.dto.response.xpay.*;
import com.x2bee.api.common.app.entity.EtEntrAcctLinkInfo;
import com.x2bee.api.common.app.entity.EtEntrBase;
import com.x2bee.api.common.app.entity.xpay.XpayBase;
import com.x2bee.api.common.app.entity.xpay.XpayProductionInfo;
import com.x2bee.api.common.app.enums.OM007;
import com.x2bee.api.common.app.repository.orderrodb.popbill.ProviderMapper;
import com.x2bee.api.common.app.repository.orderrwdb.xpay.XpayMapper;
import com.x2bee.api.common.base.XpayUtil;
import com.x2bee.common.base.exception.CommonException;
import lgdacom.XPayClient.XPayClient;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.x2bee.api.common.base.config.XpayConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static java.lang.Thread.sleep;

/**
 * packageName    : com.x2bee.api.common.app.service.xpay
 * fileName       : XpayServiceImpl
 * author         : wonpyo
 * date           : 2024-10-31
 * description    :
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 2024-10-31        wonpyo       최초 생성
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class XpayServiceImpl implements XpayService{
    private final XpayConfig xPayClient;
    private final XpayMapper xpayMapper;
    private final ProviderMapper providerMapper;

    @Value("${lgdacomPath}")
    private String LG_PATH;


    @Override
    public void regist(XpaySyncRequest request) {

        String lgMid = request.getMid();
        String lgMkey = request.getMkey();
        String lgTimestamp = xPayClient.xPayClient().GetTimeStamp();
        String setSubmallId = request.getSubmallId();
        String lgdMethod = request.getLgdMethod();
        String configPath = request.getConfigPath();
        String pForm = request.getPForm();
        String lgHashdata = lgMid+setSubmallId+lgdMethod+lgTimestamp+lgMkey; //LGD_MID + LGD_SUBMALLID + LGD_METHOD + LGD_TIMESTAMP + 상점 Mertkey
        
        try {
           lgHashdata = new String(Base64.getEncoder().encode(MessageDigest.getInstance("SHA-256").digest(lgHashdata.getBytes(StandardCharsets.UTF_8))));
        } catch (NoSuchAlgorithmException e) {
           log.error(e.getMessage());
        }

    
        xPayClient.xPayClient().Init(configPath, pForm);
        xPayClient.xPayClient().Init_TX(lgMid);
        xPayClient.xPayClient().Set("LGD_TXNAME", "ReceiptSubMall");
        xPayClient.xPayClient().Set("LGD_MID", lgMid);
        xPayClient.xPayClient().Set("LGD_SUBMALLID", setSubmallId);
        xPayClient.xPayClient().Set("LGD_METHOD", lgdMethod);
        xPayClient.xPayClient().Set("LGD_COMPANYNAME", request.getXpayAccUserInfo().getEntrNm());
        xPayClient.xPayClient().Set("LGD_BUSINESSNUMBER", request.getXpayAccUserInfo().getBmanNo());
        xPayClient.xPayClient().Set("LGD_COMPANYADDRESS", request.getXpayAccUserInfo().getZipNo() + " " + request.getXpayAccUserInfo().getZipAddr() + " " + request.getXpayAccUserInfo().getDtlAddr());
        xPayClient.xPayClient().Set("LGD_TELNO", request.getXpayAccUserInfo().getAempCellSctNo()+request.getXpayAccUserInfo().getAempCellTxnoNo()+request.getXpayAccUserInfo().getAempCellEndNo());
        xPayClient.xPayClient().Set("LGD_CEONAME", request.getXpayAccUserInfo().getRpstmnNm());
        xPayClient.xPayClient().Set("LGD_TIMESTAMP", lgTimestamp);
        xPayClient.xPayClient().Set("LGD_HASHDATA", lgHashdata);
        xPayClient.xPayClient().TX();

        log.error("Xpay 가맹점 가입 결과 code : {}",xPayClient.xPayClient().m_szResCode);
        log.error("Xpay 가맹점 가입 결과 msg : {}",xPayClient.xPayClient().m_szResMsg);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = {Exception.class, RuntimeException.class}, readOnly = false, value="orderRwdbTxManager")
    public XpayMakeDivideSlipResponse makeDivideSlip(XpayMakeDivideSlipRequest request) throws Exception {
        XpayMakeDivideSlipResponse response = new XpayMakeDivideSlipResponse();
        log.debug("XPay 매출전표 - MakeDivideSlip 호출");

        int checkIsAlreadyRequested = xpayMapper.checkIsAlreadyRequested(request.getOrdNo());
        if (checkIsAlreadyRequested > 0) {
            log.debug("Fail : XPay 매출전표 - 이미 매출전표 분할이 처리된 주문서 -> {}",request.getOrdNo());
            response.setMsg("이미 매출전표 분할이 처리된 주문서");
            return response;
        }

        OrderInfoByXpayResponse orderInfoByXpayResponse = xpayMapper.getOrderInfoByOrdNo(request.getOrdNo());
        if(orderInfoByXpayResponse == null) {
            log.debug("Fail : XPay 매출전표 - 유효하지 않은 주문서 ->  {}",request.getOrdNo());
            response.setMsg("유효하지 않은 주문서");
            return response;
        }
        if(!"10".equals(orderInfoByXpayResponse.getOrdDtlGbCd())) {
            log.debug("Fail : XPay 매출전표 - 매출전표 발행이 불가능한 주문서 상태 ->  {}, 주문상태 : {}",request.getOrdNo(),orderInfoByXpayResponse.getOrdDtlGbCdNm());
            response.setMsg("매출전표 발행이 불가능한 주문서 상태 : %s".formatted(orderInfoByXpayResponse.getOrdDtlGbCdNm()));
            return response;
        }

        if(!"11".equals(orderInfoByXpayResponse.getPayWayCd())){
            log.debug("Fail : XPay 매출전표 - 매출전표 발행 대상이 아님 ->  {}, 결제수단 : {}",request.getOrdNo(),orderInfoByXpayResponse.getPayWayCdNm());
            response.setMsg("매출전표 발행 대상이 아님");
            return response;
        }

        String MID = getMatchedXpayConfigData(orderInfoByXpayResponse,"MID");

        /** 주문한 상품에 해당하는 입점사 정보 조회 => RETURN : List<EtEntrBase> */
        List<EtEntrBase> providerInfoList = providerMapper.getProviderHistoryListByOrdNo(request.getOrdNo());
        System.out.println("providerInfoList : " + providerInfoList);

        /** API 페이로드에 서브몰 계정을 적용하기 위한 쿼리 php:343 */
        List<EtEntrAcctLinkInfo> providerUserList = xpayMapper.getEntrAcctLinkInfoList(request.getOrdNo(),MID);

        /** 분할금액을 만들기 위한 금액 정보 쿼리 php:363 */
        List<OrderItemPriceResponse> orderItemPriceResponseList = xpayMapper.getOrderItemPriceInfoByOrdNo(request.getOrdNo());
        List<OrderItemPriceResponse> orderDeliPriceResponseList = xpayMapper.getOrderDeliveryPriceInfoByOrdNo(request.getOrdNo());

        /**
         * 학교예산_후불제 및 학교예산_신용카드가 아니라면, 배송비도 세금계산서 과세 항목으로 포함되어야 함
         * 학교예산_후불제 및 학교예산_신용카드 결제타입의 주문은 배송비를 집행하지 않으므로, 배송비에 할인 적용을 진행하지 않음.
         * 위 내용은 아래 후불제 로직을 사용 안함에 따라 구현하지 않음 php: 400
         */

        /** 전체 주문 내역에서 매출전표 분할처리할 금액을 산정 php:423 */
        for(OrderItemPriceResponse orderItemPriceResponse : orderItemPriceResponseList){
            String tmpCountOfPostfix = (orderItemPriceResponse.getItemCount() > 1)
                    ? " 외 " + (orderItemPriceResponse.getItemCount() - 1) + "건"
                    : "";
            String itemName = orderItemPriceResponse.getGoodsNm().concat(tmpCountOfPostfix);
            orderItemPriceResponse.setGoodsNm(itemName);

            String[] taxTypeArray = orderItemPriceResponse.getTaxTypes() == null ?
                    orderItemPriceResponse.getTaxTypes().split("\\|") :
                    new String[0];
            orderItemPriceResponse.setTaxTypeArr(taxTypeArray);
        }

        /** 후불제인 경우 확보된 배송비 리스트에서, 세액 금액을 추가 (전액 세액으로 반영한다.)
         *  위 내용은 후불제 개념 삭제로 해당 로직 구현하지않음. php:449
         */

        /** 과세 공급가에 총액 처리했던 부분을 세액으로 분리하면서 매출전표 분할을 위한 데이터 생성 php:457 */
        List<XpayProductionInfo> xpayProductionInfoList = new ArrayList<>();
        List<XpayBase> slipInfoForBatchInsert = new ArrayList<>();
        String reqMid = "";

        for (int i = 0; i < orderItemPriceResponseList.size(); i++) {
            int taxationPrice = orderItemPriceResponseList.get(i).getTaxationPrice();
            int taxFreeAmount = orderItemPriceResponseList.get(i).getTaxfreePrice();
            String entrNo = orderItemPriceResponseList.get(i).getEntrNo();
            for (int j = 0; j < orderDeliPriceResponseList.size(); j++) { //입점사별 배송비 과/면세 계산
                if(entrNo.equals(orderDeliPriceResponseList.get(j).getEntrNo())){
                    taxationPrice += orderDeliPriceResponseList.get(j).getTaxationPrice();
                    taxFreeAmount += orderDeliPriceResponseList.get(j).getTaxfreePrice();
                }
            }

            EtEntrAcctLinkInfo providerUser = new EtEntrAcctLinkInfo();
            for(EtEntrAcctLinkInfo pu : providerUserList){
                if(pu.getEntrNo().equals(entrNo)){
                    providerUser = pu;
                    break;
                }
            }
            EtEntrBase providerInfo = new EtEntrBase();
            for(EtEntrBase pi : providerInfoList){
                if(pi.getEntrNo().equals(entrNo)){
                    providerInfo = pi;
                    break;
                }
            }


            /**
             * 실질적으로 0원 처리된 경우, 매출증빙 분할에서 제외
             *  if (intval($priceRow["taxation_supply"]) + intval($priceRow["taxfree_supply"]) == 0)
             */
            if(taxFreeAmount + taxationPrice > 0){
                XpayProductionInfo xpayProductionInfo = XpayProductionInfo.builder()
                        .submallid(providerUser.getLinkAcctId())
                        .suborderid(orderInfoByXpayResponse.getOrdNo()+"-"+entrNo)
                        .productinfo(orderItemPriceResponseList.get(i).getGoodsNm())
                        .amount(Integer.toString(taxationPrice + taxFreeAmount))
                        .taxfreeamount(Integer.toString(taxFreeAmount))
                        .build();

                XpayBase xpayBase = new XpayBase();
                xpayBase.setOrdNo(orderInfoByXpayResponse.getOrdNo());
                xpayBase.setEntrNo(entrNo);
                xpayBase.setRegistDate(LocalDateTime.now());
                xpayBase.setTid(orderInfoByXpayResponse.getTrdNo());
                xpayBase.setLinkAcctTgt(
                        (request.getSpecificMID() == null || request.getSpecificMID().equals(""))
                                ? providerUser.getLinkAcctTgt() : request.getSpecificMID()
                );
                xpayBase.setLinkAcctId(providerUser.getLinkAcctId());
                xpayBase.setCustomerName(orderInfoByXpayResponse.getOrdManNm());
                xpayBase.setProductionName(orderItemPriceResponseList.get(i).getGoodsNm());
                xpayBase.setTotPrc(taxationPrice + taxFreeAmount);
                xpayBase.setTaxPrc(taxFreeAmount);
                xpayBase.setIsValid(
                        taxationPrice + taxFreeAmount > 0
                        ? "Y":"N"
                );
                xpayBase.setIsuBmanNm(providerInfo.getEntrNm());
                /** 수기발행할때만 주석 해제 **/
                xpayBase.setSysRegId("수기발행");
                xpayBase.setSysModId("수기발행");
                xpayBase.setRegistDate(orderInfoByXpayResponse.getOrdAccpDtm());

                xpayProductionInfoList.add(xpayProductionInfo);
                slipInfoForBatchInsert.add(xpayBase);

                if(reqMid == null || reqMid.equals("")){
                    reqMid = (request.getSpecificMID() == null || request.getSpecificMID().equals(""))
                            ? providerUser.getLinkAcctTgt() : request.getSpecificMID();
                }
            }

        }


        /** 이제, 매출전표 분리 요청 진행
            여기부터는 DB내에 있는 API 정보와 config 파일 내 API 정보의 환경이 정확하게 일치해야 함. // php : 500
        */
//        XpayUtil xpayUtil = new XpayUtil();
//        String configPath = LG_PATH != null ? LG_PATH : null;

//        Map map = xpayUtil.AccountXPaySalesslipApiInfo();
//        String pForm = map.get("platform").toString();
//        String apiMethod = "REGISTER"; // 고정
//        String mertKey = getMatchedXpayConfigData(orderInfoByXpayResponse,"MKEY");
//        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
//        String hashString = reqMid+orderInfoByXpayResponse.getTrdNo()+apiMethod+timestamp+mertKey;
//        String json = "";
//        Map<String, Object> xpayProductionInfo = new HashMap<>();
//        xpayProductionInfo.put("productinfo",xpayProductionInfoList);
//        try {
//            hashString = new String(Base64.getEncoder().encode(MessageDigest.getInstance("SHA-256").digest(hashString.getBytes(StandardCharsets.UTF_8))));
//            ObjectMapper objectMapper = new ObjectMapper();
//            json = objectMapper.writeValueAsString(xpayProductionInfo);
//        } catch (Exception e) {
//            log.error(e.getMessage());
//            throw new Exception(e);
//        }

//        XPayClient xPayClientForSlip = new XPayClient();
//        xPayClientForSlip.Init(configPath, pForm);
//        xPayClientForSlip.Init_TX(reqMid);
//        xPayClientForSlip.Set("LGD_TXNAME", "ReceiptTrxDivide");
//        xPayClientForSlip.Set("LGD_MID", reqMid);
//        xPayClientForSlip.Set("LGD_TID", orderInfoByXpayResponse.getTrdNo());
//        xPayClientForSlip.Set("LGD_METHOD", apiMethod);
//        xPayClientForSlip.Set("LGD_TIMESTAMP", timestamp);
//        xPayClientForSlip.Set("LGD_PRODUCTINFO", json);
//        xPayClientForSlip.Set("LGD_HASHDATA", hashString);
//        xPayClientForSlip.TX();

//        ObjectMapper objectMapper = new ObjectMapper();
//        String s = objectMapper.writeValueAsString(xPayClientForSlip);
//        log.info(s);

        try {
            /** DB 트랜잭션 시작 - PHP : 521 */
            int slipInsertQuery = xpayMapper.insertSalesslip(slipInfoForBatchInsert);
            if (slipInsertQuery < 0) {
//                log.error("Fail : XPay 매출전표 - XPay 매출전표 정보 생성 실패 : ",json);
//                throw new Exception("Fail : XPay 매출전표 - XPay 매출전표 정보 생성 실패 : %s".formatted(json));
                log.error("Fail : XPay 매출전표 - XPay 매출전표 정보 생성 실패 : ",request.getOrdNo());
                throw new Exception("Fail : XPay 매출전표 - XPay 매출전표 정보 생성 실패 : %s".formatted(request.getOrdNo()));
            }

            boolean isRequestSuccess = false;
            int currentTryCount = 0;

            for(currentTryCount = 0; currentTryCount < 3; currentTryCount++) {
//                String xpayCode = xPayClientForSlip.Response("LGD_RESPCODE",0);
//                String xpayMsg = xPayClientForSlip.Response("LGD_RESPMSG", 0);

//                if("0000".equals(xpayCode)){
                    isRequestSuccess = true;
                    response.setSuccess(true);
                    response.setMsg("처리 완료");
//                    log.debug("Success : XPay 매출전표 - 전액 분할 API: %d번째 시도에서 성공 ->code : %s, 메세지 : %s (%s, %s, %s)".formatted(
//                            currentTryCount + 1
//                            ,xpayCode
//                            ,xpayMsg
//                            ,request.getOrdNo()
//                            ,reqMid
//                            ,orderInfoByXpayResponse.getTrdNo()
//                    ));
//                }

                if (isRequestSuccess == true) {
                    break;
                }
//                else {
//                    log.error("Fail : XPay 매출전표 - 전액 분할 API %d번째 시도 실패 -> code : %s, 메세지 : %s (%s, %s, %s)".formatted(
//                            currentTryCount + 1
//                            ,xpayCode
//                            ,xpayMsg
//                            ,request.getOrdNo()
//                            ,reqMid
//                            ,orderInfoByXpayResponse.getTrdNo()
//                    ));
//                    sleep(1000);
//                }

                /** 모든 재시도가 실패했을 경우, 최종 실패 판정 처리 php: 560*/
//                if (isRequestSuccess == false) {
//                    response.setSuccess(false);
//                    response.setMsg(xpayMsg);
//                    log.error("Fail : XPay 매출전표 - 전액 분할 API %d번째 시도 실패 -> code : %s, 최종 메세지 : %s (%s, %s, %s)".formatted(
//                            currentTryCount
//                            ,xpayCode
//                            ,xpayMsg
//                            ,request.getOrdNo()
//                            ,reqMid
//                            ,orderInfoByXpayResponse.getTrdNo()
//                    ));
//                    log.error(json);
//                    throw new Exception("전액 분할 API 최종 실패");
//                }
            }
        } catch (Exception e) {
            log.error("Fail : XPay 매출전표 - 전액 분할 요청 실패 -> code : %s, 메세지 : %s (%s, %s, %s)".formatted(
                    "500", e.getMessage(), request.getOrdNo(), reqMid, orderInfoByXpayResponse.getTrdNo()
            ));
            throw new Exception(e);
        }


        return response;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = {Exception.class, RuntimeException.class}, readOnly = false, value="orderRwdbTxManager")
    public XpayCancelDivideSlipResponse cancelDivideSlip(XpayCancelDivideSlipRequest request) throws Exception {
        String ordNo = request.getOrdNo();
        String stepId = request.getStepId();
        String rfdCd = request.getRftCd();
        List<XpayCancelPricePerProvider> cancelPricePerProvider = request.getCancelAmountList();

        XpayCancelDivideSlipResponse response = new XpayCancelDivideSlipResponse();
        log.debug("XPay 매출전표 - cancelDivideSlip 호출");

        if(ordNo.isEmpty() || (stepId != null && !stepId.isEmpty() && cancelPricePerProvider != null && cancelPricePerProvider.size() < 1 ) || cancelPricePerProvider.size() < 1){
            ObjectMapper objectMapper = new ObjectMapper();
            String s = objectMapper.writeValueAsString(cancelPricePerProvider);
            log.error("Fail : XPay 매출전표 - 유효하지 않은 주문번호 또는 입점사 리스트 :{} , {}",ordNo,s);
            response.setSuccess(false);
            response.setMessage("유효하지 않은 주문번호 또는 입점사 리스트");
            response.setCode("9999");
            return response;
        }

        List<XpaySalesInfo> salesslipList;
        if(cancelPricePerProvider.size() < 1){
            salesslipList = xpayMapper.getSalesslipInfoList(ordNo, null);
        } else {
            salesslipList = xpayMapper.getSalesslipInfoList(ordNo, cancelPricePerProvider);
        }

        if(salesslipList.size() < 1){
            log.error("Fail : XPay 매출전표 - 유효하지 않은 매출전표 리스트");

            response.setSuccess(false);
            response.setMessage("유효하지 않은 매출전표 리스트");
            response.setCode("9999");
            return response;
        }

        if(!stepId.isEmpty() && (salesslipList.size() != cancelPricePerProvider.size())){
            log.error("Fail : XPay 매출전표 - 요청한 취소 내역과 매출전표 리스트의 수가 일치하지 않음");

            response.setSuccess(false);
            response.setMessage("요청한 취소 내역과 매출전표 리스트의 수가 일치하지 않음");
            response.setCode("9999");
            return response;
        }

        /** php : 655 */
        Map<String,XpayCancelPricePerProvider>cancelPricePerProviderMap = new HashMap<>();
        for (int i = 0; i < salesslipList.size(); i++) {
            String salesSlipEntrNo = salesslipList.get(i).getEntrNo();
            for (int j = 0; j < cancelPricePerProvider.size(); j++) {
                String cancelPricePerProviderEntrNo = cancelPricePerProvider.get(j).getEntrNo();
                if (salesSlipEntrNo.equals(cancelPricePerProviderEntrNo)) {
                    cancelPricePerProvider.get(j).setOriginAmount(salesslipList.get(i).getTotPrc());
                    cancelPricePerProvider.get(j).setOriginTaxfreeAmount(salesslipList.get(i).getTaxPrc());
                    cancelPricePerProvider.get(j).setUpdateCancelAmount(
                            salesslipList.get(i).getCnclTotPrc() + cancelPricePerProvider.get(j).getCancelAmount()
                    );
                    cancelPricePerProvider.get(j).setUpdateCancelTaxfreeAmount(
                            salesslipList.get(i).getCnclTaxPrc() + cancelPricePerProvider.get(j).getCancelTaxfreeAmount()
                    );
                    cancelPricePerProviderMap.put(salesSlipEntrNo,cancelPricePerProvider.get(j));
                }
            }
        }
        if(stepId.isEmpty()) {
            stepId = "_ALL_CANCEL_";
            for (int i = 0; i < salesslipList.size(); i++) {
                String salesSlipEntrNo = salesslipList.get(i).getEntrNo();
                for (int j = 0; j < cancelPricePerProvider.size(); j++) {
                    String cancelPricePerProviderEntrNo = cancelPricePerProvider.get(j).getEntrNo();
                    if (salesSlipEntrNo.equals(cancelPricePerProviderEntrNo)) {
                        cancelPricePerProvider.get(j).setCancelAmount(salesslipList.get(i).getTotPrc());
                        cancelPricePerProvider.get(j).setCancelTaxfreeAmount(salesslipList.get(i).getTaxPrc());
                        cancelPricePerProvider.get(j).setUpdateCancelTaxfreeAmount(salesslipList.get(i).getTaxPrc());
                        cancelPricePerProvider.get(j).setUpdateCancelAmount(salesslipList.get(i).getTotPrc());
                        cancelPricePerProviderMap.put(salesSlipEntrNo,cancelPricePerProvider.get(j));
                    }
                }
            }
        }
        /** php : 675 */
        List<XpayUpdateDataByCancel> updateDataArr = new ArrayList<>();
        List<XpayProductionInfo> xpayProductionInfoList = new ArrayList<>();
        String mid = null;
        String tid = null;
        for(XpaySalesInfo xpaySalesInfo: salesslipList){
            if(mid == null){
                mid = xpaySalesInfo.getLinkAcctTgt();
            }
            if(!mid.equals(xpaySalesInfo.getLinkAcctTgt())){
                log.error("Fail : XPay 매출전표 - 해당 주문 내 매출전표 중, MID가 불일치하는 전표가 확인됨");

                response.setSuccess(false);
                response.setMessage("Fail : 해당 주문 내 매출전표 중, MID가 불일치하는 전표가 확인됨");
                response.setCode("9999");
                return response;
            }

            if(tid == null){
                tid = xpaySalesInfo.getTid();
            }
            if(!tid.equals(xpaySalesInfo.getTid())){
                log.error("Fail : XPay 매출전표 - 해당 주문 내 매출전표 중, TID가 불일치하는 전표가 확인됨");

                response.setSuccess(false);
                response.setMessage("Fail : 해당 주문 내 매출전표 중, TID가 불일치하는 전표가 확인됨");
                response.setCode("9999");
                return response;
            }
            XpayCancelPricePerProvider xpayCancelPricePerProvider = cancelPricePerProviderMap.get(xpaySalesInfo.getEntrNo());
            if(!xpayAmountValidate(xpaySalesInfo.getEntrNo(),"취소 금액",xpayCancelPricePerProvider.getOriginAmount(),xpayCancelPricePerProvider.getUpdateCancelAmount())){
                response.setSuccess(false);
                response.setMessage("유효하지 않은 취소 금액");
                response.setCode("9999");
                return response;
            }
            if(!xpayAmountValidate(xpaySalesInfo.getEntrNo(),"취소 세액",xpayCancelPricePerProvider.getOriginTaxfreeAmount(),xpayCancelPricePerProvider.getUpdateCancelTaxfreeAmount())){
                response.setSuccess(false);
                response.setMessage("유효하지 않은 취소 세액");
                response.setCode("9999");
                return response;
            }

            String checkValid = "N";
            if(xpaySalesInfo.getTotPrc() != xpayCancelPricePerProvider.getUpdateCancelAmount()){
                checkValid = "Y";
            }
            if(xpaySalesInfo.getTaxPrc() != xpayCancelPricePerProvider.getUpdateCancelTaxfreeAmount()){
                checkValid = "Y";
            }
            if("_ALL_CANCEL_".equals(stepId)){
                checkValid = "N";
            }

            XpayUpdateDataByCancel xpayUpdateDataByCancel = XpayUpdateDataByCancel.builder()
                            .updateDate(LocalDateTime.now())
                            .cancelAmount(xpayCancelPricePerProvider.getUpdateCancelAmount())
                            .cancelTaxfreeAmount(xpayCancelPricePerProvider.getUpdateCancelTaxfreeAmount())
                            .stepId(stepId)
                            .rfdCd(rfdCd)
                            .valiYn(checkValid)
                            .build();

            XpayProductionInfo xpayProductionInfo = XpayProductionInfo.builder()
                    .submallid(xpaySalesInfo.getLinkAcctId())
                    .suborderid(xpaySalesInfo.getOrdNo()+"-"+xpaySalesInfo.getEntrNo())
                    .productinfo(xpaySalesInfo.getProductionName())
                    .amount(Integer.toString(xpayCancelPricePerProvider.getCancelAmount()))
                    .taxfreeamount(Integer.toString(xpayCancelPricePerProvider.getCancelTaxfreeAmount()))
                    .build();

            updateDataArr.add(xpayUpdateDataByCancel);
            xpayProductionInfoList.add(xpayProductionInfo);
        }

        /** 전체 취소가 아니라면, XPay 분할발행 부분취소를 준비
         여기부터는 DB내에 있는 API 정보와 config 파일 내 API 정보의 환경이 정확하게 일치해야 함. // php : 752
         */
        String xpayCode = "";
        String xpayMsg = "";
        String json = "";
        if(!"_ALL_CANCEL_".equals(stepId)){
            XpayUtil xpayUtil = new XpayUtil();
            String configPath = LG_PATH != null ? LG_PATH : null;
            Map map = xpayUtil.AccountXPaySalesslipApiInfo();
            String pForm = map.get("platform").toString();
            String apiMethod = "REGISTER"; // 고정
            String mertKey = getMatchedMkeyByMid(mid);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            String hashString = mid+tid+apiMethod+timestamp+mertKey;
            Map<String, Object> xpayProductionInfo = new HashMap<>();
            xpayProductionInfo.put("productinfo",xpayProductionInfoList);
            try {
                hashString = new String(Base64.getEncoder().encode(MessageDigest.getInstance("SHA-256").digest(hashString.getBytes(StandardCharsets.UTF_8))));
                ObjectMapper objectMapper = new ObjectMapper();
                json = objectMapper.writeValueAsString(xpayProductionInfo);
            } catch (Exception e) {
                log.error(e.getMessage());
                response.setMessage("xpayProductionInfo 객체 json 파싱중 예외 : " + json);
                response.setCode("9999");
                response.setSuccess(false);
                throw e;
            }

            XPayClient xPayClientForSlip = new XPayClient();
            xPayClientForSlip.Init(configPath, pForm);
            xPayClientForSlip.Init_TX(mid);
            xPayClientForSlip.Set("LGD_TXNAME", "ReceiptTrxDivide");
            xPayClientForSlip.Set("LGD_MID", mid);
            xPayClientForSlip.Set("LGD_TID", tid);
            xPayClientForSlip.Set("LGD_METHOD", apiMethod); // REGISTER
            xPayClientForSlip.Set("LGD_TIMESTAMP", timestamp);
            xPayClientForSlip.Set("LGD_PRODUCTINFO", json);
            xPayClientForSlip.Set("LGD_HASHDATA", hashString);
            xPayClientForSlip.TX(); // 실제로 취소요청 발생

            xpayCode = xPayClientForSlip.Response("LGD_RESPCODE",0);
            xpayMsg = xPayClientForSlip.Response("LGD_RESPMSG", 0);
            ObjectMapper objectMapper = new ObjectMapper();
            String s = objectMapper.writeValueAsString(xPayClientForSlip);
            log.info(s);
        }

        try {
            for (int i = 0; i < updateDataArr.size(); i++) {
                int updateResult = xpayMapper.updateSalesslip(updateDataArr.get(i));
                if(updateResult < 1){
                    log.error("Fail : XPay 매출전표 - 매출전표 분할 부분취소 갱신 실패 || 실패Object : {}",updateDataArr.get(i));
                    response.setCode("9999");
                    response.setSuccess(false);
                    response.setMessage("XPAY 매출전표 DB 업데이트 실패");
                    throw new Exception(response.getMessage());
                }
            }

            /** 전체 취소라면, XPay 분할발행 부분취소를 진행하지 않고, 분할된 DB 데이터만 반영 진행하고 종료 php:791 */
            if("_ALL_CANCEL_".equals(stepId)) {
                log.debug("Success : XPay 매출전표 - 분할 전체취소 성공 -> 분할 부분취소 API는 실행하지 않는다.");
                response.setSuccess(true);
                response.setMessage("매출전표 전체 취소 처리 완료");
                return response;
            }

            if("0000".equals(xpayCode)){
                response.setSuccess(true);
                response.setMessage(xpayMsg);
                response.setCode(xpayCode);
                log.debug("Success : XPay 매출전표 - 분할 부분취소 API 성공 -> code : %s, 메세지 : %s (%s, %s, %s)".formatted(
                        xpayCode
                        ,xpayMsg
                        ,request.getOrdNo()
                        ,mid
                        ,tid
                ));
            } else {
                response.setSuccess(false);
                response.setMessage(xpayMsg);
                response.setCode(xpayCode);
                log.error("Success : XPay 매출전표 - 매출전표 분할 부분취소 API 실패 -> code : %s, 메세지 : %s (%s, %s, %s)".formatted(
                        xpayCode
                        ,xpayMsg
                        ,request.getOrdNo()
                        ,mid
                        ,tid
                ));
                throw new Exception(response.toString());
            }
            response.setSuccess(false);
            response.setMessage(xpayMsg);
            response.setCode(xpayCode);
            throw new Exception(response.toString());
        } catch (Exception e) {
            log.error(e.getMessage());
            throw e;
        } finally {
            return response;
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = {Exception.class, RuntimeException.class}, readOnly = false, value = "orderRwdbTxManager")
    public XpayCancelDivideSlipResponse cancelProcessAfterDoClaim(String ordNo, String claimNo,List<XpayCancelPricePerProvider> xpayCancelPricePerProviders) {
        XpayCancelDivideSlipResponse response = new XpayCancelDivideSlipResponse();
        String resCd = "9999";
        String resMsg = "";
        try {
            OrderInfoByXpayResponse orderInfoByXpayResponse = xpayMapper.getOrderInfoByOrdNo(ordNo);
            String mid = getMatchedXpayConfigData(orderInfoByXpayResponse, "MID");
            String partCancelYn = xpayCancelPricePerProviders.get(0).getPartCnclYn();
            String lgdTxname = "Y".equals(partCancelYn) ?  "PartialCancel" : "Cancel";
            String lgdCancelreason = "Y".equals(partCancelYn) ?  "부분결제취소" : "전체취소";

            XpayUtil xpayUtil = new XpayUtil();
            String configPath = LG_PATH != null ? LG_PATH : null;
            Map map = xpayUtil.AccountXPaySalesslipApiInfo();
            String pForm = map.get("platform").toString();

            XPayClient xPayClientForClaim = new XPayClient();
            xPayClientForClaim.Init(configPath, pForm);
            xPayClientForClaim.Init_TX(mid);
            xPayClientForClaim.Set("LGD_TXNAME", lgdTxname);
            xPayClientForClaim.Set("LGD_MID", mid);
            xPayClientForClaim.Set("LGD_TID", orderInfoByXpayResponse.getTrdNo());
            xPayClientForClaim.Set("LGD_CANCELREASON", lgdCancelreason);
            /** 부분취소 파라미터 */
            if("Y".equals(partCancelYn)){
                int lgdCancelamount = 0;
                int lgdCanceltaxfreeamount = 0;
                int lgdRemainamount = 0;
                for (int i = 0; i < xpayCancelPricePerProviders.size(); i++) {
                    lgdCancelamount += xpayCancelPricePerProviders.get(i).getCancelAmount();
                    lgdCanceltaxfreeamount += xpayCancelPricePerProviders.get(i).getCancelTaxfreeAmount();
                    lgdRemainamount += xpayCancelPricePerProviders.get(i).getCnclAfAmt();
                }
                lgdRemainamount = lgdCancelamount + lgdCanceltaxfreeamount + lgdRemainamount; //DB에서 취소 후 잔여액을 가져오므로 합산
                xPayClientForClaim.Set("LGD_CANCELAMOUNT",Integer.toString(lgdCancelamount));
                xPayClientForClaim.Set("LGD_REMAINAMOUNT",Integer.toString(lgdRemainamount));
                /** 부분취소 과세/비과세 파라미터 활성화 */
                xPayClientForClaim.Set("LGD_CANCELTAXFREEAMOUNT",Integer.toString(lgdCanceltaxfreeamount));

            }
            xPayClientForClaim.TX();

            resCd = xPayClientForClaim.m_szResCode;
            resMsg = xPayClientForClaim.m_szResMsg;
            boolean isSuccess;
            XpayCancelDivideSlipRequest request = new XpayCancelDivideSlipRequest();
            if("0000".equals(resCd) || "AV11".equals(resCd)) {
                isSuccess = true;
            } else {
                isSuccess = false;
            }
            if(isSuccess) {
                request.setOrdNo(ordNo);
                request.setClaimNo(claimNo);
                if("PartialCancel".equals(lgdTxname)) { // 부분취소라면
                    request.setStepId(xPayClientForClaim.Response("LGD_PARTIALCANCEL_SEQNO_SUB",0));
                    request.setCancelAmountList(xpayCancelPricePerProviders);
                } else {
                    request.setStepId(null);
                    request.setCancelAmountList(null);
                }
                response = this.cancelDivideSlip(request);
                return response;
            } else {
                response.setSuccess(false);
                response.setMessage(resMsg);
                response.setCode(resCd);
                throw new Exception(resMsg);
            }
        } catch (Exception e) {
            log.error("initializePasswordOnLogin error => {}", e.getMessage());
            response.setSuccess(false);
            response.setMessage(e.getMessage());
            response.setCode(resCd);
            throw e;
        } finally {
            return response;
        }
    }

    @Override
    public List<XpayCancelPricePerProvider> createRequestForCancelDivideSlipAfterClaim(XpayAfterClaimInfo xpayAfterClaimInfo) {
        return getXpayCancelPricePerProviders(xpayAfterClaimInfo.getClaimXpayCancelInfoResponse());
    }

    private static @NotNull List<XpayCancelPricePerProvider> getXpayCancelPricePerProviders(List<ClaimXpayCancelInfoResponse> claimXpayCancelInfoResponses) {
        List<XpayCancelPricePerProvider> pricePerProviders = new ArrayList<>();
        for(ClaimXpayCancelInfoResponse item : claimXpayCancelInfoResponses){
            XpayCancelPricePerProvider xpayCancelPricePerProvider = new XpayCancelPricePerProvider();
            xpayCancelPricePerProvider.setEntrNo(item.getEntrNo());
            xpayCancelPricePerProvider.setCancelAmount(
                    item.getTaxationPriceGoods() + item.getTaxationPriceDeli()
                            +item.getTaxfreePriceGoods() + item.getTaxfreePriceDeli()
            );
            xpayCancelPricePerProvider.setCancelTaxfreeAmount(
                    item.getTaxfreePriceGoods() + item.getTaxfreePriceDeli()
            );
            xpayCancelPricePerProvider.setPartCnclYn(item.getPartCnclYn());
            xpayCancelPricePerProvider.setCnclAfAmt(item.getCnclAfAmt());
            pricePerProviders.add(xpayCancelPricePerProvider);
        }
        return pricePerProviders;
    }


    private boolean xpayAmountValidate(String entrNo,String msg,int checkAmtA, int checkAmtB) {
        if(checkAmtA < checkAmtB){
            log.error("Fail : XPay 매출전표 - 입점사코드 : {}의 유효하지 않은 취소 {} : {} < {} 조건이 성립됨"
                    ,entrNo
                    ,msg
                    ,checkAmtA
                    ,checkAmtB
            );
            return false;
        }
        return true;
    }

    private String getMatchedXpayConfigData(OrderInfoByXpayResponse orderInfoByXpayResponse, String findType) {
        String mallCode = orderInfoByXpayResponse.getSiteNo();
        String envType =
                OM007.CC.getCd().equals(orderInfoByXpayResponse.getOrdMediaCd())
                || OM007.PC.getCd().equals(orderInfoByXpayResponse.getOrdMediaCd())
                ?"PC":"MOBILE";
        XpayUtil xpayUtil = new XpayUtil();

        String MID_PREFIX = envType;
        String MID_Middle = "312".equals(mallCode) ? "Mall" : "Hi";
        String MID_SUFFIX = findType.equals("MID") ? "Mid" : "MKey";
        String MID = xpayUtil.getMatchedValue(MID_PREFIX + MID_Middle + MID_SUFFIX);

        return MID;
    }

    private String getMatchedMkeyByMid(String mid) {
        XpayUtil xpayUtil = new XpayUtil();
        return xpayUtil.getMatchedValue(mid);
    }

}
