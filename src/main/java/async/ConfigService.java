package async;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
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
    public void loadConfig() throws Exception {
        File configFile = new ClassPathResource("config.json").getFile();
        Map<String, Object> config = objectMapper.readValue(configFile, Map.class);

        whitelist = (List<String>) config.get("whitelist");
        maxIPConcurrentRequests = (Integer) config.get("maxConcurrentRequestsPerIP");
        privateKey = (String) config.get("privateKey");
    }

    // 更新 config.json 文件中的配置
    public void updateConfig(List<String> whitelist, int maxIPConcurrentRequests) throws Exception {
        Map<String, Object> config = Map.of(
                "whitelist", whitelist,
                "maxConcurrentRequestsPerIP", maxIPConcurrentRequests,
                "privateKey", privateKey
        );
        try (FileWriter writer = new FileWriter(configFilePath)) {
            objectMapper.writeValue(writer, config);
        }
        this.whitelist = whitelist;
        this.maxIPConcurrentRequests = maxIPConcurrentRequests;
    }

    // 更新私钥
    public void updatePrivateKey(String privateKey) throws Exception {
        this.privateKey = privateKey;
        Map<String, Object> config = Map.of(
                "whitelist", whitelist,
                "maxConcurrentRequestsPerIP", maxIPConcurrentRequests,
                "privateKey", privateKey
        );
        try (FileWriter writer = new FileWriter(configFilePath)) {
            objectMapper.writeValue(writer, config);
        }
    }

    // 获取白名单列表
    public List<String> getWhitelist() {
        return whitelist;
    }

    // 获取最大同IP并发请求数
    public int getMaxIPConcurrentRequests() {
        return maxIPConcurrentRequests;
    }

    // 获取私钥
    public String getPrivateKey() {
        return privateKey;
    }
}
