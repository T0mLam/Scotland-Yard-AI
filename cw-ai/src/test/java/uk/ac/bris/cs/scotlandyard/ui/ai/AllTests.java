package uk.ac.bris.cs.scotlandyard.ui.ai;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import uk.ac.bris.cs.scotlandyard.ui.ai.heuristic.DijkstraTest;
import uk.ac.bris.cs.scotlandyard.ui.ai.heuristic.MrPossibleLocationsTest;
import uk.ac.bris.cs.scotlandyard.ui.ai.models.DetectiveOneStepLookAheadTest;
import uk.ac.bris.cs.scotlandyard.ui.ai.models.MCTSTest;
import uk.ac.bris.cs.scotlandyard.ui.ai.models.MrXOneStepLookAheadTest;

@RunWith(Suite.class)
@Suite.SuiteClasses({
        DijkstraTest.class,
        MrPossibleLocationsTest.class,
        DetectiveOneStepLookAheadTest.class,
        MCTSTest.class,
        MrXOneStepLookAheadTest.class
})

public class AllTests {
}
