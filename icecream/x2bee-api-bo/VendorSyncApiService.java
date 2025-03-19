package com.x2bee.api.bo.app.service.vendor.sync;


import com.x2bee.api.bo.app.dto.request.vendor.sync.VendorAccUserInfoRequest;
import com.x2bee.api.bo.app.dto.request.vendor.sync.VendorPopbillProcessRequest;
import com.x2bee.api.bo.app.dto.request.vendor.sync.VendorTossProcessRequest;
import com.x2bee.api.bo.app.dto.request.vendor.sync.VendorXpayProcessRequest;
import com.x2bee.api.bo.app.entity.vendor.VendorAempInfo;
import com.x2bee.api.bo.app.entity.vendor.VendorXpayAccUserInfo;

import java.util.Map;

public interface VendorSyncApiService {

    Map<String, Object> getVendorAccUserInfo(VendorAccUserInfoRequest vendorAccUserInfoRequest);
    VendorXpayAccUserInfo getVendorInfoForXpay(VendorAccUserInfoRequest vendorAccUserInfoRequest);

    VendorAempInfo getVendorAempInfo(String entrNo);
    int updateVendorSyncStatus(String type, String entrNo, String syncPopbillYn);

    //이하 연동회원가입 프로세스에 활용
    void updateVendorPopbillProcess(VendorPopbillProcessRequest request) throws Exception;
    void updateVendorTossProcess(VendorTossProcessRequest request) throws Exception;
    void updateVendorXpayProcess(VendorXpayProcessRequest request) throws Exception;
}
