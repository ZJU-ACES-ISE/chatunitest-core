package zju.cst.aces.api.impl.obfuscator;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.comments.LineComment;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.Data;
import org.objectweb.asm.tree.ClassNode;
import zju.cst.aces.api.config.Config;
import zju.cst.aces.api.impl.obfuscator.frame.SymbolFrame;
import zju.cst.aces.api.impl.obfuscator.util.ASMParser;
import zju.cst.aces.dto.PromptInfo;
import zju.cst.aces.dto.TestMessage;
import zju.cst.aces.api.impl.obfuscator.util.SymbolAnalyzer;
import zju.cst.aces.parser.ProjectParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

@Data
public class Obfuscator {

    public final Config config;
    private Map<String, String> cryptoMap;
    private Map<String, String> reversedMap;
    private Map<String, String> allCaseMap;
    private SymbolFrame symbolFrame;
    private int shift = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public List<String> targetGroupIds;

    public Obfuscator(Config config) {
        this.config = config;
        this.cryptoMap = new TreeMap<>((s1, s2) -> {
            int diff = s2.length() - s1.length();
            if (diff != 0) {
                return diff;
            } else {
                return s2.compareTo(s1);  // If the length is the same, sort in dictionary reverse order
            }
        });
        setTargetGroupIds(config);
    }

    //TODO
    public void setTargetGroupIds(Config config) {
        String projectGroupId = config.getProject().getGroupId();
        List<String> groupIds = Arrays.stream(config.getObfuscateGroupIds())
                .map(String::trim)
                .collect(Collectors.toList());
        if (!groupIds.contains(projectGroupId)) {
            groupIds.add(projectGroupId);
        }
        this.targetGroupIds = groupIds;
    }

    public PromptInfo obfuscatePromptInfo(PromptInfo promptInfo) {
        this.symbolFrame = findSymbolFrameByClass(promptInfo.getFullClassName());
        if (this.symbolFrame == null) {
            throw new RuntimeException("Cannot find symbol frame for class: " + promptInfo.getFullClassName());
        }
        this.symbolFrame.toObNames(targetGroupIds).forEach(name -> {
            encryptName(name);
        });
        promptInfo.setContext(obfuscateJava(promptInfo.getContext()));
        promptInfo.setUnitTest(obfuscateJava(promptInfo.getUnitTest()));
        promptInfo.setConstructorDeps(obfuscateDep(promptInfo.getConstructorDeps()));
        promptInfo.setMethodDeps(obfuscateDep(promptInfo.getMethodDeps()));
        promptInfo.setErrorMsg(obfuscateTestMessage(promptInfo.getErrorMsg()));

        promptInfo.setClassName(obfuscateName(promptInfo.getClassName()));
        promptInfo.setMethodName(obfuscateName(promptInfo.getMethodName()));
        promptInfo.setMethodSignature(obfuscateMethodSig(promptInfo.getMethodSignature()));
        // TODO: obfuscate other method brief
//        promptInfo.setOtherMethodBrief(promptInfo.getOtherMethodBrief().stream().map(this::obfuscateMethodBrief).collect(Collectors.toList()));
        this.reversedMap = createReversedMap(this.cryptoMap);
        this.allCaseMap = createAllCaseMap(this.reversedMap);
        return promptInfo;
    }

    public String obfuscateMethodBrief(String brief) {
        try {
            BodyDeclaration md = StaticJavaParser.parseBodyDeclaration(brief);
            md.accept(new ObfuscatorVisitor(), null);
            return md.toString().substring(0, md.toString().lastIndexOf("{"));
        } catch (Exception e) {
            config.getLog().error("Failed to obfuscate method brief: " + e);
        }
        return brief;
    }

    public String obfuscateMethodSig(String methodSig) {
        return obfuscateString(methodSig);
    }

    public String obfuscateMethod(String code) {
        if (code == "") {
            return "";
        }
        String obfuscatedCode = "";
        try {
            BodyDeclaration md = StaticJavaParser.parseBodyDeclaration(code);
            md.accept(new ObfuscatorVisitor(), null);
            obfuscatedCode = md.toString();
        } catch (Exception e) {
            config.getLog().error("Failed to obfuscate method source code: " + e);
        }
        return obfuscatedCode;
    }

