package async;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // 读取配置文件中的用户名和加密后的密码
    @Value("${app.security.username}")
    private String username;

    @Value("${app.security.password}")
    private String encodedPassword;

    // 配置密码编码器
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // 配置用户详细信息服务
    @Bean
    public UserDetailsService userDetailsService() {
        UserDetails user = User.withUsername(username)
            .password(encodedPassword)
            .roles("ADMIN")
            .build();
        return new InMemoryUserDetailsManager(user);
    }

    // 配置 Spring Security 过滤链
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf().disable()  // 禁用 CSRF（针对非浏览器应用）
            .authorizeRequests()
                .antMatchers("/admin/**").authenticated()  // 保护所有以 /admin/ 开头的 URL，必须登录后才能访问
                .antMatchers("/login").permitAll()  // 允许所有用户访问 /login 页面
                .anyRequest().permitAll()  // 允许所有其他请求不需要登录
            .and()
            .formLogin()
                .loginPage("/login.html")  // 指定自定义的登录页面
                .loginProcessingUrl("/login")  // 处理登录表单提交的 URL
                .defaultSuccessUrl("/admin.html", true)  // 登录成功后重定向到 /admin.html
                .failureUrl("/login.html?error=true")  // 登录失败时重定向回登录页面
                .permitAll()
            .and()
            .logout()
                .logoutUrl("/logout")  // 处理注销请求的 URL
                .logoutSuccessUrl("/login.html")  // 注销成功后重定向到登录页面
                .permitAll();

        return http.build();
    }
}
