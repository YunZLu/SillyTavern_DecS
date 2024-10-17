package async;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf() // 启用 CSRF 保护
            .and()
            .authorizeRequests()
                .requestMatchers("/login.html", "/", "/csrf-token").permitAll() // 允许所有用户访问登录页面和 CSRF 令牌
                .requestMatchers("/admin/**").authenticated() // 保护 /admin/** 路径
            .and()
            .formLogin()
                .loginPage("/login.html") // 指定登录页面
                .defaultSuccessUrl("/admin.html") // 登录成功后重定向到 admin.html
                .failureUrl("/login.html?error=true") // 登录失败后重定向到 login.html，并附加错误参数
                .permitAll() // 允许所有用户访问登录页面
            .and()
            .logout()
                .logoutSuccessUrl("/login.html") // 登出成功后重定向到登录页面
                .permitAll(); // 允许所有用户访问登出功能

        return http.build(); // 返回构建的 HttpSecurity
    }

    @Bean
    public AuthenticationManager authManager(HttpSecurity http) throws Exception {
        AuthenticationManagerBuilder authenticationManagerBuilder =
            http.getSharedObject(AuthenticationManagerBuilder.class);
        authenticationManagerBuilder.userDetailsService(userDetailsService).passwordEncoder(passwordEncoder());
        return authenticationManagerBuilder.build(); // 返回 AuthenticationManager
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(); // 使用 BCrypt 加密密码
    }
}
