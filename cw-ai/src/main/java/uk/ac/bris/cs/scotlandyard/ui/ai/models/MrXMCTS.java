package uk.ac.bris.cs.scotlandyard.ui.ai.models;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

import io.atlassian.fugue.Pair;
import uk.ac.bris.cs.scotlandyard.model.*;
import uk.ac.bris.cs.scotlandyard.ui.ai.MyGameStateFactory;
import uk.ac.bris.cs.scotlandyard.ui.ai.heuristic.MrXPossibleLocations;
import uk.ac.bris.cs.scotlandyard.ui.ai.models.nodes.MrXNode;
import uk.ac.bris.cs.scotlandyard.ui.ai.models.nodes.Node;

public class MrXMCTS extends Model {
  private final Random random = new Random();
  private MrXPossibleLocations mrXPossibleLocations = new MrXPossibleLocations();
  private final double C = 0.5;
  private final double W = 5;
  private long deadline;
  private MyGameStateFactory myGameStateFactory = new MyGameStateFactory();

  @Override
  public void onStart() {
    mrXPossibleLocations = new MrXPossibleLocations();
  }

  @Nonnull @Override public Move pickMove(
    @Nonnull Board board,
    Pair<Long, TimeUnit> timeoutPair) {
    long timeDuration = 1000;
    deadline = System.currentTimeMillis() + timeDuration;

    var moves = board.getAvailableMoves().asList();
    MyGameStateFactory.MyGameState gameState = new MyGameStateFactory().buildFromBoard(board, moves.get(0).source());

    // create a list of duplicate gamestates
    List<MyGameStateFactory.MyGameState> gameStates = new ArrayList<>();

    int numTrees = 16;
    for (int i = 0; i < numTrees; i++) {
      gameStates.add(myGameStateFactory.buildFromBoard(gameState, gameState.getMrX().location()));
    }

    // build a MCTS tree for each node
    Set<MrXNode> rootNodes = gameStates.parallelStream()
      .map(this::buildTree)
      .collect(Collectors.toSet());

    // combine the stats of each child of the same move
    Node bestChild = rootNodes.stream()
      .reduce(new MrXNode(
           null, null, null, null),
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
      .max(Comparator.comparingDouble(n -> n.numVisits))
      .get();

    mrXPossibleLocations.updateLocations(gameState.advance(bestChild.move));
    return bestChild.move;
  }

  private MrXNode buildTree(MyGameStateFactory.MyGameState gameState) {

    // create and populate root node
    MrXNode root = new MrXNode(gameState, null, null, mrXPossibleLocations.copy());
    root.expand();

    while (System.currentTimeMillis() < deadline) {
      // select a node
      Node node = root.selectBestChild(C, W);

      // expand at node and rollout
      double score;
      if (node.numVisits == 0) {
        score = Playout.playout(node.gameState, node.mrXPossibleLocationsLocal.copy(), null, null);
      }
      else {
        // node will already have 1 playout initiated from it
        node.expand();

        // return reward if applicable
        if (node.gameState.getWinner().contains(Piece.MrX.MRX)) score = 1;
        else if (!node.gameState.getWinner().isEmpty()) {
          score = 0;
        }
        // pick a child and rollout
        else if (!node.children.isEmpty()) {
          node = node.children.get(random.nextInt(node.children.size()));
          if (node.parent != null && node.gameState == null) {
            node.instantiateNode(node.parent.gameState.advance(node.move));
          }
          score = Playout.playout(node.gameState, node.mrXPossibleLocationsLocal.copy(), null, null);
        }
        // if the parent is a leaf node, playout from the parent
        else {
          if (node.gameState == null) {
            node.instantiateNode(node.parent.gameState.advance(node.move));
          }
          score = Playout.playout(node.gameState, node.mrXPossibleLocationsLocal.copy(), null, null);
        }
      }

      // backpropagate return
      backpropagate(node, score);
    }
    return root;
  }

  // backpropagate return
  private void backpropagate(Node node, double score) {
    node.totalReturn += score;
    node.numVisits++;

    // for all nodes 1 levels or below
    if (node.parent != null) {
      backpropagate(node.parent, score);
    }
  }
}