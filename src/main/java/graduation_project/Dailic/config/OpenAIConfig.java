package graduation_project.Dailic.config;

import java.time.Duration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class OpenAIConfig {
    @Bean
    public RestTemplate openAiRestTemplate(RestTemplateBuilder builder, OpenAIProperties properties) {
        // A known transport keeps timeout behavior independent of optional SDK dependencies.
        return builder.requestFactory(SimpleClientHttpRequestFactory.class)
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .readTimeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                .build();
    }
}
