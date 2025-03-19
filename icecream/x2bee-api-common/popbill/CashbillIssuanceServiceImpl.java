package com.x2bee.api.common.app.service.popbill.cashbill;

import com.x2bee.api.common.app.dto.request.popbill.GetAllSingleIssuanceRequest;
import com.x2bee.api.common.app.dto.request.popbill.SingleTaxInvoiceAndCashBillRequest;
import com.x2bee.api.common.app.dto.request.popbill.cashbill.CashbillSingleIssuanceRequest;
import com.x2bee.api.common.app.dto.response.popbill.cashbill.CashbillSingleIssuanceResponse;
import com.x2bee.api.common.app.entity.EtEntrBase;
import com.x2bee.api.common.app.entity.popbill.PopbillCashbillInfo;
import com.x2bee.api.common.app.enums.OM065;
import com.x2bee.api.common.app.repository.orderrodb.popbill.ProviderMapper;
import com.x2bee.api.common.app.repository.orderrwdb.popbill.PopbillTrxMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * packageName    : com.x2bee.api.common.app.service.popbill.cashbill
 * fileName       : CashbillIssuanceServiceImpl
 * author         : wonpyo
 * date           : 2024-12-11
 * description    :
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 2024-12-11        wonpyo       최초 생성
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CashbillIssuanceServiceImpl implements CashbillIssuanceService {

    private final ProviderMapper providerMapper;
    private final PopbillTrxMapper popbillTrxMapper;

    @Override
    public List<CashbillSingleIssuanceResponse> getSingleIssuanceList(String ordNo, String entrNo, boolean getinvalid) {
        // is_getinvalid에 따라 전체를 대상으로 하거나, 유효한 상대의 현금영수증만 대상으로 함
        List<String> targetStateList = new ArrayList<String>();
        if (getinvalid) {
            targetStateList.add(OM065.STANDBY.getCode());
            targetStateList.add(OM065.REGISTISSUE.getCode());
            targetStateList.add(OM065.PROGRESSISSUE.getCode());
            targetStateList.add(OM065.ISSUE.getCode());
            targetStateList.add(OM065.FAILISSUE.getCode());
            targetStateList.add(OM065.CANCELISSUE.getCode());
        } else {
            targetStateList.add(OM065.STANDBY.getCode());
            targetStateList.add(OM065.REGISTISSUE.getCode());
            targetStateList.add(OM065.PROGRESSISSUE.getCode());
            targetStateList.add(OM065.ISSUE.getCode());
        }
        CashbillSingleIssuanceRequest request = new CashbillSingleIssuanceRequest();
        request.setOrdNo(ordNo);
        request.setEntrNo(entrNo);
        request.setTargetStateList(targetStateList);

        List<CashbillSingleIssuanceResponse> cashbillPerProvider = popbillTrxMapper.getSingleIssuance(request);
        for (int i = 0; i < cashbillPerProvider.size(); i++) {
            CashbillSingleIssuanceResponse singleIssuanceInfo = cashbillPerProvider.get(i);
            /** 프론트 표기를 세금계산서 취소분과 같이 처리하기 위해, 내부에서 금액 * 오프셋을 자체 처리한다. */
            if ("Y".equals(singleIssuanceInfo.getCnclIsuYn())) {
                singleIssuanceInfo.setOrdSupPrc(singleIssuanceInfo.getOrdSupPrc() * -1);
                singleIssuanceInfo.setOrdTaxPrc(singleIssuanceInfo.getOrdTaxPrc() * -1);
                singleIssuanceInfo.setOrdTotAmt(singleIssuanceInfo.getOrdTotAmt() * -1);
            }
        }
        return cashbillPerProvider;
    }

    @Override
    public List<PopbillCashbillInfo> findSingleCashBillList(SingleTaxInvoiceAndCashBillRequest singleTaxInvoiceAndCashBillRequest) {
        List<PopbillCashbillInfo> response = popbillTrxMapper.findSingleCashBillList(singleTaxInvoiceAndCashBillRequest);
        log.info("response = {}", response);
        return response;
    }

    @Override
    public Map<String, List<CashbillSingleIssuanceResponse>> getAllSingleIssuanceList(GetAllSingleIssuanceRequest getAllSingleIssuanceRequest) {
        Map<String, List<CashbillSingleIssuanceResponse>> response = new HashMap<>();
/*
            $getProviderSeqQuery = $this->db->query("
                    SELECT
                    PROVIDER.*
                            FROM
                    FM_ORDER_ITEM AS item
                    JOIN fm_provider AS provider ON item.provider_seq = provider.provider_seq
                    WHERE
                    item.order_seq = ?
                    GROUP BY
            provider.provider_seq", array($order_seq));
            $getProviderSeqList = $getProviderSeqQuery->result_array();
            $result = array();
        */
        String ordNo = getAllSingleIssuanceRequest.getOrdNo();
        String onlyEntrNo = getAllSingleIssuanceRequest.getOnlyEntrNo();
        List<EtEntrBase> getProviderSeqList = providerMapper.getAllSingleIssuanceProviderListByRodNo(ordNo);

        /*
            foreach ($getProviderSeqList as &$providerSeqObj) {
                if ($only_provider_seq != null && $only_provider_seq != $providerSeqObj["provider_seq"])
                    continue;
                $tmpResult = $this->GetSingleIssuanceList($order_seq, $providerSeqObj["provider_seq"], $is_getinvalid);
                if ($tmpResult == false || is_array($tmpResult) == false || count($tmpResult) < 1)
                    continue;
                $result[$providerSeqObj["provider_seq"]]["provider_info"] = $providerSeqObj;
                $result[$providerSeqObj["provider_seq"]]["cashbill_list"] = $tmpResult;
            }
            return $result;
        */
        for (EtEntrBase providerInfo : getProviderSeqList) {
            if(Objects.isNull(providerInfo)) continue;
            if (!Objects.equals(onlyEntrNo, "") && !onlyEntrNo.equals(providerInfo.getEntrNo())) continue;
            String entrNo = providerInfo.getEntrNo();
            List<CashbillSingleIssuanceResponse> singleIssuanceList = this.getSingleIssuanceList(ordNo, entrNo, false);
            if (singleIssuanceList.isEmpty()) continue;
            response.put(entrNo, singleIssuanceList);
        }

        return response;
        
    }
}
