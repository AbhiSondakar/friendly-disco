package com.ecoloop.common.web;

import com.ecoloop.common.security.ActorContextResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final ActorContextResolver actorContextResolver;

    public WebConfig(ActorContextResolver actorContextResolver) {
        this.actorContextResolver = actorContextResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(actorContextResolver);
    }
}
