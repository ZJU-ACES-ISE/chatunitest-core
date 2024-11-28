package zju.cst.aces.prompt.template;


import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import lombok.Data;
import zju.cst.aces.api.Task;
import zju.cst.aces.api.config.Config;
import zju.cst.aces.dto.*;
import zju.cst.aces.parser.ProjectParser;
import zju.cst.aces.runner.AbstractRunner;
import zju.cst.aces.util.testpilot.JavadocCodeExampleCheck;
import zju.cst.aces.util.testpilot.SnippetAnalyzer;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Data
public class PromptTemplate {

    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public static final String CONFIG_FILE = "config.properties";
    public String TEMPLATE_INIT = "";
    public String TEMPLATE_INIT_SYSTEM  = "";
    public String TEMPLATE_EXTRA_SYSTEM  = "";
    public String TEMPLATE_EXTRA = "";
    public String TEMPLATE_REPAIR = "";

    public Map<String, Object> dataModel = new HashMap<>();
    public Properties properties;
    public Path promptPath;
    public int maxPromptTokens;
    public Config config;

    public PromptTemplate(Config config, Properties properties, Path promptPath, int maxPromptTokens) {
        this.config = config;
        this.properties = properties;
        this.promptPath = promptPath;
        this.maxPromptTokens = maxPromptTokens;
        TEMPLATE_INIT = properties.getProperty("PROMPT_TEMPLATE_INIT");
        TEMPLATE_INIT_SYSTEM = properties.getProperty("PROMPT_TEMPLATE_INIT_SYSTEM");
        TEMPLATE_EXTRA = properties.getProperty("PROMPT_TEMPLATE_EXTRA");
        TEMPLATE_EXTRA_SYSTEM = properties.getProperty("PROMPT_TEMPLATE_EXTRA");
        TEMPLATE_REPAIR = properties.getProperty("PROMPT_TEMPLATE_REPAIR");
    }
    //渲染
    public String renderTemplate(String templateFileName) throws IOException, TemplateException{
        Configuration configuration = new Configuration(Configuration.VERSION_2_3_30);

        if (this.promptPath == null) {
            configuration.setClassForTemplateLoading(PromptTemplate.class, "/prompt");
        } else {
            configuration.setDirectoryForTemplateLoading(this.promptPath.toFile());
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
        // adaptive foal context
        do {
            StringWriter writer = new StringWriter();
            template.process(dataModel, writer);
            generatedText = writer.toString();
            if (matches.size() > 0) {
                String key = matches.get(matches.size()-1);
                if (dataModel.containsKey(key)) {
                    if(dataModel.get(key)==null) break;
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
        } while (AbstractRunner.isExceedMaxTokens(this.maxPromptTokens, generatedText) && matches.size()>0);
        return generatedText;
    }

    public void buildDataModel(Config config, PromptInfo promptInfo) throws IOException {
        Map<String, String> cdep_temp = new HashMap<>();
        Map<String, String> mdep_temp = new HashMap<>();

        this.dataModel.put("unit_test", promptInfo.getUnitTest());
        // Map<String, String>, key: dependent class names
        this.dataModel.put("dep_packages", getDepPackages(promptInfo.getClassInfo(), promptInfo.getMethodInfo()));
        this.dataModel.put("dep_imports", getDepImports(promptInfo.getClassInfo(), promptInfo.getMethodInfo()));
        this.dataModel.put("dep_class_sigs", getDepClassSigs(promptInfo.getClassInfo(), promptInfo.getMethodInfo()));
        this.dataModel.put("dep_class_bodies", getDepClassBodies(promptInfo.getClassInfo(), promptInfo.getMethodInfo()));
        this.dataModel.put("dep_m_sigs", getDepBrief(promptInfo.getMethodInfo()));
        this.dataModel.put("dep_m_bodies", getDepBodies(promptInfo.getMethodInfo()));
        this.dataModel.put("dep_c_sigs", getDepConstructorSigs(promptInfo.getClassInfo(), promptInfo.getMethodInfo()));
        this.dataModel.put("dep_c_bodies", getDepConstructorBodies(promptInfo.getClassInfo(), promptInfo.getMethodInfo()));
        this.dataModel.put("dep_fields", getDepFields(promptInfo.getClassInfo(), promptInfo.getMethodInfo()));
        this.dataModel.put("dep_gs_sigs", getDepGSSigs(promptInfo.getClassInfo(), promptInfo.getMethodInfo()));
        this.dataModel.put("dep_gs_bodies", getDepGSBodies(promptInfo.getClassInfo(), promptInfo.getMethodInfo()));
        this.dataModel.put("dep_m_sigs_ano_com",getDepBriefWithAnoAndCom(promptInfo.getClassInfo(),promptInfo.getMethodInfo()));
        if(isTokenExceed(promptInfo.getMethodInfo().full_method_info,
                getDepClassSigs(promptInfo.getClassInfo(), promptInfo.getMethodInfo()),
                getDepBriefWithAnoAndCom(promptInfo.getClassInfo(),promptInfo.getMethodInfo()))){
            this.dataModel.put("dep_m_sigs_ano_com",getDepBriefWithAno(promptInfo.getClassInfo(),promptInfo.getMethodInfo()));
        }
        this.dataModel.put("project_full_code", getFullProjectCode(promptInfo.getClassName(), config));
        this.dataModel.put("method_name", promptInfo.getMethodName());
        this.dataModel.put("full_class_name",promptInfo.getFullClassName());
        this.dataModel.put("method_sig", promptInfo.getMethodSignature());
        this.dataModel.put("method_body", promptInfo.getMethodInfo().sourceCode);
        this.dataModel.put("class_name", promptInfo.getClassName());
        this.dataModel.put("class_sig", promptInfo.getClassInfo().classSignature);
        this.dataModel.put("package", promptInfo.getClassInfo().packageName);
        this.dataModel.put("class_body", promptInfo.getClassInfo().classDeclarationCode);
        this.dataModel.put("file_content", promptInfo.getClassInfo().compilationUnitCode);
        this.dataModel.put("imports", AbstractRunner.joinLines(promptInfo.getClassInfo().imports));
        this.dataModel.put("fields", AbstractRunner.joinLines(promptInfo.getClassInfo().fields));
        this.dataModel.put("full_method_info",promptInfo.getMethodInfo().full_method_info);
        this.dataModel.put("subClasses",promptInfo.getClassInfo().subClasses);
        if (!promptInfo.getClassInfo().constructorSigs.isEmpty()) {
            this.dataModel.put("constructor_sigs", AbstractRunner.joinLines(promptInfo.getClassInfo().constructorBrief));
            this.dataModel.put("constructor_bodies", AbstractRunner.getBodies(config, promptInfo.getClassInfo(), promptInfo.getClassInfo().constructorSigs));
        } else {
            this.dataModel.put("constructor_sigs", null);
            this.dataModel.put("constructor_bodies", null);
        }
        if (!promptInfo.getClassInfo().getterSetterSigs.isEmpty()) {
            this.dataModel.put("getter_setter_sigs", AbstractRunner.joinLines(promptInfo.getClassInfo().getterSetterBrief));
            this.dataModel.put("getter_setter_bodies", AbstractRunner.getBodies(config, promptInfo.getClassInfo(), promptInfo.getClassInfo().getterSetterSigs));
        } else {
            this.dataModel.put("getter_setter_sigs", null);
            this.dataModel.put("getter_setter_bodies", null);
        }
        if (!promptInfo.getOtherMethodBrief().trim().isEmpty()) {
            this.dataModel.put("other_method_sigs", promptInfo.getOtherMethodBrief());
            this.dataModel.put("other_method_bodies", promptInfo.getOtherMethodBodies());
        } else {
            this.dataModel.put("other_method_sigs", null);
            this.dataModel.put("other_method_bodies", null);
        }
        if (config.getExamplePath() != null) {
            ExampleUsage exampleUsage = new ExampleUsage(config.getExamplePath(), promptInfo.className);
            this.dataModel.put("example_usage", exampleUsage.getShortestUsage(promptInfo.getMethodInfo().methodSignature));
        }
        //add javaDocs
        String javadocs=promptInfo.getMethodInfo().getMethod_comment();
        this.dataModel.put("java_doc",javadocs);
        this.dataModel.put("java_doc_code",getJavaDocCodeExample(javadocs));
        this.dataModel.put("doc_infos",getSnippetCode(promptInfo.getMethodInfo().getMethodName(),config));
        //add hits
        if (promptInfo.getSliceStep() != null) {
            this.dataModel.put("step_desp", promptInfo.getSliceStep().getDesp());
            this.dataModel.put("step_code", promptInfo.getSliceStep().getCode());
        } else {
            this.dataModel.put("step_desp", "");
            this.dataModel.put("step_code", "");
        }
        //add target method invocation example in the project
        Map<String, List<String>> invocationCodeMap = get_method_invocation_code(Paths.get(config.tmpOutput.toString(),
                "methodExampleCode.json").toString(), promptInfo.getFullClassName(), promptInfo.getMethodSignature());
        if(invocationCodeMap!=null){
            this.dataModel.put("method_invocation_codes_innerclass", invocationCodeMap.get("inner_class_code"));
            this.dataModel.put("method_invocation_codes_outerclass", invocationCodeMap.get("outer_class_code"));
        }

        for (Map.Entry<String, String> entry : promptInfo.getConstructorDeps().entrySet()) {
            cdep_temp.put(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, String> entry : promptInfo.getMethodDeps().entrySet()) {
            mdep_temp.put(entry.getKey(), entry.getValue());
        }
        this.dataModel.put("c_deps", cdep_temp);
        this.dataModel.put("m_deps", mdep_temp);
        this.dataModel.put("full_fm", promptInfo.getContext());


    }

    public static void main(String[] args) {
        //add object creator example in the project
        List<String> invocation_codes=new ArrayList<>();
        Path tmpOutput = Paths.get("D:\\tmp\\chatunitest-info\\commons-cli");
        PromptInfo promptInfo = new PromptInfo();
        promptInfo.setFullClassName("org.apache.commons.cli.CommandLine");
        promptInfo.setMethodSignature("addOption(Option)");
        //遍历方法中的依赖类
        System.out.println(get_method_invocation_code(Paths.get(tmpOutput.toString(), "methodExampleCode.json").toString(), promptInfo.getFullClassName(), promptInfo.getMethodSignature()));
    }

    public static Map<String,List<String>> get_method_invocation_code(String staticCreationRecordPath,String fqcn_target_method,String methodName_target_method){
        if(!Files.exists(Paths.get(staticCreationRecordPath))){
            return null;
        }
        List<String> invocation_codes_outerclass=new ArrayList<>();
        List<String> invocation_codes_targetclass = new ArrayList<>();
        Map<String, Object> existing_creation_code = (Map<String, Object>) readJsonFile(staticCreationRecordPath);
        String full_method_name=fqcn_target_method+"."+methodName_target_method;
        if(existing_creation_code.get(full_method_name)==null){
            return null;
        }
        List<Map<String,Object>> static_code_list=((List<Map<String,Object>>)existing_creation_code.get(full_method_name));
        for (Map<String, Object> map : static_code_list) {
            String code = map.get("code").toString();
            if(map.get("className").equals(fqcn_target_method)){
                invocation_codes_targetclass.add(code);
            }
            else {
                invocation_codes_outerclass.add(code);
            }
        }
        HashMap<String, List<String>> resultMap = new HashMap<>();
        resultMap.put("inner_class_code",invocation_codes_targetclass);
        resultMap.put("outer_class_code",invocation_codes_outerclass);
        return resultMap;
    }

    //util for reading constructor.json
    public static Object readJsonFile(String filepath) {
        if (filepath == null) {
            return null;
        }
        try {
            Path path = Paths.get(filepath);
            if (!Files.exists(path)) {
                return null;
            }
            Reader reader = Files.newBufferedReader(path);
            Map<String, Object> map = new Gson().fromJson(reader, new TypeToken<Map<String, Object>>() {
            }.getType());
            reader.close();
            return map;
        } catch (Exception e) {
            System.err.println("Error reading JSON file: " + filepath);
            e.printStackTrace();
            return null;
        }
    }
    public Map<String, String> getDepBrief(MethodInfo methodInfo) throws IOException {
        Map<String, String> depBrief = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : methodInfo.dependentMethods.entrySet()) {
            String depClassName = entry.getKey();
            String fullDepClassName = Task.getFullClassName(config, depClassName);
            Path depClassInfoPath = config.getParseOutput().resolve(fullDepClassName.replace(".", File.separator)).resolve("class.json");
            if (!depClassInfoPath.toFile().exists()) {
                return depBrief;
            }
            ClassInfo depClassInfo = GSON.fromJson(new String(Files.readAllBytes(depClassInfoPath), StandardCharsets.UTF_8), ClassInfo.class);
            if (depClassInfo == null) {
                continue;
            }
            String info = "";
            for (String depMethodSig : entry.getValue()) {
                MethodInfo depMethodInfo = AbstractRunner.getMethodInfo(config, depClassInfo, depMethodSig);
                if (depMethodInfo == null) {
                    continue;
                }
                info += depMethodInfo.brief + "\n";
            }
            depBrief.put(depClassName, info.trim());
        }
        return depBrief;
    }

    /**
     * get depMethodSignature with annotation and comment(all methods in the dependent class)
     */
    public Map<String, String> getDepBriefWithAnoAndCom(ClassInfo classInfo, MethodInfo methodInfo) throws IOException {
        Map<String, String> depBrief = new HashMap<>();
        for (Map.Entry<String, ClassInfo> entry : getDepClassInfos(classInfo,methodInfo).entrySet()) {
            String depClassName = entry.getKey();
            ClassInfo depClassInfo=entry.getValue();
            if (depClassInfo == null) {
                continue;
            }
            Map<String, String> methodSigs = depClassInfo.getMethodSigs();
            Set<String> msigs = methodSigs.keySet();
            String info="";
            for (String msig : msigs) {
                MethodInfo mInfo = AbstractRunner.getMethodInfo(config, depClassInfo, msig);
                if(mInfo==null){
                    continue;
                }
                info+="-"+mInfo.method_comment+mInfo.brief.replace("{}","").replace("\r","") + "\n";
            }
            depBrief.put(depClassName, info.trim());
        }
        return depBrief;
    }

    /**
     * if token of testspark exceed
     */
    public boolean isTokenExceed(String full_method_info,Map<String,String> dep_class_sigs,Map<String,String> dep_m_sigs_ano_com ){
        String prompt=full_method_info;
        for (String dep_class_sig : dep_class_sigs.keySet()) {
            prompt+=dep_class_sig;
            prompt+=dep_m_sigs_ano_com.get(dep_class_sig);
        }
        if(AbstractRunner.isExceedMaxTokens(config.maxPromptTokens,prompt)){
            return true;
        }
        return false;
    }

    public Map<String, String> getDepBriefWithAno(ClassInfo classInfo, MethodInfo methodInfo) throws IOException {
        Map<String, String> depBrief = new HashMap<>();
        for (Map.Entry<String, ClassInfo> entry : getDepClassInfos(classInfo,methodInfo).entrySet()) {
            String depClassName = entry.getKey();
            ClassInfo depClassInfo=entry.getValue();
            if (depClassInfo == null) {
                continue;
            }
            Map<String, String> methodSigs = depClassInfo.getMethodSigs();
            Set<String> msigs = methodSigs.keySet();
            String info="";
            for (String msig : msigs) {
                MethodInfo mInfo = AbstractRunner.getMethodInfo(config, depClassInfo, msig);
                if(mInfo==null){
                    continue;
                }
                info+="-"+mInfo.brief.replace("{}","") + "\n";
            }
            depBrief.put(depClassName, info.trim());
        }
        return depBrief;
    }

    public Map<String, String> getDepBodies(MethodInfo methodInfo) throws IOException {
        Map<String, String> depBodies = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : methodInfo.dependentMethods.entrySet()) {
            String depClassName = entry.getKey();
            String fullDepClassName = Task.getFullClassName(config, depClassName);
            Path depClassInfoPath = config.getParseOutput().resolve(fullDepClassName.replace(".", File.separator)).resolve("class.json");
            if (!depClassInfoPath.toFile().exists()) {
                return depBodies;
            }
            ClassInfo depClassInfo = GSON.fromJson(new String(Files.readAllBytes(depClassInfoPath), StandardCharsets.UTF_8), ClassInfo.class);
            if (depClassInfo == null) {
                continue;
            }
            String info = "";
            for (String depMethodSig : entry.getValue()) {
                MethodInfo depMethodInfo = AbstractRunner.getMethodInfo(config, depClassInfo, depMethodSig);
                if (depMethodInfo == null) {
                    continue;
                }
                info += depMethodInfo.sourceCode + "\n";
            }
            depBodies.put(depClassName, info.trim());
        }
        return depBodies;
    }

    public Map<String, String> getDepFields(ClassInfo classInfo, MethodInfo methodInfo) throws IOException {
        Map<String, String> depFields = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : classInfo.constructorDeps.entrySet()) {
            String depClassName = entry.getKey();
            String fullDepClassName = Task.getFullClassName(config, depClassName);
            Path depClassInfoPath = config.getParseOutput().resolve(fullDepClassName.replace(".", File.separator)).resolve("class.json");
            if (!depClassInfoPath.toFile().exists()) {
                return depFields;
            }
            ClassInfo depClassInfo = GSON.fromJson(new String(Files.readAllBytes(depClassInfoPath), StandardCharsets.UTF_8), ClassInfo.class);
            if (depClassInfo == null) {
                continue;
            }
            depFields.put(depClassName, AbstractRunner.joinLines(depClassInfo.fields));
        }

        for (Map.Entry<String, Set<String>> entry : methodInfo.dependentMethods.entrySet()) {
            String depClassName = entry.getKey();
            if (depFields.containsKey(depClassName)) {
                continue;
            }
            String fullDepClassName = Task.getFullClassName(config, depClassName);
            Path depClassInfoPath = config.getParseOutput().resolve(fullDepClassName.replace(".", File.separator)).resolve("class.json");
            if (!depClassInfoPath.toFile().exists()) {
                return depFields;
            }
            ClassInfo depClassInfo = GSON.fromJson(new String(Files.readAllBytes(depClassInfoPath), StandardCharsets.UTF_8), ClassInfo.class);
            if (depClassInfo == null) {
                continue;
            }
            depFields.put(depClassName, AbstractRunner.joinLines(depClassInfo.fields));
        }
        return depFields;
    }

    public Map<String, String> getDepConstructorSigs(ClassInfo classInfo, MethodInfo methodInfo) throws IOException {
        Map<String, String> depConstructorSigs = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : classInfo.constructorDeps.entrySet()) {
            String depClassName = entry.getKey();
            String fullDepClassName = Task.getFullClassName(config, depClassName);
            Path depClassInfoPath = config.getParseOutput().resolve(fullDepClassName.replace(".", File.separator)).resolve("class.json");
            if (!depClassInfoPath.toFile().exists()) {
                return depConstructorSigs;
            }
            ClassInfo depClassInfo = GSON.fromJson(new String(Files.readAllBytes(depClassInfoPath), StandardCharsets.UTF_8), ClassInfo.class);
            if (depClassInfo == null) {
                continue;
            }
            depConstructorSigs.put(depClassName, AbstractRunner.joinLines(depClassInfo.constructorBrief));
        }

        for (Map.Entry<String, Set<String>> entry : methodInfo.dependentMethods.entrySet()) {
            String depClassName = entry.getKey();
            if (depConstructorSigs.containsKey(depClassName)) {
                continue;
            }
            String fullDepClassName = Task.getFullClassName(config, depClassName);
            Path depClassInfoPath = config.getParseOutput().resolve(fullDepClassName.replace(".", File.separator)).resolve("class.json");
            if (!depClassInfoPath.toFile().exists()) {
                return depConstructorSigs;
            }
            ClassInfo depClassInfo = GSON.fromJson(new String(Files.readAllBytes(depClassInfoPath), StandardCharsets.UTF_8), ClassInfo.class);
            if (depClassInfo == null) {
                continue;
            }
            depConstructorSigs.put(depClassName, AbstractRunner.joinLines(depClassInfo.constructorBrief));
        }
        return depConstructorSigs;
    }

