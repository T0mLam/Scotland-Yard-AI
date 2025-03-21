package uk.ac.bris.cs.scotlandyard.ui.ai.models;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

import io.atlassian.fugue.Pair;
import uk.ac.bris.cs.scotlandyard.model.*;
import uk.ac.bris.cs.scotlandyard.model.Move.*;
import uk.ac.bris.cs.scotlandyard.ui.ai.MyGameStateFactory;
import uk.ac.bris.cs.scotlandyard.ui.ai.MyGameStateFactory.*;
import uk.ac.bris.cs.scotlandyard.ui.ai.heuristic.Dijkstra;
import uk.ac.bris.cs.scotlandyard.ui.ai.heuristic.MrXPossibleLocations;
import uk.ac.bris.cs.scotlandyard.ui.ai.models.nodes.DetectiveNode;
import uk.ac.bris.cs.scotlandyard.ui.ai.models.nodes.MrXNode;
import uk.ac.bris.cs.scotlandyard.ui.ai.models.nodes.Node;

import static uk.ac.bris.cs.scotlandyard.model.ScotlandYard.standardGraph;

public class DetectiveMCTS extends Model {
  private final Random random = new Random();
  private MrXPossibleLocations mrXPossibleLocations;
  private Dijkstra dijkstra;
  private final double C = 0.5;
  private final double W = 5;
  private final MyGameStateFactory myGameStateFactory = new MyGameStateFactory();
  private long deadline;
  private final boolean singleTree = true; // use/don't use single tree determinisation

  // reset mrXPossibleLocations and Dijkstra when new game selected
  @Override
  public void onStart() {
    mrXPossibleLocations = new MrXPossibleLocations();
    try {
      dijkstra = Dijkstra.getInstance(standardGraph());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Nonnull @Override public Move pickMove(
    @Nonnull Board board,
    Pair<Long, TimeUnit> timeoutPair) {

    long timeDuration = 1000;
    deadline = System.currentTimeMillis() + timeDuration;

    mrXPossibleLocations.updateLocations(board);

    // create a list of many separate boards
    List<Board> boards = new ArrayList<>();
    int numTrees = 16;
    for (int i = 0; i < numTrees; i++) {
      boards.add(myGameStateFactory.buildFromBoard(board, mrXPossibleLocations.getRandomLocation()));
    }

    // build trees for each of the boards
    Set<Node> rootNodes = boards.parallelStream()
      .map(this::buildTree)
      .collect(Collectors.toSet());


    Node bestChild = rootNodes.stream()
      .reduce(new DetectiveNode(
        null, null, null, null, null),
        (combined, element) -> {
          if (combined.children.isEmpty()) {
            combined.children.addAll(element.children);
          } else {
            for (Node elemChild : element.children) {
              for (Node combChild : combined.children) {
                if (elemChild.move.equals(combChild.move)) {
                  combChild.numVisits += elemChild.numVisits;
                  combChild.totalReturn += elemChild.totalReturn;
                }
              }
            }
          }
          return combined;
        }
      ).children.stream()
//      .max(Comparator.comparingDouble(n -> n.numVisits))
      .min(Comparator.comparingDouble(n -> (n.totalReturn / n.numVisits) +
        0.02 * mrXPossibleLocations.getLocations().stream()
          .map(location -> dijkstra.getDistance(MoveFilter.getMoveDestination(n.move), location))
          .min(Integer::compareTo).orElse(1)
      ))
      .get();

    // remove the detective's destination from mrXPossible locations
    mrXPossibleLocations.updateLocations((Move.SingleMove) bestChild.move);
    return bestChild.move;
  }

  private Node buildTree(Board board) {
    // select a random determinisation - not actually used
    MyGameStateFactory.MyGameState gameState = new MyGameStateFactory().buildFromBoard(
      board, mrXPossibleLocations.getRandomLocation()
    );

    Node root = new DetectiveNode(myGameStateFactory, gameState, null, null, mrXPossibleLocations.copy());
    root.expand();

    Piece targetPiece = root.children.isEmpty() ? null : root.children.get(0).move.commencedBy();

    // numPlayouts < maxPlayouts
    while (System.currentTimeMillis() < deadline) {

      // returns a leaf node according to UCT
      Node node = root.selectBestChild(C, W);

      // expand at node and rollout
      double score;
      if (node.numVisits == 0) { // just playout for unvisited leaf node
        score = Playout.playout(node.gameState, node.mrXPossibleLocationsLocal.copy(), targetPiece, targetPiece);
      } else { // expand when the leaf node has had 1 playout already
        node.expand();

        // if there is a winner
        if (node.gameState.getWinner().contains(Piece.MrX.MRX)) score = 1;
        else if (!node.gameState.getWinner().isEmpty() && node.move.commencedBy() == targetPiece) score =  0;
        else if (!node.gameState.getWinner().isEmpty()) {
          score = Playout.r;
        }
        // if the node has children
        else if (!node.children.isEmpty()) {
          node = node.children.get(random.nextInt(node.children.size()));
          if (singleTree && node.parent != null && node.parent.isMrX && ((DetectiveNode) node.parent).firstHiderPly) {
            node.instantiateNode(((DetectiveNode)node.parent).moveMap.get(node.move).advance(node.move));
          } else if (node.parent != null && node.gameState == null) {
            node.instantiateNode(node.parent.gameState.advance(node.move));
          }
          score = Playout.playout(node.gameState, node.mrXPossibleLocationsLocal.copy(), targetPiece, targetPiece);
        }
        // if the node has no children (this is a terminal state)
        else {
          if (node.parent != null && node.gameState == null) {
            node.instantiateNode(node.parent.gameState.advance(node.move));
          }
          score = Playout.playout(node.gameState, node.mrXPossibleLocationsLocal.copy(), targetPiece, targetPiece);
        }
      }

      // backpropagate return
      backpropagate(node, score);

    }
    return root;
  }

  private void backpropagate(Node node, double score) {
    node.totalReturn += score;
    node.numVisits++;

    // for all nodes 1 levels or below
    if (node.parent != null) {
      backpropagate(node.parent, score);
    }
  }
}