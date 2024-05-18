package zju.cst.aces.dto;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

/**
 * Method Example Map
 */
public class MethodExampleMap {
    Map<String, TreeSet<MEC>> mem;

    public MethodExampleMap() {
        mem = new HashMap<>();
    }

    public void add(String typeName, String className, String methodName, int lineNum, String code) {
        TreeSet<MEC> invocations = mem.computeIfAbsent(typeName, k -> new TreeSet<>(new LengthComparator()));
        invocations.add(new MEC(className, methodName, lineNum, code));
        mem.put(typeName, invocations);
    }

    public Map<String, TreeSet<MEC>> getMEM() {
        return this.mem;
    }

    static class MEC {
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
    }

    static class LengthComparator implements Comparator {
        @Override
        public int compare(Object obj1, Object obj2) { //按长度排序
            MEC o1 = (MEC) obj1;
            MEC o2 = (MEC) obj2;
            return o1.code.length() - o2.code.length();
        }
    }
}
