package uk.ac.bris.cs.scotlandyard.ui.ai.models;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import uk.ac.bris.cs.scotlandyard.model.Move;
import uk.ac.bris.cs.scotlandyard.model.Piece;
import uk.ac.bris.cs.scotlandyard.ui.ai.MyGameStateFactory;
import uk.ac.bris.cs.scotlandyard.ui.ai.heuristic.Dijkstra;
import uk.ac.bris.cs.scotlandyard.ui.ai.heuristic.MrXPossibleLocations;

import java.util.*;

public class Playout {
  public static double hiderEpsilon = 0.1;
  public static double seekerEpsilon = 0.2;
  public static double r = 0.3;

  public static double playout(
    MyGameStateFactory.MyGameState gameState,
    MrXPossibleLocations mrXPossibleLocationsPlayout,
    Piece currentPiece,
    Piece targetPiece
    ) {
    Random random = new Random();
    Dijkstra dijkstra = Dijkstra.getInstance();

    ImmutableList<Move> moves = gameState.getAvailableMoves().asList();
    ImmutableSet<Piece> winners = gameState.getWinner();

    // calculate and return reward
    if (winners.contains(Piece.MrX.MRX)) {
      return 1.0;
    }
    else if (!winners.isEmpty()) {
      if (targetPiece == null || currentPiece == targetPiece) return 0.0;
      return r;
    } else if (moves.isEmpty()) {
      return gameState.getRemaining().contains(Piece.MrX.MRX) ? 0.0 : 1.0;
    }

    MyGameStateFactory.MyGameState newGameState = gameState.advance(moves.get(random.nextInt(moves.size())));
    Move bestMove = null;
    List<Move> validMoves;
    double epsilon;

    // filter out Mr X's invalid moves
    if (moves.get(0).commencedBy().isMrX()) {
      Map<Piece, Integer> shortestPath = dijkstra.shortestPath(gameState);
      validMoves = MoveFilter.applyMrXFilters(moves, gameState, Collections.min(shortestPath.values()));
      epsilon = hiderEpsilon;
    }
    else {
      validMoves = MoveFilter.applyMTCSDetectiveFilters(moves, mrXPossibleLocationsPlayout.copy());
      epsilon = seekerEpsilon;
    }

    // random move with probability epsilon
    if (random.nextDouble() < epsilon && !validMoves.isEmpty()) {
      int moveIndex = random.nextInt(validMoves.size());
      Move move = validMoves.get(moveIndex);
      newGameState = gameState.advance(move);
      if (moves.get(0).commencedBy().isDetective()) bestMove = move;
    }
    // minimise Mr X closest distance
    else if (moves.get(0).commencedBy().isMrX()) {
      int maxDistance = Integer.MIN_VALUE;
      for (Move move : validMoves) {
        MyGameStateFactory.MyGameState tempGameState = gameState.advance(move);
        int currClosestDist = Collections.min(dijkstra.shortestPath(tempGameState).values());
        // choose the move that maximize the minimum distance between every detective and mrX
        if (currClosestDist > maxDistance) {
          maxDistance = currClosestDist;
          newGameState = tempGameState;
        }
      }
    }
    // minimise total distance to Mr X
    else if (moves.get(0).commencedBy().isDetective()) {
      // for each move, measure sum the total distances to all possible locations of MRX
      int lowestDistance = Integer.MAX_VALUE;

      for (Move move : validMoves) {
        Set<Integer> locations = mrXPossibleLocationsPlayout.getLocations();

        int totalDistance = 0;
        for (Integer location : locations) {
          totalDistance += dijkstra.getDistance(((Move.SingleMove) move).destination, location);
        }

        // choose the move with the largest sum
        if (totalDistance < lowestDistance) {
          lowestDistance = totalDistance;
          newGameState = gameState.advance(move);
          bestMove = move;
        }
      }
    }
    // update MrX Locations
    if (moves.get(0).commencedBy().isMrX()) {
      mrXPossibleLocationsPlayout.updateLocations(newGameState);
    } else {
      mrXPossibleLocationsPlayout.updateLocations((Move.SingleMove) bestMove);
      currentPiece = bestMove.commencedBy();
    }
    return playout(newGameState, mrXPossibleLocationsPlayout, currentPiece, targetPiece);
  }
}
