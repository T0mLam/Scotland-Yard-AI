package uk.ac.bris.cs.scotlandyard.ui.ai.models.nodes;

import uk.ac.bris.cs.scotlandyard.model.Move;
import uk.ac.bris.cs.scotlandyard.ui.ai.MyGameStateFactory;
import uk.ac.bris.cs.scotlandyard.ui.ai.MyGameStateFactory.*;
import uk.ac.bris.cs.scotlandyard.ui.ai.heuristic.Dijkstra;
import uk.ac.bris.cs.scotlandyard.ui.ai.heuristic.MrXPossibleLocations;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static uk.ac.bris.cs.scotlandyard.model.ScotlandYard.standardGraph;

public abstract class Node {
  public MyGameState gameState;
  public MrXPossibleLocations mrXPossibleLocationsLocal;
  public double numVisits;
  public double totalReturn;
  public List<Node> children;
  public Node parent;
  public Node moveNode;
  public Move move;
  public boolean isMrX;
  public Dijkstra dijkstra;

  public abstract void expand();

  public abstract Node instantiateNode(MyGameStateFactory.MyGameState newGameState);

  public double UCT(double C, double W) {
    if (numVisits == 0) return Double.MAX_VALUE; // this is in place of division by 0
    double avgReturn = totalReturn / numVisits;
    avgReturn = parent.isMrX ? avgReturn : 1.0 - avgReturn;
    double amr = parent.isMrX ? moveNode.totalReturn / moveNode.numVisits :
            1.0 - (moveNode.totalReturn / moveNode.numVisits);

    return avgReturn + C * Math.sqrt(Math.log(parent.numVisits) / numVisits) +
      W * (amr / (numVisits * (1 - avgReturn) + 1));
  }

  public abstract Node selectBestChild(double C, double W);
}
