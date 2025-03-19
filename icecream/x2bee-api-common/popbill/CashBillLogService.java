package com.x2bee.api.common.app.service.popbill.cashbill;

import com.x2bee.api.common.app.dto.request.popbill.ClaimProcessResultResponse;
import com.x2bee.api.common.app.dto.request.popbill.cashbill.CashbillRevokeIssueRequest;
import com.x2bee.api.common.app.dto.request.popbill.cashbill.PopbillSingleRegistRequest;
import com.x2bee.api.common.app.dto.response.popbill.cashbill.CashbillRevokeReponse;
import com.x2bee.api.common.app.dto.response.popbill.cashbill.PopbillSingleRegistResponse;

/**
 * @author shguddnr2@coremethod.co.kr
 * @version 1.0
 * @description
 * @since 25. 1. 20.
 */
public interface CashBillLogService {

    String cashBillSingleRegistLogInsert(PopbillSingleRegistRequest request, PopbillSingleRegistResponse response, boolean isSuccess);

    String cashBillRevokeLogInsertForClaim(Object request, CashbillRevokeReponse response, boolean isSuccess);

}
