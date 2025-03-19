package com.x2bee.api.common.app.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * @author shguddnr2@coremethod.co.kr
 * @version 1.0
 * @description
 * @since 25. 1. 10.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PopBillUtil {

    /* 실제 세금 계산서를 발급받고 파일로 저장한 파일 경로 */
    private final String AS_IS_FILE_PATH = "./src/test/resources/as_is.html";

    /* 발급받은 세금 계산서를 실제로 html 로 저장하는 경로 */
    private final String FILE_PATH = "./src/test/resources/test.html";

    /* as-is 이미지 경로 */
    private final String[] FROM_URL = {"/images/watermark/", "/images/cash/", "/images/taxinvoice/", "/images/common/table/", "/images/common/"};

    /* to-be 이미지 경로 */
    private final String TO_URL = "/api/common/static/img/";

    private final String REPLACE_HTML_REGEX = """
            <(!DOCTYPE html|[/]*html(.*)|[/]*head(.*)|meta(.*)|title(.*)|[/]*body|link(.*)|)>|<script(.*)>(.*)</script>
            """;

    private final String UPDATE_REPLACE_HTML_REGEX = """
            <script\\b[^>]*>(.*?)</script>|<!--(.*?)-->
            """;
    
    public String urlToHtml(String urlString) {

        /* 리다이렉트 URL 추적을 위한 추가 설정을 해주는 부분 */
        RestTemplate restTemplate = getRestTemplate();

        /* 헤더에 Referer 값 설정을 해주는 부분 */
        HttpEntity<String> httpEntity = createHttpEntity(urlString);
        String callUrl = "";
        try {
            URL url = new URL(urlString);
            String host = url.getHost();
            String protocol = url.getProtocol();
            callUrl = String.format("%s://%s/", protocol, host);
        } catch (MalformedURLException mue) {
            log.error("html url 파싱 에러 = ", mue);
        }

        try {

            /* getViewUrl 을 통해 가져온 응답 추출하는 부분 */
            ResponseEntity<String> response = restTemplate.exchange(
                    urlString,
                    HttpMethod.GET,
                    httpEntity,
                    String.class
            );

            /* 리다이렉트 URL 추적하여 내용 가져오는 부분 */
            if (response.getStatusCode().is3xxRedirection()) {
                HttpHeaders headers = response.getHeaders();
                String location = Objects.requireNonNull(headers.getLocation()).toString();

                response = restTemplate.exchange(
                        String.format("%s%s", callUrl, location),
                        HttpMethod.GET,
                        httpEntity,
                        String.class
                );
            }

            /* 본문 html 설정해 주는 부분 */
            String htmlContent = response.getBody();
            StringBuilder pureHtml = new StringBuilder(Objects.requireNonNull(htmlContent));
            replaceHtml(pureHtml);
            return pureHtml.toString();

        } catch (Exception e) {
            log.error("html parse error = ", e);
            return "";
        }
    }

    /* 요청 템플릿 설정 */
    @NotNull
    private RestTemplate getRestTemplate() {
        RequestConfig requestConfig = RequestConfig.custom()
                .setRedirectsEnabled(false)
                .build();
        CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build();
        HttpComponentsClientHttpRequestFactory factory =
                new HttpComponentsClientHttpRequestFactory(httpClient);
        return new RestTemplate(factory);
    }

    /* 요청 헤더 설정 */
    private HttpEntity<String> createHttpEntity(String urlString) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Referer", urlString);
        return new HttpEntity<>(headers);
    }

    /* 테스트를 위한 html 저장 */
    private void saveToFile(String content) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(FILE_PATH, StandardCharsets.UTF_8))) {
            writer.write(content);
        }
    }

    /* html 가공 */
    private void replaceHtml(StringBuilder pureHtml) {
        String updatedHtml = pureHtml.toString().replaceAll(REPLACE_HTML_REGEX, "");
        updatedHtml = updatedHtml.replaceAll(UPDATE_REPLACE_HTML_REGEX, "");
        for (String s : FROM_URL) {
            updatedHtml = updatedHtml.replaceAll(s, TO_URL);
        }
        pureHtml.setLength(0);
        pureHtml.append(updatedHtml);
    }

}
