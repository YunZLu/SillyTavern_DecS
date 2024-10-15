package async;

import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final ConfigService configService;
    private static final String URL_REGEX = "^(https?://)[^\\s/$.?#].[^\\s]*$"; // 简单的 URL 验证正则表达式
    private static final Pattern URL_PATTERN = Pattern.compile(URL_REGEX);

    public AdminController(ConfigService configService) {
        this.configService = configService;
    }

    // 获取当前白名单
    @GetMapping("/whitelist")
    public List<String> getWhitelist() {
        return configService.getWhitelist();
    }

    // 添加 URL 到白名单
    @PostMapping("/whitelist")
    public String addToWhitelist(@RequestParam String url) throws Exception {
        List<String> whitelist = new ArrayList<>(configService.getWhitelist());
        
        // URL 验证
        if (!isValidUrl(url)) {
            return "无效的 URL: " + url;
        }

        if (!whitelist.contains(url)) {
            whitelist.add(url);
            configService.updateConfig(whitelist, configService.getMaxIPConcurrentRequests());
            return "添加成功: " + url;
        }
        return "该 URL 已存在";
    }

    // 从白名单中删除 URL
    @DeleteMapping("/whitelist")
    public String removeFromWhitelist(@RequestParam String url) throws Exception {
        List<String> whitelist = new ArrayList<>(configService.getWhitelist());
        if (whitelist.remove(url)) {
            configService.updateConfig(whitelist, configService.getMaxIPConcurrentRequests());
            return "删除成功: " + url;
        }
        return "未找到 URL";
    }

    // 获取当前的最大同IP并发请求数
    @GetMapping("/concurrent-limit")
    public int getMaxIPConcurrentRequests() {
        return configService.getMaxIPConcurrentRequests();
    }

    // 设置新的最大同IP并发请求数
    @PostMapping("/concurrent-limit")
    public String setMaxIPConcurrentRequests(@RequestParam int limit) throws Exception {
        if (limit > 0) {
            configService.updateConfig(configService.getWhitelist(), limit);
            return "设置成功, 最大同IP并发请求数为: " + limit;
        }
        return "无效的并发限制值";
    }

    // 获取当前私钥
    @GetMapping("/private-key")
    public String getPrivateKey() {
        return configService.getPrivateKey();  // ConfigService获取私钥内容
    }

    // 修改私钥
    @PostMapping("/private-key")
    public String updatePrivateKey(@RequestParam String privateKey) throws Exception {
        configService.updatePrivateKey(privateKey);  // 更新config.json中的私钥
        return "私钥更新成功";
    }

    // 通知 AsyncController 重新加载配置
    @PostMapping("/reload-config")
    public String reloadConfig() {
        AsyncController.reloadConfig();  // 调用 AsyncController 的静态方法来重新加载配置
        return "配置已重新加载成功";
    }

    // 验证 URL 的有效性
    private boolean isValidUrl(String url) {
        return URL_PATTERN.matcher(url).matches();
    }
}
