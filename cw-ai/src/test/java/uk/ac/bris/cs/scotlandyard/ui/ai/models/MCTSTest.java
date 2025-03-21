package uk.ac.bris.cs.scotlandyard.ui.ai.models;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.graph.ImmutableValueGraph;
import org.junit.Test;
import static org.junit.jupiter.api.Assertions.*;

import uk.ac.bris.cs.scotlandyard.model.*;
import uk.ac.bris.cs.scotlandyard.ui.ai.MyGameStateFactory;
import uk.ac.bris.cs.scotlandyard.ui.ai.MyGameStateFactory.*;
import uk.ac.bris.cs.scotlandyard.ui.ai.heuristic.Dijkstra;
import uk.ac.bris.cs.scotlandyard.ui.ai.heuristic.MrXPossibleLocations;
import uk.ac.bris.cs.scotlandyard.ui.ai.models.nodes.MrXNode;
import uk.ac.bris.cs.scotlandyard.ui.ai.models.nodes.*;

import java.io.IOException;
import java.util.*;
import java.util.stream.IntStream;

import static uk.ac.bris.cs.scotlandyard.model.Piece.Detective.*;
import static uk.ac.bris.cs.scotlandyard.model.Piece.MrX.MRX;
import static uk.ac.bris.cs.scotlandyard.model.ScotlandYard.*;
import static uk.ac.bris.cs.scotlandyard.model.ScotlandYard.MRX_LOCATIONS;

public class MCTSTest {
  MyGameStateFactory gameStateFactory = new MyGameStateFactory();
  Random random = new Random();
  private final List<Piece> detectivePieces = new ArrayList<>(DETECTIVES);
  MrXPossibleLocations mrXPossibleLocations = new MrXPossibleLocations();
  private ImmutableValueGraph<Integer, ImmutableSet<ScotlandYard.Transport>> graph;

  private ImmutableList<Player> generateRandomDetectives() {
    int detectiveCount = detectivePieces.size();

    // sample random detective starting locations
    List<Integer> detectiveStartingLocations = new ArrayList<>(DETECTIVE_LOCATIONS);
    Collections.shuffle(detectiveStartingLocations);
    List<Integer> randomDetectiveLocations = detectiveStartingLocations.subList(0, detectiveCount);

    // get a list of detectives
    List<Player> detectives = IntStream.range(0, detectiveCount)
      .mapToObj(i -> new Player(detectivePieces.get(i), defaultDetectiveTickets(), randomDetectiveLocations.get(i)))
      .toList();

    return ImmutableList.copyOf(detectives);
  }

