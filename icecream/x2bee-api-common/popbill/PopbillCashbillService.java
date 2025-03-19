package com.x2bee.api.common.app.service.popbill.cashbill;

import com.google.gson.JsonObject;
import com.x2bee.api.common.app.dto.request.popbill.cashbill.CashbillChangeInfoRequest;
import com.x2bee.api.common.app.dto.request.popbill.ClaimProcessResultResponse;
import com.x2bee.api.common.app.dto.request.popbill.cashbill.CashbillRevokeIssueRequest;
import com.x2bee.api.common.app.dto.request.popbill.cashbill.CashbillSendEmailRequest;
import com.x2bee.api.common.app.dto.request.popbill.cashbill.PopbillSingleRegistRequest;
import com.x2bee.api.common.app.dto.response.popbill.cashbill.*;
import com.x2bee.api.common.app.entity.OpShareInfo;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
 * packageName    : com.x2bee.api.common.app.service.popbill
 * fileName       : PopbillCashbillCommonService
 * author         : wonpyo
 * date           : 2024-11-05
 * description    :
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 2024-11-05        wonpyo       최초 생성
 */
public interface PopbillCashbillService {
    PopbillSingleRegistResponse singleRegistIssue(PopbillSingleRegistRequest request) throws Exception;
    void setShareInfo(OpShareInfo opShareInfo);
    String setToIssueState(JsonObject object, HttpServletRequest request);
    CashbillCancelIssueResponse cancelIssue(String crSeq);
    List<CashbillCancelIssueResponse> singleCancelIssue(String ordNo);
    CashbillRevokeReponse revokeIssue(CashbillRevokeIssueRequest request) throws Exception;

    CashbillSendEmailResponse resendMail(CashbillSendEmailRequest request);

    List<CashbillRevokeIssueRequest> createRequestForRevokeIssueAfterClaim(ClaimProcessResultResponse response);
}
