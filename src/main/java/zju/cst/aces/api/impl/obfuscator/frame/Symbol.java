package zju.cst.aces.api.impl.obfuscator.frame;

import lombok.Data;

import java.util.List;

@Data
public class Symbol {
    private String name;
    private String owner;
    private String type;
    private Integer lineNum;

    public Symbol(String name, String owner, String type, Integer line) {
        this.name = name;
        this.owner = owner;
        this.type = type;
        this.lineNum = line;
    }

    public boolean isInGroup(List<String> groupIds) {
        for (String gid : groupIds) {
            if (owner.contains(gid) || type.contains(gid)) {
                return true;
            }
        }
        return false;
    }
}
