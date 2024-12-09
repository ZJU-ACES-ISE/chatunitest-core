package zju.cst.aces.util.testpilot;


import java.util.*;

import org.sonarsource.analyzer.commons.recognizers.CodeRecognizer;

public class  JavadocCodeExampleCheck{

  private static final String MESSAGE = "Extracted Javadoc code example.";
  private final CodeRecognizer codeRecognizer;

  public JavadocCodeExampleCheck() {
    codeRecognizer = new CodeRecognizer(0.9, new JavaFootprint());
  }


  public List<String> getJavaDocCodeExample(List<String> javaDocs) {
    List<String> codeExamples = codeRecognizer.extractCodeLines(javaDocs);
    return codeExamples;
  }
}
