package zju.cst.aces.util;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import zju.cst.aces.runner.AbstractRunner;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CodeExtractor {
    private boolean hasCode;
    private boolean hasSyntacticError;
    private String extractedCode;

    public CodeExtractor(String text) {
        extractedCode = extractText(text);
    }

    public String extractText(String text) {
        if (text.contains("<STEP>")) {
            List<String> infoList = List.of(text.split("<STEP>"));

//            String importsInfo = infoList.get(1);
//            Pattern pattern = Pattern.compile("```[java]*([\\s\\S]*?)```");
//            Matcher matcher = pattern.matcher(importsInfo);
//            List<String> importList = new ArrayList<>();
//            while (matcher.find()) {
//                importList.addAll(Arrays.asList(matcher.group(1).trim().split("\n")));
//                break;
//            }

            for (int i = infoList.size() - 1; i >= 0; i--) {
                String info = infoList.get(i);
                if (info.contains("```")) {
                    text = AbstractRunner.repairImports(extract(info), new ArrayList<>());
                    return text;
                }
            }
        }
        return extract(text);
    }

    /**
     * Extract the code in response and auto-repair the syntax errors.
     * @param text
     * @return
     */
    public String extract(String text) {
        String ec = "";


        // If the string is valid code, return true
        if (isSyntacticCorrect(text)) {
            hasCode = true;
            ec = text;
            hasSyntacticError = false;
        } else {
            hasCode = false;
            hasSyntacticError = false;

            // Define regex pattern to match the code blocks
            Pattern pattern = Pattern.compile("```[java]*([\\s\\S]*?)```");
            Matcher matcher = pattern.matcher(text);

            // Find all matches in the text
            while (matcher.find()) {
                String match = matcher.group(1).trim();
                if (isTest(match)) {
                    if (match.contains("class") && match.contains("import")) { // class
                        ec= syntacticCheck(match);
                        hasSyntacticError = !match.equals(ec);
                        if (!ec.equals("")) {
                            hasCode = true;
                            break;
                        }
                    } else if (isTestMethod(match)) { // method
                        ec = syntacticCheck(match);
                        hasSyntacticError = !match.equals(ec);
                        if (!ec.equals("")) {
                            hasCode = true;
                            break;
                        }
                    }
                }
            }

            if (!hasCode) {
                if (text.contains("```java")) {
                    String separateString = text.split("```java")[1];
                    if (isTest(separateString)) {
                        ec = syntacticCheck(separateString);
                        hasSyntacticError = !separateString.equals(ec);
                        if (!ec.equals("")) {
                            hasCode = true;
                        }
                    }
                } else if (text.contains("```")) {
                    String[] separateStrings = text.split("```");
                    for (String separateString : separateStrings) {
                        if (isTest(separateString)) {
                            ec = syntacticCheck(separateString);
                            hasSyntacticError = !separateString.equals(ec);
                            if (!ec.equals("")) {
                                hasCode = true;
                                break;
                            }
                        }
                    }
                } else {
                    // Define boundary
                    String[] allowed = {"import", "packages", "", "@"};
                    String[] codeLines = text.split("\\n");
                    int start = -1, anchor = -1, end = -1;
                    boolean[] allowedLines = new boolean[codeLines.length];
                    Map<Integer, Integer> leftBrace = new HashMap<>();
                    Map<Integer, Integer> rightBrace = new HashMap<>();

                    for (int i = 0; i < codeLines.length; i++) {
                        leftBrace.put(i, codeLines[i].length() - codeLines[i].replace("{", "").length());
                        rightBrace.put(i, codeLines[i].length() - codeLines[i].replace("}", "").length());

                        String stripedLine = codeLines[i].trim();
                        for (String allowStart : allowed) {
                            if (stripedLine.startsWith(allowStart)) {
                                allowedLines[i] = true;
                                break;
                            }
                        }

                        if (codeLines[i].matches(".*public class .*Test.*") && anchor == -1) {
                            anchor = i;
                        }
                    }

                    if (anchor != -1) {
                        start = anchor;
                        while (start > 0) {
                            if (allowedLines[start]) {
                                start -= 1;
                            } else {
                                break;
                            }
                        }

                        end = anchor;
                        int leftSum = 0, rightSum = 0;
                        while (end < codeLines.length) {
                            leftSum += leftBrace.get(end);
                            rightSum += rightBrace.get(end);
                            if (leftSum == rightSum && leftSum >= 1 && rightSum >= 1) {
                                break;
                            }
                            end += 1;
                        }

                        String tempCode = String.join("\n", Arrays.copyOfRange(codeLines, start, end + 1));
                        ec = syntacticCheck(tempCode);
                        hasSyntacticError = !tempCode.equals(ec);
                        if (!ec.equals("")) {
                            hasCode = true;
                        }
                    }
                }
            }
        }

        ec = ec.trim();
//        System.out.println("Has Code: " + hasCode);
//        System.out.println("Extracted Code:\n" + ec);
//        System.out.println("Has Syntactic Error: " + hasSyntacticError);
        return ec;
    }

    private static boolean isSyntacticCorrect(String code) {
        return checkFileCorrect(code) || checkClassCorrect(code) || checkMethodCorrect(code);
    }

    private static boolean checkMethodCorrect(String code) {
        try {
            MethodDeclaration md = StaticJavaParser.parseMethodDeclaration(code);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean checkClassCorrect(String code) {
        try {
            ClassOrInterfaceType cd = StaticJavaParser.parseClassOrInterfaceType(code);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean checkFileCorrect(String code) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(code);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isTest(String code) {
        if ((code.contains("@Test") || code.contains("@ParameterizedTest"))
                && (code.contains("assert") ||  code.contains("Assert"))) {
            return true;
        } else {
            return false;
        }
    }

    public static boolean isTestMethod(String code) {
        if (isTest(code)) {
            return checkMethodCorrect(code);
        } else {
            return false;
        }
    }

    /**
     * Check and fix the syntax.
     * TODO: match @ParameterizedTest anotation
     */
    public String syntacticCheck(String code) {
        if (isSyntacticCorrect(code)) {
            return code;
        } else {
            String[] stopPoints = {";", "}", "{", " "}; // Stop point
            for (int idx = code.length() - 1; idx >= 0; idx--) {
                if (contains(stopPoints, code.charAt(idx))) {
                    code = code.substring(0, idx + 1);
                    break;
                }
            }
            int leftBracket = countOccurrences(code, "{");
            int rightBracket = countOccurrences(code, "}");
            for (int idx = leftBracket - rightBracket; idx > 0; idx--) {
                code += "}\n";
            }

            if (isSyntacticCorrect(code)) {
                return code;
            }

            Pattern pattern = Pattern.compile("(?<=\\})[^\\}]+(?=@)");
            Matcher matcher = pattern.matcher(code);
            List<String> matches = new ArrayList<>();
            while (matcher.find()) {
                matches.add(matcher.group());
            }
            if (!matches.isEmpty()) {
                String lastMatch = matches.get(matches.size() - 1);
                int endIdx = code.lastIndexOf(lastMatch) + lastMatch.length();
                code = code.substring(0, endIdx).trim();
                int leftCount = countOccurrences(code, "{");
                int rightCount = countOccurrences(code, "}");
                for (int i = leftCount - rightCount; i > 0; i--) {
                    code += "\n}";
                }
            }
            if (isSyntacticCorrect(code)) {
                return code;
            } else {
                return "";
            }
        }
    }

    private boolean contains(String[] arr, char target) {
        for (String c : arr) {
            if (c.charAt(0) == target) {
                return true;
            }
        }
        return false;
    }

    private int countOccurrences(String str, String target) {
        int count = 0;
        int idx = 0;
        while ((idx = str.indexOf(target, idx)) != -1) {
            count++;
            idx += target.length();
        }
        return count;
    }

    public boolean getHasCode() {
        return hasCode;
    }

    public boolean getHasSyntacticError() {
        return hasSyntacticError;
    }

    public String getExtractedCode() {
        if (extractedCode == null) {
            System.out.println("=^^^^^^^^^^^^^^^^^^^^^^\n    Extracted code is null!");
            return "";
        }
        return extractedCode;
    }
}
