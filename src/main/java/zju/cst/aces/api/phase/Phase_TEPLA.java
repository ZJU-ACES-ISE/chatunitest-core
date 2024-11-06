package zju.cst.aces.api.phase;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import zju.cst.aces.api.config.Config;
import zju.cst.aces.dto.MethodExampleMap;
import zju.cst.aces.util.JavaParserUtil;

public class Phase_TEPLA  extends Phase {
    public Phase_TEPLA(Config config) {
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



}
