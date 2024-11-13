package zju.cst.aces.runner.solution_runner;

import zju.cst.aces.api.config.Config;
import zju.cst.aces.api.impl.ChatGenerator;
import zju.cst.aces.api.impl.PromptConstructorImpl;
import zju.cst.aces.api.impl.RepairImpl;
import zju.cst.aces.dto.*;
import zju.cst.aces.prompt.template.PromptTemplate;
import zju.cst.aces.runner.AbstractRunner;
import zju.cst.aces.runner.MethodRunner;
import zju.cst.aces.util.CodeExtractor;
import zju.cst.aces.util.chattester.TesterValidator;


import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatTesterRunner extends MethodRunner {
    public ChatTesterRunner(Config config, String fullClassName, MethodInfo methodInfo) throws IOException {
        super(config, fullClassName, methodInfo);
    }

    /**
     * Main process of ChatTester, including:
     * 1. Generate intention for focal method, then
     * 2. Use intention and focal context to generate test, and
     * 3. Iteratively repair the test until it passes.
     * @param num
     * @return If the generation process is successful
     */
    @Override
    public boolean startRounds(final int num) throws IOException {
        String testName = className + separator + methodInfo.methodName + separator
                + classInfo.methodSigs.get(methodInfo.methodSignature) + separator + num + separator + "Test";
        String fullTestName = fullClassName + separator + methodInfo.methodName + separator
                + classInfo.methodSigs.get(methodInfo.methodSignature) + separator + num + separator + "Test";
         config.getLogger().info("\n==========================\n[ChatUniTest] Generating test for method < "
                + methodInfo.methodName + " > number " + num + "...\n");

        config.setValidator(new TesterValidator(config.getTestOutput(), config.getCompileOutputPath(),  config.getProject().getBasedir().toPath().resolve("target"), config.getClassPaths())); // 设置chattester的专属验证器


        ChatGenerator generator = new ChatGenerator(config);
        PromptConstructorImpl pc = new PromptConstructorImpl(config);
        RepairImpl repair = new RepairImpl(config, pc);
        //prompt generation里面的
        pc.setFullTestName(fullTestName);
        pc.setTestName(testName);

        PromptInfo promptInfo = pc.getPromptInfo();
        promptInfo.setFullTestName(fullTestName);
        Path savePath = config.getTestOutput().resolve(fullTestName.replace(".", File.separator) + ".java");
        promptInfo.setTestPath(savePath);

        int errorNum = Integer.MAX_VALUE;
        int invalidRefinementCount = 0;
        for (int rounds = 0; rounds < config.getMaxRounds(); rounds++) {
            promptInfo.addRecord(new RoundRecord(rounds));
            RoundRecord record = promptInfo.getRecords().get(rounds);
            record.setAttempt(num);
            List<ChatMessage> prompt;
            PromptTemplate pt = this.promptGenerator.promptTemplate;
            pt.buildDataModel(config, promptInfo);

            if (rounds == 0) {
                // generate method intention
                 config.getLogger().info("Creating intention for method < " + methodInfo.methodName + " > ...");
                List<ChatMessage> intentionPrompt = this.promptGenerator.generateMessages(promptInfo, pt.TEMPLATE_EXTRA);
                ChatResponse response = ChatGenerator.chat(config, intentionPrompt);
                String intention = ChatGenerator.getContentByResponse(response);

                // set intention in user prompt
                prompt = promptGenerator.generateMessages(promptInfo);
                ChatMessage userChatMessage = prompt.get(1);
                String oldContent = userChatMessage.getContent();
                int lastBraceIndex = oldContent.lastIndexOf("}");
                userChatMessage.setContent(
                        new StringBuilder(oldContent).insert(lastBraceIndex + 1, "\n//Method intention\n" + intention).toString()
                );

                 config.getLogger().info("Generating test for method < " + methodInfo.methodName + " > round " + rounds + " ...");
            } else if (promptInfo.getErrorMsg() != null) {
                assert(!promptInfo.getErrorMsg().getErrorMessage().isEmpty());
                if (promptInfo.getErrorMsg().getErrorMessage().size() >= errorNum) { //todo 这里errorNum为int max,基本触发不了吧
                    invalidRefinementCount++;
                    if (invalidRefinementCount >= 3) {
                         config.getLogger().info("Exceeding maximum invalid refinement count, break.");
                        break;
                    }
                }
                errorNum = promptInfo.getErrorMsg().getErrorMessage().size();
                // iterate repair process
                 config.getLogger().info("Fixing test for method < " + methodInfo.methodName + " > round " + rounds + " ...");
                prompt = promptGenerator.generateMessages(promptInfo);
                TestMessage errorMsg = promptInfo.getErrorMsg();
                if (errorMsg.getErrorType().equals(TestMessage.ErrorType.COMPILE_ERROR)) {
                    List<CompilerError> compilerErrors = new ArrayList<>();
                    for (String error : errorMsg.getErrorMessage()) {
                        compilerErrors.addAll(parseCompilerErrors(error));
                    }
                    Set<String> classInError = new HashSet<>();
                    Map<String, String> methodInError = new HashMap<>();
                    for (CompilerError error : compilerErrors) {
                        if (error.symbolType != null && error.symbolType.equals("class")) {
                            classInError.add(error.symbolName);
                        } else if (error.symbolType != null && error.symbolType.equals("method")) {
                            methodInError.put(error.symbolName, error.variableType);
                        }
                    }

                    String repairPrompt = prompt.get(0).getContent();
                    StringBuilder deps = new StringBuilder();

                    for (String className : classInError) {
                        ClassInfo depInfo = AbstractRunner.getClassInfo(config, className);
                        if (depInfo != null) {
                            deps.append("// ").append(className).append(" class\n");
                            deps.append(depInfo.getClassSignature()).append("{\n");
                            deps.append(joinLines(depInfo.getConstructorSigs())).append("\n}");
                        }
                    }
                    for (String methodName : methodInError.keySet()) {
                        String methodType = methodInError.get(methodName);
                        if (deps.toString().contains(methodType)) {
                            continue;
                        }
                        ClassInfo typeInfo = AbstractRunner.getClassInfo(config, methodType);
                        deps.append("// ").append(methodType).append(" class\n");
                        deps.append(typeInfo.getClassSignature()).append("{\n");
                        MethodInfo depInfo = null;
                        for (String mSig : typeInfo.getMethodSigs().keySet()) {
                            if (mSig.split("\\(")[0].equals(methodName.split("\\(")[0])) {
                                depInfo = AbstractRunner.getMethodInfo(config, typeInfo, mSig);
                                if (depInfo != null) {
                                    deps.append(depInfo.methodSignature).append(";\n");
                                }
                            }
                        }
                        if (depInfo == null) {
                            deps.append(joinLines(typeInfo.getMethodsBrief()));
                        }
                        deps.append("}");
                    }

                    if (!deps.toString().isEmpty()) {
//                         config.getLogger().info("==================================================");
//                         config.getLogger().info("[ChatTester Deps in Repair Process]: \n" + deps);
//                         config.getLogger().info("==================================================");
                        int lastBraceIndex = repairPrompt.lastIndexOf("}");
                        prompt.get(0).setContent(
                                new StringBuilder(repairPrompt).insert(lastBraceIndex + 1, deps).toString()
                        );
                    }
                }
            } else {
                prompt = promptGenerator.generateMessages(promptInfo);
            }

            // start generate test
            String code = generateTest(prompt, record);
            if (!record.isHasCode()) {
                continue;
            }

            if (CodeExtractor.isTestMethod(code)) {
                TestSkeleton skeleton = new TestSkeleton(promptInfo); // test skeleton to wrap a test method
                code = skeleton.build(code);
            } else {
                code = repair.ruleBasedRepair(code);
            }
            promptInfo.setUnitTest(code);

            record.setCode(code);
            repair.LLMBasedRepair(code, record.getRound());
            if (repair.isSuccess()) {
                record.setHasError(false);
                exportRecord(promptInfo, classInfo, record.getAttempt());
                return true;
            }
            record.setHasError(true);
            record.setErrorMsg(promptInfo.getErrorMsg());
        }
        exportRecord(pc.getPromptInfo(), classInfo, num);
        return false;
    }

    public String generateTest(List<ChatMessage> prompt, RoundRecord record) {

        if (MethodRunner.isExceedMaxTokens(config.getMaxPromptTokens(), prompt)) {
            config.getLogger().error("Exceed max prompt tokens: " + methodInfo.methodName + " Skipped.");
            record.setPromptToken(-1);
            record.setHasCode(false);
            return "";
        }
        config.getLogger().debug("[Prompt]:\n" + prompt);

        ChatResponse response = ChatGenerator.chat(config, prompt);
        String content = ChatGenerator.getContentByResponse(response);
        config.getLogger().debug("[Response]:\n" + content);
        String code = ChatGenerator.extractCodeByContent(content);

        record.setPromptToken(response.getUsage().getPromptTokens());
        record.setResponseToken(response.getUsage().getCompletionTokens());
        record.setPrompt(prompt);
        record.setResponse(content);
        if (code.isEmpty()) {
            config.getLogger().info("Test for method < " + methodInfo.methodName + " > extract code failed");
            record.setHasCode(false);
            return "";
        }
        record.setHasCode(true);
        return code;
    }

    public static class CompilerError {
        public String testName;
        public int lineNumber;
        public String symbolType;
        public String symbolName;
        public String variableType;
        public String variableName;
        public String locationDetail;

        @Override
        public String toString() {
            return "ErrorLocation: " + testName + ", LineNumber: " + lineNumber
                    + ", SymbolType: " + symbolType + ", SymbolName: " + symbolName
                    + ", VariableType: " + variableType + ", VariableName: " + variableName;
        }
    }

    public static List<CompilerError> parseCompilerErrors(String errorChatMessages) {
        List<CompilerError> errors = new ArrayList<>();
        String pattern = "Error in (.+?): line (\\d+) : (cannot find symbol|找不到符号)\\n\\s+(符号|symbol):\\s+(方法|变量|类|method|variable|class) ([^\\n]+)\\n\\s+(位置|location): ([^\\n]+)";
        Pattern r = Pattern.compile(pattern);
        Matcher m = r.matcher(errorChatMessages);

        while (m.find()) {
            CompilerError error = new CompilerError();
            error.testName = m.group(1);
            error.lineNumber = Integer.parseInt(m.group(2));
            error.symbolType = m.group(5);
            error.symbolName = m.group(6).trim();

            if (error.symbolType.equals("类")) {
                error.symbolType = "class";
            } else if (error.symbolType.equals("方法")) {
                error.symbolType = "method";
            } else if (error.symbolType.equals("变量")) {
                error.symbolType = "variable";
            }

            error.locationDetail = m.group(8).trim();
            if (error.symbolType.equals("method")) {
                if (error.locationDetail.contains("类型为 ")) {
                    // 解析中文错误信息中的位置信息
                    Pattern locationPattern = Pattern.compile("类型为 (\\S+)的变量 (\\S+)");
                    Matcher locationMatcher = locationPattern.matcher(error.locationDetail);
                    if (locationMatcher.find()) {
                        error.variableType = locationMatcher.group(1);
                        error.variableName = locationMatcher.group(2);
                    }
                } else if (error.locationDetail.contains("类 ")) {
                    // 如果是类错误，我们将解析出类的全限定名
                    Pattern locationPattern = Pattern.compile("类 (\\S+)");
                    Matcher locationMatcher = locationPattern.matcher(error.locationDetail);
                    if (locationMatcher.find()) {
                        error.variableType = locationMatcher.group(1);
                        // 如果是类错误，则没有可获取的变量名
                        error.variableName = "";
                    }
                } else if (error.locationDetail.contains("class ")) {
                    // 如果是类错误，我们将解析出类的全限定名
                    Pattern locationPattern = Pattern.compile("class (\\S+)");
                    Matcher locationMatcher = locationPattern.matcher(error.locationDetail);
                    if (locationMatcher.find()) {
                        error.variableType = locationMatcher.group(1);
                        // 如果是类错误，则没有可获取的变量名
                        error.variableName = "";
                    }
                } else if (error.locationDetail.contains("variable ")) {
                    // 如果错误与变量相关，我们同时解析变量的名称和类型。
                    Pattern locationPattern = Pattern.compile("variable (\\S+) of type (\\S+)");
                    Matcher locationMatcher = locationPattern.matcher(error.locationDetail);
                    if (locationMatcher.find()) {
                        error.variableName = locationMatcher.group(1);
                        error.variableType = locationMatcher.group(2);
                    }
                }
            }

            errors.add(error);
        }

        return errors;
    }
}
