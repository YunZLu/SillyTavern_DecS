package async;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

@Configuration
public class SecurityConfig extends WebSecurityConfigurerAdapter {

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

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
            .csrf().disable()  // 禁用 CSRF 防护
            .authorizeRequests()
                .antMatchers("/login.html", "/css/**", "/js/**", "/images/**").permitAll()  // 允许公开访问这些静态资源
                .anyRequest().authenticated()  // 其他请求需要认证
            .and()
            .formLogin()
                .loginPage("/login.html")  // 自定义登录页面
                .loginProcessingUrl("/login")  // 处理登录请求的 URL
                .defaultSuccessUrl("/admin.html", true)  // 登录成功后跳转到管理页面
                .failureUrl("/login.html?error=true")  // 登录失败时重定向回登录页面，并显示错误信息
                .permitAll()
            .and()
            .logout()
                .logoutUrl("/logout")  // 注销请求的 URL
                .logoutSuccessUrl("/login.html")  // 注销成功后跳转回登录页面
                .permitAll();
    }

    @Override
    public void configure(WebSecurity web) throws Exception {
        // 完全忽略静态资源的安全过滤，包括 login.html 页面
        web.ignoring()
            .antMatchers("/login.html", "/css/**", "/js/**", "/images/**");
    }
}
