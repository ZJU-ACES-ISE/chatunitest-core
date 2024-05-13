package slicing.graphs.exceptionsensitive;

import slicing.graphs.jsysdg.JSysDG;
import slicing.slicing.AllenSlicingAlgorithm;
import slicing.slicing.SlicingAlgorithm;

public class AllenSDG extends JSysDG {
    @Override
    protected SlicingAlgorithm createSlicingAlgorithm() {
        return new AllenSlicingAlgorithm(this);
    }
}
