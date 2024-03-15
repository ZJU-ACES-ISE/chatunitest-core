package zju.cst.aces.api.impl.obfuscator.util;

import okio.BufferedSource;
import okio.Okio;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import zju.cst.aces.api.config.Config;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

public class ASMParser {

    private final Config config;

    public ASMParser(Config config) {
        this.config = config;
    }

    Set<String> getEntries(Set<ClassNode> classNodes, Collection<String> methodSigs) {
        Set<String> entries = new HashSet<>();
        return entries;
    }

    public Set<ClassNode> loadClasses(File classFile) throws IOException {
        Set<ClassNode> classes = new HashSet<>();
        InputStream is = new FileInputStream(classFile);
        return readClass(classFile.getName(), is, classes);
    }


    public Set<ClassNode> loadClasses(JarFile jarFile) throws IOException {
        Set<ClassNode> targetClasses = new HashSet<>();
        Stream<JarEntry> str = jarFile.stream();
        str.forEach(z -> readJar(jarFile, z, targetClasses));
        jarFile.close();
        return targetClasses;
    }


    private Set<ClassNode> readClass(String className, InputStream is, Set<ClassNode> targetClasses) {
        try {
            BufferedSource source = Okio.buffer(Okio.source(is));
            byte[] bytes = source.readByteArray();
            String cafebabe = String.format("%02X%02X%02X%02X", bytes[0], bytes[1], bytes[2], bytes[3]);
            if (!cafebabe.toLowerCase().equals("cafebabe")) {
                // This class doesn't have a valid magic
                return targetClasses;
            }
            ClassNode cn = getNode(bytes);
            targetClasses.add(cn);
        } catch (Exception e) {
//            config.getLog().warn("Fail to read class {}" + className + e);
            throw new RuntimeException("Fail to read class {}" + className + ": " + e);
        }
        return targetClasses;
    }


    private Set<ClassNode> readJar(JarFile jar, JarEntry entry, Set<ClassNode> targetClasses) {
        String name = entry.getName();
        if (name.endsWith(".class")) {
            String className = name.replace(".class", "").replace("/", ".");
            // if relevant options are not specified, classNames will be empty
            try (InputStream jis = jar.getInputStream(entry)) {
                return readClass(className, jis, targetClasses);
            } catch (IOException e) {
                config.getLogger().warn("Fail to read class {} in jar {}" + entry + jar.getName() + e);
            }
        } else if (name.endsWith("jar") || name.endsWith("war")) {

        }
        return targetClasses;
    }


    private ClassNode getNode(byte[] bytes) {
        ClassReader cr = new ClassReader(bytes);
        ClassNode cn = new ClassNode();
        try {
            cr.accept(cn, 0);
        } catch (Exception e) {
            e.printStackTrace();
        }
        // garbage collection friendly
        cr = null;
        return cn;
    }
}