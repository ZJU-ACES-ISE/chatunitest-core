package slicing.slicing;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.SwitchStmt;
import slicing.arcs.pdg.StructuralArc;
import slicing.graphs.sdg.SDG;
import slicing.nodes.GraphNode;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BranchLineSlicingCriterion implements SlicingCriterion {
  protected final String fqcn;
  protected final String methodSig;
  protected final int branchIdx;

  public BranchLineSlicingCriterion(String fqcn, String methodSig, int branchIdx) {
    this.fqcn = fqcn;
    this.methodSig = methodSig;
    this.branchIdx = branchIdx;
  }

  @Override
  public Set<GraphNode<?>> findNode(SDG graph) {
    Optional<CompilationUnit> optCu = findCompilationUnit(graph.getCompilationUnits());
    if (optCu.isEmpty()) throw new NoSuchElementException();

    MethodDeclaration method = optCu.get().findAll(MethodDeclaration.class, this::matchesMethodSignature)
            .stream()
            .findFirst()
            .orElseThrow(() -> new NoSuchElementException("Method not found"));

    List<Node> branches = method.findAll(Node.class, this::isBranchNode);
    if (branchIdx >= branches.size()) {
      throw new IndexOutOfBoundsException("Branch index is out of bounds");
    }

    Node selectedBranch = branches.get(branchIdx);
    return Stream.of(selectedBranch)
            .map(graph::findNodeByASTNode)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .flatMap(node -> locateAllNodes(node, graph))
            .collect(Collectors.toSet());
  }

  protected Optional<CompilationUnit> findCompilationUnit(NodeList<CompilationUnit> cus) {
    for (CompilationUnit cu : cus) {
      if (cu.getTypes().stream()
          .map(type -> type.getFullyQualifiedName().orElse(null))
          .anyMatch(fqcn::equals))
        return Optional.of(cu);
    }
    return Optional.empty();
  }

  protected boolean matchesMethodSignature(MethodDeclaration method) {
    return method.getSignature().toString().equals(methodSig);
  }

  protected boolean isBranchNode(Node node) {
    // Todo: can be improved in the future
    return node instanceof IfStmt || node instanceof SwitchStmt;
  }

  protected Stream<GraphNode<?>> locateAllNodes(GraphNode<?> graphNode, SDG graph) {
    Stream<GraphNode<?>> result =
        graph.outgoingEdgesOf(graphNode).stream()
            .filter(StructuralArc.class::isInstance)
            .map(graph::getEdgeTarget)
            .flatMap(node -> locateAllNodes(node, graph));
    return Stream.concat(Stream.of(graphNode), result);
  }

  @Override
  public String toString() {
    return String.format("BranchLineSlicingCriterion (FQCN: '%s', MethodSig: '%s', BranchIdx: %d)", fqcn, methodSig, branchIdx);
  }
}
