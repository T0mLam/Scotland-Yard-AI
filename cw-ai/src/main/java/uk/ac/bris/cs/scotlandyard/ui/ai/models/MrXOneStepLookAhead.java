package uk.ac.bris.cs.scotlandyard.ui.ai.models;

import com.google.common.collect.ImmutableList;
import io.atlassian.fugue.Pair;
import uk.ac.bris.cs.scotlandyard.model.Board;
import uk.ac.bris.cs.scotlandyard.model.Move;
import uk.ac.bris.cs.scotlandyard.model.Piece;
import uk.ac.bris.cs.scotlandyard.ui.ai.MyGameStateFactory;
import uk.ac.bris.cs.scotlandyard.ui.ai.MyGameStateFactory.*;
import uk.ac.bris.cs.scotlandyard.ui.ai.heuristic.Dijkstra;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static uk.ac.bris.cs.scotlandyard.model.ScotlandYard.standardGraph;

public class MrXOneStepLookAhead extends Model {
    private final MyGameStateFactory myGameStateFactory = new MyGameStateFactory();
    private Dijkstra dijkstra;

    @Override
    public void onStart() {
        try {
            dijkstra = Dijkstra.getInstance(standardGraph());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Nonnull
    @Override
    public Move pickMove(@Nonnull Board board, Pair<Long, TimeUnit> timeoutPair) {
        ImmutableList<Move> moves = board.getAvailableMoves().asList();
        MyGameState gameState = myGameStateFactory.buildFromBoard(board, moves.get(0).source());

        int maximumDistance = Integer.MIN_VALUE;
        Map<Piece, Integer> shortestPath = dijkstra.shortestPath(gameState);
        List<Move> filteredMoves = MoveFilter.applyMrXFilters(moves, gameState, Collections.min(shortestPath.values()));
        Move bestMove = moves.get(0);

        // Find the move that maximises the distance to the closest detective
        for (Move move : filteredMoves) {
            MyGameState newGameState = gameState.advance(move);

            // Find the closest detective after the move
            shortestPath = dijkstra.shortestPath(newGameState);
            int closestDistance = Collections.min(shortestPath.values());

            // Maximise the distance to the closest detective
            if (closestDistance > maximumDistance) {
                maximumDistance = closestDistance;
                bestMove = move;
            }
        }
        return bestMove;
    }
}
