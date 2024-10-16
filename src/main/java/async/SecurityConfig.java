package async;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        UserDetails user = User.withUsername("admin")
                .password(passwordEncoder().encode("password"))
                .roles("ADMIN")
                .build();
        return new InMemoryUserDetailsManager(user);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf().and()  // 启用 CSRF 防护
            .authorizeHttpRequests()
                .requestMatchers("/login", "/login.html", "/css/**", "/js/**").permitAll()  // 允许访问这些资源
                .anyRequest().authenticated()  // 其他请求需要认证
            .and()
            .formLogin()
                .loginPage("/login.html")  // 自定义登录页面
                .loginProcessingUrl("/login")  // 处理登录请求的 URL
                .defaultSuccessUrl("/admin.html", true)  // 登录成功后跳转
                .failureUrl("/login.html?error=true")  // 登录失败跳转回登录页面
                .permitAll()
            .and()
            .logout()
                .logoutUrl("/logout")  // 注销请求的 URL
                .logoutSuccessUrl("/login.html")  // 注销成功后跳转到登录页面
                .permitAll();

        return http.build();
    }
}
