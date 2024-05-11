package zju.cst.aces.dto;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

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

    static class OCC {
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
    }

    static class LengthComparator implements Comparator {
        @Override
        public int compare(Object obj1, Object obj2) { //按长度排序
            OCC o1 = (OCC) obj1;
            OCC o2 = (OCC) obj2;
            return o1.code.length() - o2.code.length();
        }
    }
}
