package com.x2bee.api.common.base;

import com.x2bee.api.common.base.config.properties.EnvironmentsConfig;
import com.x2bee.common.base.context.ApplicationContextWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.boot.model.naming.IllegalIdentifierException;

import java.lang.reflect.Field;
import java.util.*;

/**
 * packageName    : com.x2bee.api.common.base
 * fileName       : XpayUtil
 * author         : wonpyo
 * date           : 2024-12-27
 * description    :
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 2024-12-27        wonpyo       최초 생성
 */
@Slf4j
@RequiredArgsConstructor
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

    public String getMatchedValue(String value){
        Properties configProperties = config.getConfigProperties();
        this.initVariable(configProperties);
        Field[] fields = XpayUtil.class.getDeclaredFields();
        for(Field field : fields){
            if(field.getName().equalsIgnoreCase(value)){
                field.setAccessible(true);
                try {
                    return field.get(this).toString();
                } catch (IllegalAccessException e) {
                    log.error(e.getMessage());
                }
                return null;
            }
        }
        return null;
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



}
