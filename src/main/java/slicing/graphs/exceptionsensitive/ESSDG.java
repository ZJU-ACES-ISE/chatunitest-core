package slicing.graphs.exceptionsensitive;

import slicing.arcs.sdg.ReturnArc;
import slicing.graphs.augmented.PPDG;
import slicing.graphs.augmented.PSDG;
import slicing.graphs.cfg.CFG;
import slicing.graphs.pdg.PDG;
import slicing.nodes.exceptionsensitive.ExitNode;
import slicing.nodes.exceptionsensitive.ReturnNode;
import slicing.slicing.ExceptionSensitiveSlicingAlgorithm;
import slicing.slicing.SlicingAlgorithm;

/** An exception-sensitive SDG, equivalent to an PSDG, that is built using the {@link ESPDG}
 *  instead of {@link PPDG}. It features a different slicing algorithm
 *  and return arcs, which connect an exit node to a return node. */
public class ESSDG extends PSDG {
    @Override
    protected Builder createBuilder() {
        return new Builder();
    }

    @Override
    protected SlicingAlgorithm createSlicingAlgorithm() {
        return new ExceptionSensitiveSlicingAlgorithm(this);
    }

    public void addReturnArc(ExitNode source, ReturnNode target) {
        addEdge(source, target, new ReturnArc());
    }

    /** Populates an ESSDG, using ESPDG and ESCFG as default graphs.
     * @see PSDG.Builder
     * @see ExceptionSensitiveCallConnector */
    protected class Builder extends PSDG.Builder {
        @Override
        protected CFG createCFG() {
            return new ESCFG();
        }

        @Override
        protected PDG createPDG(CFG cfg) {
            assert cfg instanceof ESCFG;
            return new ESPDG((ESCFG) cfg);
        }

        @Override
        protected void connectCalls() {
            new ExceptionSensitiveCallConnector(ESSDG.this).connectAllCalls(callGraph);
        }
    }
}