    public String obfuscateJava(String code) {
        if (code == "") {
            return "";
        }
        String obfuscatedCode = "";
        try {
            CompilationUnit cu = StaticJavaParser.parse(code);
            PackageDeclaration pd = cu.getPackageDeclaration().orElse(null);
            if (pd != null) {
                String packageName = pd.getNameAsString();
                String obfuscatedPackage = Arrays.stream(packageName.split("\\.")).map(this::caesarCipher).collect(Collectors.joining("."));
                putCryptoMap(packageName, obfuscatedPackage);
                pd.setName(obfuscatedPackage);
            }

            List<ImportDeclaration> imports = cu.getImports();
            if (imports != null) {
                imports.forEach(id -> {
                    if (SymbolFrame.isInGroup(id.toString(), targetGroupIds)) {
                        String importName = id.getNameAsString();
                        String obfuscatedId = Arrays.stream(importName.split("\\.")).map(this::caesarCipher).collect(Collectors.joining("."));
                        id.setName(obfuscatedId);
                        putCryptoMap(importName, obfuscatedId);
                    }
                });
            }
            cu.accept(new ObfuscatorVisitor(), null);
            obfuscatedCode = cu.toString();
        } catch (Exception e) {
            //TODO: solve dep constructors and methods code
            config.getLog().error("Failed to obfuscate code: " + e);
        }
        return obfuscatedCode;
    }

    public String deobfuscateJava(String code) {
        if (code == "") {
            return "";
        }
        String obfuscatedCode = "";
        try {
            CompilationUnit cu = StaticJavaParser.parse(code);
            PackageDeclaration pd = cu.getPackageDeclaration().orElseThrow();
            String packageName = pd.getNameAsString();
            String deobfuscatedPackage = decryptName(packageName);
            pd.setName(deobfuscatedPackage);

            List<ImportDeclaration> imports = cu.getImports();
            List<ImportDeclaration> toRemove = new ArrayList<>();
            if (imports != null) {
                imports.forEach(id -> {
                    String importName = id.getNameAsString();
                    String deobfuscatedId = decryptName(importName);
                    if (deobfuscatedId.split("\\.")[0].equals(encryptName(packageName.split("\\.")[0]))) {
                        toRemove.add(id);
                    } else {
                        id.setName(deobfuscatedId);
                    }
                });
            }
            toRemove.forEach(id -> id.remove());
            cu.accept(new DeobfuscatorVisitor(), null);
            obfuscatedCode = cu.toString();
        } catch (Exception e) {
            config.getLog().error("Failed to deobfuscate code: " + e);
            e.printStackTrace();
        }
        return obfuscatedCode;
    }

    public String obfuscateName(String name) {
        if (name.contains("\\.")) {
            String[] names = name.split("\\.");
            String obfuscatedName = Arrays.stream(names).map(this::caesarCipher).collect(Collectors.joining("."));
            putCryptoMap(name, obfuscatedName);
            return obfuscatedName;
        } else {
            return encryptName(name);
        }
    }

    public String deobfuscateName(String name) {
        return decryptName(name);
    }

    public String obfuscateSig(String sig) {
        return obfuscateString(sig);
    }

    public String deobfuscateSig(String sig) {
        return deobfuscateString(sig);
    }

    public String obfuscateText(String text) {
        return obfuscateString(text);
    }

    public String deobfuscateText(String text) {
        return deobfuscateString(text);
    }

    public String obfuscateString(String str) {
        if (cryptoMap.size() == 0) {
            throw new RuntimeException("Crypto map is empty! Must run obfuscateJava first!");
        }
        try {
            for (String key : cryptoMap.keySet()) {
                str = str.replaceAll(capitalize(key), capitalize(cryptoMap.get(key)));
                str = str.replaceAll(decapitalize(key), decapitalize(cryptoMap.get(key)));
            }
        } catch (Exception e) {
            config.getLog().error("Failed to obfuscate String: " + e);
        }
        return str;
    }

