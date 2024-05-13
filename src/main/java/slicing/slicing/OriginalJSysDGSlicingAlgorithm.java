package slicing.slicing;

import slicing.arcs.Arc;
import slicing.arcs.pdg.ObjectFlowDependencyArc;
import slicing.arcs.pdg.TotalDefinitionDependenceArc;
import slicing.arcs.sdg.ParameterInOutArc;
import slicing.graphs.jsysdg.JSysDG;

public class OriginalJSysDGSlicingAlgorithm extends JSysDGSlicingAlgorithm {
    public OriginalJSysDGSlicingAlgorithm(JSysDG graph) {
        super(graph);
    }

    @Override
    protected boolean commonIgnoreConditions(Arc arc) {
        return arc instanceof ParameterInOutArc.ObjectFlow ||
                arc instanceof ObjectFlowDependencyArc ||
                arc instanceof TotalDefinitionDependenceArc ||
                ppdgIgnore(arc) || essdgIgnore(arc);
    }
}
