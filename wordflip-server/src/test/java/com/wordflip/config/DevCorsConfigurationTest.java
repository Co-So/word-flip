package com.wordflip.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DevCorsConfigurationTest {

    @Test
    void devProfileAllowsCurrentWebOriginWithoutWildcard() throws IOException {
        CorsProperties properties = bindDevCorsProperties();
        CorsConfigurationSource source = new CorsConfig().corsConfigurationSource(properties);
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/auth/login");

        CorsConfiguration configuration = source.getCorsConfiguration(request);

        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowedOrigins())
                .contains("http://127.0.0.1:5273")
                .doesNotContain("*");
    }

    /** 加载真实 dev YAML 后再绑定配置，避免测试只验证手写的测试属性。 */
    private static CorsProperties bindDevCorsProperties() throws IOException {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        List<PropertySource<?>> loaded = loader.load(
                "application-dev",
                new ClassPathResource("application-dev.yml")
        );
        MutablePropertySources sources = new MutablePropertySources();
        loaded.forEach(sources::addLast);
        return new Binder(ConfigurationPropertySources.from(sources))
                .bind("wordflip.cors", Bindable.of(CorsProperties.class))
                .orElseThrow(() -> new IllegalStateException("无法绑定 application-dev.yml 的 CORS 配置"));
    }
}
