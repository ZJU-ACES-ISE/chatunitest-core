package zju.cst.aces.api;
import lombok.AllArgsConstructor;
import zju.cst.aces.api.config.Config;
import zju.cst.aces.api.impl.ChatGenerator;
import zju.cst.aces.api.impl.Parser;
import zju.cst.aces.dto.ChatMessage;
import zju.cst.aces.parser.ProjectParser;

import java.util.List;

public class Phase {
    Config config;

    public Phase(Config config) {
        this.config = config;
    }

    @AllArgsConstructor
    public class Preparation {

        public void run() {
            Parser parser = new Parser(new ProjectParser(config), config.getProject(), config.getParseOutput(), config.getLogger());
            process(parser);
        }

        public void process(PreProcess preProcessor) {
            preProcessor.process();
        }
    }

    @AllArgsConstructor
    public class PromptGeneration {

    }

    @AllArgsConstructor
    public class Chatting {

        List<ChatMessage> chatMessages;
        int tokenCount = 0;
        static final String separator = "_";

        public String chat(List<ChatMessage> messages) {
            return new ChatGenerator(config).generate(messages);
        }
    }

    @AllArgsConstructor
    public class Validation {

    }

    @AllArgsConstructor
    public class Repair {

    }

}
