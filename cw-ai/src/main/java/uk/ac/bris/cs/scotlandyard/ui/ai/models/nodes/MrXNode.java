package uk.ac.bris.cs.scotlandyard.ui.ai.models.nodes;

import uk.ac.bris.cs.scotlandyard.model.Move;
import uk.ac.bris.cs.scotlandyard.model.Piece;
import uk.ac.bris.cs.scotlandyard.ui.ai.MyGameStateFactory;
import uk.ac.bris.cs.scotlandyard.ui.ai.heuristic.Dijkstra;
import uk.ac.bris.cs.scotlandyard.ui.ai.heuristic.MrXPossibleLocations;
import uk.ac.bris.cs.scotlandyard.ui.ai.models.MoveFilter;

import java.util.*;

public class MrXNode extends Node{

  public MrXNode(MyGameStateFactory.MyGameState gameState, MrXNode parent, Move move, MrXPossibleLocations mrXPossibleLocationsLocal) {
    this.numVisits = 0;
    this.totalReturn = 0;
    this.gameState = gameState;
    this.children = new ArrayList<>();
    this.parent = parent;
    this.move = move;
    this.mrXPossibleLocationsLocal = mrXPossibleLocationsLocal;
    dijkstra = Dijkstra.getInstance();

    // execute this if gamestate is initialised now
    if (gameState != null) {
      this.isMrX = gameState.getRemaining().contains(Piece.MrX.MRX);
      if (parent != null && move.commencedBy().isMrX()) this.mrXPossibleLocationsLocal.updateLocations(this.gameState);
      else if (parent != null) this.mrXPossibleLocationsLocal.updateLocations((Move.SingleMove) move);
    }

    // reference of moving average return required for progressive history
    if (parent != null && parent.parent == null) {
      this.moveNode = this;
    } else if (parent != null) {
      this.moveNode = parent.moveNode;
    }
  }

  // if gamestate was not initialised in the constructor, do it here
  @Override
  public Node instantiateNode(MyGameStateFactory.MyGameState newGameState) {
    this.gameState = newGameState;
    this.isMrX = gameState.getRemaining().contains(Piece.MrX.MRX);
    if (move.commencedBy().isMrX()) this.mrXPossibleLocationsLocal.updateLocations(this.gameState);
    else this.mrXPossibleLocationsLocal.updateLocations((Move.SingleMove) move);
    return this;
  }

  // add the children to the parent
  @Override
  public void expand() {
    List<Move> moves = new ArrayList<Move>(gameState.getAvailableMoves());

    if (moves.isEmpty()) return;

    Map<Piece, Integer> shortestPath = dijkstra.shortestPath(gameState);

    // apply detective filters if detective moves
    List<Move> filteredMoves = isMrX ?
      MoveFilter.applyMrXFilters(moves, gameState, Collections.min(shortestPath.values())) :
      MoveFilter.applyDetectiveFiltersWithoutOrdering(moves, shortestPath);

    for (Move move : filteredMoves) {
      children.add(new MrXNode(null, this, move, this.mrXPossibleLocationsLocal.copy()));
    }
  }

  // for a given node, return the best child node
  @Override
  public Node selectBestChild(double C, double W) {
    // leaf node
    if (children.isEmpty()) return this;

    // fully expanded node 1 layer down the tree
    Node nextNode = children.stream()
      .max(Comparator.comparingDouble(n -> n.UCT(C, W)))
      .get();

    // instantiate the node
    if (nextNode.gameState == null) nextNode.instantiateNode(nextNode.parent.gameState.advance(nextNode.move));
    return nextNode.selectBestChild(C, W);
  }
}
