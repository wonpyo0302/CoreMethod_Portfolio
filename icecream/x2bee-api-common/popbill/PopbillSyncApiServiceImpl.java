package com.x2bee.api.common.app.service.popbill;

import com.popbill.api.*;
import com.x2bee.api.common.app.dto.request.popbill.PopbillSyncRequest;
import com.x2bee.api.common.app.dto.response.popbill.VendorPopbillResultResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * packageName    : com.x2bee.api.common.app.service.popbill
 * fileName       : PopbillServiceImpl
 * author         : wonpyo
 * date           : 2024-10-30
 * description    :
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 2024-10-30        wonpyo       최초 생성
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PopbillSyncApiServiceImpl implements PopbillSyncApiService {

    private final CashbillService cashbillService;

    @Override
    public Response updateContact(@RequestBody PopbillSyncRequest request) {
        Response response = new Response();
        try {
            response = cashbillService.updateContact(request.getBmanNo(),request.getContactInfo(),"");
        } catch (PopbillException e) {
            log.error("팝빌 당담자 정보 수정 중 예외 발생: {}",e.getMessage());
        }
        return response;
    }

    @Override
    public Response updateCorpInfo(@RequestBody PopbillSyncRequest request) {
        Response response = new Response();
        try {
            response = cashbillService.updateCorpInfo(request.getBmanNo().replaceAll("-",""), request.getCorpInfo());
        } catch (PopbillException e) {
            log.error("팝빌 사업자 정보 수정 중 예외 발생: {}",e.getMessage());
        }
        return response;
    }

    @Override
    public VendorPopbillResultResponse joinMember(@RequestBody PopbillSyncRequest request) {
        VendorPopbillResultResponse result = new VendorPopbillResultResponse();
        try {
            request.getJoinForm().setPassword(request.getPassword());
            Response response = cashbillService.joinMember(request.getJoinForm());
            result.setMessage(response.getMessage());
            result.setCode(response.getCode());
        } catch (PopbillException e) {
            log.error("팝빌 입점사 계정 등록 중 예외 발생: {}",e.getMessage());
            result.setMessage(e.getMessage());
            result.setCode(e.getCode());
            return result;
        }
        return result;
    }
}
