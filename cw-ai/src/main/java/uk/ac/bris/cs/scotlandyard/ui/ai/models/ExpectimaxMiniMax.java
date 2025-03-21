package uk.ac.bris.cs.scotlandyard.ui.ai.models;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.atlassian.fugue.Pair;
import uk.ac.bris.cs.scotlandyard.model.Board;
import uk.ac.bris.cs.scotlandyard.model.Move;
import uk.ac.bris.cs.scotlandyard.model.Piece;
import uk.ac.bris.cs.scotlandyard.ui.ai.MyGameStateFactory;
import uk.ac.bris.cs.scotlandyard.ui.ai.MyGameStateFactory.*;
import uk.ac.bris.cs.scotlandyard.ui.ai.heuristic.DetectiveEval;
import uk.ac.bris.cs.scotlandyard.ui.ai.heuristic.Dijkstra;
import uk.ac.bris.cs.scotlandyard.ui.ai.heuristic.MrXPossibleLocations;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static uk.ac.bris.cs.scotlandyard.model.ScotlandYard.standardGraph;

public class ExpectimaxMiniMax extends Model {
  private final int DEPTH = 5; // Depth of the expectimax search
  private Dijkstra dijkstra;
  private final DetectiveEval evaluator = new DetectiveEval(); // Evaluator for detectives
  private long deadline; // Deadline for the search
  private final long buffer = 2500; // Buffer time to stop the search before the actual deadline
  private MrXPossibleLocations mrXPossibleLocations;
  private final MyGameStateFactory gameStateFactory = new MyGameStateFactory();

  @Override
  public void onStart() {
    mrXPossibleLocations = new MrXPossibleLocations();
    try {
      dijkstra = Dijkstra.getInstance(standardGraph());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  // Expectimax algorithm with alpha-beta pruning
  private float minimax(
    MyGameState gameState,
    int depth,
    boolean isMaximising,
    float alpha,
    float beta,
    MrXPossibleLocations mrXPossibleLocations
  ) {
    // Check if the deadline is near
    if (System.currentTimeMillis() > deadline - buffer) {
      return isMaximising ? -10000 : 10000;
    }

    ImmutableList<Move> moves = gameState.getAvailableMoves().asList();

    // Check for terminal states
    ImmutableSet<Piece> winners = gameState.getWinner();
    if (winners.contains(Piece.MrX.MRX)) {
      return -10000;
    }
    else if (!winners.isEmpty()) {
      return 10000;
    }
    else if (depth == 0) {
      return evaluator.evaluateNode(gameState, mrXPossibleLocations);
    }

    float backupValue = isMaximising ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY;

    // Use dijkstra to get closest detective, then only consider the closest detective's moves
    Map<Piece, Integer> shortestPath = dijkstra.shortestPath(gameState);
    int minDistanceToDetectives = Collections.min(shortestPath.values());

    List<Move> filteredMoves = isMaximising ?
            MoveFilter.applyDetectiveFilters(moves) :
            MoveFilter.applyMrXFilters(moves, gameState, minDistanceToDetectives);

    // Recursively apply expectimax to each move
    for (Move move : filteredMoves) {
      MyGameState newGameState = gameState.advance(move);
      MrXPossibleLocations newMrXPossibleLocations = mrXPossibleLocations.copy();
//
//      if (isMaximising) {
//        newMrXPossibleLocations.updateLocations((Move.SingleMove) move);
//      }
//      else {
//        newMrXPossibleLocations.updateLocations(newGameState);
//      }

      // recursive minimax call
      float tempValue = minimax(
              newGameState,
              depth - 1,
              !isMaximising,
              alpha,
              beta,
              newMrXPossibleLocations
      );

      // update the backup value
      if (isMaximising) {
        backupValue = Math.max(backupValue, tempValue);
        alpha = Math.max(alpha, tempValue);
      }
      else {
        backupValue = Math.min(tempValue, backupValue);
        beta = Math.min(beta, tempValue);
      }

      if (beta <= alpha) break;
    }

    return backupValue;
  }

  @Nonnull @Override public Move pickMove(
    @Nonnull Board board,
    Pair<Long, TimeUnit> timeoutPair) {
    long timeDuration = timeoutPair.right().toMillis(timeoutPair.left());
    deadline = System.currentTimeMillis() + timeDuration;

    ImmutableList<Move> moves = board.getAvailableMoves().asList();

    // use the first move as the target detective
    Piece targetDetective = moves.get(0).commencedBy();

    mrXPossibleLocations.updateLocations(board);

    // Use a filter to filter out all moves that move to another detective,
    Set<Integer> otherDetectiveLocations = board.getPlayers()
      .stream()
      .filter(piece -> !piece.isMrX() && !piece.equals(targetDetective))
      .map(piece -> board.getDetectiveLocation((Piece.Detective) piece).get())
      .collect(Collectors.toSet());

    List<Move> filteredMoves = MoveFilter.applyDetectiveFilters(moves);

    // Evaluate each move using expectimax
    Move bestMove = filteredMoves.parallelStream()
      // Use filter to make sure the first move of the target detective does not land on other detectives
      .filter(move -> !otherDetectiveLocations.contains(MoveFilter.getMoveDestination(move)))
      // Use filter to only allow moves that are commenced by the target detective
      .filter(move -> move.commencedBy().equals(targetDetective))
      .map(move -> {
        if (System.currentTimeMillis() > deadline - buffer) return new Pair<>(Float.NEGATIVE_INFINITY, move);

        // Shuffle the possible locations and sample the first 6
        List<Integer> shuffledLocations = new ArrayList<>(mrXPossibleLocations.getLocations());
        Collections.shuffle(shuffledLocations);

        // Find the total score for each possible move in every mrx location
        float totalScore = shuffledLocations.parallelStream()
                .limit(5)
          .map(mrXPossibleLocation -> {
            MyGameState gameState = gameStateFactory.buildFromBoard(
              board, mrXPossibleLocation, targetDetective
            ).advance(move);
            MrXPossibleLocations newMrXPossibleLocations = mrXPossibleLocations.copy();
            newMrXPossibleLocations.updateLocations((Move.SingleMove) move);
            float score = minimax(
                    gameState,
                    DEPTH - 1,
                    false,
                    Float.NEGATIVE_INFINITY,
                    Float.POSITIVE_INFINITY,
                    newMrXPossibleLocations
            );
            return score;
          })
          .reduce(0f, Float::sum);
        return new Pair<>(totalScore, move);
      })
            // Pick the move with the largest total score
      .max(Comparator.comparingDouble(Pair::left))
      .orElse(new Pair<>(Float.NEGATIVE_INFINITY, moves.get(0)))
      .right();

    return bestMove;
  }
}