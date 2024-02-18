package zju.cst.aces.api;

import zju.cst.aces.dto.MethodInfo;

public interface Runner {

    public void runClass(String fullClassName);

    public void runMethod(String fullClassName, MethodInfo methodInfo);
}