package uk.ac.bris.cs.scotlandyard.ui.ai.models;

public class MinimaxFactory extends ModelFactory {
  @Override
  public Model getMrXModel() {
    return new ParanoidMiniMax();
  }

  @Override
  public Model getDetectiveModel() {
    return new ExpectimaxMiniMax();
  }
}
