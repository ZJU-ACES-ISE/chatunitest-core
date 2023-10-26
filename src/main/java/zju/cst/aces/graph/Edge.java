package zju.cst.aces.graph;

public abstract class Edge<N extends Node<?>>  {
    private N source;
    private N target;

    public Edge(N source, N target) {
        this.source = source;
        this.target = target;
    }

    public N getSource() {
        return source;
    }

    public N getTarget() {
        return target;
    }
}
