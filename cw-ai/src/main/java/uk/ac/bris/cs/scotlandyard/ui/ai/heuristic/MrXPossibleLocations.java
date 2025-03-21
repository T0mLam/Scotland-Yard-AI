package uk.ac.bris.cs.scotlandyard.ui.ai.heuristic;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.graph.ImmutableValueGraph;
import uk.ac.bris.cs.scotlandyard.model.*;
import uk.ac.bris.cs.scotlandyard.model.Move.*;
import uk.ac.bris.cs.scotlandyard.model.Piece.*;
import uk.ac.bris.cs.scotlandyard.ui.ai.MyGameStateFactory.*;
import static uk.ac.bris.cs.scotlandyard.model.ScotlandYard.*;

import java.util.*;
import java.util.stream.Collectors;

public class MrXPossibleLocations {
  private Set<Integer> locations = new HashSet<>(MRX_LOCATIONS);
  private int logPosition = 0;
  private final Random random = new Random();

  public MrXPossibleLocations() {}

  public MrXPossibleLocations(Set<Integer> locations, int logPosition) {
    this.locations = locations;
    this.logPosition = logPosition;
  }

  public void updateLocations(SingleMove move) {
    locations.remove(move.destination);
  }

  public void updateLocations(MyGameState gameState)  {
    updateLocations((Board) gameState);
  }

  public void updateLocations(Board board) {
    ImmutableList<LogEntry> log = board.getMrXTravelLog();
    ImmutableList<Boolean> isRevealMoves = board.getSetup().moves;
    ImmutableValueGraph<Integer, ImmutableSet<ScotlandYard.Transport>> graph = board.getSetup().graph;

    if (logPosition < log.size()) {
      Ticket ticket = log.get(logPosition).ticket();

      // set sole location when move revealed
      if (isRevealMoves.get(logPosition)) {
        locations.clear();
        // locations.add(getMoveDestination(move, log));
        locations.add(log.get(logPosition).location().get());
      }
      else {
        Set<Integer> newLocations = new HashSet<>();
        Set<Integer> detectiveLocations = board.getPlayers()
          .stream()
          .parallel()
          // filter all detectives from the players
          .filter(Piece::isDetective)
          // map each detective to its location
          .map(p ->  board.getDetectiveLocation((Detective) p).get())
          .collect(Collectors.toSet());


        for (Integer location : locations) {
          for(int destination : graph.adjacentNodes(location)) {
            newLocations.addAll(
              graph.edgeValueOrDefault(location, destination, ImmutableSet.of())
                .parallelStream()
                // check for required ticket
                .filter((Transport t) -> t.requiredTicket() == ticket || ticket == Ticket.SECRET)
                // extract the destination
                .map(t -> destination)
                // check no detectives at the destination
                .filter(l -> !detectiveLocations.contains(l))
                .collect(Collectors.toSet()));
          }
        }
        locations = newLocations;
      }

      // increment log position and update locations again if it was a double move
      if (++logPosition < log.size()) updateLocations(board);
    }
  }

  public Set<Integer> getLocations() { return locations; }

  public int getRandomLocation() {
    return List.copyOf(locations).get(random.nextInt(locations.size()));
  }

  public MrXPossibleLocations copy() {
    return new MrXPossibleLocations(new HashSet<>(locations), logPosition);
  }
}
