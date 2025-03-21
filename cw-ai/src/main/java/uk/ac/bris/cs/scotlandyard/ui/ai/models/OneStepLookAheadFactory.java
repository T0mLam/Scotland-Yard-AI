package uk.ac.bris.cs.scotlandyard.ui.ai.models;

public class OneStepLookAheadFactory extends ModelFactory {
    @Override
    public Model getMrXModel() {
        return new MrXOneStepLookAhead();
    }

    @Override
    public Model getDetectiveModel() {
        return new DetectiveOneStepLookAhead();
    }
}
