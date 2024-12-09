package zju.cst.aces.dto;

import lombok.Data;

import java.util.*;

/**
 * Object Construction Map
 */
public class OCM {
    Map<String, TreeSet<OCC>> ocm;

    public OCM() {
        ocm = new HashMap<>();
    }

    public void add(String typeName, String className, String methodName, int lineNum, String code) {
        TreeSet<OCC> invocations = ocm.computeIfAbsent(typeName, k -> new TreeSet<>(new LengthComparator()));
        invocations.add(new OCC(className, methodName, lineNum, code));
        ocm.put(typeName, invocations);
    }

    public Map<String, TreeSet<OCC>> getOCM() {
        return this.ocm;
    }
    @Data
    public static class OCC {
        String className;
        String methodName;
        int lineNum;
        String code;

        public OCC(String className, String methodName, int lineNum, String code) {
            this.className = className;
            this.methodName = methodName;
            this.lineNum = lineNum;
            this.code = code;
        }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            OCC occ = (OCC) o;
            return lineNum == occ.lineNum &&
                    Objects.equals(className, occ.className) &&
                    Objects.equals(methodName, occ.methodName) &&
                    Objects.equals(code, occ.code);
        }

        @Override
        public int hashCode() {
            return Objects.hash(className, methodName, lineNum, code);
        }

    }

    static class LengthComparator implements Comparator<OCM.OCC> {
        @Override
        public int compare(OCM.OCC o1, OCM.OCC o2) {
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
