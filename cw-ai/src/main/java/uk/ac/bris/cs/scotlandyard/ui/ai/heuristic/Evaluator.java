package uk.ac.bris.cs.scotlandyard.ui.ai.heuristic;

import uk.ac.bris.cs.scotlandyard.ui.ai.MyGameStateFactory.*;


public interface Evaluator {
    public float evaluateNode(MyGameState gameState, MrXPossibleLocations mrXPossibleLocations);
}
