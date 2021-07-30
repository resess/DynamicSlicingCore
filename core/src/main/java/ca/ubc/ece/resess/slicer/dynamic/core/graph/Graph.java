package ca.ubc.ece.resess.slicer.dynamic.core.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Graph {
  private Map<EdgeBounds, Edge> edgeSet;
  private Map<Integer, Set<Edge>> edgeMapFromSource;
  private Map<Integer, Set<Edge>> edgeMapFromDestination;

  public Graph() {
    edgeSet = new HashMap<>();
    edgeMapFromSource = new HashMap<>();
    edgeMapFromDestination = new HashMap<>();
  }

  public void setEdgeType(int source, int destination, EdgeType edgeType) {
    Edge edge = new Edge(source, destination, edgeType);
    EdgeBounds edgeVertices = new EdgeBounds(source, destination);
    if (edgeSet.containsKey(edgeVertices)) {
      edgeSet.get(edgeVertices).setEdgeType(edgeType);
    } else {
      edgeSet.put(edgeVertices, edge);
      Set<Edge> mappedEdges = edgeMapFromSource.getOrDefault(source, new HashSet<>());
      mappedEdges.add(edge);
      edgeMapFromSource.put(source, mappedEdges);

      mappedEdges = edgeMapFromDestination.getOrDefault(destination, new HashSet<>());
      mappedEdges.add(edge);
      edgeMapFromDestination.put(destination, mappedEdges);
    }
  }

  public Set<Edge> getEdgeSet() {
    return new HashSet<>(edgeSet.values());
  }

  public void removeAllEdges(Set<Edge> edges) {
    for (Edge edge: edges) {
      edgeSet.remove(new EdgeBounds(edge.getSource(), edge.getDestination()));

      Set<Edge> mappedEdges = edgeMapFromSource.getOrDefault(edge.getSource(), new HashSet<>());
      mappedEdges.remove(edge);
      edgeMapFromSource.put(edge.getSource(), mappedEdges);

      mappedEdges = edgeMapFromDestination.getOrDefault(edge.getDestination(), new HashSet<>());
      mappedEdges.remove(edge);
      edgeMapFromDestination.put(edge.getDestination(), mappedEdges);
    }

  }

  public List<Integer> predecessorListOf(int vertex) {
    Set<Integer> preds = new HashSet<>();
    if (edgeMapFromDestination.containsKey(vertex)) {
      edgeMapFromDestination.get(vertex).forEach(v -> preds.add(v.getSource()));
    }
    return new ArrayList<>(preds);
  }

  public List<Integer> successorListOf(int vertex) {
    Set<Integer> successors = new HashSet<>();
    if (edgeMapFromSource.containsKey(vertex)) {
      edgeMapFromSource.get(vertex).forEach(v -> successors.add(v.getDestination()));
    }
    return new ArrayList<>(successors);
  }

  public Edge getEdge(int source, int destination) {
    return edgeSet.get(new EdgeBounds(source, destination));
  }

  @Override
  public String toString() {
    if (edgeSet.isEmpty()) {
      return "Empty";
    }
    StringBuilder sb = new StringBuilder();
    Iterator<Edge> it = edgeSet.values().iterator();
    Edge e = it.next();
    sb.append(e.toString());
    while (it.hasNext()) {
      e = it.next();
      sb.append("\n");
      sb.append(e.toString());
    }
    return sb.toString();
  }
}
