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

    // 更新私钥并保存到 config.json 文件
    public void updatePrivateKey(String privateKey) throws Exception {
        // 移除换行符并保存
        String sanitizedPrivateKey = privateKey.replaceAll("\\n", "");
        this.privateKey = sanitizedPrivateKey;
        
        Map<String, Object> config = Map.of(
            "whitelist", whitelist,
            "maxConcurrentRequestsPerIP", maxIPConcurrentRequests,
            "privateKey", sanitizedPrivateKey
        );
        try (FileWriter writer = new FileWriter(configFilePath)) {
            objectMapper.writeValue(writer, config);
        }
    }

    // 获取私钥并格式化为带换行符的 PEM 格式
    public String getPrivateKey() {
        // 恢复私钥格式，每64个字符插入一个换行符
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
