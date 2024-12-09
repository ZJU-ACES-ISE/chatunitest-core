package zju.cst.aces.api.phase.solution;

import zju.cst.aces.api.config.Config;
import zju.cst.aces.api.impl.PromptConstructorImpl;
import zju.cst.aces.api.phase.PhaseImpl;
import zju.cst.aces.dto.ClassInfo;
import zju.cst.aces.dto.MethodInfo;

import java.io.IOException;
import java.util.Map;

public class MUTAP extends PhaseImpl {
    public MUTAP(Config config){
        super(config);
    }

}
