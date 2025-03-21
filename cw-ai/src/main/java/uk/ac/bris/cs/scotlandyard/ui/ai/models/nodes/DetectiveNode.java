package uk.ac.bris.cs.scotlandyard.ui.ai.models.nodes;

import uk.ac.bris.cs.scotlandyard.model.Move;
import uk.ac.bris.cs.scotlandyard.model.Piece;
import uk.ac.bris.cs.scotlandyard.ui.ai.MyGameStateFactory;
import uk.ac.bris.cs.scotlandyard.ui.ai.MyGameStateFactory.MyGameState;
import uk.ac.bris.cs.scotlandyard.ui.ai.heuristic.Dijkstra;
import uk.ac.bris.cs.scotlandyard.ui.ai.heuristic.MrXPossibleLocations;
import uk.ac.bris.cs.scotlandyard.ui.ai.models.MoveFilter;

import java.util.*;

public class DetectiveNode extends Node {
  public boolean firstHiderPly;
  public Map<Move, MyGameState> moveMap;
  private final MyGameStateFactory myGameStateFactory;
  private final boolean singleTree = true;

  public DetectiveNode(
    MyGameStateFactory myGameStateFactory,
    MyGameStateFactory.MyGameState gameState,
    Node parent,
    Move move,
    MrXPossibleLocations mrXPossibleLocationsLocal
  ) {
    this.numVisits = 0;
    this.totalReturn = 0;
    this.gameState = gameState;
    this.children = new ArrayList<>();
    this.parent = parent;
    this.move = move;
    this.mrXPossibleLocationsLocal = mrXPossibleLocationsLocal;
    this.myGameStateFactory = myGameStateFactory;
    this.dijkstra = Dijkstra.getInstance();

    // execute this if gamestate is already created
    if (gameState != null) {
      this.isMrX = gameState.getRemaining().contains(Piece.MrX.MRX);
      if (parent != null && move.commencedBy().isMrX()) this.mrXPossibleLocationsLocal.updateLocations(this.gameState);
      else if (parent != null) this.mrXPossibleLocationsLocal.updateLocations((Move.SingleMove) move);
      this.firstHiderPly = parent == null || (((DetectiveNode) parent).firstHiderPly && !parent.isMrX);
    }

    // reference of move average return required for progressive history
    if (parent != null && parent.parent == null) {
      this.moveNode = this;
    } else if (parent != null) {
      this.moveNode = parent.moveNode;
    }
  }

  // if gamestate was not initialised in the constructor, do it here
  @Override
  public Node instantiateNode(MyGameState newGameState) {
    this.gameState = newGameState;
    this.isMrX = gameState.getRemaining().contains(Piece.MrX.MRX);
    if (move.commencedBy().isMrX()) this.mrXPossibleLocationsLocal.updateLocations(this.gameState);
    else this.mrXPossibleLocationsLocal.updateLocations((Move.SingleMove) move);
    this.firstHiderPly = ((DetectiveNode) parent).firstHiderPly && !parent.isMrX;
    return this;
  }

  // add the parent's children
  @Override
  public void expand() {

    // if a determinisation is required, generate all possible moves
    if (isMrX && firstHiderPly && singleTree) {

      // mapping between the gamestate and the available moves - used if determinisation required
      this.moveMap = new HashMap<>();
      for (int location : mrXPossibleLocationsLocal.getLocations()) {

        // get the gamestate and moves corresponding to a specific determinisation
        MyGameState candidateGameState = myGameStateFactory.buildFromBoard(gameState, location);
        List<Move> candidateMoves = new ArrayList<>(candidateGameState.getAvailableMoves());

        Map<Piece, Integer> shortestPath = dijkstra.shortestPath(gameState);

        List<Move> filteredMoves = isMrX ?
          MoveFilter.applyMrXFilters(candidateMoves, candidateGameState, Collections.min(shortestPath.values())) :
          MoveFilter.applyMTCSDetectiveFilters(candidateMoves, this.mrXPossibleLocationsLocal.copy());

        for (Move move : filteredMoves) {
          moveMap.put(move, candidateGameState);
        }
      }

      if (moveMap.isEmpty()) return;

      for (Move move : moveMap.keySet()) {
        children.add(new DetectiveNode(myGameStateFactory, null, this, move, this.mrXPossibleLocationsLocal.copy()));
      }
    }

    // otherwise add available moves from the current gamestate
    else {

      // set of all available moves - used if no determinisation required
      List<Move> moves = new ArrayList<>(gameState.getAvailableMoves());

      if (moves.isEmpty()) return;

      Map<Piece, Integer> shortestPath = dijkstra.shortestPath(gameState);

      List<Move> filteredMoves = isMrX ?
        MoveFilter.applyMrXFilters(moves, gameState, Collections.min(shortestPath.values())) :
        MoveFilter.applyMTCSDetectiveFilters(moves, this.mrXPossibleLocationsLocal.copy());

      for (Move move : filteredMoves) {
        children.add(new DetectiveNode(myGameStateFactory, null, this, move, this.mrXPossibleLocationsLocal.copy()));
      }
    }
  }

  // for a given node, return the best child node
  @Override
  public Node selectBestChild(double C, double W) {
    // leaf node
    if (children.isEmpty()) {
      return this;
    }

    // must be a fully expanded node 1 layer down in the tree
    Node nextNode;

    // if a determinisation is required (only once)
    if (isMrX && firstHiderPly && singleTree) {
      final int mrXLocation = mrXPossibleLocationsLocal.getRandomLocation();
      nextNode = children
        .stream()
        // filter on the move being consistent with the determinisation
        .filter(node -> move.source() == mrXLocation)
        // instantiate the required nodes
        .max(Comparator.comparingDouble(n -> n.UCT(C, W)))
        .orElse(this);

      // get instantiate the node with the corresponding gamestate and then advance with that move
      if (nextNode.gameState == null) nextNode.instantiateNode(moveMap.get(nextNode.move).advance(nextNode.move));

      // return this if no available moves from the determinised gamestate (leaf node)
      if (nextNode == this) return this;
    }
    // if no determinisation is required
    else {
      nextNode = children.stream()
        .max(Comparator.comparingDouble(n -> n.UCT(C, W)))
        .get();

      // get instantiate the node
      if (nextNode.gameState == null) nextNode.instantiateNode(nextNode.parent.gameState.advance(nextNode.move));
    }
    return nextNode.selectBestChild(C, W);
  }
}
