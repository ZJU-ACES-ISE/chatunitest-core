package slicing.nodes;

import slicing.arcs.pdg.FlowDependencyArc;
import slicing.graphs.jsysdg.JSysPDG;
import slicing.nodes.oo.MemberNode;

import static slicing.nodes.ObjectTree.ROOT_NAME;

/** A connection that represents a value dependence, from one element of an object tree to the
 *  main GraphNode that represents the instruction. */
public class ValueConnection implements VariableAction.PDGConnection {
    protected final VariableAction action;
    protected final String member;

    protected boolean applied = false;

    public ValueConnection(VariableAction action, String member) {
        this.action = action;
        this.member = member.isEmpty() ? ROOT_NAME : ROOT_NAME + "." + member;
    }

    @Override
    public void apply(JSysPDG graph) {
        if (applied)
            return;
        GraphNode<?> statementNode;
        if (action instanceof VariableAction.Movable)
            statementNode = ((VariableAction.Movable) action).getRealNode();
        else
            statementNode = action.getGraphNode();
        if (action.hasPolyTreeMember(member))
            for (MemberNode source : action.getObjectTree().getNodesForPoly(member)) {
                graph.addEdge(source, statementNode, new FlowDependencyArc());
                applied = true;
            }

    }
}
