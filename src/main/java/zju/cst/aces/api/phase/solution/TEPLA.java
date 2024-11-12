package zju.cst.aces.api.phase.solution;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import zju.cst.aces.api.config.Config;
import zju.cst.aces.api.impl.PromptConstructorImpl;
import zju.cst.aces.api.phase.PhaseImpl;
import zju.cst.aces.dto.ClassInfo;
import zju.cst.aces.dto.ExampleUsage;
import zju.cst.aces.dto.MethodExampleMap;
import zju.cst.aces.dto.MethodInfo;
import zju.cst.aces.util.JavaParserUtil;

import java.util.Map;

public class TEPLA extends PhaseImpl {
    public TEPLA(Config config) {
        super(config);
    }
    @Override
    public void prepare() {
        JavaParserUtil javaParserUtil=new JavaParserUtil(config);
        JavaParserUtil.cus=javaParserUtil.getParseResult();
        JavaParserUtil.cusWithTest=javaParserUtil.addFilesToCompilationUnits(config.counterExamplePath);
        NodeList<CompilationUnit> cus = javaParserUtil.cus;
        MethodExampleMap methodExampleMap = javaParserUtil.createMethodExampleMap(cus);
        config.methodExampleMap=methodExampleMap;
        javaParserUtil.findBackwardAnalysis(methodExampleMap);
        javaParserUtil.exportMethodExampleMap(methodExampleMap);
        javaParserUtil.exportBackwardAnalysis(methodExampleMap);
        super.prepare();
    }

    @Override
    public PromptConstructorImpl generatePrompt(ClassInfo classInfo, MethodInfo methodInfo, int num) {
//        // String
//        if (config.getTmpOutput() != null) {
//            // 获取前向分析结果
//            Map<String, String> forwardAnalysis = getForwardAnalysis(promptInfo.getClassInfo(), promptInfo.getMethodInfo());
//            // 获取后向分析结果
//            Map<String, String>  backwardAnalysis = getBackwardAnalysis(promptInfo.getClassInfo(), promptInfo.getMethodInfo());
//
//            // 检查分析结果是否非空
//            if (forwardAnalysis != null) {
//                this.dataModel.put("forward_analysis", forwardAnalysis);
//            }
//            if (backwardAnalysis != null) {
//                this.dataModel.put("backward_analysis", backwardAnalysis);
//            }
//        }
//        String counterExampleCode = getCounterExampleCode(promptInfo);
//        if(counterExampleCode!=null){
//            this.dataModel.put("counter_examples",counterExampleCode);
//        }
        return super.generatePrompt(classInfo, methodInfo, num);
    }
}
