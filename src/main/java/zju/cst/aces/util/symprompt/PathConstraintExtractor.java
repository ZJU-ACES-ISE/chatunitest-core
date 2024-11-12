package zju.cst.aces.util.symprompt;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PathConstraintExtractor {

    public static void main(String[] args) {
        String methodCode = "public int method(int a,int b,int c,int d){\n" +
                "        int count=0;\n" +
                "        if(a>0){\n" +
                "            count+=a;\n" +
                "        }\n" +
                "        if(b==0){\n" +
                "            count+=b;\n" +
                "        }\n" +
                "        if(c<0){\n" +
                "            if(d!=0){\n" +
                "                if(c<d){\n" +
                "                    count+=c;\n" +
                "                }\n" +
                "            }\n" +
                "            else {\n" +
                "                count+=d;\n" +
                "            }\n" +
                "        }\n" +
                "        return count;\n" +
                "    }";

        List<List<String>> minPathConstraints = extractPathConstraints(methodCode);
        for (List<String> path : minPathConstraints) {
            System.out.println(path);
        }
    }

    public static List<List<String>> extractPathConstraints(String methodCode) {
        // Parse the method code
        JavaParser javaParser = new JavaParser();
        ParseResult<CompilationUnit> parseResult = javaParser.parse(new StringReader("class Temp { " + methodCode + " }"));

        // Ensure the parsing was successful
        if (parseResult.isSuccessful() && parseResult.getResult().isPresent()) {
            CompilationUnit cu = parseResult.getResult().get();

            // Create a visitor to extract path constraints
            PathConstraintVisitor pathConstraintVisitor = new PathConstraintVisitor();
            cu.accept(pathConstraintVisitor, null);

            // Generate the path constraints
            List<List<String>> pathConstraints = generatePathConstraints(pathConstraintVisitor.getConditions());

            // Generate minpaths, return all minPathConstraints
            return generateMinpaths(pathConstraints);
        } else {
            throw new RuntimeException("Parsing failed: " + parseResult.getProblems());
        }
    }

    private static class PathConstraintVisitor extends VoidVisitorAdapter<Void> {
        private final List<String> conditions = new ArrayList<>();

        @Override
        public void visit(IfStmt n, Void arg) {
            conditions.add(n.getCondition().toString());
            if (n.getElseStmt().isPresent() && n.getElseStmt().get() instanceof IfStmt) {
                visit((IfStmt) n.getElseStmt().get(), arg);
            } else {
                conditions.add("not (" + n.getCondition().toString() + ")");
            }
            super.visit(n, arg);
        }

        @Override
        public void visit(WhileStmt n, Void arg) {
            super.visit(n, arg);
            conditions.add(n.getCondition().toString());
            conditions.add("not (" + n.getCondition().toString() + ")");
        }

        public List<String> getConditions() {
            return conditions;
        }
    }

    private static List<List<String>> generatePathConstraints(List<String> conditions) {
        List<List<String>> pathConstraints = new ArrayList<>();
        generatePathConstraintsHelper(conditions, 0, new ArrayList<>(), pathConstraints);
        return pathConstraints;
    }

    private static void generatePathConstraintsHelper(List<String> conditions, int index, List<String> currentPath, List<List<String>> pathConstraints) {
        if (index == conditions.size()) {
            pathConstraints.add(new ArrayList<>(currentPath));
            return;
        }
        currentPath.add(conditions.get(index));
        generatePathConstraintsHelper(conditions, index + 2, currentPath, pathConstraints);
        currentPath.remove(currentPath.size() - 1);

        currentPath.add(conditions.get(index + 1));
        generatePathConstraintsHelper(conditions, index + 2, currentPath, pathConstraints);
        currentPath.remove(currentPath.size() - 1);
    }

    private static List<List<String>> generateMinpaths(List<List<String>> pathConstraints) {
        List<List<String>> minPaths = new ArrayList<>();
        Set<String> minconstraints = new HashSet<>();

        for (List<String> path : pathConstraints) {
            boolean containsNew = false;
            for (String condition : path) {
                if (!minconstraints.contains(condition)) {
                    containsNew = true;
                    break;
                }
            }
            if (containsNew) {
                minPaths.add(new ArrayList<>(path));
                minconstraints.addAll(path);
            }
        }
        return minPaths;
    }
}
