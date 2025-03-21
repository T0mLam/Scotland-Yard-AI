package uk.ac.bris.cs.scotlandyard.ui.ai;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.atlassian.fugue.Pair;
import uk.ac.bris.cs.scotlandyard.model.Ai;
import uk.ac.bris.cs.scotlandyard.model.Board;
import uk.ac.bris.cs.scotlandyard.model.Move;
import uk.ac.bris.cs.scotlandyard.model.Piece;
import uk.ac.bris.cs.scotlandyard.ui.ai.heuristic.MrXPossibleLocations;
import uk.ac.bris.cs.scotlandyard.ui.ai.models.*;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class MCTS implements Ai {
  private final ModelFactory modelFactory = new MCTSFactory();
  private final Model mrXModel = modelFactory.getMrXModel();
  private final Model detectivesModel = modelFactory.getDetectiveModel();

  @Nonnull
  @Override
  public String name() {
    return "MCTS";
  }

  @Override
  public void onStart() {
    mrXModel.onStart();
    detectivesModel.onStart();
  }

  @Nonnull
  @Override
  public Move pickMove(@Nonnull Board board, Pair<Long, TimeUnit> timeoutPair) {
    boolean isMrX = board.getAvailableMoves().asList().get(0).commencedBy().isMrX();

    return isMrX ?
            mrXModel.pickMove(board, timeoutPair) :
            detectivesModel.pickMove(board, timeoutPair);
  }
}
