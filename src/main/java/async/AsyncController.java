package async;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.core.io.buffer.DataBuffer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import javax.crypto.Cipher;
import javax.xml.bind.DatatypeConverter;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
public class AsyncController {

    private static final Logger logger = LoggerFactory.getLogger(AsyncController.class);

    // 常量定义
    private static final String ENCRYPTION_PREFIX = "ENC:";
    private static final String ENCRYPTION_ALGORITHM = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";

    private static PrivateKey privateKey; // 私钥
    private static List<String> whitelist; // 白名单
    private static int maxIPConcurrentRequests; // 最大同IP并发请求数

    private final WebClient webClient = WebClient.create(); // 用于非阻塞HTTP请求
    private final Map<String, AtomicInteger> ipRequestCount = new ConcurrentHashMap<>(); // 记录每个IP的并发请求数

    // 定义一个有限大小的LRU缓存，缓存解密过的数据，避免重复解密
    private final Cache<String, String> decryptionCache = CacheBuilder.newBuilder()
            .maximumSize(1000) // 最大缓存大小
            .expireAfterWrite(10, TimeUnit.MINUTES) // 缓存过期时间为10分钟
            .build();

    private final String claudeUrl = "https://claude-url.com";
    private final String clewdUrl = "https://clewd-url.com"; // clewd对应的URL

    @PostConstruct
    public void init() {
        reloadConfig(); // 从config.json加载配置
        startWatchService(); // 启动WatchService监控config.json文件变化
    }

    private InputStream getConfigFileStream() {
        // 优先检查环境变量或系统属性中的路径
        String configPath = System.getenv("CONFIG_JSON_PATH");
        if (configPath == null || configPath.isEmpty()) {
            configPath = System.getProperty("config.json.path");
        }

        // 如果提供了外部路径，优先使用
        if (configPath != null && !configPath.isEmpty()) {
            File externalConfig = new File(configPath);
            if (externalConfig.exists() && externalConfig.isFile()) {
                logger.info("加载外部配置文件: {}", externalConfig.getAbsolutePath());
                try {
                    return new FileInputStream(externalConfig);
                } catch (FileNotFoundException e) {
                    logger.error("无法加载外部配置文件: {}", e.getMessage());
                }
            }
        }

        // 尝试通过ClassLoader加载JAR包中的配置文件
        ClassLoader classLoader = getClass().getClassLoader();
        InputStream inputStream = classLoader.getResourceAsStream("config.json");
        if (inputStream != null) {
            logger.info("成功加载内部配置文件");
            return inputStream;
        }

        // 如果没有找到配置文件，抛出异常
        throw new IllegalStateException("配置文件不存在或无效。");
    }

