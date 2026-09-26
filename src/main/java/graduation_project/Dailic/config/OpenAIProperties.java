package graduation_project.Dailic.config;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "openai")
public class OpenAIProperties {
    private String key;
    private String url = "https://api.openai.com/v1/chat/completions";
    private String model;
    @Min(1)
    private int connectTimeoutMs = 5000;
    @Min(1)
    private int readTimeoutMs = 30000;
}
