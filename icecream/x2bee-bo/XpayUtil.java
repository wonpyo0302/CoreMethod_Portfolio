package com.x2bee.bo.base.util;

import com.x2bee.bo.base.properties.EnvironmentsConfig;
import com.x2bee.common.base.context.ApplicationContextWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@RequiredArgsConstructor
@Slf4j
public class XpayUtil {
    EnvironmentsConfig config = ApplicationContextWrapper.getBean(EnvironmentsConfig.class);

    private String platform;

    private String pcMallMid;
    private String pcMallMKey;
    private String moMallMid;
    private String moMallMKey;

    private String pcLifeMid;
    private String pcLifeMKey;
    private String moLifeMid;
    private String moLifeMKey;

    private String pcHiMid;
    private String pcHiMKey;
    private String moHiMid;
    private String moHiMKey;

    private String hqCorpNum;

    private void initVariable(Properties configProperties){
        this.platform = configProperties.getProperty("xpayPlatform");
        this.pcMallMid = configProperties.getProperty("xpayPcMallMid");
        this.pcMallMKey = configProperties.getProperty("xpayPcMallMkey");
        this.moMallMid = configProperties.getProperty("xpayMoMallMid");
        this.moMallMKey = configProperties.getProperty("xpayMoMallMkey");

        this.pcLifeMid = configProperties.getProperty("xpayPcLifeMid");
        this.pcLifeMKey = configProperties.getProperty("xpayPcLifeMkey");
        this.moLifeMid = configProperties.getProperty("xpayMoLifeMid");
        this.moLifeMKey = configProperties.getProperty("xpayMoLifeMkey");

        this.pcHiMid = configProperties.getProperty("xpayPcHiMid");
        this.pcHiMKey = configProperties.getProperty("xpayPcHiMkey");
        this.moHiMid = configProperties.getProperty("xpayMoHiMid");
        this.moHiMKey = configProperties.getProperty("xpayMoHiMkey");

        this.hqCorpNum = configProperties.getProperty("xpayHqCorpNum");
    }

    public Map AccountXPaySalesslipApiInfo() {
        Properties configProperties = config.getConfigProperties();
        this.initVariable(configProperties);
        String domain = configProperties.getProperty("cookieDomain");
        log.error("환경설정 값 확인 : {}",configProperties.getProperty("xpayPcMallMid"));
        Map<String, Object> xPaySalesslipApiInfo = new HashMap<>();
        xPaySalesslipApiInfo.put("platform", platform);

        Map<String, Object> mallGroup = setMallGroup();
        xPaySalesslipApiInfo.put("mallGroup", mallGroup);
        return xPaySalesslipApiInfo;
    }

    private Map<String, Object> setMallGroup() {
        Map<String, Object> mallGroup = new HashMap<>();

        mallGroup.put("0001", createMallGroup(pcMallMid, pcMallMKey, moMallMid, moMallMKey));
        mallGroup.put("0002", createMallGroup(pcLifeMid, pcLifeMKey, moLifeMid, moLifeMKey));
        mallGroup.put("0003", createMallGroup(pcHiMid, pcHiMKey, moHiMid, moHiMKey));

        return mallGroup;
    }

    private Map<String, Object> createMallGroup(String pcMid, String pcMKey, String moMid, String moMKey) {
        Map<String, Object> group = new HashMap<>();
        group.put("PC", createInfo(pcMid, pcMKey));
        group.put("MOBILE", createInfo(moMid, moMKey));
        return group;
    }

    private Map<String, Object> createInfo(String mid, String mKey) {
        Map<String, Object> info = new HashMap<>();
        info.put("MID", mid);
        info.put("MKEY", mKey);
        return info;
    }

    private List<String> setList(String... values) {
        return new ArrayList<>(Arrays.asList(values));
    }

    public List<String> setMidList() {
        return setList(pcMallMid, moMallMid, pcLifeMid, moLifeMid, pcHiMid, moHiMid);
    }

    public List<String> setMkeyList() {
        return setList(pcMallMKey, moMallMKey, pcLifeMKey, moLifeMKey, pcHiMKey, moHiMKey);
    }
}
