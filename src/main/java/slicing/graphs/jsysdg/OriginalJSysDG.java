package slicing.graphs.jsysdg;

import slicing.graphs.cfg.CFG;
import slicing.graphs.pdg.PDG;
import slicing.slicing.OriginalJSysDGSlicingAlgorithm;
import slicing.slicing.SlicingAlgorithm;

public class OriginalJSysDG extends JSysDG {
    @Override
    protected Builder createBuilder() {
        return new Builder();
    }

    @Override
    protected SlicingAlgorithm createSlicingAlgorithm() {
        return new OriginalJSysDGSlicingAlgorithm(this);
    }

    public class Builder extends JSysDG.Builder {
        @Override
        protected PDG createPDG(CFG cfg) {
            assert cfg instanceof JSysCFG;
            return new OriginalJSysPDG((JSysCFG) cfg);
        }
    }
}
