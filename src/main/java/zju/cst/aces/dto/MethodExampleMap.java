package zju.cst.aces.dto;

import lombok.Data;

import java.util.*;

/**
 * Method Example Map
 */
public class MethodExampleMap {
    Map<String, TreeSet<MEC>> mem;
    Map<String, Set<List<MethodExampleMap.MEC>>> memList;

    public MethodExampleMap() {
        mem = new HashMap<>();
        memList = new HashMap<>();
    }

    public void add(String typeName, String className, String methodName, int lineNum, String code) {
        TreeSet<MEC> invocations = mem.computeIfAbsent(typeName, k -> new TreeSet<>(new LengthComparator()));
        invocations.add(new MEC(className, methodName, lineNum, code));
        mem.put(typeName, invocations);
    }
    public void addBackWardPath(String typeName, List<MEC> paths) {
        Set<List<MEC>> invocations = memList.computeIfAbsent(typeName, k -> new HashSet<>());
        invocations.add(paths);
        memList.put(typeName, invocations);
    }

    public Map<String, TreeSet<MEC>> getMEM() {
        return this.mem;
    }

    public Map<String, Set<List<MEC>>> getMemList() {
        return this.memList;
    }

    @Data
    public static class MEC {
        String className;
        String methodName;
        int lineNum;
        String code;

        public MEC(String className, String methodName, int lineNum, String code) {
            this.className = className;
            this.methodName = methodName;
            this.lineNum = lineNum;
            this.code = code;
        }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MethodExampleMap.MEC mec = (MethodExampleMap.MEC) o;
            return lineNum == mec.lineNum &&
                    Objects.equals(className, mec.className) &&
                    Objects.equals(methodName, mec.methodName) &&
                    Objects.equals(code, mec.code);
        }

        @Override
        public int hashCode() {
            return Objects.hash(className, methodName, lineNum, code);
        }
    }

    static class LengthComparator implements Comparator<MEC> {
        @Override
        public int compare(MEC o1, MEC o2) {
            int lengthComparison = Integer.compare(o1.code.length(), o2.code.length());
            if (lengthComparison != 0) {
                return lengthComparison;
            }
            // 如果长度相同，比较其他字段
            int classNameComparison = o1.className.compareTo(o2.className);
            if (classNameComparison != 0) {
                return classNameComparison;
            }
            int methodNameComparison = o1.methodName.compareTo(o2.methodName);
            if (methodNameComparison != 0) {
                return methodNameComparison;
            }
            return Integer.compare(o1.lineNum, o2.lineNum);
        }
    }
}
