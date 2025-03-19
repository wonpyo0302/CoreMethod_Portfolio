package com.x2bee.api.common.app.service.popbill;

import com.popbill.api.Response;
import com.x2bee.api.common.app.dto.request.popbill.PopbillSyncRequest;
import com.x2bee.api.common.app.dto.response.popbill.VendorPopbillResultResponse;

/**
 * packageName    : com.x2bee.api.common.app.service.popbill
 * fileName       : PopbillService
 * author         : wonpyo
 * date           : 2024-10-30
 * description    :
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 2024-10-30        wonpyo       최초 생성
 */
public interface PopbillSyncApiService {
    Response updateContact(PopbillSyncRequest request);
    Response updateCorpInfo(PopbillSyncRequest request);
    VendorPopbillResultResponse joinMember(PopbillSyncRequest request);
}
