package com.x2bee.api.bo.app.service.vendor.sync;

import com.popbill.api.*;
import com.x2bee.api.bo.app.dto.request.vendor.sync.*;
import com.x2bee.api.bo.app.dto.response.vendor.VendorPopbillResultResponse;
import com.x2bee.api.bo.app.entity.vendor.VendorAempInfo;
import com.x2bee.api.bo.app.entity.vendor.VendorXpayAccUserInfo;
import com.x2bee.api.bo.app.repository.displayrodb.vendor.EtEntrSyncMapper;
import com.x2bee.api.bo.app.repository.displayrwdb.vendor.EtEntrSyncTrxMapper;
import com.x2bee.common.base.exception.CommonException;
import com.x2bee.common.base.rest.RestApiUtil;
import lgdacom.XPayClient.XPayClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@RequiredArgsConstructor
@Service
@Slf4j
public class VendorSyncApiServiceImpl implements VendorSyncApiService {
    private final EtEntrSyncMapper etEntrSyncMapper;
    private final EtEntrSyncTrxMapper etEntrSyncTrxMapper;
    private final RestApiUtil restApiUtil;
    @Value("${app.apiUrl.common}")
    private String commonApiUrl;

    @Value("${spring.profiles.active}")
    private String activeProfile;
    @Value("${popbill.linkId}")
    private String LinkId;
    @Value("${popbill.hqCorpNum}")
    private String HqCorpNum;
    @Value("${popbill.submallId}")
    private String SubmallId;
    @Value("${popbill.subUserPwd}")
    private String SubUserPwd;
    @Value("${toss.mid}")
    private String mid;
    @Value("${toss.api-url}")
    private String apiUrl;
    @Value("${toss.client-key}")
    private String clientKey;
    @Value("${toss.secret-key}")
    private String secretKey;
    @Value("${toss.basic-token}")
    private String basicToken;

    private final URL LG_PATH = getClass().getClassLoader().getResource("lgdacom");

    @Override
    public Map<String, Object> getVendorAccUserInfo(VendorAccUserInfoRequest vendorAccUserInfoRequest) {
        Map<String, Object> map = new HashMap<>();
        if (vendorAccUserInfoRequest.getLinkAcctTypeCd().equals("10")) {
            map.put("accUserInfo",etEntrSyncMapper.getVendorPopbillAccUserInfo(vendorAccUserInfoRequest));
        } else if (vendorAccUserInfoRequest.getLinkAcctTypeCd().equals("20")) {
            map.put("accUserInfo",etEntrSyncMapper.getVendorTossAccUserInfo(vendorAccUserInfoRequest));
        } else {
            map.put("accUserInfo",etEntrSyncMapper.getVendorXpayAccUserInfo(vendorAccUserInfoRequest));
        }
        return map;
    }

    @Override
    public VendorXpayAccUserInfo getVendorInfoForXpay(VendorAccUserInfoRequest vendorAccUserInfoRequest){
        return etEntrSyncMapper.getVendorInfoForXpay(vendorAccUserInfoRequest);
    }

