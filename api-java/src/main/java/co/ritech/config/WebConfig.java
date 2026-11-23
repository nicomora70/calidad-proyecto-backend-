package co.ritech.config;

import java.time.Clock;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import co.ritech.license.LicenseInterceptor;
import co.ritech.license.LicenseService;
import co.ritech.security.CurrentUserArgumentResolver;
import co.ritech.security.PasswordChangeRequiredInterceptor;

@Configuration
@EnableConfigurationProperties(RitechProperties.class)
@EnableSpringDataWebSupport(pageSerializationMode = PageSerializationMode.VIA_DTO)
@EnableAsync
@EnableScheduling
public class WebConfig implements WebMvcConfigurer {

    private final ObjectProvider<LicenseService> license;

    public WebConfig(ObjectProvider<LicenseService> license) {
        this.license = license;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentUserArgumentResolver());
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new PasswordChangeRequiredInterceptor()).addPathPatterns("/api/**");
        license.ifAvailable(service ->
                registry.addInterceptor(new LicenseInterceptor(service)).addPathPatterns("/api/**"));
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
