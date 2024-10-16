package async;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class ConfigService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private List<String> whitelist;
    private int maxIPConcurrentRequests;
    private String privateKey;

    // 加载 config.json 文件中的配置
    public void loadConfig() throws Exception {
        try (InputStream inputStream = new ClassPathResource("config.json").getInputStream()) {
            Map<String, Object> config = objectMapper.readValue(inputStream, Map.class);

            whitelist = (List<String>) config.get("whitelist");
            maxIPConcurrentRequests = (Integer) config.get("maxConcurrentRequestsPerIP");
            privateKey = (String) config.get("privateKey");
        } catch (Exception e) {
            System.err.println("加载配置文件时发生错误: " + e.getMessage());
            throw e;  // 重新抛出异常以便调用方处理
        }
    }

    // 加载配置并返回私钥对象
    public PrivateKey loadAndGetPrivateKey() throws Exception {
        loadConfig();  // 加载配置
        return getPrivateKeyObject();  // 获取私钥对象
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

    // 获取格式化私钥
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
