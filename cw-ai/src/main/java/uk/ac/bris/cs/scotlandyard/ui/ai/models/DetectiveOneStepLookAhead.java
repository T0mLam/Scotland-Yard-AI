package uk.ac.bris.cs.scotlandyard.ui.ai.models;

import com.google.common.collect.ImmutableList;
import io.atlassian.fugue.Pair;
import uk.ac.bris.cs.scotlandyard.model.Board;
import uk.ac.bris.cs.scotlandyard.model.Move;
import uk.ac.bris.cs.scotlandyard.model.Move.*;
import uk.ac.bris.cs.scotlandyard.ui.ai.MyGameStateFactory;
import uk.ac.bris.cs.scotlandyard.ui.ai.MyGameStateFactory.MyGameState;
import uk.ac.bris.cs.scotlandyard.ui.ai.heuristic.Dijkstra;
import uk.ac.bris.cs.scotlandyard.ui.ai.heuristic.MrXPossibleLocations;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static uk.ac.bris.cs.scotlandyard.model.ScotlandYard.standardGraph;

public class DetectiveOneStepLookAhead extends Model {
    private final MyGameStateFactory myGameStateFactory = new MyGameStateFactory();
    private Dijkstra dijkstra;
    private MrXPossibleLocations mrXPossibleLocations = new MrXPossibleLocations();

    @Override
    public void onStart() {
        mrXPossibleLocations = new MrXPossibleLocations();
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

        mrXPossibleLocations.updateLocations(gameState);
        List<Move> filteredMoves = MoveFilter.applyDetectiveFilters(moves);

        int lowestDistance = Integer.MAX_VALUE;
        Move bestMove = moves.get(0);

        // Find the move that minimises the sum of the distances to all possible MrX locations
        for (Move move : filteredMoves) {
            Set<Integer> locations = mrXPossibleLocations.getLocations();

            int totalDistance = 0;
            // Calculate the total distance to all possible MrX locations
            for (Integer location : locations) {
                totalDistance += dijkstra.getDistance(((SingleMove) move).destination, location);
            }

            // Choose the move that minimises the total distance
            if (totalDistance < lowestDistance) {
                lowestDistance = totalDistance;
                bestMove = move;
            }
        }
        return bestMove;
    }
}
