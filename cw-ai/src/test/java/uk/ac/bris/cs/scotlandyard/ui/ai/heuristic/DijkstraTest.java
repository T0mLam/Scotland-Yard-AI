package uk.ac.bris.cs.scotlandyard.ui.ai.heuristic;

import com.google.common.collect.ImmutableSet;
import com.google.common.graph.ImmutableValueGraph;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import uk.ac.bris.cs.scotlandyard.model.*;
import uk.ac.bris.cs.scotlandyard.ui.ai.MyGameStateFactory;
import uk.ac.bris.cs.scotlandyard.ui.ai.MyGameStateFactory.*;

import java.io.IOException;
import java.util.Map;

import static uk.ac.bris.cs.scotlandyard.model.Piece.Detective.*;
import static uk.ac.bris.cs.scotlandyard.model.Piece.MrX.MRX;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static uk.ac.bris.cs.scotlandyard.model.ScotlandYard.*;
import static uk.ac.bris.cs.scotlandyard.model.ScotlandYard.MRX_LOCATIONS;

public class DijkstraTest {
    private ImmutableValueGraph<Integer, ImmutableSet<ScotlandYard.Transport>> graph;
    private Dijkstra dijkstra;
    private final MyGameStateFactory gameStateFactory = new MyGameStateFactory();

    @Before
    public void setUp() {
        try {
            graph = standardGraph();
            dijkstra = Dijkstra.getInstance(graph);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testGetDistance() {
        assertEquals(0, dijkstra.getDistance(1, 1));
        assertEquals(1, dijkstra.getDistance(79, 111));
        assertEquals(1, dijkstra.getDistance(157, 194));
        assertEquals(2, dijkstra.getDistance(29, 54));
        assertEquals(2, dijkstra.getDistance(88, 128));
        // TODO: add more test cases with longer distance
    }

    @Test
    public void testShortestPath() {
        // test case 1
        Board.GameState board = gameStateFactory.build(
                new GameSetup(graph, STANDARD24MOVES),
                new Player(MRX, defaultMrXTickets(), 35),
                new Player(RED, defaultDetectiveTickets(), 26),
                new Player(BLUE, defaultDetectiveTickets(), 50),
                new Player(GREEN, defaultDetectiveTickets(), 94),
                new Player(WHITE, defaultDetectiveTickets(), 155),
                new Player(YELLOW, defaultDetectiveTickets(), 174)
        );
        MyGameState myGameState = gameStateFactory.buildFromBoard(board, 35);

        Map<Piece, Integer> expectedShortestPath = Map.of(
                RED, 5, BLUE, 3, GREEN, 5, WHITE, 5, YELLOW, 6
        );

        assertEquals(expectedShortestPath, dijkstra.shortestPath(myGameState));
        assertEquals(expectedShortestPath, dijkstra.shortestPath(myGameState, 35));

        // test case 2
        board = gameStateFactory.build(
                new GameSetup(graph, STANDARD24MOVES),
                new Player(MRX, defaultMrXTickets(), 190),
                new Player(RED, defaultDetectiveTickets(), 2),
                new Player(BLUE, defaultDetectiveTickets(), 10),
                new Player(GREEN, defaultDetectiveTickets(), 45),
                new Player(WHITE, defaultDetectiveTickets(), 182),
                new Player(YELLOW, defaultDetectiveTickets(), 90)
        );
        myGameState = gameStateFactory.buildFromBoard(board, 190);

        expectedShortestPath = Map.of(
                RED, 8, BLUE, 7, GREEN, 6, WHITE, 3, YELLOW, 6
        );

        assertEquals(expectedShortestPath, dijkstra.shortestPath(myGameState));
        assertEquals(expectedShortestPath, dijkstra.shortestPath(myGameState, 190));
    }
}
