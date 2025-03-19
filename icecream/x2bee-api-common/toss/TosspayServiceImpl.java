package com.x2bee.api.common.app.service.toss;

import com.x2bee.api.common.app.dto.request.toss.TossSyncRequest;
import com.x2bee.common.base.exception.CommonException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * packageName    : com.x2bee.api.common.app.service.toss
 * fileName       : TosspayServiceImpl
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
public class TosspayServiceImpl implements TosspayService{
    @Value("${toss.basic-token}")
    private String basicToken;

    @Override
    public void submallRegist(TossSyncRequest request) {
        int responseCode = 0;
        String jsonInputString = request.getJsonInputString();
        try {
            URL url = new URL(request.getUrl());
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Basic " + basicToken);
            conn.setRequestProperty("Content-type", "application/json");
            conn.setConnectTimeout(60000); // 60s
            conn.setReadTimeout(60000); // 60s
            conn.setDoOutput(true);

            // OutputStream에 데이터 쓰기
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
                os.flush(); // 명시적으로 flush 호출
            }

            // 응답 코드 확인
            responseCode = conn.getResponseCode();
            log.error("Toss api 리턴코드확인 : {}", responseCode);

            InputStream inputStream = null;
            if (responseCode >= 200 && responseCode < 300) {
                inputStream = conn.getInputStream(); // 성공 응답 스트림
            } else {
                inputStream = conn.getErrorStream(); // 오류 응답 스트림
                if (inputStream == null) {
                    log.error("Toss api 에러코드: {}", responseCode);
                    return;
                }
            }

            // 응답 읽기
            try (BufferedReader in = new BufferedReader(new InputStreamReader(inputStream))) {
                String inputLine;
                StringBuffer response = new StringBuffer();

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                log.error("Toss api 리턴확인 : {}", response);
            }
            if(!(responseCode >= 200 && responseCode < 300)){
                throw new CommonException("TOSS API 서브몰 등록 또는 수정 실패");
            }
        } catch (Exception e) {
            log.error("Exception 발생: ", e);
        }
    }
}
