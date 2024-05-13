package slicing.slicing;

import com.github.javaparser.ast.stmt.CatchClause;
import slicing.arcs.Arc;
import slicing.graphs.jsysdg.JSysDG;
import slicing.nodes.GraphNode;
import slicing.nodes.exceptionsensitive.ExceptionExitNode;

public class JSysDGSlicingAlgorithm extends ExceptionSensitiveSlicingAlgorithm {
    public JSysDGSlicingAlgorithm(JSysDG graph) {
        super(graph);
    }

    @Override
    protected boolean commonIgnoreConditions(Arc arc) {
        return objectFlowIgnore(arc) || super.commonIgnoreConditions(arc);
    }

    protected boolean objectFlowIgnore(Arc arc) {
        GraphNode<?> target = graph.getEdgeTarget(arc);
        return arc.isObjectFlow() &&                                  // 1. The arc is object flow
                !slicingCriterion.contains(target) &&                 // 2. The target is not the slicing criterion
                reachedStream(target).noneMatch(Arc::isObjectFlow) && // 3. The target hasn't been reached by object flow arcs
                !graph.isPredicate(target) &&                         // 4. The target is not a predicate
                !(target.getAstNode() instanceof CatchClause) && !(target instanceof ExceptionExitNode); // Some extra conditions for exceptions
    }
}
