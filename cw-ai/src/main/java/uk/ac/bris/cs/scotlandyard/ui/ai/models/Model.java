package uk.ac.bris.cs.scotlandyard.ui.ai.models;

import io.atlassian.fugue.Pair;
import uk.ac.bris.cs.scotlandyard.model.Board;
import uk.ac.bris.cs.scotlandyard.model.Move;

import javax.annotation.Nonnull;
import java.util.concurrent.TimeUnit;

public abstract class Model {
  public void onStart() {};

  @Nonnull
  public abstract Move pickMove(@Nonnull Board board, Pair<Long, TimeUnit> timeoutPair);
}