    public Map<String, String> getDepConstructorBodies(ClassInfo classInfo, MethodInfo methodInfo) throws IOException {
        Map<String, String> depConstructorBodies = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : classInfo.constructorDeps.entrySet()) {
            String depClassName = entry.getKey();
            String fullDepClassName = Task.getFullClassName(config, depClassName);
            Path depClassInfoPath = config.getParseOutput().resolve(fullDepClassName.replace(".", File.separator)).resolve("class.json");
            if (!depClassInfoPath.toFile().exists()) {
                return depConstructorBodies;
            }
            ClassInfo depClassInfo = GSON.fromJson(new String(Files.readAllBytes(depClassInfoPath), StandardCharsets.UTF_8), ClassInfo.class);
            if (depClassInfo == null) {
                continue;
            }

            String info = "";
            for (String sig : depClassInfo.constructorSigs) {
                MethodInfo depConstructorInfo = AbstractRunner.getMethodInfo(config, depClassInfo, sig);
                if (depConstructorInfo == null) {
                    continue;
                }
                info += depConstructorInfo.sourceCode + "\n";
            }
            depConstructorBodies.put(depClassName, info.trim());
        }

        for (Map.Entry<String, Set<String>> entry : methodInfo.dependentMethods.entrySet()) {
            String depClassName = entry.getKey();
            if (depConstructorBodies.containsKey(depClassName)) {
                continue;
            }
            String fullDepClassName = Task.getFullClassName(config, depClassName);
            Path depClassInfoPath = config.getParseOutput().resolve(fullDepClassName.replace(".", File.separator)).resolve("class.json");
            if (!depClassInfoPath.toFile().exists()) {
                return depConstructorBodies;
            }
            ClassInfo depClassInfo = GSON.fromJson(new String(Files.readAllBytes(depClassInfoPath), StandardCharsets.UTF_8), ClassInfo.class);
            if (depClassInfo == null) {
                continue;
            }

            String info = "";
            for (String sig : depClassInfo.constructorSigs) {
                MethodInfo depConstructorInfo = AbstractRunner.getMethodInfo(config, depClassInfo, sig);
                if (depConstructorInfo == null) {
                    continue;
                }
                info += depConstructorInfo.sourceCode + "\n";
            }
            depConstructorBodies.put(depClassName, info.trim());
        }
        return depConstructorBodies;
    }

    public Map<String, String> getDepClassSigs(ClassInfo classInfo, MethodInfo methodInfo) throws IOException {
        Map<String, String> depClassSigs = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : classInfo.constructorDeps.entrySet()) {
            String depClassName = entry.getKey();
            String fullDepClassName = Task.getFullClassName(config, depClassName);
            Path depClassInfoPath = config.getParseOutput().resolve(fullDepClassName.replace(".", File.separator)).resolve("class.json");
            if (!depClassInfoPath.toFile().exists()) {
//                return depClassSigs;
                continue;
            }
            ClassInfo depClassInfo = GSON.fromJson(new String(Files.readAllBytes(depClassInfoPath), StandardCharsets.UTF_8), ClassInfo.class);
            if (depClassInfo == null) {
                continue;
            }
            depClassSigs.put(depClassName, depClassInfo.classSignature);
        }

        for (Map.Entry<String, Set<String>> entry : methodInfo.dependentMethods.entrySet()) {
            String depClassName = entry.getKey();
            if (depClassSigs.containsKey(depClassName)) {
                continue;
            }
            String fullDepClassName = Task.getFullClassName(config, depClassName);
            Path depClassInfoPath = config.getParseOutput().resolve(fullDepClassName.replace(".", File.separator)).resolve("class.json");
            if (!depClassInfoPath.toFile().exists()) {
//                return depClassSigs;
                continue;
            }
            ClassInfo depClassInfo = GSON.fromJson(new String(Files.readAllBytes(depClassInfoPath), StandardCharsets.UTF_8), ClassInfo.class);
            if (depClassInfo == null) {
                continue;
            }
            depClassSigs.put(depClassName, depClassInfo.classSignature);
        }
        return depClassSigs;
    }

    public Map<String, ClassInfo> getDepClassInfos(ClassInfo classInfo, MethodInfo methodInfo) throws IOException {
        Map<String, ClassInfo> depClassSigs = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : classInfo.constructorDeps.entrySet()) {
            String depClassName = entry.getKey();
            String fullDepClassName = Task.getFullClassName(config, depClassName);
            Path depClassInfoPath = config.getParseOutput().resolve(fullDepClassName.replace(".", File.separator)).resolve("class.json");
            if (!depClassInfoPath.toFile().exists()) {
                return depClassSigs;
            }
            ClassInfo depClassInfo = GSON.fromJson(new String(Files.readAllBytes(depClassInfoPath), StandardCharsets.UTF_8), ClassInfo.class);
            if (depClassInfo == null) {
                continue;
            }
            depClassSigs.put(depClassName, depClassInfo);
        }

        for (Map.Entry<String, Set<String>> entry : methodInfo.dependentMethods.entrySet()) {
            String depClassName = entry.getKey();
            if (depClassSigs.containsKey(depClassName)) {
                continue;
            }
            String fullDepClassName = Task.getFullClassName(config, depClassName);
            Path depClassInfoPath = config.getParseOutput().resolve(fullDepClassName.replace(".", File.separator)).resolve("class.json");
            if (!depClassInfoPath.toFile().exists()) {
                continue;
            }
            ClassInfo depClassInfo = GSON.fromJson(new String(Files.readAllBytes(depClassInfoPath), StandardCharsets.UTF_8), ClassInfo.class);
            if (depClassInfo == null) {
                continue;
            }
            depClassSigs.put(depClassName, depClassInfo);
        }
        return depClassSigs;
    }

    public Map<String, String> getDepClassBodies(ClassInfo classInfo, MethodInfo methodInfo) throws IOException {
        Map<String, String> depClassBodies = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : classInfo.constructorDeps.entrySet()) {
            String depClassName = entry.getKey();
            String fullDepClassName = Task.getFullClassName(config, depClassName);
            Path depClassInfoPath = config.getParseOutput().resolve(fullDepClassName.replace(".", File.separator)).resolve("class.json");
            if (!depClassInfoPath.toFile().exists()) {
                return depClassBodies;
            }
            ClassInfo depClassInfo = GSON.fromJson(new String(Files.readAllBytes(depClassInfoPath), StandardCharsets.UTF_8), ClassInfo.class);
            if (depClassInfo == null) {
                continue;
            }
            depClassBodies.put(depClassName, depClassInfo.classDeclarationCode);
        }

        for (Map.Entry<String, Set<String>> entry : methodInfo.dependentMethods.entrySet()) {
            String depClassName = entry.getKey();
            if (depClassBodies.containsKey(depClassName)) {
                continue;
            }
            String fullDepClassName = Task.getFullClassName(config, depClassName);
            Path depClassInfoPath = config.getParseOutput().resolve(fullDepClassName.replace(".", File.separator)).resolve("class.json");
            if (!depClassInfoPath.toFile().exists()) {
                return depClassBodies;
            }
            ClassInfo depClassInfo = GSON.fromJson(new String(Files.readAllBytes(depClassInfoPath), StandardCharsets.UTF_8), ClassInfo.class);
            if (depClassInfo == null) {
                continue;
            }
            depClassBodies.put(depClassName, depClassInfo.classDeclarationCode);
        }
        return depClassBodies;
    }

    public Map<String, String> getDepPackages(ClassInfo classInfo, MethodInfo methodInfo) throws IOException {
        Map<String, String> depPackages = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : classInfo.constructorDeps.entrySet()) {
            String depClassName = entry.getKey();
            String fullDepClassName = Task.getFullClassName(config, depClassName);
            Path depClassInfoPath = config.getParseOutput().resolve(fullDepClassName.replace(".", File.separator)).resolve("class.json");
            if (!depClassInfoPath.toFile().exists()) {
                return depPackages;
            }
            ClassInfo depClassInfo = GSON.fromJson(new String(Files.readAllBytes(depClassInfoPath), StandardCharsets.UTF_8), ClassInfo.class);
            if (depClassInfo == null) {
                continue;
            }
            depPackages.put(depClassName, depClassInfo.packageName);
        }

        for (Map.Entry<String, Set<String>> entry : methodInfo.dependentMethods.entrySet()) {
            String depClassName = entry.getKey();
            if (depPackages.containsKey(depClassName)) {
                continue;
            }
            String fullDepClassName = Task.getFullClassName(config, depClassName);
            Path depClassInfoPath = config.getParseOutput().resolve(fullDepClassName.replace(".", File.separator)).resolve("class.json");
            if (!depClassInfoPath.toFile().exists()) {
                return depPackages;
            }
            ClassInfo depClassInfo = GSON.fromJson(new String(Files.readAllBytes(depClassInfoPath), StandardCharsets.UTF_8), ClassInfo.class);
            if (depClassInfo == null) {
                continue;
            }
            depPackages.put(depClassName, depClassInfo.packageName);
        }
        return depPackages;
    }

    public Map<String, String> getDepImports(ClassInfo classInfo, MethodInfo methodInfo) throws IOException {
        Map<String, String> depImports = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : classInfo.constructorDeps.entrySet()) {
            String depClassName = entry.getKey();
            String fullDepClassName = Task.getFullClassName(config, depClassName);
            Path depClassInfoPath = config.getParseOutput().resolve(fullDepClassName.replace(".", File.separator)).resolve("class.json");
            if (!depClassInfoPath.toFile().exists()) {
                return depImports;
            }
            ClassInfo depClassInfo = GSON.fromJson(new String(Files.readAllBytes(depClassInfoPath), StandardCharsets.UTF_8), ClassInfo.class);
            if (depClassInfo == null) {
                continue;
            }
            depImports.put(depClassName, AbstractRunner.joinLines(depClassInfo.imports));
        }

        for (Map.Entry<String, Set<String>> entry : methodInfo.dependentMethods.entrySet()) {
            String depClassName = entry.getKey();
            if (depImports.containsKey(depClassName)) {
                continue;
            }
            String fullDepClassName = Task.getFullClassName(config, depClassName);
            Path depClassInfoPath = config.getParseOutput().resolve(fullDepClassName.replace(".", File.separator)).resolve("class.json");
            if (!depClassInfoPath.toFile().exists()) {
                return depImports;
            }
            ClassInfo depClassInfo = GSON.fromJson(new String(Files.readAllBytes(depClassInfoPath), StandardCharsets.UTF_8), ClassInfo.class);
            if (depClassInfo == null) {
                continue;
            }
            depImports.put(depClassName, AbstractRunner.joinLines(depClassInfo.imports));
        }
        return depImports;
    }

    public Map<String, String> getDepGSSigs(ClassInfo classInfo, MethodInfo methodInfo) throws IOException {
        Map<String, String> depGSSigs = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : classInfo.constructorDeps.entrySet()) {
            String depClassName = entry.getKey();
            String fullDepClassName = Task.getFullClassName(config, depClassName);
            Path depClassInfoPath = config.getParseOutput().resolve(fullDepClassName.replace(".", File.separator)).resolve("class.json");
            if (!depClassInfoPath.toFile().exists()) {
                return depGSSigs;
            }
            ClassInfo depClassInfo = GSON.fromJson(new String(Files.readAllBytes(depClassInfoPath), StandardCharsets.UTF_8), ClassInfo.class);
            if (depClassInfo == null) {
                continue;
            }
            depGSSigs.put(depClassName, AbstractRunner.joinLines(depClassInfo.getterSetterSigs));
        }

        for (Map.Entry<String, Set<String>> entry : methodInfo.dependentMethods.entrySet()) {
            String depClassName = entry.getKey();
            if (depGSSigs.containsKey(depClassName)) {
                continue;
            }
            String fullDepClassName = Task.getFullClassName(config, depClassName);
            Path depClassInfoPath = config.getParseOutput().resolve(fullDepClassName.replace(".", File.separator)).resolve("class.json");
            if (!depClassInfoPath.toFile().exists()) {
                return depGSSigs;
            }
            ClassInfo depClassInfo = GSON.fromJson(new String(Files.readAllBytes(depClassInfoPath), StandardCharsets.UTF_8), ClassInfo.class);
            if (depClassInfo == null) {
                continue;
            }
            depGSSigs.put(depClassName, AbstractRunner.joinLines(depClassInfo.getterSetterSigs));
        }
        return depGSSigs;
    }

    public Map<String, String> getDepGSBodies(ClassInfo classInfo, MethodInfo methodInfo) throws IOException {
        Map<String, String> depGSBodies = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : classInfo.constructorDeps.entrySet()) {
            String depClassName = entry.getKey();
            String fullDepClassName = Task.getFullClassName(config, depClassName);
            Path depClassInfoPath = config.getParseOutput().resolve(fullDepClassName.replace(".", File.separator)).resolve("class.json");
            if (!depClassInfoPath.toFile().exists()) {
                return depGSBodies;
            }
            ClassInfo depClassInfo = GSON.fromJson(new String(Files.readAllBytes(depClassInfoPath), StandardCharsets.UTF_8), ClassInfo.class);
            if (depClassInfo == null) {
                continue;
            }

            String info = "";
            for (String sig : depClassInfo.getterSetterSigs) {
                MethodInfo depConstructorInfo = AbstractRunner.getMethodInfo(config, depClassInfo, sig);
                if (depConstructorInfo == null) {
                    continue;
                }
                info += depConstructorInfo.sourceCode + "\n";
            }
            depGSBodies.put(depClassName, info.trim());
        }

        for (Map.Entry<String, Set<String>> entry : methodInfo.dependentMethods.entrySet()) {
            String depClassName = entry.getKey();
            if (depGSBodies.containsKey(depClassName)) {
                continue;
            }
            String fullDepClassName = Task.getFullClassName(config, depClassName);
            Path depClassInfoPath = config.getParseOutput().resolve(fullDepClassName.replace(".", File.separator)).resolve("class.json");
            if (!depClassInfoPath.toFile().exists()) {
                return depGSBodies;
            }
            ClassInfo depClassInfo = GSON.fromJson(new String(Files.readAllBytes(depClassInfoPath), StandardCharsets.UTF_8), ClassInfo.class);
            if (depClassInfo == null) {
                continue;
            }

            String info = "";
            for (String sig : depClassInfo.getterSetterSigs) {
                MethodInfo depConstructorInfo = AbstractRunner.getMethodInfo(config, depClassInfo, sig);
                if (depConstructorInfo == null) {
                    continue;
                }
                info += depConstructorInfo.sourceCode + "\n";
            }
            depGSBodies.put(depClassName, info.trim());
        }
        return depGSBodies;
    }

    public String getFullProjectCode(String className, Config config) {
        String fullProjectCode = "";
        List<String> classPaths = ProjectParser.scanSourceDirectory(config.project);
        // read the file content of each path and append to fullProjectCode
        for (String path : classPaths) {
            String cn = path.substring(path.lastIndexOf(File.separator) + 1, path.lastIndexOf("."));
            if (cn.equals(className)) {
                continue;
            }
            try {
                fullProjectCode += new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8) + "\n";
            } catch (IOException e) {
                config.getLogger().warn("Failed to append class code for " + className);
            }
        }
        return fullProjectCode;
    }
    public String getJavaDocCodeExample(String javadocs) {
        List<String> lines = Arrays.asList(javadocs.split("\r\n"));
        JavadocCodeExampleCheck javadocCodeExampleCheck=new JavadocCodeExampleCheck();
        List<String> javaDocCodeExample = javadocCodeExampleCheck.getJavaDocCodeExample(lines);
        return  String.join("\r\n", javaDocCodeExample);
    }
    public List<String> getSnippetCode(String methodName, Config config) {
        String absolutePath = config.getProject().getBasedir().getAbsolutePath();
        SnippetAnalyzer snippetAnalyzer=new SnippetAnalyzer();
        List<String> docSnippets = snippetAnalyzer.getDocSnippets(absolutePath, methodName);
        return docSnippets;
    }

}
