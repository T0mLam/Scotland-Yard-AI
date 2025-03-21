package uk.ac.bris.cs.scotlandyard.ui.ai.heuristic;

import uk.ac.bris.cs.scotlandyard.model.Piece;
import uk.ac.bris.cs.scotlandyard.model.Piece.*;
import uk.ac.bris.cs.scotlandyard.ui.ai.MyGameStateFactory.*;

import java.util.Random;
import java.util.Set;

public class DetectiveEval {
  private Dijkstra dijkstra;
  Random rand = new Random();

  public float evaluateNode(MyGameState gameState, MrXPossibleLocations mrXPossibleLocations) {
    dijkstra = Dijkstra.getInstance();

    float distanceSum = 0;

    Piece targetPiece = gameState.getPlayers()
            .stream()
            .filter(Piece::isDetective)
            .findAny()
            .get();

    int targetDetectiveLoc = gameState.getDetectiveLocation(((Detective) targetPiece)).get();
    Set<Integer> mrXLocations = mrXPossibleLocations.getLocations();

    for (int location : mrXLocations) {
      distanceSum += dijkstra.getDistance(targetDetectiveLoc, location);
    }

    return -distanceSum + rand.nextFloat();
  }
}
