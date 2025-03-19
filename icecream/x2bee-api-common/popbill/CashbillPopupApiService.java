package com.x2bee.api.common.app.service.popbill.cashbill;

import com.popbill.api.PopbillException;
import com.x2bee.api.common.app.dto.request.popbill.GetViewHtmlBulkRequest;
import com.x2bee.api.common.app.dto.request.popbill.cashbill.CashBillPopupRequest;
import com.x2bee.api.common.app.dto.response.popbill.GetViewHtmlBulkResponse;

import java.util.List;

/**
 * packageName    : com.x2bee.api.common.app.service.popbill
 * fileName       : PopbillCashbillService
 * author         : wonpyo
 * date           : 2024-11-05
 * description    :
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 2024-11-05        wonpyo       최초 생성
 */
public interface CashbillPopupApiService {

    String getPopupUrl(CashBillPopupRequest request) throws PopbillException;

    String getViewURL(CashBillPopupRequest request) throws PopbillException;

    String getPrintURL(CashBillPopupRequest request) throws PopbillException;

    String getMailURL(CashBillPopupRequest request) throws PopbillException;

    String getPDFURL(CashBillPopupRequest request) throws PopbillException;

    List<GetViewHtmlBulkResponse> getViewHTMLBulk(GetViewHtmlBulkRequest getViewHtmlBulkRequest);

}
