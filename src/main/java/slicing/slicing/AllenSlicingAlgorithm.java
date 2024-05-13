package slicing.slicing;

import slicing.arcs.Arc;
import slicing.arcs.pdg.ConditionalControlDependencyArc;
import slicing.graphs.jsysdg.JSysDG;

public class AllenSlicingAlgorithm extends JSysDGSlicingAlgorithm {

    public AllenSlicingAlgorithm(JSysDG graph) {
        super(graph);
    }

    @Override
    protected boolean commonIgnoreConditions(Arc arc) {
        return arc instanceof ConditionalControlDependencyArc.CC1 ||
                arc instanceof ConditionalControlDependencyArc.CC2 ||
                objectFlowIgnore(arc) ||
                ppdgIgnore(arc);
    }
}
