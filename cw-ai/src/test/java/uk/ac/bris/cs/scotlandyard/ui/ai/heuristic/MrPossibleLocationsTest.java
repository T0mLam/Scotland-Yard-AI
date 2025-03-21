package uk.ac.bris.cs.scotlandyard.ui.ai.heuristic;

import com.google.common.collect.ImmutableSet;
import com.google.common.graph.ImmutableValueGraph;
import org.junit.Before;
import org.junit.Test;
import uk.ac.bris.cs.scotlandyard.model.*;
import uk.ac.bris.cs.scotlandyard.model.Move.*;
import uk.ac.bris.cs.scotlandyard.ui.ai.MyGameStateFactory;
import uk.ac.bris.cs.scotlandyard.ui.ai.MyGameStateFactory.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static uk.ac.bris.cs.scotlandyard.model.Piece.Detective.*;
import static uk.ac.bris.cs.scotlandyard.model.Piece.MrX.MRX;
import static uk.ac.bris.cs.scotlandyard.model.ScotlandYard.*;
import static uk.ac.bris.cs.scotlandyard.model.ScotlandYard.defaultDetectiveTickets;


public class MrPossibleLocationsTest {
    private ImmutableValueGraph<Integer, ImmutableSet<Transport>> graph;
    private MrXPossibleLocations mrXPossibleLocations;
    private Set<Integer> expectedLocations;

    @Before
    public void setUp() {
        mrXPossibleLocations = new MrXPossibleLocations();
        try {
            graph = standardGraph();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    @Test
    public void testUpdateLocationsWithMove() {
        SingleMove move = new SingleMove(Piece.Detective.RED, 1, ScotlandYard.Ticket.BUS, 35);
        mrXPossibleLocations.updateLocations(move);
        expectedLocations = new HashSet<>(Arrays.asList(45, 51, 71, 78, 104, 106, 127, 132, 166, 170, 172));
        assertEquals(mrXPossibleLocations.getLocations(), expectedLocations);

        move = new SingleMove(Piece.Detective.RED, 35, ScotlandYard.Ticket.BUS, 42);
        mrXPossibleLocations.updateLocations(move);
        assertEquals(mrXPossibleLocations.getLocations(), expectedLocations);

        move = new SingleMove(Piece.Detective.RED, 42, ScotlandYard.Ticket.TAXI, 170);
        mrXPossibleLocations.updateLocations(move);
        expectedLocations.remove(170);
        assertEquals(mrXPossibleLocations.getLocations(), expectedLocations);
    }

    @Test
    public void testUpdateLocationsWithGameState() {
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
        MyGameState myGameState = gameStateFactory.buildFromBoard(board, 35);

        MyGameState newGameState = myGameState.advance(new SingleMove(Piece.MrX.MRX, 35, Ticket.TAXI, 36));
        mrXPossibleLocations.updateLocations(newGameState);
        assertEquals(
                mrXPossibleLocations.getLocations(),
                new HashSet<>(Arrays.asList(
                        128, 65, 67, 68, 133, 70, 134, 72, 140, 77, 79, 22, 86, 151, 153,
                        89, 157, 159, 32, 97, 36, 38, 39, 105, 107, 46, 48,
                        114, 115, 52, 116, 181, 183, 55, 185, 58, 187, 59, 60, 61, 126)
                )
        );

        // test case 2
        SingleMove newMove = new SingleMove(Piece.Detective.RED, 26, Ticket.TAXI, 27);
        newGameState = newGameState.advance(newMove);
        mrXPossibleLocations.updateLocations(newMove);
        newMove = new SingleMove(Piece.Detective.BLUE, 50, Ticket.TAXI, 37);
        newGameState = newGameState.advance(newMove);
        mrXPossibleLocations.updateLocations(newMove);
        newMove = new SingleMove(Piece.Detective.GREEN, 94, Ticket.BUS, 74);
        newGameState = newGameState.advance(newMove);
        mrXPossibleLocations.updateLocations(newMove);
        newMove = new SingleMove(Piece.Detective.WHITE, 155, Ticket.TAXI, 156);
        newGameState = newGameState.advance(newMove);
        mrXPossibleLocations.updateLocations(newMove);
        newMove = new SingleMove(Piece.Detective.YELLOW, 174, Ticket.TAXI, 175);
        newGameState = newGameState.advance(newMove);
        mrXPossibleLocations.updateLocations(newMove);

        newGameState = newGameState.advance(new SingleMove(Piece.MrX.MRX, 36, Ticket.TAXI, 49));
        mrXPossibleLocations.updateLocations(newGameState);
        assertTrue(
                mrXPossibleLocations.getLocations().containsAll(
                new HashSet<>(Arrays.asList(
                        131, 132, 133, 139, 11, 140, 141, 142, 143, 19,
                        150, 23, 152, 24, 25, 154, 26, 158, 160, 33, 34,
                        35, 165, 166, 167, 39, 40, 42, 170, 172, 44, 45
                )))
        );
    }
}
