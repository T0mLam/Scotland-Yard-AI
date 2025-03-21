package uk.ac.bris.cs.scotlandyard.ui.ai.models;

import com.google.common.collect.ImmutableSet;
import com.google.common.graph.ImmutableValueGraph;
import io.atlassian.fugue.Pair;
import org.junit.Before;
import org.junit.Test;
import uk.ac.bris.cs.scotlandyard.model.*;
import uk.ac.bris.cs.scotlandyard.ui.ai.MyGameStateFactory;
import uk.ac.bris.cs.scotlandyard.ui.ai.heuristic.MrXPossibleLocations;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static uk.ac.bris.cs.scotlandyard.model.Piece.Detective.*;
import static uk.ac.bris.cs.scotlandyard.model.Piece.MrX.MRX;
import static uk.ac.bris.cs.scotlandyard.model.ScotlandYard.*;
import static uk.ac.bris.cs.scotlandyard.model.ScotlandYard.defaultDetectiveTickets;

public class DetectiveOneStepLookAheadTest {
    private ImmutableValueGraph<Integer, ImmutableSet<Transport>> graph;
    private Model model;

    @Before
    public void setUp() {
        model = new DetectiveOneStepLookAhead();
        model.onStart();
        try {
            graph = standardGraph();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testPickMove1() {
        // test case 1
        MyGameStateFactory gameStateFactory = new MyGameStateFactory();
        Board.GameState board = gameStateFactory.build(
                new GameSetup(graph, STANDARD24MOVES),
                new Player(MRX, defaultMrXTickets(), 35),
                new Player(RED, defaultDetectiveTickets(), 26),
                new Player(BLUE, defaultDetectiveTickets(), 50),
                new Player(GREEN, defaultDetectiveTickets(), 94),
                new Player(WHITE, defaultDetectiveTickets(), 155),
                new Player(YELLOW, defaultDetectiveTickets(), 174)
        );
        board = board.advance(new Move.SingleMove(MRX, 35,Ticket.TAXI, 48));

        Move pickedMove = model.pickMove(board, new Pair<>(20L, TimeUnit.SECONDS));
        assertEquals(new Move.SingleMove(WHITE, 155,Ticket.TAXI, 154), pickedMove);
    }

    @Test
    public void testPickMove2() {
        // test case 1
        MyGameStateFactory gameStateFactory = new MyGameStateFactory();
        Board.GameState board = gameStateFactory.build(
                new GameSetup(graph, STANDARD24MOVES),
                new Player(MRX, defaultMrXTickets(), 190),
                new Player(RED, defaultDetectiveTickets(), 2),
                new Player(BLUE, defaultDetectiveTickets(), 10),
                new Player(GREEN, defaultDetectiveTickets(), 45),
                new Player(WHITE, defaultDetectiveTickets(), 182),
                new Player(YELLOW, defaultDetectiveTickets(), 90)
        );

        board = board.advance(new Move.SingleMove(MRX, 190,Ticket.TAXI, 189));
        Move pickedMove = model.pickMove(board, new Pair<>(20L, TimeUnit.SECONDS));
        assertEquals(new Move.SingleMove(GREEN, 45,Ticket.TAXI, 46), pickedMove);
    }
}