    // 启动WatchService，监听config.json文件的变化
    private void startWatchService() {
        try {
            File configFilePath = new File(System.getenv("CONFIG_JSON_PATH") != null ? System.getenv("CONFIG_JSON_PATH") : "src/main/resources/config.json");
            if (configFilePath.exists()) {
                WatchService watchService = FileSystems.getDefault().newWatchService();
                Path configDir = configFilePath.getParentFile().toPath();
                configDir.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);

                new Thread(() -> {
                    while (true) {
                        try {
                            WatchKey key = watchService.take();
                            for (WatchEvent<?> event : key.pollEvents()) {
                                if (event.kind() == StandardWatchEventKinds.ENTRY_MODIFY && configFilePath.getName().equals(event.context().toString())) {
                                    logger.info("检测到 config.json 文件更新，重新加载配置");
                                    reloadConfig();
                                }
                            }
                            key.reset();
                        } catch (Exception e) {
                            logger.error("文件监控发生错误: {}", e.getMessage());
                        }
                    }
                }).start();
            } else {
                logger.warn("无法启动WatchService，外部配置文件路径无效");
            }
        } catch (IOException e) {
            logger.error("启动WatchService失败: {}", e.getMessage());
        }
    }

    // 重新加载配置，并仅在私钥更改时清空缓存
    public void reloadConfig() {
        try (InputStream configStream = getConfigFileStream()) {
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, Object> config = objectMapper.readValue(configStream, Map.class);

            // 加载新私钥
            String privateKeyString = (String) config.get("privateKey");
            byte[] keyBytes = Base64.getDecoder().decode(privateKeyString);

            try {
                PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
                KeyFactory kf = KeyFactory.getInstance("RSA");
                PrivateKey newPrivateKey = kf.generatePrivate(spec);

                // 仅在私钥变化时清空缓存
                if (!newPrivateKey.equals(privateKey)) {
                    privateKey = newPrivateKey;
                    decryptionCache.invalidateAll(); // 私钥变化，清空缓存
                    logger.info("私钥已更改，缓存已清空");
                }
            } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
                logger.error("加载私钥时发生错误: {}", e.getMessage());
                return; // 处理异常时可以根据实际需求决定是否继续加载其他配置
            }

            // 更新其他配置
            whitelist = (List<String>) config.get("whitelist");
            maxIPConcurrentRequests = (int) config.get("maxConcurrentRequestsPerIP");

            logger.info("配置已成功从 config.json 文件加载");
        } catch (IOException e) {
            logger.error("加载 config.json 文件时发生错误，将继续使用上次的配置: {}", e.getMessage());
        }
    }

    // 生成加密消息的哈希值
    private String getHash(String message) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            logger.error("生成哈希值时发生错误: {}", e.getMessage());
            return message; // 返回原始消息作为回退
        }
    }

    // 判断是否需要解密，根据前缀 "ENC:" 判断是否加密
    private boolean isEncrypted(String content) {
        return content.startsWith(ENCRYPTION_PREFIX);
    }

    // 解密消息，首先检查缓存，如果没有缓存则解密并存入缓存
    private String decryptAndCache(String encryptedMessage) {
        String hashKey = getHash(encryptedMessage);
        try {
            return decryptionCache.get(hashKey, () -> {
                Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
                cipher.init(Cipher.DECRYPT_MODE, privateKey);

                byte[] encryptedBytes = DatatypeConverter.parseHexBinary(encryptedMessage.substring(ENCRYPTION_PREFIX.length())); // 移除前缀
                byte[] decryptedMessage = cipher.doFinal(encryptedBytes);
                return new String(decryptedMessage);
            });
        } catch (Exception e) {
            logger.error("解密消息时发生错误: {}", e.getMessage());
            return encryptedMessage; // 解密失败时返回原始消息
        }
    }

    @PostMapping("/{urlOrParam}")
    public Flux<DataBuffer> captureAndForward(@PathVariable String urlOrParam,
                                              @RequestBody RequestBodyData requestBodyData,
                                              @RequestHeader HttpHeaders headers,
                                              @RequestHeader(value = "X-Forwarded-For", defaultValue = "localhost") String clientIp) {
        if (requestBodyData.getMessages() == null || requestBodyData.getMessages().isEmpty()) {
            return Flux.error(new IllegalArgumentException("没有消息需要处理"));
        }

        AtomicInteger currentRequests = ipRequestCount.computeIfAbsent(clientIp, k -> new AtomicInteger(0));
        if (currentRequests.incrementAndGet() > maxIPConcurrentRequests) {
            currentRequests.decrementAndGet(); // 减少并发数
            return Flux.error(new IllegalArgumentException("来自该IP的并发请求过多"));
        }

        String targetUrl = resolveTargetUrl(urlOrParam);
        if (!whitelist.contains(targetUrl)) {
            currentRequests.decrementAndGet();
            return Flux.error(new IllegalArgumentException("URL不在白名单中"));
        }

        HttpHeaders filteredHeaders = filterHeaders(headers);
        logger.info("转发到目标URL: {}", targetUrl);
        logger.debug("转发的请求数据: {}", requestBodyData);

        return Flux.fromIterable(requestBodyData.getMessages())
            .flatMap(message -> {
                try {
                    // 仅在消息以 "ENC:" 开头时才尝试解密
                    if (isEncrypted(message.getContent())) {
                        String decryptedContent = decryptAndCache(message.getContent());
                        message.setContent(decryptedContent);
                    }
                    return Mono.just(message);  // 返回已修改或未修改的消息
                } catch (Exception e) {
                    logger.error("解密消息时发生错误: {}", e.getMessage());
                    return Mono.error(e);
                }
            })
            .thenMany(webClient.post()
                .uri(targetUrl)
                .headers(httpHeaders -> httpHeaders.addAll(filteredHeaders))
                .bodyValue(requestBodyData)  // 将更新后的 message 发送出去
                .retrieve()
                .bodyToFlux(DataBuffer.class))
            .doFinally(signalType -> currentRequests.decrementAndGet());
    }

    private String resolveTargetUrl(String urlOrParam) {
        return switch (urlOrParam.toLowerCase()) {
            case "claude" -> claudeUrl;
            case "clewd" -> clewdUrl;
            default -> urlOrParam.startsWith("http") ? urlOrParam : "https://" + urlOrParam;
        };
    }

    private HttpHeaders filterHeaders(HttpHeaders headers) {
        HttpHeaders filteredHeaders = new HttpHeaders();
        headers.forEach((key, value) -> {
            if (!key.equalsIgnoreCase("Host") &&
                !key.equalsIgnoreCase("Content-Length") &&
                !key.equalsIgnoreCase("Accept-Encoding") &&
                !key.equalsIgnoreCase("Connection")) {
                filteredHeaders.put(key, value);
            }
        });
        return filteredHeaders;
    }
}