    @Override
    public VendorAempInfo getVendorAempInfo(String entrNo) {
        return etEntrSyncMapper.getVendorAempInfo(entrNo);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int updateVendorSyncStatus(String type, String entrNo, String syncYn) {
        return etEntrSyncTrxMapper.updateVendorSyncStatus(type,entrNo,syncYn);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = {Exception.class, RuntimeException.class}, readOnly = false, value = "displayRwdbTxManager")
    public void updateVendorPopbillProcess(VendorPopbillProcessRequest request) throws Exception {
        //팝빌 API 연동 플래그 반영
        String setSubmallId = request.getInfo().getLinkAcctId();
        try {
            int updateVendorPopbillSyncStatusResult = this.updateVendorSyncStatus(request.getRequest().getLinkAcctTypeCd(),request.getRequest().getEntrNo(),"Y");
            if(updateVendorPopbillSyncStatusResult < 1){
                throw new CommonException("팝빌 연동계정 정보 추가/갱신 실패 : 팝빌 API 연동 플래그 반영 실패");
            }

            boolean isUpdatableCondition = (request.getRequest().getBmanNo().equals(request.getInfo().getBmanNo()) || request.getInfo().getExistBusinessnumSubmallId() != null);
            if(setSubmallId != null && isUpdatableCondition == true){

                int updateResultToInValidResult = etEntrSyncTrxMapper.updateResultToInValid(request.getRequest().getEntrNo(),request.getInfo().getLinkAcctTypeCd());
                int updateResultToRecoverDataResult = etEntrSyncTrxMapper.updateResultToRecoverData(request.getRequest());

                if(updateResultToInValidResult < 1 || updateResultToRecoverDataResult < 1){
                    throw new CommonException("팝빌 API 서브계정 관련 유효성 플래그 갱신 실패");
                }
                CorpInfo corpInfo = setCorpInfo(request);
                Response corpInfoResponse = UpdateCorpInfo(request.getRequest().getBmanNo(),corpInfo);
                if(corpInfoResponse.getCode() != 1) {
                    updateVendorSyncStatus(request.getRequest().getLinkAcctTypeCd(),request.getRequest().getEntrNo(),"N");
                    throw new CommonException("팝빌 연동계정 갱신 API 실패");
                }
                /** 입점사 팝빌 정보 업데이트 */
                ContactInfo contactInfo = setContactInfo(request);
                Response response = UpdateContact(request.getRequest().getBmanNo(), contactInfo);
                log.error("팝빌 담당자 수정 결과 코드: {}",response.getCode());
                log.error("팝빌 담당자 수정 결과 메시지: {}",response.getMessage());

            } else if (setSubmallId!=null && isUpdatableCondition == false) { //사업자번호 변경이 확인됨. 재연동을 시도
                /** TO-BE에서는 사업자 등록번호 수정이 불가하여 해당 로직을 태우지 않음 */
                String submallId = request.getInfo().getMaxSubmallId();
                if(!submallId.equals("test_provider")){
                    String[] submallIdExploded = request.getInfo().getMaxSubmallId().split("-"); // 기존 가입 정보를 무효처리하고, 신규 가입 정보를 생성
                    if (submallIdExploded.length < 1) {
                        throw new CommonException("유효하지 않은 서브몰 ID");
                    } else if (submallIdExploded.length == 1) {
                        submallId = submallIdExploded[0] + "_1";
                    } else {
                        submallId = submallIdExploded[0] + "_" + (Integer.parseInt(submallIdExploded[1])+1);
                    }
                    int updateResultToInValid = etEntrSyncTrxMapper.updateToInValid(request.getRequest().getEntrNo(),"N",request.getInfo().getLinkAcctTypeCd());
                    if(updateResultToInValid < 1){
                        throw new CommonException("팝빌 연동계정 재가입 API 실패 : 팝빌 API 서브계정 관련 유효성 플래그 갱신 실패");
                    }
                }
                /** 사업자번호 변경에 따른 추가 가입 절차 진행 */
                VendorPopbillAccountRequest vendorPopbillAccountRequest = new VendorPopbillAccountRequest();
                vendorPopbillAccountRequest.setSubmallId(submallId);
                vendorPopbillAccountRequest.setEntrNo(request.getRequest().getEntrNo());
                vendorPopbillAccountRequest.setBmanNo(request.getRequest().getBmanNo());
                int insertResult = etEntrSyncTrxMapper.insertPopbillAccount(vendorPopbillAccountRequest);
                if(submallId.isEmpty() || insertResult < 1){
                    updateToInValidAfterInsert(request.getRequest().getEntrNo());
                    throw new CommonException("팝빌 연동계정 재가입 API 실패 : 팝빌 API 연동 플래그 또는 데이터 반영 실패");
                }
                int updateToValidByTargetIdResult = etEntrSyncTrxMapper.updateToValidByTargetId(request.getRequest().getEntrNo(),request.getInfo().getLinkAcctId());
                if(updateToValidByTargetIdResult < 1){
                    updateVendorSyncStatus(request.getRequest().getLinkAcctTypeCd(),request.getRequest().getEntrNo(),"N");
                    throw new CommonException("팝빌 연동계정 기존 가입 정보 비활성 실패 : 팝빌 API 연동 데이터 갱신 실패");
                }
                /**
                 * 팝빌 연동계정 재가입
                 */
                JoinForm joinForm = setJoinInfo(request);
                VendorPopbillResultResponse joinMemberResponse = JoinMember(joinForm);
                if(joinMemberResponse.getCode() != 1 && "기존회원".equals(joinMemberResponse.getMessage())) {
                    updateVendorSyncStatus(request.getRequest().getLinkAcctTypeCd(),request.getRequest().getEntrNo(),"N");
                    throw new CommonException("팝빌 연동계정 재가입 API 실패 : "+joinMemberResponse.getCode()+" :: "+joinMemberResponse.getMessage());
                }
            } else {
                /** 신규 가입절차 실행 */
                VendorPopbillAccountRequest vendorPopbillAccountRequest = new VendorPopbillAccountRequest();
                String entrNo = request.getInfo().getSubmallId();
                vendorPopbillAccountRequest.setSubmallId(entrNo);
                vendorPopbillAccountRequest.setEntrNo(request.getRequest().getEntrNo());
                vendorPopbillAccountRequest.setBmanNo(request.getInfo().getBmanNo());
                int insertResult = etEntrSyncTrxMapper.insertPopbillAccount(vendorPopbillAccountRequest);
                if(insertResult < 1){
                    throw new CommonException("팝빌 연동계정 정보 추가 실패 : 팝빌 API 연동 데이터 생성 실패");
                }
                setSubmallId = "submall".concat(request.getInfo().getEntrNo());// 신규가입시 DB에 연동계정id가 없으므로 만들어준 값을 대입
                request.getInfo().setSubmallId(setSubmallId);
                JoinForm joinForm = setJoinInfo(request);
                VendorPopbillResultResponse joinMemberResponse = JoinMember(joinForm);
                long resultCode = joinMemberResponse.getCode();
                if(resultCode != 1 && resultCode != -10001000) {
                    updateVendorSyncStatus(request.getRequest().getLinkAcctTypeCd(),request.getRequest().getEntrNo(),"N");
                    throw new CommonException("팝빌 연동계정 신규가입 API 실패 : "+joinMemberResponse.getCode()+" :: "+joinMemberResponse.getMessage());
                }
            }
        }catch (PopbillException e){
            updateVendorSyncStatus(request.getRequest().getLinkAcctTypeCd(),request.getRequest().getEntrNo(),"N");
            throw new CommonException(e.getMessage());
        } catch (Exception e) {
            updateVendorSyncStatus(request.getRequest().getLinkAcctTypeCd(),request.getRequest().getEntrNo(),"N");
            log.error(e.getMessage());
            throw new Exception(e);
        }
    }


    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = {Exception.class, RuntimeException.class}, readOnly = false, value = "displayRwdbTxManager")
    public void updateVendorTossProcess(VendorTossProcessRequest request) throws Exception {
        String apiPath = apiUrl;
        request.getRequest().setMid(mid);
        request.getRequest().setApiUrl(apiUrl);
        request.getRequest().setSecretKey(secretKey);
        request.getRequest().setClientKey(clientKey);
        request.getRequest().setBasicToken(basicToken);
        request.setInfo(etEntrSyncMapper.getVendorTossAccUserInfo(request.getRequest()));

        boolean isUpdatableCondition = (request.getRequest().getBmanNo().equals(request.getInfo().getBmanNo()) || request.getInfo().getExistBusinessnumSubmallId() != null);
        String setSubmallId = request.getInfo().getLinkAcctId(); //입점사 저장 후 해당 입점사 entr_no로 되어있는 연동 계정이 있는지 확인
        int updateVendorTossSyncStatusResult = this.updateVendorSyncStatus(request.getRequest().getLinkAcctTypeCd(),request.getRequest().getEntrNo(),"Y");
        if(updateVendorTossSyncStatusResult < 1){
            throw new CommonException("Toss API 연동 상태변경 실패");
        }
        /** 수정절차진행 */
        if(setSubmallId != null && isUpdatableCondition){
            log.debug("check 일반적인 수정 절차 or 이전 사업자번호의 계정 복원을 확인: {}",request.getInfo().getExistBusinessnumSubmallId());
            int updateResultToInValidResult = etEntrSyncTrxMapper.updateResultToInValid(request.getRequest().getEntrNo(),request.getInfo().getLinkAcctTypeCd());
            int updateResultToRecoverDataResult = etEntrSyncTrxMapper.updateResultToRecoverData(request.getRequest());

            if(updateResultToInValidResult < 1 || updateResultToRecoverDataResult < 1){
                throw new CommonException("Toss 연동계정 정보 갱신 실패 : 지급대행 API 연동 데이터 갱신 실패");
            }

            apiPath += "/"+request.getInfo().getExistBusinessnumSubmallId();
            setSubmallId = request.getInfo().getExistBusinessnumSubmallId();
        } else if (setSubmallId != null && !isUpdatableCondition){ // 사업자번호가 바뀌고 연동계정 정보가 있을때
            /** 수정절차진행
             *  기존 가입 정보를 무효처리하고, 신규 가입 정보를 생성
             */
            /** TO-BE에서는 사업자 등록번호 수정이 불가하여 해당 로직을 태우지 않음 */
            String[] submallIdExploded = {};
            if(!setSubmallId.equals("test_provider")){
                submallIdExploded = request.getInfo().getMaxSubmallId().split("-");

                if (submallIdExploded.length < 1) {
                    throw new CommonException("유효하지 않은 서브몰 ID");
                } else if (submallIdExploded.length == 1) {
                    setSubmallId = submallIdExploded[0] + "_1";
                } else {
                    setSubmallId = submallIdExploded[0] + "_" + (Integer.parseInt(submallIdExploded[1])+1);
                }
                int updateResultToInValid = etEntrSyncTrxMapper.updateToInValid(request.getRequest().getEntrNo(),"N",request.getInfo().getLinkAcctTypeCd());
                if(updateResultToInValid < 1){
                    throw new CommonException("Toss 연동계정 재가입 API 실패 : Toss API 서브계정 관련 유효성 플래그 갱신 실패");
                }
            }
            VendorTossAccountRequest tossAccountRequest = new VendorTossAccountRequest();
            tossAccountRequest.setEntrNo(request.getRequest().getEntrNo());
            tossAccountRequest.setMid(mid);
            tossAccountRequest.setSecretKey(secretKey);
            tossAccountRequest.setSubmallId(setSubmallId);
            tossAccountRequest.setBmanNo(request.getRequest().getBmanNo());
            int insertResult = etEntrSyncTrxMapper.insertTossAccount(tossAccountRequest); //사업자번호 변경에 따른 추가 가입 절차 진행
            if(submallIdExploded.length < 1 || (setSubmallId.isEmpty() && setSubmallId != null) && insertResult <1){
                updateVendorSyncStatus(request.getRequest().getLinkAcctTypeCd(),request.getRequest().getEntrNo(),"N");
                throw new CommonException("Toss 연동계정 재가입 API 실패 : Toss API 연동 플래그 또는 데이터 반영 실패");
            }
            int updateResultToInValid = etEntrSyncTrxMapper.updateToInValid(request.getRequest().getEntrNo(),"N",request.getInfo().getLinkAcctTypeCd());
            if(updateResultToInValid < 1){
                updateVendorSyncStatus(request.getRequest().getLinkAcctTypeCd(),request.getRequest().getEntrNo(),"N");
                throw new CommonException("Toss 연동계정 기존 가입 정보 비활성 실패 : Toss API 연동 데이터 갱신 실패");
            }
        } else {
            /** 신규 가입 절자 진행 */
            setSubmallId = "submall".concat(request.getInfo().getEntrNo());
            apiPath = apiUrl;

            VendorTossAccountRequest tossAccountRequest = new VendorTossAccountRequest();

            tossAccountRequest.setEntrNo(request.getRequest().getEntrNo());
            tossAccountRequest.setMid(mid);
            tossAccountRequest.setSecretKey(secretKey);
            tossAccountRequest.setSubmallId(setSubmallId);
            tossAccountRequest.setBmanNo(request.getRequest().getBmanNo());
            int insertResult = etEntrSyncTrxMapper.insertTossAccount(tossAccountRequest);
            if(insertResult < 1){
                throw new CommonException("Toss 연동계정 정보 추가 실패 : 지급대행 API 연동 데이터 추가 실패");
            }
        }
        String jsonInputString = getString(request, setSubmallId);
        try {
            this.submallRegist(jsonInputString,apiPath);
        } catch (CommonException e) {
            this.updateVendorSyncStatus("20",request.getRequest().getEntrNo(),"N");
        } catch (Exception e) {
            throw new Exception(e);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = {Exception.class, RuntimeException.class}, readOnly = false, value = "displayRwdbTxManager")
    public void updateVendorXpayProcess(VendorXpayProcessRequest request) throws Exception {
        List<String> midList = this.getMidList(request.getXpayInfo());
        List<String> mkeyList = this.getMkeyList(request.getXpayInfo());
        Map<String,VendorXpayAccUserInfo> vendorXpayAccUserInfos = this.GetXPaySubmallInfo(midList, request.getRequest());
        String pForm = request.getXpayInfo().get("platform").toString();
        String submallId = "submall" + request.getRequest().getEntrNo();
        if(request.getRequest().getBmanNo().equals(HqCorpNum)){
            // return "본사 사업자번호와 동일한 입점사는 처리 대상이 아닙니다.";
            submallId = SubmallId;
        }
        try {
            for(int i = 0; i < midList.size(); i++ ){
                String mid = midList.get(i);
                String mkey = mkeyList.get(i);
                VendorXpayAccUserInfo xpayAccUserInfo = vendorXpayAccUserInfos.get(mid);
                int updateVendorXpaySyncStatusResult = this.updateVendorSyncStatus("30",request.getRequest().getEntrNo(),"Y");
                if(updateVendorXpaySyncStatusResult < 1){
                    throw new CommonException("XPay 연동계정 정보 추가/갱신 실패");
                }

                String lgdMethod = "";
                String setSubmallId = submallId;
                boolean isUpdatableCondition = (request.getRequest().getBmanNo().equals(xpayAccUserInfo.getBmanNo()) || xpayAccUserInfo.getExistBusinessnumSubmallId() != null);

                if(xpayAccUserInfo.getLinkAcctId() != null && isUpdatableCondition){
                    /** 수정절차 */
                    lgdMethod = "CHANGE";
                    int updateResultToInValidResult = etEntrSyncTrxMapper.updateResultToInValid(request.getRequest().getEntrNo(),xpayAccUserInfo.getLinkAcctTypeCd());
                    int updateResultToRecoverDataResult = etEntrSyncTrxMapper.updateResultToRecoverData(request.getRequest());

                    if(updateResultToInValidResult < 1 || updateResultToRecoverDataResult < 1){
                        throw new CommonException("Xpay 연동계정 정보 갱신 실패 : 분할매출전표 API 연동 데이터 갱신 실패");
                    }

                    setSubmallId = xpayAccUserInfo.getExistBusinessnumSubmallId();
                } else if(xpayAccUserInfo.getLinkAcctId() != null && !isUpdatableCondition){
                    /** 기존가입 무효처리하고 신규 가입 정보 생성 */
                    /** TO-BE에서는 사업자 등록번호 수정이 불가하여 해당 로직을 태우지 않음 */
                    String[] submallIdExploded = {};
                    if(!setSubmallId.equals("test_provider")){
                        submallIdExploded = xpayAccUserInfo.getMaxSubmallId().split("-");

                        if (submallIdExploded.length < 1) {
                            throw new CommonException("유효하지 않은 서브몰 ID");
                        } else if (submallIdExploded.length == 1) {
                            setSubmallId = submallIdExploded[0] + "_1";
                        } else {
                            setSubmallId = submallIdExploded[0] + "_" + (Integer.parseInt(submallIdExploded[1])+1);
                        }
                    }
                    lgdMethod = "REGISTER";
                } else {
                    /** 신규가입 */
                    lgdMethod = "REGISTER";
                    VendorXpayAccountRequest xpayAccountRequest = new VendorXpayAccountRequest();
                    xpayAccountRequest.setEntrNo(request.getInfo().getEntrNo());
                    xpayAccountRequest.setMid(mid);
                    xpayAccountRequest.setSubmallId(setSubmallId);
                    xpayAccountRequest.setBmanNo(request.getInfo().getBmanNo());
                    int insertResult = etEntrSyncTrxMapper.insertXpayAccount(xpayAccountRequest);
                    if(insertResult < 1) {
                        throw new CommonException("분할매출전표 API 연동 데이터 생성 실패");
                    }
                }
                String configPath = LG_PATH != null ? LG_PATH.getFile() : null;
                this.syncXpay(xpayAccUserInfo,mid,mkey,configPath,lgdMethod,setSubmallId,pForm);
            }
        } catch (CommonException e) {
            throw new Exception(e);
        }
    }

    @Transactional( propagation = Propagation.REQUIRES_NEW)
    public void updateToInValidAfterInsert(String entrNo){
        etEntrSyncTrxMapper.updateToInValidAfterInsert(entrNo);
    }

    private Response UpdateContact(String bmanNo, ContactInfo contactInfo) throws PopbillException{
        PopbillSyncRequest request = new PopbillSyncRequest();
        request.setBmanNo(bmanNo);
        request.setContactInfo(contactInfo);
        return restApiUtil.post(commonApiUrl + "/api/common/v1/popbill/updateContact", request, new ParameterizedTypeReference<com.x2bee.common.base.rest.Response<Response>>() {}).getPayload();
    }

    private Response UpdateCorpInfo(String bmanNo, CorpInfo corpInfo) throws PopbillException{
        PopbillSyncRequest request = new PopbillSyncRequest();
        request.setBmanNo(bmanNo);
        request.setCorpInfo(corpInfo);
        return restApiUtil.post(commonApiUrl + "/api/common/v1/popbill/updateCorpInfo", request, new ParameterizedTypeReference<com.x2bee.common.base.rest.Response<Response>>() {}).getPayload();
    }

    private VendorPopbillResultResponse JoinMember(JoinForm joinForm) throws PopbillException{
        PopbillSyncRequest request = new PopbillSyncRequest();
        request.setPassword(SubUserPwd);
        request.setJoinForm(joinForm);
        VendorPopbillResultResponse payload = restApiUtil.post(
                commonApiUrl + "/api/common/v1/popbill/joinMember", request, new ParameterizedTypeReference<com.x2bee.common.base.rest.Response<VendorPopbillResultResponse>>() {}
        ).getPayload();
        return payload;
    }

    private Response submallRegist(String jsonString, String apiUrl){
        TossSyncRequest request = new TossSyncRequest();
        request.setJsonInputString(jsonString);
        request.setUrl(apiUrl);
        return restApiUtil.post(commonApiUrl + "/api/common/v1/tosspay/submall", request, new ParameterizedTypeReference<com.x2bee.common.base.rest.Response<Response>>() {}).getPayload();
    }

    private Response syncXpay(VendorXpayAccUserInfo xpayAccUserInfo
            ,String mid,String mkey,String configPath
            ,String lgdMethod,String setSubmallId, String pForm){

        XpaySyncRequest request = new XpaySyncRequest();
        request.setXpayAccUserInfo(xpayAccUserInfo);
        request.setMid(mid);
        request.setMkey(mkey);
        request.setConfigPath(configPath);
        request.setLgdMethod(lgdMethod);
        request.setSubmallId(setSubmallId);
        request.setPForm(pForm);
        return restApiUtil.post(commonApiUrl + "/api/common/v1/xpay/submall", request, new ParameterizedTypeReference<com.x2bee.common.base.rest.Response<Response>>() {}).getPayload();
    };

    private CorpInfo setCorpInfo(VendorPopbillProcessRequest request){
        StringBuilder stringBuilder = new StringBuilder(request.getInfo().getZipAddr());
        if(request.getInfo().getDtlAddr() != null && !request.getInfo().getDtlAddr().isEmpty()){
            stringBuilder.append(" ").append(request.getInfo().getDtlAddr());
        }
        CorpInfo corpInfo = new CorpInfo();
        corpInfo.setCeoname(request.getInfo().getRpstmnNm());
        corpInfo.setCorpName(request.getInfo().getEntrNm());
        corpInfo.setAddr(stringBuilder.toString());
        corpInfo.setBizType(request.getInfo().getBtyp());
        corpInfo.setBizClass(request.getInfo().getBkind());
        return corpInfo;
    }

    private ContactInfo setContactInfo(VendorPopbillProcessRequest request){
        String tel = "";
        if((request.getAempInfo().getTelRgnNo()!=null && request.getAempInfo().getTelRgnNo().length()>0)
            &&(request.getAempInfo().getTelTxnoNo()!=null && request.getAempInfo().getTelTxnoNo().length()>0)
            &&(request.getAempInfo().getTelEndNo()!=null && request.getAempInfo().getTelEndNo().length()>0)){
             tel = String.format("%s%s%s",
                    request.getAempInfo().getTelRgnNo(),
                    request.getAempInfo().getTelTxnoNo(),
                    request.getAempInfo().getTelEndNo());
        }
        String cell = String.format("%s%s%s",
                request.getAempInfo().getCellSctNo(),
                request.getAempInfo().getCellTxnoNo(),
                request.getAempInfo().getCellEndNo());

        ContactInfo contactInfo = new ContactInfo();
        contactInfo.setId(request.getInfo().getLinkAcctId());
        contactInfo.setPersonName(request.getAempInfo().getAempNm());
        contactInfo.setEmail(request.getAempInfo().getEmailAddr());
        contactInfo.setTel( !tel.isEmpty() ? tel : cell );
        contactInfo.setSearchRole(3);
        return contactInfo;
    }

    private JoinForm setJoinInfo(VendorPopbillProcessRequest request){
        StringBuilder stringBuilder = new StringBuilder(request.getInfo().getZipAddr());
        if(request.getInfo().getDtlAddr() != null && !request.getInfo().getDtlAddr().isEmpty()){
            stringBuilder.append(" ").append(request.getInfo().getDtlAddr());
        }
        String cell = String.format("%s%s%s",
                request.getAempInfo().getCellSctNo(),
                request.getAempInfo().getCellTxnoNo(),
                request.getAempInfo().getCellEndNo());

        JoinForm joinForm = new JoinForm();
        joinForm.setID(request.getInfo().getSubmallId());
        joinForm.setPassword(SubUserPwd);
        joinForm.setLinkID(LinkId);
        joinForm.setCorpNum(request.getInfo().getBmanNo().replaceAll("-",""));
        joinForm.setCEOName(request.getInfo().getRpstmnNm());
        joinForm.setCorpName(request.getInfo().getEntrNm());
        joinForm.setAddr(stringBuilder.toString());
        joinForm.setBizType(request.getInfo().getBtyp());
        joinForm.setBizClass(request.getInfo().getBkind());
        joinForm.setContactName(request.getAempInfo().getAempNm());
        joinForm.setContactEmail(request.getAempInfo().getEmailAddr());
        joinForm.setContactTEL(cell);
        if(!activeProfile.equals("prd")){
            joinForm.setContactName("T 이원표");
            joinForm.setContactEmail("wplee0102@plateer.com");
            joinForm.setContactTEL("01040529585");
            joinForm.setBizType("업태");
            joinForm.setBizClass("업종");
        }
        return joinForm;
    }

    private List<String> getMidList(Map xpayInfo){
        Map<String,Object> mallGroup = (Map<String,Object>)xpayInfo.get("mallGroup");
        List<String> midList = new ArrayList<>();
        try {
            for(Map.Entry<String,Object> mallGroupEntry : mallGroup.entrySet()){
                /** xpayInfo을 순회하여 mallGroup을 꺼내옴 */
                Map<String,Object> groupCode = (Map<String,Object>) mallGroupEntry.getValue();

                for(Map.Entry<String,Object> groupCodeEntry : groupCode.entrySet()){
                    /** mallGroup을 순회하여 midGroup을 꺼내옴(PC,MOBILE) */
                    Map<String,Object> codeType = (Map<String,Object>) groupCodeEntry.getValue();

                    for (Map.Entry<String,Object> midEntry : codeType.entrySet()){
                        /** midGroup의 mid값을 리스트에 추가 */
                        String key = midEntry.getKey();
                        String value = midEntry.getValue().toString();
                        if(key.equals("MID")){
                            midList.add(value);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("EXTRACTING EXCEPTION : {}",e.getMessage());
        }

        return midList;
    }

    private List<String> getMkeyList(Map xpayInfo){
        Map<String,Object> mallGroup = (Map<String,Object>)xpayInfo.get("mallGroup");
        List<String> mKeyList = new ArrayList<>();
        try {
            for(Map.Entry<String,Object> mallGroupEntry : mallGroup.entrySet()){
                /** xpayInfo을 순회하여 mallGroup을 꺼내옴 */
                Map<String,Object> groupCode = (Map<String,Object>) mallGroupEntry.getValue();

                for(Map.Entry<String,Object> groupCodeEntry : groupCode.entrySet()){
                    /** mallGroup을 순회하여 midGroup을 꺼내옴(PC,MOBILE) */
                    Map<String,Object> codeType = (Map<String,Object>) groupCodeEntry.getValue();

                    for (Map.Entry<String,Object> midEntry : codeType.entrySet()){
                        /** midGroup의 mkey 값을 리스트에 추가 */
                        String key = midEntry.getKey();
                        String value = midEntry.getValue().toString();
                        if(key.equals("MKEY")){
                            mKeyList.add(value);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("EXTRACTING EXCEPTION : {}",e.getMessage());
        }

        return mKeyList;
    }

    private  Map<String,VendorXpayAccUserInfo> GetXPaySubmallInfo(List<String> midList, VendorAccUserInfoRequest vendorAccUserInfoRequest){
        Map<String, VendorXpayAccUserInfo> result = new HashMap<>();
        List<VendorXpayAccUserInfo> vendorXpayAccUserInfoList = etEntrSyncMapper.getVendorXpayAccUserInfoList(vendorAccUserInfoRequest);
        VendorXpayAccUserInfo vendorXpayAccUserInfoDefault = etEntrSyncMapper.getVendorInfoForXpay(vendorAccUserInfoRequest);
        try {
            for (String mid : midList) {
                Optional<VendorXpayAccUserInfo> info
                        = vendorXpayAccUserInfoList.stream()
                        .filter(x -> mid.equals(x.getLinkAcctTgt()))
                        .findFirst();
                // MID에 해당하는 정보가 없으면 기본 정보 설정
                result.put(mid, info.orElse(vendorXpayAccUserInfoDefault));
            }

        } catch (Exception e) {
            log.error("FIND EQUALS XPAY INFO BY MID EXCEPTION: {}",e.getMessage());
        }
        return result;
    }

    private static @NotNull String getString(VendorTossProcessRequest request, String setSubmallId) {
        String subMallId = setSubmallId;
        String companyName = request.getInfo().getEntrNm();
        String representativeName = request.getInfo().getRpstmnNm();
        String businessNumber = request.getRequest().getBmanNo();
        String bank = request.getInfo().getDptBankCd();
        String accountNumber = request.getInfo().getActnNo().replace("-","");
        String holderName = request.getInfo().getDepositorNm();
        String type = request.getInfo().getBmanGbCd().equals("20")? "INDIVIDUAL" : "CORPORATE";
        String email = request.getInfo().getAempEmail();
        String phoneNumber = request.getInfo().getAempCellSctNo() + request.getInfo().getAempCellTxnoNo() + request.getInfo().getAempCellEndNo();

        String jsonInputString = "{\"subMallId\":\""+subMallId+"\"" +
                ",\"account\":{\"bank\":\""+bank+"\"" +
                ",\"accountNumber\":\""+accountNumber+"\"" +
                ",\"holderName\":\""+holderName+"\"}" +
                ",\"type\":\""+type+"\"" +
                ",\"email\":\""+email+"\"" +
                ",\"phoneNumber\":\""+phoneNumber+"\"" +
                ",\"representativeName\":\""+representativeName+"\"" +
                ",\"businessNumber\":\""+businessNumber+"\"}" +
                ",\"companyName\":\""+companyName+"\"";
        return jsonInputString;

    }

}
