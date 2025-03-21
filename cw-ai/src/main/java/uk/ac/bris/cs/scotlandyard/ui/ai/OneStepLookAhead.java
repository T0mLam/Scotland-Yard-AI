package uk.ac.bris.cs.scotlandyard.ui.ai;

import io.atlassian.fugue.Pair;
import uk.ac.bris.cs.scotlandyard.model.Ai;
import uk.ac.bris.cs.scotlandyard.model.Board;
import uk.ac.bris.cs.scotlandyard.model.Move;
import uk.ac.bris.cs.scotlandyard.ui.ai.models.MinimaxFactory;
import uk.ac.bris.cs.scotlandyard.ui.ai.models.Model;
import uk.ac.bris.cs.scotlandyard.ui.ai.models.ModelFactory;
import uk.ac.bris.cs.scotlandyard.ui.ai.models.OneStepLookAheadFactory;

import javax.annotation.Nonnull;
import java.util.concurrent.TimeUnit;

public class OneStepLookAhead implements Ai {
  private final ModelFactory modelFactory = new OneStepLookAheadFactory();
  private final Model mrXModel = modelFactory.getMrXModel();
  private final Model detectivesModel = modelFactory.getDetectiveModel();

  @Nonnull
  @Override
  public String name() {
    return "OneStepLookAhead";
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