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
    private Search search = new Search();
    private Boolean debugLogEnabled = false;
    private Integer embeddingDimensions = 768;

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
        private Models models = new Models();
    }

    @Data
    public static class Models {
        private String vlm = "qwen-vl-max";
        private String embedding = "text-embedding-v2";
        private String search = "qwen-plus";
    }

    @Data
    public static class Google {
        private Gemini gemini = new Gemini();
    }

    @Data
    public static class Gemini {
        private String apiKey;
        private String model = "gemini-1.5-flash";
    }

    @Data
    public static class Search {
        private String provider = "google";
    }
}
