package com.searchpic.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "ai")
public class AiProperties {
    private Aliyun aliyun = new Aliyun();
    private Google google = new Google();
    private Prompts prompts = new Prompts();

    @Data
    public static class Prompts {
        private String vlmImageAnalyze;
        private String llmQueryAnalyze;
    }

    @Data
    public static class Aliyun {
        private Dashscope dashscope = new Dashscope();
    }

    @Data
    public static class Dashscope {
        private String apiKey;
    }

    @Data
    public static class Google {
        private Gemini gemini = new Gemini();
    }

    @Data
    public static class Gemini {
        private String apiKey;
    }
}
