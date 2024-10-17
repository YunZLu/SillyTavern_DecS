package async;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

@RestController
public class CsrfController {

    @GetMapping("/csrf-token")
    public Map<String, String> getCsrfToken(HttpServletRequest request) {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        Map<String, String> tokenMap = new HashMap<>();
        
        if (csrfToken != null) {
            tokenMap.put("token", csrfToken.getToken());
        } else {
            // 返回适当的错误响应或抛出异常
            throw new IllegalStateException("CSRF token not found");
        }
        
        return tokenMap;
    }
}

