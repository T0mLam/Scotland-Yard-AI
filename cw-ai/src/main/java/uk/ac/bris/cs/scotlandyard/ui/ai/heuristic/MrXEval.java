package uk.ac.bris.cs.scotlandyard.ui.ai.heuristic;

import uk.ac.bris.cs.scotlandyard.model.Piece;
import uk.ac.bris.cs.scotlandyard.model.ScotlandYard.*;
import uk.ac.bris.cs.scotlandyard.ui.ai.MyGameStateFactory.*;

import java.util.Collections;
import java.util.Map;
import java.util.Random;


public class MrXEval implements Evaluator {
    private Dijkstra dijkstra;
    Random rand = new Random();

    @Override
    public float evaluateNode(MyGameState gameState, MrXPossibleLocations mrXPossibleLocations) {
        dijkstra = Dijkstra.getInstance();
        Map<Piece, Integer> shortestPath = dijkstra.shortestPath(gameState);
        int mcd = Collections.min(shortestPath.values());
        int blackTicketCount = gameState.getMrX().tickets().get(Ticket.SECRET);
        return 100 * mcd + 10 * blackTicketCount + mrXPossibleLocations.getLocations().size() + rand.nextFloat();
    }
}
