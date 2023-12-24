package zju.cst.aces.prompt;


import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import zju.cst.aces.api.config.Config;
import zju.cst.aces.runner.AbstractRunner;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class PromptTemplate {

    public static final String CONFIG_FILE = "config.properties";
    public String TEMPLATE_NO_DEPS = "";
    public String TEMPLATE_DEPS = "";
    public String TEMPLATE_ERROR = "";
    public Map<String, Object> dataModel = new HashMap<>();
    public Config config;

    public PromptTemplate(Config config) {
        this.config = config;
    }

    public void readProperties() throws IOException {
        Properties properties = new Properties();
        InputStream inputStream = PromptTemplate.class.getClassLoader().getResourceAsStream(CONFIG_FILE);
        properties.load(inputStream);
        TEMPLATE_NO_DEPS = properties.getProperty("PROMPT_TEMPLATE_NO_DEPS");//p1.ftl
        TEMPLATE_DEPS = properties.getProperty("PROMPT_TEMPLATE_DEPS");//p2.ftl
        TEMPLATE_ERROR = properties.getProperty("PROMPT_TEMPLATE_ERROR");//error.ftl
    }

    //渲染
    public String renderTemplate(String templateFileName) throws IOException, TemplateException{
        Configuration configuration = new Configuration(Configuration.VERSION_2_3_30);

        if (config.getPromptPath() == null) {
            configuration.setClassForTemplateLoading(PromptTemplate.class, "/prompt");
        } else {
            configuration.setDirectoryForTemplateLoading(config.getPromptPath().toFile());
        }

        configuration.setDefaultEncoding("utf-8");
        Template template = configuration.getTemplate(templateFileName);

        Pattern pattern = Pattern.compile("\\$\\{([a-zA-Z_][\\w]*)\\}");
        Matcher matcher = pattern.matcher(template.toString());
        List<String> matches = new ArrayList<>();
        while (matcher.find()) {
            String e = matcher.group(1);
            if (!matches.contains(e)) {
                matches.add(e);
            }
        }

        String generatedText;
        do {
            StringWriter writer = new StringWriter();
            template.process(dataModel, writer);
            generatedText = writer.toString();
            if (matches.size() > 0) {
                String key = matches.get(matches.size()-1);
                if (dataModel.containsKey(key)) {
                    if (dataModel.get(key) instanceof String) {
                        dataModel.put(key, "");
                    } else if (dataModel.get(key) instanceof List) {
                        dataModel.put(key, new ArrayList<String>());
                    } else if (dataModel.get(key) instanceof Map) {
                        dataModel.put(key, new HashMap<String, String>());
                    } else {
                        break;
                    }
                }
                matches.remove(matches.size()-1);
            }
        } while (AbstractRunner.isExceedMaxTokens(config, generatedText) && matches.size()>0);
        return generatedText;
    }
}
