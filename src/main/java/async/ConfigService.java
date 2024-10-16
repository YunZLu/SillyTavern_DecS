package async;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class ConfigService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String configFilePath = "src/main/resources/config.json";
    private List<String> whitelist;
    private int maxIPConcurrentRequests;
    private String privateKey;

    // 加载 config.json 文件中的配置
    public void loadConfig() {
        try {
            // 使用 InputStream 从 JAR 内读取资源文件
            Resource resource = new ClassPathResource("config.json");
            try (InputStream inputStream = resource.getInputStream()) {
                Map<String, Object> config = objectMapper.readValue(inputStream, Map.class);
                whitelist = (List<String>) config.get("whitelist");
                maxIPConcurrentRequests = (Integer) config.get("maxConcurrentRequestsPerIP");
                privateKey = (String) config.get("privateKey");
            }
        } catch (IOException e) {
            System.err.println("无法加载配置文件: " + e.getMessage());
        }
    }

    // 加载配置并返回私钥对象
    public PrivateKey loadAndGetPrivateKey() {
        loadConfig(); // 加载配置
        return getPrivateKeyObject(); // 获取私钥对象
    }

    // 获取私钥对象
    public PrivateKey getPrivateKeyObject() {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(privateKey);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePrivate(spec);
        } catch (IllegalArgumentException e) {
            System.err.println("私钥无效，无法解码: " + e.getMessage());
            return null;
        } catch (Exception e) {
            System.err.println("加载私钥时发生错误: " + e.getMessage());
            return null;
        }
    }

    // 更新 config.json 文件中的配置
    public void updateConfig(List<String> whitelist, int maxIPConcurrentRequests) {
        this.whitelist = whitelist;
        this.maxIPConcurrentRequests = maxIPConcurrentRequests;
        saveConfig();
    }

    // 更新私钥并保存到 config.json 文件
    public void updatePrivateKey(String privateKey) {
        this.privateKey = privateKey.replaceAll("\\n", "");
        saveConfig();
    }

    // 保存配置到 config.json 文件
    private void saveConfig() {
        Map<String, Object> config = Map.of(
            "whitelist", whitelist,
            "maxConcurrentRequestsPerIP", maxIPConcurrentRequests,
            "privateKey", privateKey
        );

        try (FileWriter writer = new FileWriter(configFilePath)) {
            objectMapper.writeValue(writer, config);
        } catch (IOException e) {
            System.err.println("保存配置文件时出错: " + e.getMessage());
        }
    }

    // 获取格式化后的私钥
    public String getPrivateKey() {
        return formatPrivateKey(privateKey);
    }

    // 格式化私钥
    private String formatPrivateKey(String privateKey) {
        StringBuilder formattedPrivateKey = new StringBuilder();
        formattedPrivateKey.append("-----BEGIN PRIVATE KEY-----\n");

        int length = privateKey.length();
        for (int i = 0; i < length; i += 64) {
            int endIndex = Math.min(i + 64, length);
            formattedPrivateKey.append(privateKey, i, endIndex).append("\n");
        }

        formattedPrivateKey.append("-----END PRIVATE KEY-----");
        return formattedPrivateKey.toString();
    }

    // 获取白名单列表
    public List<String> getWhitelist() {
        return whitelist;
    }

    // 获取最大同IP并发请求数
    public int getMaxIPConcurrentRequests() {
        return maxIPConcurrentRequests;
    }
}
