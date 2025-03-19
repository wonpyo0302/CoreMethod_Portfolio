package com.x2bee.api.common.app.service.popbill;

import com.popbill.api.*;
import com.popbill.api.cashbill.CBSearchResult;
import com.popbill.api.cashbill.Cashbill;
import com.popbill.api.taxinvoice.MgtKeyType;
import com.popbill.api.taxinvoice.TISearchResult;
import com.popbill.api.taxinvoice.Taxinvoice;
import com.popbill.api.taxinvoice.TaxinvoiceInfo;
import com.x2bee.api.common.app.dto.request.popbill.MakeAllSinglePriceRequest;
import com.x2bee.api.common.app.dto.request.popbill.PopbillSearchRequest;
import com.x2bee.api.common.app.dto.request.popbill.PopbillUpdateRequest;
import com.x2bee.api.common.app.dto.request.popbill.cashbill.PopbillSinglePriceRequest;
import com.x2bee.api.common.app.dto.request.popbill.cashbill.SingleRegistLoadRequest;
import com.x2bee.api.common.app.dto.request.popbill.tax.TaxInvoiceSingleIssuanceRequest;
import com.x2bee.api.common.app.dto.response.popbill.OrderShippingPaymentResponse;
import com.x2bee.api.common.app.dto.response.popbill.OrderShippingResponse;
import com.x2bee.api.common.app.dto.response.popbill.PopbillCheckDownStateResponse;
import com.x2bee.api.common.app.dto.response.popbill.cashbill.CashbillSingleIssuanceResponse;
import com.x2bee.api.common.app.dto.response.popbill.cashbill.PopbillSinglePriceResponse;
import com.x2bee.api.common.app.dto.response.popbill.tax.TaxinvoiceSingleIssuanceResponse;
import com.x2bee.api.common.app.entity.OpPpblCrSngOrdInfo;
import com.x2bee.api.common.app.entity.OrderAmount;
import com.x2bee.api.common.app.entity.popbill.OpTaxinvoiceLinkInfo;
import com.x2bee.api.common.app.entity.popbill.OrderPriceForMakeIssueablePrice;
import com.x2bee.api.common.app.entity.popbill.PopbillCashbillInfo;
import com.x2bee.api.common.app.enums.*;
import com.x2bee.api.common.app.repository.orderrodb.popbill.OpTaxinvoiceLinkInfoMapper;
import com.x2bee.api.common.app.repository.orderrwdb.OrderMapper;
import com.x2bee.api.common.app.repository.orderrwdb.popbill.PopbillTrxMapper;
import com.x2bee.api.common.app.service.popbill.cashbill.CashbillIssuanceService;
import com.x2bee.api.common.app.service.popbill.tax.TaxinvoiceIssuanceService;
import com.x2bee.common.base.exception.CommonException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * packageName    : com.x2bee.api.common.app.service.popbill
 * fileName       : PopbillCommonServiceImpl
 * author         : wonpyo
 * date           : 2024-11-05
 * description    :
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 2024-11-05        wonpyo       최초 생성
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PopbillCommonServiceImpl implements PopbillCommonService {
    private final CloseDownService closeDownService;
    private final CashbillIssuanceService cashbillIssuanceService;
    private final TaxinvoiceIssuanceService taxinvoiceIssuanceService;
    private final PopbillTrxMapper popbillTrxMapper;
    private final OrderMapper orderMapper;
    private final CashbillService cashbillService;
    private final TaxinvoiceService taxinvoiceService;
    private final OpTaxinvoiceLinkInfoMapper opTaxinvoiceLinkInfoMapper;

    @Value("${spring.application.name}")
    private String applicationName;
    @Value("${popbill.hqCorpNum}")
    private String hqCorpNum;
    private final double VAT = 0.1;

    /**
     * 회계 TFT 프로젝트 : 휴폐업 조회 실시 (사업자번호 기준)
     * date : 2024-12-16
     *
     * @param bmanNo
     * @return PopbillCheckDownStateResponse
     * @ state 리스트
     * null : 미확인
     * 0 : 미등록 or 유효하지 않은 사업자
     * 1 : 정상 운영 중
     * 2 : 폐업
     * 3 : 휴업
     */
    @Override
    public PopbillCheckDownStateResponse checkDownState(String bmanNo) {
        String targetCorpNum = bmanNo.replaceAll("-", "");
        PopbillCheckDownStateResponse response = new PopbillCheckDownStateResponse();
        response.setIsSuccess("Y");
        response.setMessage("정상적으로 조회를 완료했습니다.");
        try {
            if (!applicationName.equals("prd")) {
                // 개발서버에서는 2022.11.29부터 서비스 사용 불가 상태로 전환됨. 자체 성공 처리
                response.setState("1");
                return response;
            }
            CorpState corpState = closeDownService.CheckCorpNum(hqCorpNum, targetCorpNum);
            response.setState(corpState.getState());
        } catch (PopbillException e) {
            response.setIsSuccess("N");
            response.setMessage(e.getMessage());
            return response;
        } catch (Exception e) {
            response.setIsSuccess("N");
            response.setMessage(e.getMessage());
            return response;
        }

        return response;
    }


    @Override
    public List<PopbillSinglePriceResponse> makeAllSingleIssuablePrice(MakeAllSinglePriceRequest makeAllSinglePriceRequest) {
        List<PopbillSinglePriceResponse> result = new ArrayList<>();
        /** 발행가능 금액 생성을 위한 입점사 조회 */
        List<String> entrNos = popbillTrxMapper.getEntrNoByOrdNo(makeAllSinglePriceRequest.getOrdNo());
        for (String entrNo : entrNos) {
            PopbillSinglePriceRequest psr = new PopbillSinglePriceRequest();
            psr.setEntrNo(entrNo);
            psr.setOrdNo(makeAllSinglePriceRequest.getOrdNo());
            psr.setWithoutIssued(makeAllSinglePriceRequest.isWithoutIssued());
            result.add(this.makeSingleIssuablePrice(psr));
        }

        return result;
    }

    @Override
    public PopbillSinglePriceResponse makeSingleIssuablePrice(PopbillSinglePriceRequest request) {
        PopbillSinglePriceResponse response = new PopbillSinglePriceResponse();
        /** 계산 이후 response에 최종적으로 세팅할 변수 선언 */
        int total = 0;
        int totalTaxation = 0;
        int taxationSupply = 0;
        int taxationTax = 0;
        int taxFreeSupply = 0;
        int issuedCashbillSupply = 0;
        int issuedCashbillTax = 0;
        int issuedInvoiceSupply = 0;
        int issuedInvoiceTax = 0;
        int refundedPrice = 0;
        String proofTyp = "";
        /** 금액 계산을 위한 주문 데이터 인출 시작 php:3514 */
        List<OrderPriceForMakeIssueablePrice> orderPrice = orderMapper.getOrderPrice(request.getOrdNo(), request.getEntrNo());
        log.debug("ooderPrice: {}", orderPrice); // pass 확인 01.09

        //todo 추후 포인트결제가 섞인 주문건에 대한 테스트 및 수정 필요 !!
        for(OrderPriceForMakeIssueablePrice price : orderPrice) {
            log.debug("금액 계산 시작 - price: {}", price);
            if(PR007.TAX.getCode().equals(price.getTaxTypeCd()) || VD004.TAX.getCode().equals(price.getTaxTypeCd())){
                /** 상품이 과세 타입이거나, 과세업자 입점사의 경우 상품과 배송비 모두 과세 처리 */
                total += price.getTaxationPriceDeli() + price.getTaxationPriceGoods() - price.getPointDivAmt();
                totalTaxation += price.getTaxationPriceDeli() + price.getTaxationPriceGoods() - price.getPointDivAmt() - price.getBenefitPrc();
            } else {
                /** 아니라면 비과세 처리 */
                total += price.getTaxfreePriceDeli() + price.getTaxfreePriceGoods() - price.getPointDivAmt();
                taxFreeSupply += price.getTaxfreePriceDeli() + price.getTaxfreePriceGoods() - price.getPointDivAmt() - price.getBenefitPrc();
            }

        }

        /**
         * $issuanceInvoiceList = $this->popbillinvoice_model->GetSingleIssuanceList($order_seq, $provider_seq);
         * $issuanceCashbillList = $this->popbillcashbill_model->GetSingleIssuanceList($order_seq, $provider_seq);
         */
        List<CashbillSingleIssuanceResponse> issuanceCashbillList = cashbillIssuanceService.getSingleIssuanceList(request.getOrdNo(), request.getEntrNo(), false);
        List<TaxinvoiceSingleIssuanceResponse> issuanceTaxinvoiceList = taxinvoiceIssuanceService.findTaxInvoiceSingleIssuanceResponse(
                TaxInvoiceSingleIssuanceRequest.from(request.getOrdNo(), request.getEntrNo(), false)
        );

        int issuedTaxationSupply = 0;
        int issuedTaxationTax = 0;
        int issuedTaxfreeSupply = 0;
        for (TaxinvoiceSingleIssuanceResponse taxInvoiceData : issuanceTaxinvoiceList) {

            boolean isManual = "Y".equals(taxInvoiceData.getManualIsuYn());
            String taxGbCd = taxInvoiceData.getTaxGbCd();
            PR007 taxType = PR007.lookup(taxGbCd);

            int supplyCostTotal = Integer.parseInt(taxInvoiceData.getOrdSupPrc());
            int taxTotal = Integer.parseInt(taxInvoiceData.getOrdTaxPrc());
            int totalAmount = Integer.parseInt(taxInvoiceData.getOrdTotAmt());

            if (isManual) continue;

            /*
                // 세금계산서는 수정발행 여부에 상관없이 금액을 그대로 처리하면 됨.
                // (-) 발행한 경우는 알아서 발행가는 금액이 +로 처리될 것임
            */
            if (PR007.TAX == taxType) { // 과세 처리
                issuedTaxationSupply += supplyCostTotal;
                issuedTaxationTax += taxTotal;
                response.setTaxTyp("01");
            } else {    // 면세 or 영세 처리이나, 일단은 면세로 대응
                issuedTaxfreeSupply += totalAmount;
                response.setTaxTyp("02");
            }

            issuedInvoiceSupply += supplyCostTotal;
            issuedInvoiceTax += taxTotal;

            proofTyp = "20"; // 20 : 세금계산서
        }

        for (CashbillSingleIssuanceResponse cashbillData : issuanceCashbillList) {
            // 취소 현금영수증일 경우 DB상의 금액을 발행가능 금액 +로 처리해야 함
            // GetSingleIssuanceList > 프론트 표기를 세금계산서 취소분과 같이 처리하기 위해, 내부에서 금액 * 오프셋을 자체 처리한다.
            //$offSet = ($cashbillData["isCancel"] == 0 ? 1 : -1);
            if (PR007.TAX.getCodeName().equals(cashbillData.getTaxTyp())) {
                // 과세 처리
                issuedTaxationSupply += cashbillData.getOrdSupPrc();
                issuedTaxationTax += cashbillData.getOrdTaxPrc();
                response.setTaxTyp("01");
            } else {
                // 비과세 처리
                issuedTaxfreeSupply += cashbillData.getOrdTotAmt();
                response.setTaxTyp("02");
            }
            issuedCashbillSupply += cashbillData.getOrdSupPrc();
            issuedCashbillTax += cashbillData.getOrdTaxPrc();

            proofTyp = "10"; // 10 : 현금영수증
        }

        taxationSupply = (int) Math.round(totalTaxation / (1+VAT));
        taxationTax = totalTaxation - taxationSupply;

        response.setCode("0");
        response.setTotal(Integer.toString(total));
        response.setTotalTaxation(Integer.toString(totalTaxation));
        response.setTaxationSupply(Integer.toString(taxationSupply));
        response.setTaxationTax(Integer.toString(taxationTax));
        response.setTaxfreeSupply(Integer.toString(taxFreeSupply));

        response.setIssuedInvoiceSupply(Integer.toString(issuedInvoiceSupply));
        response.setIssuedInvoiceTax(Integer.toString(issuedInvoiceTax));
        response.setIssuedCashbillSupply(Integer.toString(issuedCashbillSupply));
        response.setIssuedCashbillTax(Integer.toString(issuedCashbillTax));
        response.setRefundedPrice(Integer.toString(refundedPrice));
        response.setEntrNo(request.getEntrNo());

        if (!request.isWithoutIssued()) {
            taxationSupply -= issuedTaxationSupply;
            taxationTax -= issuedTaxationTax;
            taxFreeSupply -= issuedTaxfreeSupply;
        }

        if (taxFreeSupply <= 0 && taxationSupply <= 0 && taxationTax <= 0) {
            response.setCode("-2");
        }
        // 과세 || 면세 타입 설정 01 : 과제, 02 : 면세
//        if(taxationSupply > 0 || taxationTax > 0) response.setTaxTyp("01");
//        if(taxFreeSupply > 0) response.setTaxTyp("02");
        // 입점사 명 세팅
        response.setEntrNo(request.getEntrNo());
        response.setEntrNm(orderMapper.getEntrNm(request.getEntrNo()));
        // 증빙종류 세팅
        response.setProofTyp(proofTyp);

        return response;
    }

    @Override
    public Map<String, Object> HeaderUtil(HttpServletRequest request) {
        Map<String, Object> headersMap = new HashMap<>();
        Enumeration<String> headers = request.getHeaderNames();
        while (headers.hasMoreElements()) {
            String headerName = headers.nextElement();
            String headerValue = request.getHeader(headerName);
            headersMap.put(headerName.toLowerCase(), headerValue);
        }
        return headersMap;
    }


    /**
     * 입점사 SEQ부여방식이 달라 아래 PHP 로직 사용 불가
     * BUT 기존 이관된 입점사 SEQ는 사용해야하므로 분기처리
     * 현금영수증 : "C".date("Ymd") , 세금계산서 :  "T".date("Ymd");
     * EX)
     * $mgtKey = "C".date("Ymd") // 오늘 날짜를 yyyymmdd 형식으로
     * $mgtKey .= "-".sprintf("%05d", $provider_seq);		// 입점사를 5자리 채워서
     * $mgtKey .= "-".sprintf("%08d", ++$maxInCashbillVar % 100000000); // 나머지는 현재 생성된 cashbill 번호를 이용
     */
    @Override
    public String createMgtKey(String docType, int maxVar, String entrNo) {
        String mgtKey = "";
        if (docType.equals("cashbill")) {
            mgtKey = "C" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        } else {
            mgtKey = "T" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        }
        if (entrNo.length() < 7) {
            mgtKey += "-" + String.format("%05d", Integer.parseInt(entrNo));
            mgtKey += "-" + String.format("%08d", ++maxVar % 100000000);
        } else {
            mgtKey += "-" + entrNo; //8
            mgtKey += "-" + String.format("%06d", ++maxVar % 1000000); //TOBE는 입점사 시퀀스가 7자리
        }
        return mgtKey;
    }

    @Override
    public List<com.popbill.api.cashbill.CashbillInfo> searchItemKey(PopbillSearchRequest popbillSearchRequest) throws PopbillException {
        popbillSearchRequest.setCorpNum(popbillSearchRequest.getCorpNum().replaceAll("-",""));
        CBSearchResult result = cashbillService.search(popbillSearchRequest.getCorpNum(), popbillSearchRequest.getDType(), popbillSearchRequest.getSDate(), popbillSearchRequest.getEDate()
                ,null, null, null, null, null, null, null);

        return result.getList().stream().filter(item -> item.getMgtKey() == null).collect(Collectors.toList());

    }

    @Override
    @Transactional
    public void updateMgtKey(PopbillUpdateRequest popbillUpdateRequest) throws PopbillException {

        // mgtKey 생성
        String maxVar = popbillTrxMapper.getMaxVar();
        int maxInCashbillVar = maxVar == null ? 1 : Integer.parseInt(maxVar);

        // 협력사정보 저회
        popbillUpdateRequest.setBmanNo(popbillUpdateRequest.getBmanNo().replaceAll("-",""));
        String entrNo = popbillTrxMapper.getEntrNoByBmanNo(popbillUpdateRequest);

        // 수기발행한 문서에 문서번호 할당
        String mgtKey = createMgtKey(popbillUpdateRequest.getDocType(), maxInCashbillVar, entrNo);
        cashbillService.assignMgtKey(popbillUpdateRequest.getBmanNo(), popbillUpdateRequest.getItemKey(), mgtKey, null);

        // 상세정보 조회
        Cashbill detailInfo = cashbillService.getDetailInfo(popbillUpdateRequest.getBmanNo(), mgtKey);

        // 발행대상 테이블 insert
        popbillUpdateRequest.setEntrNo(entrNo);
        popbillUpdateRequest.setMgtKey(mgtKey);
        PopbillCashbillInfo cashbillinfo = this.setCashbillInsertData(detailInfo, popbillUpdateRequest, maxInCashbillVar);
        int insertCashbillResult = popbillTrxMapper.insertCashbillInfo(cashbillinfo);
        if (insertCashbillResult < 1) {
            log.error("Fail : 현금영수증 정보 생성 실패");
        }

        // 12. 단일 주문 내역 정보 세팅 후 DB 저장
        OpPpblCrSngOrdInfo opPpblCrSngOrdInfo = new OpPpblCrSngOrdInfo();
        opPpblCrSngOrdInfo.setCrSeq(cashbillinfo.getCrSeq());
        opPpblCrSngOrdInfo.setOrdNo(cashbillinfo.getOrdNo());
        opPpblCrSngOrdInfo.setEntrNo(cashbillinfo.getEntrNo());
        opPpblCrSngOrdInfo.setSupPrce(cashbillinfo.getOrdSupPrc());
        opPpblCrSngOrdInfo.setTaxPrce(cashbillinfo.getOrdTaxPrc());
        opPpblCrSngOrdInfo.setSysRegId("수기발행");
        opPpblCrSngOrdInfo.setSysModId("수기발행");
        int insertOpPpblCrSngOrdInfoResult = popbillTrxMapper.insertOpPpblCrSngOrdInfo(opPpblCrSngOrdInfo);
        // 단일 주문 내역 맵핑이 진행되지 못했다면 작업을 중단
        if (insertOpPpblCrSngOrdInfoResult < 1) {
            log.error("현금영수증 맵핑 정보 생성 실패");
        }
    }

    private PopbillCashbillInfo setCashbillInsertData(Cashbill request, PopbillUpdateRequest popbillUpdateRequest, int maxInCashbillVar) {
        PopbillCashbillInfo insertCashbillInfo = new PopbillCashbillInfo();
        insertCashbillInfo.setCrSeq(String.valueOf(maxInCashbillVar));
        insertCashbillInfo.setOrdNo(popbillUpdateRequest.getOrdNo());
        insertCashbillInfo.setEntrNo(popbillUpdateRequest.getEntrNo());
        insertCashbillInfo.setMgrNo(popbillUpdateRequest.getMgtKey());
        insertCashbillInfo.setIsuDt(request.getTradeDate());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        // 문자열을 LocalDateTime으로 변환
        LocalDateTime localDateTime = LocalDateTime.parse(request.getTradeDT(), formatter);
        // LocalDateTime을 Timestamp로 변환
        insertCashbillInfo.setIsuDtm(Timestamp.valueOf(localDateTime).toLocalDateTime());
        insertCashbillInfo.setSgtDtm(LocalDateTime.now());
        insertCashbillInfo.setTaxTyp(request.getTaxationType());
        insertCashbillInfo.setOrdTotAmt(Integer.parseInt(request.getSupplyCost()) + Integer.parseInt(request.getTax()));
        insertCashbillInfo.setOrdSupPrc(Integer.parseInt(request.getSupplyCost()));
        insertCashbillInfo.setOrdTaxPrc(Integer.parseInt(request.getTax()));
        insertCashbillInfo.setRmk(request.getItemName());
        insertCashbillInfo.setPpblIsuStat(OM065.REGISTISSUE.getCode());
        insertCashbillInfo.setStatCd("0");
        // TODO 등록 시 api통신이 아니므로 id식별 불가, 확인필요함.
        insertCashbillInfo.setSysRegId("수기발행");
        insertCashbillInfo.setSysModId("수기발행");
        insertCashbillInfo.setCnclIsuYn(request.getTradeType().equals("취소거래") ? "Y" : "N");
        // 검색기능을 위한 성능 개선용 플래그를 세팅. 원발행에서는 환불코드를 입력하지 않음
        insertCashbillInfo.setAdminIsuYn("Y"); //TODO 관리자 권한에 따라 값 세팅 로직 추가필요
        insertCashbillInfo.setSngIsuYn("Y");
        insertCashbillInfo.setSctnIsuYn("N");
        insertCashbillInfo.setManualIsuYn("Y");
        insertCashbillInfo.setIsuBmanNm(popbillUpdateRequest.getEntrNm());
        insertCashbillInfo.setIsuBmanNo(popbillUpdateRequest.getBmanNo());
        //
        insertCashbillInfo.setPrchsrIdefNo(request.getIdentityNum());
        insertCashbillInfo.setPrchsrName(request.getCustomerName());
        insertCashbillInfo.setPrsntPurps(request.getTradeUsage());
        insertCashbillInfo.setTrnsNo(request.getConfirmNum());
        return insertCashbillInfo;
    }

}
