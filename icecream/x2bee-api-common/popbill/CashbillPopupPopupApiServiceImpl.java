package com.x2bee.api.common.app.service.popbill.cashbill;

import com.popbill.api.CashbillService;
import com.popbill.api.PopbillException;
import com.x2bee.api.common.app.dto.request.popbill.GetViewHtmlBulkRequest;
import com.x2bee.api.common.app.dto.request.popbill.PopBillDocInfoRequest;
import com.x2bee.api.common.app.dto.request.popbill.cashbill.CashBillPopupRequest;
import com.x2bee.api.common.app.dto.response.popbill.GetViewHtmlBulkResponse;
import com.x2bee.api.common.app.dto.response.popbill.PopBillDocInfoResponse;
import com.x2bee.api.common.app.dto.response.popbill.PopBillViewResponse;
import com.x2bee.api.common.app.enums.PopBillDocType;
import com.x2bee.api.common.app.repository.orderrwdb.popbill.PopbillTrxMapper;
import com.x2bee.api.common.app.util.PopBillUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * packageName    : com.x2bee.api.common.app.service.popbill
 * fileName       : PopbillCashbillServiceImpl
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
public class CashbillPopupPopupApiServiceImpl implements CashbillPopupApiService {

    private final PopBillUtil popBillUtil;
    private final CashbillService cashbillService;
    private final PopbillTrxMapper popbillTrxMapper;


    /**
     * 현금영수증 상세 정보 팝업 URL
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = {Exception.class}, readOnly = false, value = "orderRwdbTxManager")
    public String getPopupUrl(CashBillPopupRequest request) throws PopbillException {
        String CorpNum = request.getBmanNo();
        String MgtKey = request.getMgrNo();
        return cashbillService.getPopUpURL(CorpNum, MgtKey);
    }

    /**
     * 현금영수증 상세 정보 팝업 URL [메뉴/버튼 제외]
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = {Exception.class}, readOnly = false, value = "orderRwdbTxManager")
    public String getViewURL(CashBillPopupRequest request) throws PopbillException {
        String CorpNum = request.getBmanNo();
        String MgtKey = request.getMgrNo();
        return cashbillService.getViewURL(CorpNum, MgtKey);
    }

    /**
     * 현금영수증 인쇄 팝업 URL
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = {Exception.class}, readOnly = false, value = "orderRwdbTxManager")
    public String getPrintURL(CashBillPopupRequest request) throws PopbillException {
        String CorpNum = request.getBmanNo();
        String MgtKey = request.getMgrNo();
        return cashbillService.getPrintURL(CorpNum, MgtKey);
    }

    /**
     * 현금영수증 안내메일 버튼 팝업 URL
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = {Exception.class}, readOnly = false, value = "orderRwdbTxManager")
    public String getMailURL(CashBillPopupRequest request) throws PopbillException {
        String CorpNum = request.getBmanNo();
        String MgtKey = request.getMgrNo();
        return cashbillService.getMailURL(CorpNum, MgtKey);
    }

    /**
     * 현금영수증 PDF 다운로드 URL
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = {Exception.class}, readOnly = false, value = "orderRwdbTxManager")
    public String getPDFURL(CashBillPopupRequest request) throws PopbillException {
        String CorpNum = request.getBmanNo();
        String MgtKey = request.getMgrNo();
        return cashbillService.getPDFURL(CorpNum, MgtKey);
    }


    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = {Exception.class}, readOnly = false, value = "orderRwdbTxManager")
    public List<GetViewHtmlBulkResponse> getViewHTMLBulk(GetViewHtmlBulkRequest getViewHtmlBulkRequest) {
        String ordNo = getViewHtmlBulkRequest.getOrdNo();
        PopBillDocInfoRequest request = PopBillDocInfoRequest.from(ordNo, false);
        List<PopBillDocInfoResponse> docInfoResponses = popbillTrxMapper.popBillDocInfoForHtml(request);
        if (docInfoResponses.isEmpty()) return new ArrayList<>();
        return docInfoResponses.stream()
                .map(item -> {
                    String mgrNo = item.getDocSeq();
                    String corpNum = item.getCorpNum();
                    PopBillViewResponse response;
                    try {
                        response = PopBillViewResponse.success(this.getViewURL(CashBillPopupRequest.from(mgrNo, corpNum, "")));
                    } catch (PopbillException e) {
                        response = PopBillViewResponse.fail();
                    }
                    return response.getCode() == 200
                            ? GetViewHtmlBulkResponse.from(
                            PopBillDocType.CASH_BILL,
                            item.getDocSeq(),
                            popBillUtil.urlToHtml(response.getUrlString()),
                            item.getIsuDtm())
                            : null;
                })
                .toList();
    }

}
