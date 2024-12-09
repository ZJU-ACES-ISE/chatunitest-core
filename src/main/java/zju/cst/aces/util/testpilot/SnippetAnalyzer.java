package zju.cst.aces.util.testpilot;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class SnippetAnalyzer {


    public List<String> getDocSnippets(String dirName, String method) {
        Set<Path> mdFiles = findMdFiles(dirName);
        List<String> result = new ArrayList<>();

        for (Path mdFile : mdFiles) {
            Set<String> snippets = findCodeSnippets(mdFile);
            for (String snippet : snippets) {
                List<String> tokens = tokenize(snippet);
                if (tokens.contains(method) && nrLinesInSnippet(snippet) >= 1 && callsAPIMethod(snippet, method)) {
                    result.add(snippet);
                }
            }
        }

        return result;
    }

    private Set<Path> findMdFiles(String dirName) {
        Set<Path> mdFiles = new HashSet<>();
        try {
            Files.walk(Paths.get(dirName))
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".md"))
                    .forEach(mdFiles::add);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return mdFiles;
    }

    private Set<String> findCodeSnippets(Path file) {
        Set<String> snippets = new HashSet<>();
        try {
            String content = new String(Files.readAllBytes(file));
            // Match code blocks in markdown, capturing blocks regardless of language specified
            Matcher matcher = Pattern.compile("(?s)```.*?\\n(.*?)```").matcher(content);
            while (matcher.find()) {
                snippets.add(matcher.group(1));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return snippets;
    }

    private List<String> tokenize(String code) {
        return Arrays.asList(code.split("\\W+"));
    }

    private int nrLinesInSnippet(String snippet) {
        return snippet.split("\n").length;
    }

    private boolean callsAPIMethod(String snippet, String methodName) {
        Pattern pattern = Pattern.compile("\\b" + Pattern.quote(methodName) + "\\(");
        Matcher matcher = pattern.matcher(snippet);
        return matcher.find();
    }

}