  private ImmutableValueGraph<Integer, ImmutableSet<Transport>> readGraph() {
    try {
      return standardGraph();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private MyGameStateFactory.MyGameState generateRandomGameState() {
    return (MyGameState) gameStateFactory.build(
      new GameSetup(readGraph(), STANDARD24MOVES), // standard24MoveSetup
      new Player(Piece.MrX.MRX, defaultMrXTickets(), MRX_LOCATIONS.get(random.nextInt(MRX_LOCATIONS.size()))),
      generateRandomDetectives()
    );
  }


  private MyGameState getGameState() {
    Board.GameState board = gameStateFactory.build(
            new GameSetup(graph, STANDARD24MOVES),
            new Player(MRX, defaultMrXTickets(), 35),
            new Player(RED, defaultDetectiveTickets(), 26),
            new Player(BLUE, defaultDetectiveTickets(), 50),
            new Player(GREEN, defaultDetectiveTickets(), 94),
            new Player(WHITE, defaultDetectiveTickets(), 155),
            new Player(YELLOW, defaultDetectiveTickets(), 174)
    );
    return gameStateFactory.buildFromBoard(board, 35);
  }

  @Test
  public void testUTC() {
    MyGameState gameState = generateRandomGameState();
    List<Move> moves = new ArrayList<>(gameState.getAvailableMoves());
    Node parent = new MrXNode(gameState, null, null, mrXPossibleLocations.copy());
    parent.numVisits = 8; parent.totalReturn = 5;
    Node node = new MrXNode(gameState.advance(moves.get(0)), (MrXNode) parent, moves.get(0), mrXPossibleLocations.copy());
    node.numVisits = 5; node.totalReturn = 3; node.moveNode = node;
    double C = 0.5; double W = 5.0;
    assertEquals(1.9224470143821955, node.UCT(C, W));

    node.numVisits = 0; node.totalReturn = 0;
    assertEquals(Double.MAX_VALUE, node.UCT(C, W));

    node.numVisits = 5; node.totalReturn = 3; parent.isMrX = false;
    assertEquals(1.2224470143821955, node.UCT(C, W));
  }

  @Test
  public void testInstantiateNode() {
    graph = readGraph();
    MyGameState gameState = getGameState();
    List<Move> moves = new ArrayList<>(gameState.getAvailableMoves());
    Move move = new Move.DoubleMove(MRX, 35, Ticket.SECRET, 65, Ticket.BUS, 22);
    Node mrXNode = new MrXNode(null, null, move,  new MrXPossibleLocations());
    mrXNode.instantiateNode(gameState.advance(move));
    assertTrue(!mrXNode.isMrX);
    assertEquals(new HashSet<>(Arrays.asList(128, 65, 1, 67, 3, 133, 199, 135, 72, 74, 140, 13, 77, 142, 78, 82, 22,
            86, 23, 87, 89, 154, 156, 157, 29, 161, 34, 102, 105, 41, 42, 107, 108, 46, 52, 116, 180, 55, 184, 185,
            58, 187, 124, 63, 127)), mrXNode.mrXPossibleLocationsLocal.getLocations());

    DetectiveNode parent = new DetectiveNode(gameStateFactory, null, null, move, mrXPossibleLocations.copy());
    parent.firstHiderPly = true;
    DetectiveNode detectiveNode = new DetectiveNode(gameStateFactory, null, parent, move, mrXPossibleLocations.copy());
    detectiveNode.instantiateNode(gameState.advance(move));
    assertTrue(!detectiveNode.isMrX);
    assertEquals(new HashSet<>(Arrays.asList(128, 65, 1, 67, 3, 133, 199, 135, 72, 74, 140, 13, 77, 142, 78, 82, 22,
            86, 23, 87, 89, 154, 156, 157, 29, 161, 34, 102, 105, 41, 42, 107, 108, 46, 52, 116, 180, 55, 184, 185,
            58, 187, 124, 63, 127)), detectiveNode.mrXPossibleLocationsLocal.getLocations());
    assertTrue(detectiveNode.firstHiderPly);
  }

  @Test
  public void testExpand() {
    graph = readGraph();
    MyGameState gameState = getGameState();
    List<Move> moves = new ArrayList<>(gameState.getAvailableMoves());
    Move move = moves.get(0);
    Node mrXNode = new MrXNode(gameState.advance(move), null, move, mrXPossibleLocations.copy());

    try {
      mrXNode.dijkstra = Dijkstra.getInstance(standardGraph());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    mrXNode.expand();
    assertNull(mrXNode.children.get(0).gameState);
    assertEquals(mrXNode, mrXNode.children.get(0).parent);

    DetectiveNode detectiveNode = new DetectiveNode(gameStateFactory, gameState.advance(moves.get(1)),
            null, moves.get(1), mrXPossibleLocations.copy());
  }

  @Test
  public void testSelectBestChild() {
    graph = readGraph();
    MyGameState gameState = getGameState();
    List<Move> moves = new ArrayList<>(gameState.getAvailableMoves());
    Move move = moves.get(0);
    MyGameState newGameState = gameState.advance(move);
    MrXNode mrXNode = new MrXNode(newGameState, null, move, mrXPossibleLocations.copy());

    assertEquals(mrXNode, mrXNode.selectBestChild(0.5, 5));

    moves = new ArrayList<>(newGameState.getAvailableMoves());
    for (Move childMove : moves) {
      mrXNode.children.add(new MrXNode(newGameState.advance(childMove), mrXNode, move, mrXPossibleLocations.copy()));
    }

    assertEquals(19, mrXNode.children.size());
    assertEquals(mrXNode.children.get(0), mrXNode.selectBestChild(0.5, 5));

    mrXNode.numVisits = 36;
    for (Node child : mrXNode.children) {
      if (child != mrXNode.children.get(9)) child.numVisits = 2;
    }

    assertEquals(mrXNode.children.get(9), mrXNode.selectBestChild(0.5, 5));

    mrXNode.numVisits = 37;
    mrXNode.children.get(9).numVisits = 1;
    assertEquals(mrXNode.children.get(9), mrXNode.selectBestChild(0.5, 5));
  }
}
