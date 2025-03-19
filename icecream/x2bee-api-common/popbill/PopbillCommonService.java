package com.x2bee.api.common.app.service.popbill;

import com.popbill.api.PopbillException;
import com.popbill.api.cashbill.CBSearchResult;
import com.popbill.api.taxinvoice.Taxinvoice;
import com.popbill.api.taxinvoice.TaxinvoiceInfo;
import com.x2bee.api.common.app.dto.request.popbill.MakeAllSinglePriceRequest;
import com.x2bee.api.common.app.dto.request.popbill.PopbillSearchRequest;
import com.x2bee.api.common.app.dto.request.popbill.PopbillUpdateRequest;
import com.x2bee.api.common.app.dto.request.popbill.cashbill.PopbillSinglePriceRequest;
import com.x2bee.api.common.app.dto.response.popbill.CheckIssuanceTypeResponse;
import com.x2bee.api.common.app.dto.response.popbill.PopbillCheckDownStateResponse;
import com.x2bee.api.common.app.dto.response.popbill.cashbill.PopbillSinglePriceResponse;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.Map;

/**
 * packageName    : com.x2bee.api.common.app.service.popbill
 * fileName       : PopbillCommonService
 * author         : wonpyo
 * date           : 2024-11-05
 * description    :
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 2024-11-05        wonpyo       최초 생성
 */
public interface PopbillCommonService {
    List<PopbillSinglePriceResponse> makeAllSingleIssuablePrice(MakeAllSinglePriceRequest makeAllSinglePriceRequest);

    PopbillSinglePriceResponse makeSingleIssuablePrice(PopbillSinglePriceRequest request);

    PopbillCheckDownStateResponse checkDownState(String bmanNo);

    Map<String, Object> HeaderUtil(HttpServletRequest request);

    String createMgtKey(String docType, int maxVar, String entrNo);

    List<com.popbill.api.cashbill.CashbillInfo> searchItemKey(PopbillSearchRequest popbillSearchRequest) throws PopbillException;

    void updateMgtKey(PopbillUpdateRequest popbillUpdateRequest) throws PopbillException;
}
