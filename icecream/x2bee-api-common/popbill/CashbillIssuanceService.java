package com.x2bee.api.common.app.service.popbill.cashbill;

import com.x2bee.api.common.app.dto.request.popbill.GetAllSingleIssuanceRequest;
import com.x2bee.api.common.app.dto.request.popbill.SingleTaxInvoiceAndCashBillRequest;
import com.x2bee.api.common.app.dto.response.popbill.cashbill.CashbillSingleIssuanceResponse;
import com.x2bee.api.common.app.dto.response.popbill.tax.TaxinvoiceSingleIssuanceResponse;
import com.x2bee.api.common.app.entity.popbill.PopbillCashbillInfo;

import java.util.List;
import java.util.Map;

/**
 * packageName    : com.x2bee.api.common.app.service.popbill.cashbill
 * fileName       : CashbillIssuanceService
 * author         : wonpyo
 * date           : 2024-12-11
 * description    :
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 2024-12-11        wonpyo       최초 생성
 */
public interface CashbillIssuanceService {

    List<CashbillSingleIssuanceResponse> getSingleIssuanceList(String ordNo, String entrNo, boolean getinvalid);

    List<PopbillCashbillInfo> findSingleCashBillList(SingleTaxInvoiceAndCashBillRequest singleTaxInvoiceAndCashBillRequest);

    Map<String, List<CashbillSingleIssuanceResponse>> getAllSingleIssuanceList(GetAllSingleIssuanceRequest getAllSingleIssuanceRequest);

}
