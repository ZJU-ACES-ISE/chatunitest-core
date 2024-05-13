package slicing.arcs.pdg;

import slicing.arcs.Arc;
import slicing.utils.Utils;

/** Represents a data dependency in an object-oriented SDG or PDG. */
public class FlowDependencyArc extends Arc {
    public FlowDependencyArc() {
        super();
    }

    public FlowDependencyArc(String variable) {
        super(variable);
    }

    public FlowDependencyArc(String[] member) {
        super(Utils.arrayJoin(member, "."));
    }

}
