package uk.ac.bris.cs.scotlandyard.ui.ai.benchmarks;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.graph.ImmutableValueGraph;
import io.atlassian.fugue.Pair;
import uk.ac.bris.cs.scotlandyard.model.*;
import uk.ac.bris.cs.scotlandyard.ui.ai.*;
import uk.ac.bris.cs.scotlandyard.ui.ai.MyGameStateFactory;
import uk.ac.bris.cs.scotlandyard.model.Board.*;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.stream.IntStream;

import static uk.ac.bris.cs.scotlandyard.model.Piece.Detective.*;
import static uk.ac.bris.cs.scotlandyard.model.ScotlandYard.*;

public class Benchmark {
  private final Ai mrXAi;
  private final Ai detectiveAi;
  private final int iterations;
  private final long mrXTimeLimit = 20;
  private final long detectiveTimeLimit = 15;
  private final MyGameStateFactory gameStateFactory = new MyGameStateFactory();
  private final List<Piece> detectivePieces = new ArrayList<>(DETECTIVES);
  private final Random random = new Random();

  public Benchmark(Ai mrXAi, Ai detectiveAi, int iterations) {
    this.mrXAi = mrXAi;
    this.detectiveAi = detectiveAi;
    this.iterations = iterations;
  }

  private ImmutableValueGraph<Integer, ImmutableSet<Transport>> readGraph() {
    try {
      return standardGraph();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private ImmutableList<Player> generateRandomDetectives() {
    int detectiveCount = detectivePieces.size();

    // Sample random detective starting locations
    List<Integer> detectiveStartingLocations = new ArrayList<>(DETECTIVE_LOCATIONS);
    Collections.shuffle(detectiveStartingLocations);
    List<Integer> randomDetectiveLocations = detectiveStartingLocations.subList(0, detectiveCount);

    // Get a list of detectives
    List<Player> detectives = IntStream.range(0, detectiveCount)
      .mapToObj(i -> new Player(detectivePieces.get(i), defaultDetectiveTickets(), randomDetectiveLocations.get(i)))
      .toList();

    return ImmutableList.copyOf(detectives);
  }

  private GameState generateRandomGameState() {
    return gameStateFactory.build(
      new GameSetup(readGraph(), STANDARD24MOVES), // standard24MoveSetup
      new Player(MrX.MRX, defaultMrXTickets(), MRX_LOCATIONS.get(random.nextInt(MRX_LOCATIONS.size()))),
      generateRandomDetectives()
    );
  }

  private int runAllGames(Logger logger) {
    int mrXWinningCount = 0;

    for (int i = 0; i < iterations; i++) {
      mrXAi.onStart();
      detectiveAi.onStart();

      GameState gameState = generateRandomGameState();
      Set<Piece> winners = gameState.getWinner();
      Piece lastPlayer = null;

      // Run the game until a winner is found
      while (winners.isEmpty()) {
        List<Move> moves = gameState.getAvailableMoves().asList();
        if (moves.isEmpty()) {
          winners = lastPlayer.isMrX() ? Set.of(MrX.MRX) : ImmutableSet.copyOf(detectivePieces);
          break;
        }

        lastPlayer = moves.get(0).commencedBy();

        // Pick a move for the current player
        Move selectedMove = lastPlayer.isMrX() ?
          mrXAi.pickMove(gameState, new Pair<>(mrXTimeLimit, TimeUnit.SECONDS)) :
          detectiveAi.pickMove(gameState, new Pair<>(detectiveTimeLimit, TimeUnit.SECONDS));

        gameState = gameState.advance(selectedMove);
        winners = gameState.getWinner();
      }

      if (winners.contains(MrX.MRX)) mrXWinningCount++;
      logger.info("Game " + (i + 1) + " ended with winners " + winners);

      mrXAi.onTerminate();
      detectiveAi.onTerminate();
    }

    return mrXWinningCount;
  }

  public void run() {
    Logger logger = Logger.getLogger(Benchmark.class.getName());
    FileHandler fileHandler = null;

    try {
      // Create a log file
      String filename = "benchmark-%s.log";
      SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
      String formmatedDate = sdf.format(new Date());

      // Create a new file handler
      fileHandler = new FileHandler(String.format(filename, formmatedDate), true);
      logger.addHandler(fileHandler);

      // Create a simple formatter
      SimpleFormatter formatter = new SimpleFormatter();
      fileHandler.setFormatter(formatter);

      // Log the benchmark results
      logger.info("Starting benchmark...");
      logger.info("MrX AI: " + mrXAi.name() + ", Detective AI: " + detectiveAi.name());

      int mrXWinningCount = runAllGames(logger);

      logger.info("MrX Win Rate: " + ((float) mrXWinningCount / iterations));
      logger.info("Ending benchmark...");
    }
    catch (IOException e) {
      // Log the exception
      e.printStackTrace();
    }
    finally {
      if (fileHandler != null) {
        fileHandler.close();
        logger.removeHandler(fileHandler);
      }
    }
  }

  public static void main(String[] args) {
    new Benchmark(new MCTS(), new MCTS(), 7).run();
  }
}
