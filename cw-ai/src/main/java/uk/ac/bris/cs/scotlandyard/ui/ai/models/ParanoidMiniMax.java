package uk.ac.bris.cs.scotlandyard.ui.ai.models;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.atlassian.fugue.Pair;
import uk.ac.bris.cs.scotlandyard.model.*;
import uk.ac.bris.cs.scotlandyard.ui.ai.MyGameStateFactory;
import uk.ac.bris.cs.scotlandyard.ui.ai.MyGameStateFactory.*;
import uk.ac.bris.cs.scotlandyard.ui.ai.heuristic.Dijkstra;
import uk.ac.bris.cs.scotlandyard.ui.ai.heuristic.MrXEval;
import uk.ac.bris.cs.scotlandyard.ui.ai.heuristic.MrXPossibleLocations;
import uk.ac.bris.cs.scotlandyard.ui.ai.heuristic.Evaluator;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static uk.ac.bris.cs.scotlandyard.model.ScotlandYard.standardGraph;

public class ParanoidMiniMax extends Model {
    private final int DEPTH = 6; // Depth of the minimax search
    private Dijkstra dijkstra;
    private final Evaluator evaluator = new MrXEval(); // Evaluator for MrX
    private long deadline; // Deadline for the search
    private final long buffer = 4000; // Buffer time to stop the search before the actual deadline
    private MrXPossibleLocations mrXPossibleLocations;

    @Override
    public void onStart() {
        mrXPossibleLocations = new MrXPossibleLocations();
        try {
            dijkstra = Dijkstra.getInstance(standardGraph());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // Minimax algorithm with alpha-beta pruning
    private float minimax(MyGameState gameState, int depth, boolean isMaximising, float alpha, float beta, MrXPossibleLocations mrXPossibleLocations) {
        // Check if the deadline is near
        if (System.currentTimeMillis() > deadline - buffer) {
            return isMaximising ? -10000 : 10000;
        }

        ImmutableList<Move> moves = gameState.getAvailableMoves().asList();

        // Check for terminal states
        ImmutableSet<Piece> winners = gameState.getWinner();
        if (winners.contains(Piece.MrX.MRX)) {
            return 10000;
        }
        else if (!winners.isEmpty()) {
            return -10000;
        }
        else if (depth == 0) {
            return evaluator.evaluateNode(gameState, mrXPossibleLocations);
        }

        float backupValue = isMaximising ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY;

        // Use dijkstra to get closest detective, then only consider the closest detective's moves
        Map<Piece, Integer> shortestPath = dijkstra.shortestPath(gameState);
        int minDistanceToDetectives = Collections.min(shortestPath.values());

        List<Move> filteredMoves = isMaximising ?
                MoveFilter.applyParanoidMrXFilters(moves, gameState, minDistanceToDetectives) :
                MoveFilter.applyDetectiveFiltersWithoutOrdering(moves, shortestPath);

        // Recursively apply minimax to each move
        for (Move move : filteredMoves) {
            MyGameState newGameState = gameState.advance(move);
            MrXPossibleLocations newMrXPossibleLocations = mrXPossibleLocations.copy();

            // Update Mr X possible locations
            if (isMaximising) {
                newMrXPossibleLocations.updateLocations(newGameState);
            }
            else {
                newMrXPossibleLocations.updateLocations((Move.SingleMove) move);
            }

            // Recursive call
            float tempValue = minimax(
                    newGameState,
                    depth - 1,
                    newGameState.getRemaining().contains(Piece.MrX.MRX),
                    alpha,
                    beta,
                    newMrXPossibleLocations
            );

            // Update backup value
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

        mrXPossibleLocations.updateLocations(board);

        ImmutableList<Move> moves = board.getAvailableMoves().asList();
        MyGameState gameState = new MyGameStateFactory().buildFromBoard(board, moves.get(0).source());

        // Apply filters to Mr X's available moves
        int minDistanceToDetectives = Collections.min(dijkstra.shortestPath(gameState).values());
        List<Move> filteredMoves = MoveFilter.applyParanoidMrXFilters(moves, gameState, minDistanceToDetectives);

        // Evaluate each move using minimax
        List<Pair<Float, Move>> scores = filteredMoves.parallelStream()
          .map(move -> {
              if (System.currentTimeMillis() > deadline - buffer) return new Pair<>(Float.NEGATIVE_INFINITY, move);

              MyGameState newGameState = gameState.advance(move);
              MrXPossibleLocations newMrXPossibleLocations = mrXPossibleLocations.copy();
              newMrXPossibleLocations.updateLocations(newGameState);
              float tempScore = minimax(newGameState, DEPTH - 1, false, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, newMrXPossibleLocations);
              return new Pair<>(tempScore, move);
          })
          .toList();

        // Return the move with the highest score
        return scores
          .stream()
          .max(Comparator.comparingDouble(Pair::left))
          .orElse(new Pair<>(Float.NEGATIVE_INFINITY, moves.get(0)))
          .right();
    }
}