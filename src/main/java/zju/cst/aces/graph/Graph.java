package zju.cst.aces.graph;

import java.util.ArrayList;
import java.util.List;

public abstract class Graph<N extends Node<?>, E extends Edge<N>> {
    private List<N> nodes;
    private List<E> edges;

    public Graph() {
        this.nodes = new ArrayList<>();
        this.edges = new ArrayList<>();
    }

    public void addNode(N node) {
        nodes.add(node);
    }

    public void addEdge(E edge) {
        edges.add(edge);
    }

    public List<N> getNodes() {
        return nodes;
    }

    public List<E> getEdges() {
        return edges;
    }
}
