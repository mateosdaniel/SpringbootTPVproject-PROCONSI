package com.proconsi.electrobazar.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import java.time.Duration;
import java.util.Locale;

@Configuration
public class LocaleConfig implements WebMvcConfigurer {

    @Bean
    public LocaleResolver localeResolver() {
        CookieLocaleResolver clr = new CookieLocaleResolver("APP_LOCALE");
        clr.setDefaultLocale(Locale.forLanguageTag("es"));
        clr.setCookieMaxAge(Duration.ofDays(365));
        clr.setCookieHttpOnly(false);
        return clr;
    }

    @Bean
    public LocaleChangeInterceptor localeChangeInterceptor() {
        LocaleChangeInterceptor lci = new LocaleChangeInterceptor();
        lci.setParamName("lang");
        return lci;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(localeChangeInterceptor());
    }

    @Bean
    public org.springframework.web.servlet.resource.ResourceUrlEncodingFilter resourceUrlEncodingFilter() {
        return new org.springframework.web.servlet.resource.ResourceUrlEncodingFilter();
    }

    @Override
    public void addResourceHandlers(org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry registry) {
        // Multi-location handler for standard assets
        registry.addResourceHandler("/js/**", "/css/**", "/images/**", "/img/**", "/vendor/**", "/icons/**", "/webjars/**")
                .addResourceLocations(
                    "classpath:/static/js/", 
                    "classpath:/static/css/", 
                    "classpath:/static/images/", 
                    "classpath:/static/img/",
                    "classpath:/static/vendor/",
                    "classpath:/static/icons/",
                    "classpath:/META-INF/resources/webjars/"
                )
                .setCacheControl(org.springframework.http.CacheControl.maxAge(7, java.util.concurrent.TimeUnit.DAYS))
                .resourceChain(true)
                .addResolver(new org.springframework.web.servlet.resource.VersionResourceResolver()
                        .addContentVersionStrategy("/**"));
        
        // Catch-all for any other static files at root
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new org.springframework.web.servlet.resource.VersionResourceResolver()
                        .addContentVersionStrategy("/**"));
    }
}
