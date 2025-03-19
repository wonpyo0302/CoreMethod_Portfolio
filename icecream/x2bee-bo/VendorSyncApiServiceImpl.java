package com.x2bee.bo.app.service.vendor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.x2bee.bo.app.dto.request.vendor.VendorAccUserInfoRequest;
import com.x2bee.bo.app.dto.request.vendor.VendorPopbillProcessRequest;
import com.x2bee.bo.app.dto.request.vendor.VendorTossProcessRequest;
import com.x2bee.bo.app.dto.request.vendor.VendorXpayProcessRequest;
import com.x2bee.bo.app.dto.response.vendor.VendorSyncResponse;
import com.x2bee.bo.app.entity.vendor.VendorPopbillAccUserInfo;
import com.x2bee.bo.app.entity.vendor.VendorAempInfo;
import com.x2bee.bo.app.entity.vendor.VendorTossAccUserInfo;
import com.x2bee.bo.app.entity.vendor.VendorXpayAccUserInfo;
import com.x2bee.bo.base.util.XpayUtil;
import com.x2bee.common.base.exception.CommonException;
import com.x2bee.common.base.rest.Response;
import com.x2bee.common.base.rest.RestApiUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.util.Map;

@RequiredArgsConstructor
@Service
@Slf4j
public class VendorSyncApiServiceImpl implements VendorSyncApiService{
    @Value("${popbill.hqCorpNum}")
    private String HqCorpNum;

    private final RestApiUtil restApiUtil;
    private final ObjectMapper objectMapper;

    @Value("${app.apiUrl.bo}")
    private String boApiUrl;
    @Value("${provider.icemall.seq}")
    private String icemallSeq;
    @Value("${spring.profiles.active}")
    private String env;
    private static final String VENDOR_POP_API_URL = "/api/bo/v1/popup/partnerMgmt";

    @Override
    public VendorSyncResponse syncApi(VendorAccUserInfoRequest vendorAccUserInfoRequest){
        VendorSyncResponse response = new VendorSyncResponse();
        try {
            response = syncPopbill(vendorAccUserInfoRequest); // popbill
        } catch (Exception e) {
            log.error(e.getMessage());
        }
//        try {
//            XpayUtil xpayUtil = new XpayUtil();
//            Map map = xpayUtil.AccountXPaySalesslipApiInfo();
//            response = syncXpay(vendorAccUserInfoRequest,map); // xpay
//        } catch (Exception e) {
//            log.error(e.getMessage());
//        }
        try {
            response = syncToss(vendorAccUserInfoRequest); // toss
        } catch (Exception e) {
            log.error(e.getMessage());
        }
        return response;
    }

    private VendorSyncResponse syncPopbill(VendorAccUserInfoRequest vendorAccUserInfoRequest) {
        vendorAccUserInfoRequest.setLinkAcctTypeCd("10");
        VendorPopbillAccUserInfo vendorPopbillAccUserInfo = getVendorAccUserInfo(vendorAccUserInfoRequest, VendorPopbillAccUserInfo.class);
        /** DB에 존재하는 입점사인지 확인 */
        if (vendorPopbillAccUserInfo == null) {
            throw new CommonException("올바르지 않은 입정사 정보입니다.");
        }
        /** 거래중인 입점사인지 확인 */
        if(!vendorPopbillAccUserInfo.getTrdStatCd().equals("20")){
            updateVendorPopbillSyncStatus(vendorAccUserInfoRequest);
            throw new CommonException("해당 입점사는 운영중이 아닙니다.");
        }
        /** 해당 입점사의 정산 담당자 유무 확인 */
        VendorAempInfo vendorAempInfo = getVendorAempInfo(vendorPopbillAccUserInfo);
        if( vendorAempInfo == null){
            throw new CommonException("올바르지 않은 입점사 정산 담당자 정보입니다.");
        }
        String submallId = "submall".concat(vendorPopbillAccUserInfo.getEntrNo());
//        if(env.equals("local")||env.equals("dev")){
//            submallId = "test_provider";
//        }
//        if(vendorPopbillAccUserInfo.getEntrNo().equals(icemallSeq)){
//            submallId = "iscmall";
//        } else if(HqCorpNum.equals(vendorAccUserInfoRequest.getBmanNo().replaceAll("-",""))){
//            throw new CommonException("본사 사업자번호와 동일한 입점사는 처리 대상이 아닙니다.");
//        }
        vendorPopbillAccUserInfo.setSubmallId(submallId);
        /** POPBILL 관련 프로세스 실행 */
        return updateVendorPopbillProcess(vendorAccUserInfoRequest, vendorPopbillAccUserInfo,vendorAempInfo);
    }

    private VendorSyncResponse syncToss(VendorAccUserInfoRequest vendorAccUserInfoRequest) {
        vendorAccUserInfoRequest.setLinkAcctTypeCd("20");
        VendorTossAccUserInfo vendorTossAccUserInfo = getVendorAccUserInfo(vendorAccUserInfoRequest, VendorTossAccUserInfo.class);
        /** DB에 존재하는 입점사인지 확인 */
        if (vendorTossAccUserInfo == null) {
            throw new CommonException("올바르지 않은 입정사 정보입니다.");
        }
        /** 거래중인 입점사인지 확인  */
        if(!vendorTossAccUserInfo.getTrdStatCd().equals("20")){
            updateVendorTossSyncStatus(vendorAccUserInfoRequest);
            throw new CommonException("해당 입점사는 운영중이 아닙니다.");
        }
        String submallId = "submall".concat(vendorTossAccUserInfo.getEntrNo());
        if(vendorTossAccUserInfo.getEntrNo().equals(icemallSeq)){
            submallId = "iscmall";
        }
//        if(env.equals("local")||env.equals("dev")){
//            submallId = "test_provider";
//        }
        vendorTossAccUserInfo.setSubmallId(submallId);
        /** TOSS 관련 프로세스 실행 */
        return updateVendorTossProcess(vendorAccUserInfoRequest, vendorTossAccUserInfo);
    }