    public String deobfuscateString(String str) {
        if (cryptoMap.size() == 0) {
            throw new RuntimeException("Crypto map is empty! Must run obfuscateJava first!");
        }
        try {
            for (String key : cryptoMap.keySet()) {
                if (key.length() < 4) {
                    continue;
                }
                // process the upper case and lower case of the crypto string.
                str = str.replaceAll(capitalize(cryptoMap.get(key)), capitalize(key));
                str = str.replaceAll(decapitalize(cryptoMap.get(key)), decapitalize(key));
            }
        } catch (Exception e) {
            config.getLog().error("Failed to deobfuscate String: " + e);
        }
        return str;
    }

    public Map<String, String> obfuscateDep(Map<String, String> dep) {
        Map<String, String> obfuscatedDep = new HashMap<>();
        for (String key : dep.keySet()) {
            SymbolFrame sf = findSymbolFrameByClass(key);
            if (sf == null) {
                continue;
            }
            sf.toObNames(targetGroupIds).forEach(name -> {
                encryptName(name);
            });
            obfuscatedDep.put(obfuscateName(key), obfuscateJava(dep.get(key)));
        }
        return obfuscatedDep;
    }

    public Map<String, String> deobfuscateDep(Map<String, String> dep) {
        Map<String, String> deobfuscatedDep = new HashMap<>();
        for (String key : dep.keySet()) {
            deobfuscatedDep.put(deobfuscateName(key), deobfuscateJava(dep.get(key)));
        }
        return deobfuscatedDep;
    }

    public TestMessage obfuscateTestMessage(TestMessage msg) {
        if (msg == null) {
            return null;
        }
        msg.setErrorMessage(msg.getErrorMessage().stream().map(this::obfuscateText).collect(Collectors.toList()));
        return msg;
    }

    public TestMessage deobfuscateTestMessage(TestMessage msg) {
        if (msg == null) {
            return null;
        }
        msg.setErrorMessage(msg.getErrorMessage().stream().map(this::deobfuscateText).collect(Collectors.toList()));
        return msg;
    }

    private String encryptName(String oldName) {
        if (symbolFrame == null) {
            throw new RuntimeException("Symbol frames are not initialized!");
        }
        if (cryptoMap.containsKey(oldName)) {
            return cryptoMap.get(oldName);
        }
        if (oldName.length() < 4) {
            return oldName;
        }

        if (oldName.startsWith("set") || oldName.startsWith("get") || oldName.startsWith("is") || oldName.startsWith("has")) {
            String prefix = "";
            if (oldName.startsWith("is")) {
                prefix = oldName.substring(0, 2);
            } else {
                prefix = oldName.substring(0, 3);
            }
            String suffix = oldName.substring(prefix.length());
            String newName = prefix + caesarCipher(suffix);
            putCryptoMap(oldName, newName);
            return newName;
        } else {
            String newName = caesarCipher(oldName);
            putCryptoMap(oldName, newName);
            return newName;
        }
    }

    private String encryptIfExist(String oldName) {
        if (symbolFrame == null) {
            throw new RuntimeException("Symbol frames are not initialized!");
        }
        if (cryptoMap.containsKey(oldName)) {
            return cryptoMap.get(oldName);
        }
        return oldName;
    }

