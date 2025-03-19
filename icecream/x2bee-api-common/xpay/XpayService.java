package com.x2bee.api.common.app.service.xpay;

import com.x2bee.api.common.app.dto.request.popbill.ClaimProcessResultResponse;
import com.x2bee.api.common.app.dto.request.xpay.*;
import com.x2bee.api.common.app.dto.response.xpay.XpayCancelDivideSlipResponse;
import com.x2bee.api.common.app.dto.response.xpay.XpayMakeDivideSlipResponse;

import java.util.List;

/**
 * packageName    : com.x2bee.api.common.app.service.xpay
 * fileName       : XpayService
 * author         : wonpyo
 * date           : 2024-10-31
 * description    :
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 2024-10-31        wonpyo       최초 생성
 */
public interface XpayService {
    void regist(XpaySyncRequest request);
    XpayMakeDivideSlipResponse makeDivideSlip(XpayMakeDivideSlipRequest request) throws Exception;
    XpayCancelDivideSlipResponse cancelDivideSlip(XpayCancelDivideSlipRequest request) throws Exception;
    XpayCancelDivideSlipResponse cancelProcessAfterDoClaim(String ordNo, String claimNo,List<XpayCancelPricePerProvider> XpayCancelPricePerProviders);
    List<XpayCancelPricePerProvider> createRequestForCancelDivideSlipAfterClaim(XpayAfterClaimInfo xpayAfterClaimInfo);
}
