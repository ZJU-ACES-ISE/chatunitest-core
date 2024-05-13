package slicing.arcs.pdg;

import slicing.arcs.Arc;
import slicing.graphs.pdg.PDG;
import slicing.graphs.sdg.SDG;

/**
 * An arc used in the {@link PDG} and {@link SDG}
 * used to represent control dependence between two nodes. The traditional definition of
 * control dependence is: a node {@code a} is <it>control dependent</it> on node
 * {@code b} if and only if {@code b} alters the number of times {@code a} is executed.
 */
public class ControlDependencyArc extends Arc {
    private boolean ppdgExclusive = false;

    /** Mark this arc as PPDG exclusive.
     * @see #isPPDGExclusive() */
    public void setPPDGExclusive() {
        this.ppdgExclusive = true;
    }

    /** Whether this arc appears in the PPDG or subsequent graphs, but not in the APDG. */
    public boolean isPPDGExclusive() {
        return ppdgExclusive;
    }

    @Override
    public String getLabel() {
        return ppdgExclusive ? "*" : super.getLabel();
    }
}