    // Caesar Cipher
    private String caesarCipher(String old) {
        StringBuilder sb = new StringBuilder();
        for (char c : old.toCharArray()) {
            if (c >= 'a' && c < 'z' || c >= 'A' && c < 'Z') {
                sb.append((char) (c + this.shift));
            } else if (c == 'z') {
                sb.append('a');
            } else if (c == 'Z') {
                sb.append('A');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private String decryptName(String oldName) {
        if (this.reversedMap == null) {
            this.reversedMap = createReversedMap(this.cryptoMap);
        }
        if (this.allCaseMap == null) {
            this.allCaseMap = createAllCaseMap(this.reversedMap);
        }

        if (allCaseMap.containsKey(oldName)) {
            return allCaseMap.get(oldName);
        }
        return oldName;
    }

    // TODO: export to json
    public Map<String, SymbolFrame> generateSymbolFrames() {
        Set<ClassNode> candidateClasses = new HashSet<>();
        ASMParser asmParser = new ASMParser(config);
        Map<String, SymbolFrame> symbolFrames = new HashMap<>();
        try {
            Path artifactPath = config.getProject().getArtifactPath();
            JarFile projectJar = new JarFile(artifactPath.toString());
            candidateClasses.addAll(asmParser.loadClasses(projectJar));
            for (ClassNode classNode : candidateClasses) {
                String className = classNode.name;
                if (!SymbolFrame.isClassInGroup(className, targetGroupIds)) {
                    continue;
                }
                SymbolAnalyzer analyzer = new SymbolAnalyzer();
                SymbolFrame frame = analyzer.analyze(classNode);
                frame.filterSymbolsByGroupId(targetGroupIds);

                String packageDecl = className.substring(0, className.lastIndexOf("/")).replace("/", ".");
                String name = className.contains("$") ?
                        className.substring(className.lastIndexOf("$") + 1): className.substring(className.lastIndexOf("/") + 1);
                symbolFrames.put(packageDecl + "." + name, frame); // should be full qualified name
            }
        } catch (Exception e) {
            throw new RuntimeException("In Obfuscator.generateSymbolFrames: " + e);
        }
        return symbolFrames;
    }

    public void exportSymbolFrame() {
        ProjectParser.exportJson(config.getSymbolFramePath(), generateSymbolFrames());
    }

    public SymbolFrame findSymbolFrameByClass(String fullClassName) {
        try {
            Map<String, SymbolFrame> symbolFrames = GSON.fromJson(Files.readString(config.getSymbolFramePath(), StandardCharsets.UTF_8), new TypeToken<Map<String, SymbolFrame>>(){}.getType());
            return symbolFrames.get(fullClassName);
        } catch (IOException e) {
            throw new RuntimeException("In Obfuscator.findSymbolFrameByClass: " + e);
        }
    }

    public void putCryptoMap(String k, String v) {
        this.cryptoMap.put(k, v);
    }

    private Map<String, String> createReversedMap(Map<String, String> map) {
        return map.entrySet()
                .stream()
                .sorted(Map.Entry.<String, String>comparingByKey(Comparator.comparing(String::length).reversed().thenComparing(Comparator.reverseOrder())))
                .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey, (e1, e2) -> e1, LinkedHashMap::new));
    }

    private Map<String, String> createAllCaseMap(Map<String, String> map) {
        Map<String, String> allCaseMap = new HashMap<>();
        for (String key : map.keySet()) {
            allCaseMap.put(capitalize(key), capitalize(map.get(key)));
            allCaseMap.put(decapitalize(key), decapitalize(map.get(key)));
        }
        return allCaseMap;
    }

    public static String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }

    public static String decapitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return Character.toLowerCase(str.charAt(0)) + str.substring(1);
    }

    private class ObfuscatorVisitor extends VoidVisitorAdapter<Void> {
        public void visit(SimpleName n, Void arg) {
            n.setIdentifier(encryptIfExist(n.getIdentifier()));
            super.visit(n, arg);
        }

        @Override
        public void visit(MethodReferenceExpr n, Void arg) {
            n.setIdentifier(encryptIfExist(n.getIdentifier()));
            super.visit(n, arg);
        }
    }

    private class DeobfuscatorVisitor extends VoidVisitorAdapter<Void> {

        public void visit(ClassOrInterfaceDeclaration n, Void arg) {
            String className = n.getNameAsString();
            if (className.contains("Test")) {
                n.setName(decryptName(className.replace("Test", "")) + "Test");
            }
            super.visit(n, arg);
        }

        public void visit(MethodDeclaration n, Void arg) {
            String methodName = n.getNameAsString();
            n.setName(deobfuscateString(methodName));
            super.visit(n, arg);
        }

        public void visit(StringLiteralExpr n, Void arg) {
            n.setValue(deobfuscateString(n.getValue()));
            super.visit(n, arg);
        }

        public void visit(LineComment n, Void arg) {
            n.setContent(deobfuscateString(n.getContent()));
            super.visit(n, arg);
        }

        public void visit(SimpleName n, Void arg) {
            n.setIdentifier(decryptName(n.getIdentifier()));
            super.visit(n, arg);
        }

        @Override
        public void visit(MethodReferenceExpr n, Void arg) {
            n.setIdentifier(decryptName(n.getIdentifier()));
            super.visit(n, arg);
        }
    }
}
