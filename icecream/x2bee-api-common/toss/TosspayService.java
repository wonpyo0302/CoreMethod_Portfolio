package com.x2bee.api.common.app.service.toss;

import com.x2bee.api.common.app.dto.request.toss.TossSyncRequest;

/**
 * packageName    : com.x2bee.api.common.app.service.toss
 * fileName       : TosspayService
 * author         : wonpyo
 * date           : 2024-10-30
 * description    :
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 2024-10-30        wonpyo       최초 생성
 */
public interface TosspayService {
    void submallRegist(TossSyncRequest request);
}
