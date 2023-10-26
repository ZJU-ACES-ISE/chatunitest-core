package zju.cst.aces.graph;

import java.util.*;

class GraphHelper {

    public static <N extends Node<?>, E extends Edge<N>> Set<N> findPredecessors(Graph<N, E> graph, N startNode) {
        Set<N> predecessors = new HashSet<>();
        Queue<N> queue = new LinkedList<>();
        queue.add(startNode);

        while (!queue.isEmpty()) {
            N current = queue.poll();
            for (E edge : graph.getEdges()) {
                if (edge.getTarget().equals(current) && !predecessors.contains(edge.getSource())) {
                    predecessors.add(edge.getSource());
                    queue.add(edge.getSource());
                }
            }
        }

        return predecessors;
    }

    public static <N extends Node<?>, E extends Edge<N>> List<N> dfs(Graph<N, E> graph, N startNode) {
        List<N> visited = new ArrayList<>();
        Stack<N> stack = new Stack<>();
        stack.push(startNode);

        while (!stack.isEmpty()) {
            N current = stack.pop();
            if (!visited.contains(current)) {
                visited.add(current);
                for (E edge : graph.getEdges()) {
                    if (edge.getSource().equals(current)) {
                        stack.push(edge.getTarget());
                    }
                }
            }
        }

        return visited;
    }

    public static <N extends Node<?>, E extends Edge<N>> List<N> bfs(Graph<N, E> graph, N startNode) {
        List<N> visited = new ArrayList<>();
        Queue<N> queue = new LinkedList<>();
        queue.add(startNode);

        while (!queue.isEmpty()) {
            N current = queue.poll();
            if (!visited.contains(current)) {
                visited.add(current);
                for (E edge : graph.getEdges()) {
                    if (edge.getSource().equals(current)) {
                        queue.add(edge.getTarget());
                    }
                }
            }
        }

        return visited;
    }
}