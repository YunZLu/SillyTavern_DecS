package async;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.core.io.buffer.DataBuffer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import javax.crypto.Cipher;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
public class AsyncController {

    private static final Logger logger = LoggerFactory.getLogger(AsyncController.class);

    private PrivateKey privateKey;
    private final WebClient webClient = WebClient.create();  // WebClient用于非阻塞HTTP请求

    // 动态配置服务类
    private final ConfigService configService;

    private final Map<String, Integer> ipRequestCount = new ConcurrentHashMap<>();  // 记录每个IP的并发请求数

    // 特定参数映射的目标URL
    private final String claudeUrl = "https://claude-url.com";
    private final String clewdUrl = "https://clewd-url.com";  // clewd 对应的 URL

    public AsyncController(ConfigService configService) {
        this.configService = configService;
    }

    @PostConstruct
    public void init() throws Exception {
        configService.loadConfig();  // 加载白名单和并发限制配置
        loadPrivateKey();            // 加载私钥
    }

    public void loadPrivateKey() throws Exception {
        // 使用 ClassPathResource 从 resources 目录加载私钥文件
        Resource resource = new ClassPathResource("private_key.pem");
        String privateKeyContent = new String(resource.getInputStream().readAllBytes());

        privateKeyContent = privateKeyContent.replaceAll("\\n", "")
                                             .replace("-----BEGIN PRIVATE KEY-----", "")
                                             .replace("-----END PRIVATE KEY-----", "");
        byte[] keyBytes = Base64.getDecoder().decode(privateKeyContent);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        this.privateKey = keyFactory.generatePrivate(spec);
    }

    // 异步解密消息
    public Mono<String> decryptMessage(String encryptedMessage) {
        return Mono.fromSupplier(() -> {
            try {
                Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
                cipher.init(Cipher.DECRYPT_MODE, privateKey);
                byte[] decodedMessage = Base64.getDecoder().decode(encryptedMessage);
                byte[] decryptedMessage = cipher.doFinal(decodedMessage);
                return new String(decryptedMessage);
            } catch (Exception e) {
                logger.error("解密消息失败: ", e);
                return null;
            }
        });
    }

    // 捕获请求，解密消息，转发请求到目标服务器，并转发过滤后的请求头
    @PostMapping("/{urlOrParam}")
    public Flux<DataBuffer> captureAndForward(@PathVariable String urlOrParam, 
                                              @RequestBody RequestBodyData requestBodyData,
                                              @RequestHeader HttpHeaders headers,  // 捕获所有请求头
                                              @RequestHeader(value = "X-Forwarded-For", defaultValue = "localhost") String clientIp) {
        if (requestBodyData.getMessages() == null || requestBodyData.getMessages().isEmpty()) {
            return Flux.error(new IllegalArgumentException("No messages to process"));
        }

        // 检查同IP并发请求限制
        int currentRequests = ipRequestCount.getOrDefault(clientIp, 0);
        if (currentRequests >= configService.getMaxIPConcurrentRequests()) {
            logger.warn("同IP请求超出限制: {}", clientIp);
            return Flux.error(new IllegalArgumentException("Too many concurrent requests from this IP"));
        }

        // 增加当前IP的并发请求数
        ipRequestCount.put(clientIp, currentRequests + 1);

        // 处理特殊参数的逻辑
        String targetUrl;
        if (urlOrParam.equalsIgnoreCase("claude")) {
            targetUrl = claudeUrl;  // 如果是 claude 参数，使用 claude 对应的 URL
        } else if (urlOrParam.equalsIgnoreCase("clewd")) {
            targetUrl = clewdUrl;  // 如果是 clewd 参数，使用 clewd 对应的 URL
        } else {
            targetUrl = urlOrParam.startsWith("http") ? urlOrParam : "https://" + urlOrParam;
        }

        // 检查URL是否在白名单中
        if (!configService.getWhitelist().contains(targetUrl)) {
            ipRequestCount.put(clientIp, ipRequestCount.get(clientIp) - 1);  // 减少并发数
            logger.warn("目标URL不在白名单中: {}", targetUrl);
            return Flux.error(new IllegalArgumentException("URL not in whitelist"));
        }

        // 过滤掉不必要的头信息
        HttpHeaders filteredHeaders = new HttpHeaders();
        headers.forEach((key, value) -> {
            if (!key.equalsIgnoreCase("Host") &&
                !key.equalsIgnoreCase("Content-Length") &&
                !key.equalsIgnoreCase("Accept-Encoding") &&
                !key.equalsIgnoreCase("Connection")) {
                filteredHeaders.put(key, value);
            }
        });

        // 日志：打印转发的URL、请求头和请求数据
        logger.info("转发到目标URL: {}", targetUrl);
        logger.debug("转发的请求头: {}", filteredHeaders);
        logger.debug("转发的请求数据: {}", requestBodyData);

        // 并发执行解密操作
        Mono<Void> decryptTasks = Mono.when(
            requestBodyData.getMessages().stream()
                .map(message -> decryptMessage(message.getContent()).doOnNext(decryptedContent -> {
                    message.setContent(decryptedContent);  // 解密后的内容重新设置回 message
                }))
                .toList()
        );

        // 当解密完成后，转发解密后的数据到目标服务器，且转发过滤后的请求头
        return decryptTasks
            .thenMany(webClient.post()
                .uri(targetUrl)  // 使用解密后的 URL 或特殊参数的映射 URL
                .headers(httpHeaders -> httpHeaders.addAll(filteredHeaders))  // 添加转发的过滤后的请求头
                .bodyValue(requestBodyData)  // 转发整个解密后的 requestBodyData
                .retrieve()
                .bodyToFlux(DataBuffer.class))  // 流式接收响应
            .doFinally(signalType -> {
                // 请求完成后，减少当前IP的并发请求数
                ipRequestCount.put(clientIp, ipRequestCount.get(clientIp) - 1);
            });
    }
}
