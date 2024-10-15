package async;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.List;
import java.util.Map;
import java.util.Base64;

@Service
public class ConfigService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String configFilePath = "src/main/resources/config.json";
    private List<String> whitelist;
    private int maxIPConcurrentRequests;
    private String privateKey;

    // 加载 config.json 文件中的配置
    public void loadConfig() throws Exception {
        File configFile = new ClassPathResource("config.json").getFile();
        Map<String, Object> config = objectMapper.readValue(configFile, Map.class);

        whitelist = (List<String>) config.get("whitelist");
        maxIPConcurrentRequests = (Integer) config.get("maxConcurrentRequestsPerIP");
        privateKey = (String) config.get("privateKey");
    }

    // 更新 config.json 文件中的配置
    public void updateConfig(List<String> whitelist, int maxIPConcurrentRequests) throws Exception {
        this.whitelist = whitelist;
        this.maxIPConcurrentRequests = maxIPConcurrentRequests;
        saveConfig();
    }

    // 更新私钥并保存到 config.json 文件
    public void updatePrivateKey(String privateKey) throws Exception {
        this.privateKey = privateKey.replaceAll("\\n", "");
        saveConfig();
    }

    // 保存配置到 config.json
    private void saveConfig() throws Exception {
        Map<String, Object> config = Map.of(
            "whitelist", whitelist,
            "maxConcurrentRequestsPerIP", maxIPConcurrentRequests,
            "privateKey", privateKey
        );
        try (FileWriter writer = new FileWriter(configFilePath)) {
            objectMapper.writeValue(writer, config);
        }
    }

    // 获取私钥对象
    public PrivateKey getPrivateKeyObject() throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(privateKey);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePrivate(spec);
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