    private VendorSyncResponse syncXpay(VendorAccUserInfoRequest vendorAccUserInfoRequest, Map map) {
        vendorAccUserInfoRequest.setLinkAcctTypeCd("30");
        VendorXpayAccUserInfo vendorXpayAccUserInfo = getVendorAccUserInfo(vendorAccUserInfoRequest, VendorXpayAccUserInfo.class);
        /** DB에 존재하는 입점사인지 확인 */
        if (vendorXpayAccUserInfo == null) {
            throw new CommonException("올바르지 않은 입정사 정보입니다.");
        }
        /** 거래중인 입점사인지 확인 */
        if(!vendorXpayAccUserInfo.getTrdStatCd().equals("20")){
            updateVendorXpaySyncStatus(vendorAccUserInfoRequest);
            throw new CommonException("해당 입점사는 운영중이 아닙니다.");
        }
        String submallId = "submall".concat(vendorXpayAccUserInfo.getEntrNo());
        if(vendorXpayAccUserInfo.getEntrNo().equals(icemallSeq)){
            submallId = "iscmall";
        }
//        if(env.equals("local")||env.equals("dev")){
//            submallId = "test_provider";
//        }
        vendorXpayAccUserInfo.setSubmallId(submallId);
        return updateVendorXpayProcess(vendorAccUserInfoRequest,vendorXpayAccUserInfo, map);
    }

    private <T> T getVendorAccUserInfo(VendorAccUserInfoRequest vendorAccUserInfoRequest, Class<T> responseType) {
        Map<String, Object> accUserInfoMap = restApiUtil.get(boApiUrl + VENDOR_POP_API_URL+"/getVendorAccUserInfo", vendorAccUserInfoRequest,
                new ParameterizedTypeReference<Response<Map<String, Object>>>() {}).getPayload();

        return objectMapper.convertValue(accUserInfoMap.get("accUserInfo"), responseType);
    }
    private VendorAempInfo getVendorAempInfo(VendorPopbillAccUserInfo vendorPopbillAccUserInfo) {
        return restApiUtil.get(boApiUrl + VENDOR_POP_API_URL+"/getVendorAempInfo", vendorPopbillAccUserInfo,
                new ParameterizedTypeReference<Response<VendorAempInfo>>() {}).getPayload();
    }

    private VendorSyncResponse updateVendorPopbillProcess(VendorAccUserInfoRequest vendorAccUserInfoRequest
            , VendorPopbillAccUserInfo vendorPopbillAccUserInfo
            , VendorAempInfo vendorAempInfo){
        VendorPopbillProcessRequest request = new VendorPopbillProcessRequest();
        request.setRequest(vendorAccUserInfoRequest);
        request.setInfo(vendorPopbillAccUserInfo);
        request.setAempInfo(vendorAempInfo);
        return restApiUtil.put(boApiUrl + VENDOR_POP_API_URL+"/updateVendorPopbillProcess", request,
                new ParameterizedTypeReference<Response<VendorSyncResponse>>() {}).getPayload();
    }

    private VendorSyncResponse updateVendorTossProcess(VendorAccUserInfoRequest vendorAccUserInfoRequest
            , VendorTossAccUserInfo vendorTossAccUserInfo){
        VendorTossProcessRequest request = new VendorTossProcessRequest();
        request.setRequest(vendorAccUserInfoRequest);
        request.setInfo(vendorTossAccUserInfo);
        return restApiUtil.put(boApiUrl + VENDOR_POP_API_URL+"/updateVendorTossProcess", request,
                new ParameterizedTypeReference<Response<VendorSyncResponse>>() {}).getPayload();
    }

    private VendorSyncResponse updateVendorXpayProcess(VendorAccUserInfoRequest vendorAccUserInfoRequest
            , VendorXpayAccUserInfo vendorXpayAccUserInfo
            , Map map ){
        VendorXpayProcessRequest request = new VendorXpayProcessRequest();
        request.setRequest(vendorAccUserInfoRequest);
        request.setInfo(vendorXpayAccUserInfo);
        request.setXpayInfo(map);
        return restApiUtil.put(boApiUrl + VENDOR_POP_API_URL+"/updateVendorXpayProcess", request,
                new ParameterizedTypeReference<Response<VendorSyncResponse>>() {}).getPayload();
    }

    private int updateVendorPopbillSyncStatus(VendorAccUserInfoRequest vendorAccUserInfoRequest){
        return restApiUtil.put(boApiUrl + VENDOR_POP_API_URL+"/updateVendorPopbillSyncStatus", vendorAccUserInfoRequest,
                new ParameterizedTypeReference<Response<Integer>>() {}).getPayload();
    }
    private int updateVendorTossSyncStatus(VendorAccUserInfoRequest vendorAccUserInfoRequest){
        return restApiUtil.put(boApiUrl + VENDOR_POP_API_URL+"/updateVendorTossSyncStatus", vendorAccUserInfoRequest,
                new ParameterizedTypeReference<Response<Integer>>() {}).getPayload();
    }
    private int updateVendorXpaySyncStatus(VendorAccUserInfoRequest vendorAccUserInfoRequest){
        return restApiUtil.put(boApiUrl + VENDOR_POP_API_URL+"/updateVendorXpaySyncStatus", vendorAccUserInfoRequest,
                new ParameterizedTypeReference<Response<Integer>>() {}).getPayload();
    }
}
