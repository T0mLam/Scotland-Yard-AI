package uk.ac.bris.cs.scotlandyard.ui.ai.models;

public class MCTSFactory extends ModelFactory {
  @Override
  public Model getMrXModel() {
    return new MrXMCTS();
  }

  @Override
  public Model getDetectiveModel() {
    return new DetectiveMCTS();
  }
}
