package zju.cst.aces.api.config;

import java.util.Arrays;
import java.util.stream.Collectors;

public enum Model {
    GPT_3_5_TURBO("gpt-3.5-turbo", new ModelConfig.Builder()
            .withModelName("gpt-3.5-turbo")
            .withUrl("https://api.openai.com/v1/chat/completions")
            .withContextLength(4096)
            .withTemperature(0.5)
            .withFrequencyPenalty(0)
            .withPresencePenalty(0)
            .build()),
    GPT_3_5_TURBO_1106("gpt-3.5-turbo-1106", new ModelConfig.Builder()
            .withModelName("gpt-3.5-turbo-1106")
            .withUrl("https://api.openai.com/v1/chat/completions")
            .withContextLength(16385)
            .withTemperature(0.5)
            .withFrequencyPenalty(0)
            .withPresencePenalty(0)
            .build()),
    CODE_LLAMA("code-llama", new ModelConfig.Builder()
            .withModelName("code-llama")
            .withUrl(null)
            .withContextLength(16385)
            .withTemperature(0.5)
            .withFrequencyPenalty(0)
            .withPresencePenalty(0)
            .build());
    // 添加更多模型

    private final String modelName;
    private final ModelConfig defaultConfig;

    Model(String modelName, ModelConfig defaultConfig) {
        this.modelName = modelName;
        this.defaultConfig = defaultConfig;
    }

    public String getModelName() {
        return modelName;
    }

    public ModelConfig getDefaultConfig() {
        return defaultConfig;
    }

    public static Model fromString(String modelName) {
        for (Model model : Model.values()) {
            if (model.getModelName().equalsIgnoreCase(modelName)) {
                return model;
            }
        }
        throw new IllegalArgumentException("No Model with name " + modelName +
                "\nSupport models: " + Arrays.stream(Model.values()).map(Model::getModelName).collect(Collectors.joining(", ")));
    }
}
