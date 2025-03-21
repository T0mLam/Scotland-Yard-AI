package uk.ac.bris.cs.scotlandyard.ui.ai.heuristic;

import com.google.common.collect.ImmutableSet;
import com.google.common.graph.ImmutableValueGraph;
import uk.ac.bris.cs.scotlandyard.model.Piece;
import uk.ac.bris.cs.scotlandyard.model.Piece.Detective;
import uk.ac.bris.cs.scotlandyard.model.ScotlandYard.Transport;
import uk.ac.bris.cs.scotlandyard.ui.ai.MyGameStateFactory.*;

import java.util.*;

public class Dijkstra {
  private int[][] shortestDistances;
  private static Dijkstra instance;

  private class Node {
    int id;
    int value;

    Node(int id, int value) {
      this.id = id;
      this.value = value;
    }
  }

  private Dijkstra(ImmutableValueGraph<Integer, ImmutableSet<Transport>> graph) {
    shortestDistances = new int[200][200];

    for (int[] row : shortestDistances) {
      Arrays.fill(row, Integer.MAX_VALUE);
    }
    buildShortestDistances(graph);
  }

  public static Dijkstra getInstance(ImmutableValueGraph<Integer, ImmutableSet<Transport>> graph) {
    if (instance == null) {
      instance = new Dijkstra(graph);
    }
    return instance;
  }

  public static Dijkstra getInstance() {
    return instance;
  }

  private void buildShortestDistances(ImmutableValueGraph<Integer, ImmutableSet<Transport>> graph) {
    for (int source : graph.nodes()) {
      shortestDistances[source][source] = 0;

      PriorityQueue<Node> minHeap = new PriorityQueue<>(Comparator.comparingInt(n -> n.value));
      minHeap.add(new Node(source, 0));

      while (!minHeap.isEmpty()) {
        // get the node with the smallest value that has not yet been visited
        Node current = minHeap.poll();

        // find all adjacent nodes
        for (int neighbour_id : graph.adjacentNodes(current.id)) {
          int newDist = current.value + 1;

          // add update node if a route with a shorter path has been found
          if (newDist < shortestDistances[source][neighbour_id]) {
            // if previous distance value for node, it is overwritten
            shortestDistances[source][neighbour_id] = newDist;
            minHeap.add(new Node(neighbour_id, newDist));
          }
        }
      }
    }
  }

  public int getDistance(int source, int destination) {
    return shortestDistances[source][destination];
  }

  public Map<Piece, Integer> shortestPath(MyGameState gameState) {
    int source = gameState.getMrX().location();
    return shortestPath(gameState, source);
  }

  public Map<Piece, Integer> shortestPath(MyGameState gameState, int mrXLocation) {
    Set<Piece> players = gameState.getPlayers();
    Map<Piece, Integer> distances = new HashMap<>();
    Map<Integer, Piece> locations = new HashMap<>();

    // find the locations of each detective
    for (Piece player : players) {
      if (player.isDetective()) {
        Integer location = gameState.getDetectiveLocation((Detective) player).get();
        locations.put(location, player);
      }
    }

    // find the distances to each detective
    for (int destination : locations.keySet()) {
      int distance = getDistance(mrXLocation, destination);
      distances.put(locations.get(destination), distance);
    }

    return distances;
  }
}
