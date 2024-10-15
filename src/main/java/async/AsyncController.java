package async;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import javax.xml.bind.DatatypeConverter;  // 用于十六进制字符串转字节数组
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
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
        byte[] keyBytes = DatatypeConverter.parseBase64Binary(privateKeyContent);  // Base64解码私钥
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        this.privateKey = keyFactory.generatePrivate(spec);
    }

    // 修改解密消息的方法，不进行 Base64 解码，而是直接处理十六进制字符串转字节数组
    public Mono<String> decryptMessage(String encryptedMessage) {
        return Mono.fromSupplier(() -> {
            try {
                Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
                cipher.init(Cipher.DECRYPT_MODE, privateKey);
                
                // 将十六进制字符串转换为字节数组
                byte[] encryptedBytes = DatatypeConverter.parseHexBinary(encryptedMessage);
                
                // 解密
                byte[] decryptedMessage = cipher.doFinal(encryptedBytes);
                return new String(decryptedMessage);  // 返回解密后的字符串
            } catch (Exception e) {
                return encryptedMessage;  // 如果解密失败，直接返回原始未加密内容
            }
        });
    }

    // 捕获请求，异步解密消息，并保持顺序
    @PostMapping("/{urlOrParam}")
    public Flux<DataBuffer> captureAndForward(@PathVariable String urlOrParam, 
                                              @RequestBody RequestBodyData requestBodyData,
                                              @RequestHeader HttpHeaders headers,  
                                              @RequestHeader(value = "X-Forwarded-For", defaultValue = "localhost") String clientIp) {
        if (requestBodyData.getMessages() == null || requestBodyData.getMessages().isEmpty()) {
            return Flux.error(new IllegalArgumentException("No messages to process"));
        }

        // 检查同IP并发请求限制
        int currentRequests = ipRequestCount.getOrDefault(clientIp, 0);
        if (currentRequests >= configService.getMaxIPConcurrentRequests()) {
            return Flux.error(new IllegalArgumentException("Too many concurrent requests from this IP"));
        }

        ipRequestCount.put(clientIp, currentRequests + 1);

        // 处理特殊参数的逻辑
        String targetUrl;
        if (urlOrParam.equalsIgnoreCase("claude")) {
            targetUrl = claudeUrl;  
        } else if (urlOrParam.equalsIgnoreCase("clewd")) {
            targetUrl = clewdUrl;  
        } else {
            targetUrl = urlOrParam.startsWith("http") ? urlOrParam : "https://" + urlOrParam;
        }

        // 检查URL是否在白名单中
        if (!configService.getWhitelist().contains(targetUrl)) {
            ipRequestCount.put(clientIp, ipRequestCount.get(clientIp) - 1);
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

        // 使用 Flux.concat() 保证解密顺序
        Flux<Void> decryptTasks = Flux.concat(
            requestBodyData.getMessages().stream()
                .map(message -> decryptMessage(message.getContent()).doOnNext(decryptedContent -> {
                    message.setContent(decryptedContent);  // 解密成功则更新内容，失败则保留原始内容
                }))
                .toList()
        );

        // 当解密完成后，转发解密后的数据到目标服务器，且转发过滤后的请求头
        return decryptTasks
            .thenMany(webClient.post()
                .uri(targetUrl)  
                .headers(httpHeaders -> httpHeaders.addAll(filteredHeaders))  
                .bodyValue(requestBodyData)  
                .retrieve()
                .bodyToFlux(DataBuffer.class))  
            .doFinally(signalType -> {
                // 请求完成后，减少当前IP的并发请求数
                ipRequestCount.put(clientIp, ipRequestCount.get(clientIp) - 1);
            });
    }
}
