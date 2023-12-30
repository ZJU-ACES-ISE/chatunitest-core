package zju.cst.aces.api.config;

import lombok.Data;

@Data
public class ModelConfig {
    public String modelName;
    public String url;
    public int contextLength;
    public double temperature;
    public int frequencyPenalty;
    public int presencePenalty;

    private ModelConfig(Builder builder) {
        this.modelName = builder.modelName;
        this.url = builder.url;
        this.contextLength = builder.contextLength;
        this.temperature = builder.temperature;
        this.frequencyPenalty = builder.frequencyPenalty;
        this.presencePenalty = builder.presencePenalty;
    }

    public static class Builder {
        private String modelName = "gpt-3.5-turbo";
        private String url = "https://api.openai.com/v1/chat/completions";
        private int contextLength = 4096;
        private double temperature = 0.5;
        private int frequencyPenalty = 0;
        private int presencePenalty = 0;

        public Builder withModelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public Builder withUrl(String url) {
            this.url = url;
            return this;
        }

        public Builder withContextLength(int contextLength) {
            this.contextLength = contextLength;
            return this;
        }

        public Builder withPresencePenalty(int penalty) {
            this.presencePenalty = penalty;
            return this;
        }

        public Builder withFrequencyPenalty(int penalty) {
            this.frequencyPenalty = penalty;
            return this;
        }

        public Builder withTemperature(double temperature) {
            this.temperature = temperature;
            return this;
        }

        public ModelConfig build() {
            return new ModelConfig(this);
        }
    }
}
