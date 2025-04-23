package zju.cst.aces.api.phase.solution;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import zju.cst.aces.api.config.Config;
import zju.cst.aces.api.impl.PromptConstructorImpl;
import zju.cst.aces.api.phase.PhaseImpl;
import zju.cst.aces.coverage.CodeCoverageAnalyzer;
import zju.cst.aces.dto.*;
import zju.cst.aces.parser.ProjectParser;
import zju.cst.aces.util.telpa.JavaParserUtil;


import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TELPA extends PhaseImpl {
    public static  MethodExampleMap methodExampleMap;
    public static  Map<String, String> forwardAnalysis;
    public static  Map<String, String> backwardAnalysis;
    public static  String counterExampleCode;
    public static  boolean isCovered=false;
    private static JavaParserUtil javaParserUtil;
    private static final Object lock = new Object();
    private static Config staticConfig;

    public TELPA(Config config) {
        super(config);
        staticConfig = config;
        // 在构造函数中不立即初始化JavaParserUtil，而是在第一次使用时初始化
    }

    /**
     * 获取JavaParserUtil实例，确保只在第一次调用时创建
     * @return JavaParserUtil实例
     */
    private static JavaParserUtil getJavaParserUtil() {
        if (javaParserUtil == null) {
            synchronized (lock) {
                if (javaParserUtil == null) {
                    javaParserUtil = new JavaParserUtil(staticConfig);
                }
            }
        }
        return javaParserUtil;
    }
    /**
     * 获取MethodExampleMap实例，确保只在第一次调用时创建
     * @return MethodExampleMap实例
     */
    private static MethodExampleMap getMethodExampleMap() {
        if (methodExampleMap == null) {
            synchronized (lock) {
                if (methodExampleMap == null) {
                    ProjectParser.config = staticConfig;
                    MethodExampleMap newMap = getJavaParserUtil().createMethodExampleMap(getJavaParserUtil().getCompilationUnits());
                    getJavaParserUtil().findBackwardAnalysis(newMap);
                    getJavaParserUtil().exportMethodExampleMap(newMap);
                    getJavaParserUtil().exportBackwardAnalysis(newMap);
                    methodExampleMap = newMap;
                }
            }
        }
        return methodExampleMap;
    }

    @Override
    public void prepare() {
        // 确保初始化MethodExampleMap
        getMethodExampleMap();
        super.prepare();
    }

    @Override
    public PromptConstructorImpl generatePrompt(ClassInfo classInfo, MethodInfo methodInfo, int num) {
        // String
        if (config.getTmpOutput() != null) {
            // Get forward analysis results
            Map<String, String>  forwardAnalysis = null;
            Map<String, String>  backwardAnalysis=null;
            try {
                forwardAnalysis = getForwardAnalysis(classInfo, methodInfo);
                // Get backward analysis results
                backwardAnalysis = getBackwardAnalysis(classInfo, methodInfo);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            this.forwardAnalysis=forwardAnalysis;
            this.backwardAnalysis=backwardAnalysis;
        }
        String counterExampleCode = getCounterExampleCode(classInfo,methodInfo);
        if(counterExampleCode!=null){
            this.counterExampleCode=counterExampleCode;
        }
        return super.generatePrompt(classInfo, methodInfo, num);
    }
    @Override
    public void generateTest(PromptConstructorImpl pc){
        super.generateTest(pc);
    }

    public String getCounterExampleCode(ClassInfo classInfo,MethodInfo methodInfo) {
        NodeList<CompilationUnit> parseResult = getJavaParserUtil().addTestFiles(config.getCounterExamplePath());
        //Initialize the Coverage Relevant Variables
        StringBuilder counterExampleCode = new StringBuilder();
        List<Map<String, Object>> coverageResults = new ArrayList<>();
        Map<String, Object> bestCoverageInfo = null;
        List<String> selectedMethods = new ArrayList<>();
        Set<String> totalUncoveredLines = new HashSet<>();


        String targetMethodName = classInfo.getFullClassName() + "." + methodInfo.getMethodSignature();

        // Backward analysis result data structure as follows:
        Map<String, Set<List<MethodExampleMap.MEC>>> methodPaths = getMethodExampleMap().getMemList();

        if(methodPaths!=null&&methodPaths.containsKey(targetMethodName)) {
            for (List<MethodExampleMap.MEC> path : methodPaths.get(targetMethodName)) {
                MethodExampleMap.MEC topMethod = path.get(path.size() - 1);  // Get the top method of the path
                Map<String, String> testMethodInfo = getJavaParserUtil().findCodeByMethodInfo(topMethod.getMethodName(), parseResult);
                try {
                    for (Map.Entry<String, String> testMethod : testMethodInfo.entrySet()) {
                        Map<String, Object> coverageInfo = new CodeCoverageAnalyzer().analyzeCoverage(
                                testMethod.getValue(), testMethod.getKey(),
                                classInfo.getFullClassName(),
                                methodInfo.getMethodSignature(),
                                config.project.getBuildPath().toString(),
                                config.project.getCompileSourceRoots().get(0),
                                config.classPaths
                        );
                        coverageResults.add(coverageInfo);
                    }
                } catch (Exception e) {
                    config.getLogger().error("Failed to analyze coverage for " + topMethod.getClassName());
                }
            }
        }
        // Sort by coverage rate
        coverageResults.sort((a, b) -> {
            Double lineCoverageA = (Double) a.get("lineCoverage");
            Double lineCoverageB = (Double) b.get("lineCoverage");
            return Double.compare(lineCoverageB,lineCoverageA);
        });

        // Select the method with the highest coverage
        if (!coverageResults.isEmpty()) {
            bestCoverageInfo = coverageResults.get(0);
            // Coverage reached 100.00%
            if(bestCoverageInfo.get("lineCoverage").equals(100.0)){
                config.getLogger().info("The test generated by the SBST has reached 100%");
                isCovered=true;
                return null;
            }
            totalUncoveredLines.addAll((Collection<String>) bestCoverageInfo.get("uncoveredLines"));
            selectedMethods.add(bestCoverageInfo.get("methodCode").toString());
        }

        // Check if other methods can reduce uncovered lines
        for (int  i = 1; i < coverageResults.size(); i++) {
            Map<String, Object> coverageInfo = coverageResults.get(i);
            List<String> uncoveredLines = (List<String>) coverageInfo.get("uncoveredLines");
            boolean reducesUncoveredLines = false;

            for (String line : totalUncoveredLines) {
                if (!uncoveredLines.contains(line)) {
                    reducesUncoveredLines = true;
                    break;
                }
            }
            // Take intersection
            if (reducesUncoveredLines) {
                totalUncoveredLines.retainAll(uncoveredLines);
                selectedMethods.add((String) coverageInfo.get("methodCode"));
            }
        }

        // Build the return result
        counterExampleCode.append("these counter-examples enter the target method via the selected sequence of method invocations:\n");
        for (String method : selectedMethods) {
            counterExampleCode.append(method).append("\n");
        }

        return counterExampleCode.toString(); // Return result
    }


    public Map<String, String> getBackwardAnalysis(ClassInfo classInfo, MethodInfo methodInfo) throws IOException {
        Map<String, String> backwardAnalysis = new HashMap<>();
        MethodExampleMap methodExampleMap = getMethodExampleMap();
        String targetMethodName = classInfo.getFullClassName() + "." + methodInfo.getMethodSignature();

        // Directly get the value corresponding to targetMethodName
        Set<List<MethodExampleMap.MEC>> paths = methodExampleMap.getMemList().get(targetMethodName);
        if (paths != null) {
            StringBuilder info = new StringBuilder();
            for (List<MethodExampleMap.MEC> path : paths) {
                for (int i = path.size() - 1; i >= 0; i--) {
                    MethodExampleMap.MEC mec = path.get(i);
                    info.append(mec.getCode()).append(" -> ");
                }
                info.append(targetMethodName).append("\n");
            }

            if (info.length() > 0) {
                backwardAnalysis.put(targetMethodName, info.toString().trim());
            }
        }
        return backwardAnalysis;
    }


    public Map<String, String> getForwardAnalysis(ClassInfo classInfo, MethodInfo methodInfo) throws IOException {
        Map<String, String> forwardAnalysis = new HashMap<>();
        Map<String, TreeSet<OCM.OCC>> ocm = config.ocm.getOCM();
        String targetClassName = classInfo.getFullClassName();
        for (Map.Entry<String, TreeSet<OCM.OCC>> entry : ocm.entrySet()) {
            String key = entry.getKey();
            if (targetClassName.equals(key)) {
                TreeSet<OCM.OCC> occSet = entry.getValue();
                StringBuilder info = new StringBuilder();
                for (OCM.OCC occ : occSet) {
                    info.append(occ.getClassName()).append(".").append(occ.getMethodName())
                            .append(" at line ").append(occ.getLineNum()).append(" the construction code is ").append(occ.getCode()).append("\n");
                }

                if (info.length() > 0) {
                    forwardAnalysis.put(targetClassName, info.toString().trim());
                }
                break;
            }
        }
        return forwardAnalysis;
    }
}
