package slicing.arcs.sdg;

import slicing.nodes.GraphNode;
import slicing.nodes.io.ActualIONode;
import slicing.nodes.io.CallNode;
import slicing.nodes.io.FormalIONode;
import slicing.nodes.io.OutputNode;
import slicing.nodes.oo.MemberNode;

/** An interprocedural arc connecting {@link ActualIONode actual} and {@link FormalIONode formal}
 * nodes. The source and target must match: both must either be inputs or outputs. This arc may be an input or output. */
public class ParameterInOutArc extends InterproceduralArc {
    @Override
    public boolean isInterproceduralInputArc() {
        GraphNode<?> source = getNodeCommon(getSource());
        GraphNode<?> target = getNodeCommon(getTarget());
        return source instanceof ActualIONode && ((ActualIONode) source).isInput() &&
                target instanceof FormalIONode && ((FormalIONode) target).isInput();
    }

    @Override
    public boolean isInterproceduralOutputArc() {
        GraphNode<?> source = getNodeCommon(getSource());
        GraphNode<?> target = getNodeCommon(getTarget());
        return (source instanceof FormalIONode && ((FormalIONode) source).isOutput() &&
                target instanceof ActualIONode && ((ActualIONode) target).isOutput()) ||
                (source instanceof OutputNode && target instanceof CallNode.Return);
    }

    protected GraphNode<?> getNodeCommon(Object node) {
        GraphNode<?> graphNode = (GraphNode<?>) node;
        while (graphNode instanceof MemberNode)
            graphNode = ((MemberNode) graphNode).getParent();
        return graphNode;
    }

    /** Represents an input/output arc with an additional traversal restriction.
     *  It merges the functions of an interprocedural input/output arc and an
     *  object-flow arc. */
    public static class ObjectFlow extends ParameterInOutArc {
        @Override
        public boolean isObjectFlow() {
            return true;
        }
    }
}
