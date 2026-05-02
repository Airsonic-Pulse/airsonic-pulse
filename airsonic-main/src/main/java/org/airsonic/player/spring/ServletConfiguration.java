package org.airsonic.player.spring;

import org.airsonic.player.config.AirsonicHomeConfig;
import org.airsonic.player.controller.JAXBWriter;
import org.airsonic.player.controller.PodcastController;
import org.airsonic.player.filter.BootstrapVerificationFilter;
import org.airsonic.player.filter.MetricsFilter;
import org.airsonic.player.filter.ParameterDecodingFilter;
import org.airsonic.player.filter.RESTFilter;
import org.airsonic.player.filter.RequestEncodingFilter;
import org.airsonic.player.filter.ResponseHeaderFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.Validator;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;
import org.springframework.web.util.UrlPathHelper;

import jakarta.servlet.Filter;
import jakarta.servlet.Servlet;

import java.util.Properties;

@Configuration
public class ServletConfiguration implements WebMvcConfigurer {

    @Autowired
    private AirsonicHomeConfig homeConfig;

    @Autowired
    private JAXBWriter jaxbWriter;

    @Bean
    public ServletRegistrationBean<Servlet> cxfServletBean() {
        return new ServletRegistrationBean<>(new org.apache.cxf.transport.servlet.CXFServlet(), "/ws/*");
    }

    @Bean
    public FilterRegistrationBean<Filter> bootstrapVerificationFilterRegistration() {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(bootstrapVerificationFilter());
        registration.addUrlPatterns("/*");
        registration.setName("BootstrapVerificationFilter");
        registration.setOrder(1);
        return registration;
    }

    @Bean
    public BootstrapVerificationFilter bootstrapVerificationFilter() {
        return new BootstrapVerificationFilter(homeConfig);
    }

    @Bean
    public FilterRegistrationBean<Filter> parameterDecodingFilterRegistration() {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>(parameterDecodingFilter());
        registration.addUrlPatterns("/*");
        registration.setName("ParameterDecodingFilter");
        registration.setOrder(2);
        return registration;
    }

    @Bean
    public ParameterDecodingFilter parameterDecodingFilter() {
        return new ParameterDecodingFilter();
    }

    @Bean
    public FilterRegistrationBean<Filter> restFilterRegistration() {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>(restFilter());
        registration.addUrlPatterns("/rest/*");
        registration.setName("RESTFilter");
        registration.setOrder(3);
        return registration;
    }

    @Bean
    public RESTFilter restFilter() {
        return new RESTFilter(jaxbWriter);
    }

    @Bean
    public FilterRegistrationBean<Filter> requestEncodingFilterRegistration() {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>(requestEncodingFilter());
        registration.addUrlPatterns("/*");
        registration.addInitParameter("encoding", "UTF-8");
        registration.setName("RequestEncodingFilter");
        registration.setOrder(4);
        return registration;
    }

    @Bean
    public RequestEncodingFilter requestEncodingFilter() {
        return new RequestEncodingFilter();
    }

    @Bean
    public FilterRegistrationBean<Filter> cacheFilterRegistration() {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>(cacheFilter());
        registration.addUrlPatterns("/icons/*", "/style/*", "/script/*", "/icons/*", "/coverArt.view", "/avatar.view");
        registration.addInitParameter("Cache-Control", "max-age=36000");
        registration.setName("CacheFilter");
        registration.setOrder(5);
        return registration;
    }

    @Bean
    public ResponseHeaderFilter cacheFilter() {
        return new ResponseHeaderFilter();
    }

    @Bean
    public FilterRegistrationBean<Filter> noCacheFilterRegistration() {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>(noCacheFilter());
        registration.addUrlPatterns("/statusChart.view", "/userChart.view", "/playQueue.view", "/podcastChannels.view", "/podcastChannel.view", "/help.view", "/top.view", "/home.view");
        registration.addInitParameter("Cache-Control", "no-cache, post-check=0, pre-check=0");
        registration.addInitParameter("Pragma", "no-cache");
        registration.addInitParameter("Expires", "Thu, 01 Dec 1994 16:00:00 GMT");
        registration.setName("NoCacheFilter");
        registration.setOrder(6);
        return registration;
    }

    @Bean
    public ResponseHeaderFilter noCacheFilter() {
        return new ResponseHeaderFilter();
    }

    @Bean
    public FilterRegistrationBean<Filter> metricsFilterRegistration() {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>(metricsFilter());
        registration.setOrder(7);
        return registration;
    }

    @Bean
    public MetricsFilter metricsFilter() {
        return new MetricsFilter();
    }

    @Bean
    public SimpleUrlHandlerMapping podcastMapping(PodcastController podcastController) {
        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        UrlPathHelper urlPathHelper = new UrlPathHelper();
        urlPathHelper.setAlwaysUseFullPath(true);
        mapping.setUrlPathHelper(urlPathHelper);

        Properties properties = new Properties();
        properties.put("/podcast/**", podcastController);
        mapping.setMappings(properties);

        return mapping;
    }

    @Autowired
    private MessageSource ms;

    @Override
    public Validator getValidator() {
        // need to get constraint violation message codes translated
        LocalValidatorFactoryBean factory = new LocalValidatorFactoryBean();
        factory.setValidationMessageSource(ms);
        return factory;
    }
}
