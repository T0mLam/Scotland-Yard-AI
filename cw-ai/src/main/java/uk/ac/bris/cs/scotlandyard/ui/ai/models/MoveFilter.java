package uk.ac.bris.cs.scotlandyard.ui.ai.models;

import com.google.common.collect.ImmutableSet;
import com.google.common.graph.ImmutableValueGraph;
import uk.ac.bris.cs.scotlandyard.model.Piece;
import uk.ac.bris.cs.scotlandyard.model.Move;
import uk.ac.bris.cs.scotlandyard.model.Move.*;
import uk.ac.bris.cs.scotlandyard.model.ScotlandYard.*;
import uk.ac.bris.cs.scotlandyard.ui.ai.MyGameStateFactory.*;
import uk.ac.bris.cs.scotlandyard.ui.ai.heuristic.Dijkstra;
import uk.ac.bris.cs.scotlandyard.ui.ai.heuristic.MrXPossibleLocations;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class MoveFilter {
  public static List<Move> applyParanoidMrXFilters(List<Move> moves, MyGameState gameState, int minDistanceToDetectives) {
    // Filter moves by unique destination first.
    List<Move> filteredMoves = filterMovesByDestination(moves);
    List<Move> remainingDoubleMoves = filterUseAllRemainingSecretTickets(filteredMoves, gameState);

    if (remainingDoubleMoves.size() != filteredMoves.size()) {
      return remainingDoubleMoves;
    }

    List<Move> validFilteredMoves = filteredMoves.stream()
            .filter(move -> validMrXMove(gameState, move))
            .toList();
    validFilteredMoves = filterDoubleMoves(validFilteredMoves, gameState);
    return validFilteredMoves.isEmpty() ? filteredMoves : validFilteredMoves;
  }

  public static List<Move> applyMrXFilters(List<Move> moves, MyGameState gameState, int minDistanceToDetectives) {
    // Filter moves by unique destination first.
    List<Move> filteredMoves = filterMovesByDestination(moves);

    List<Move> validFilteredMoves = filteredMoves.stream()
      .filter(move -> validMrXMove(gameState, move))
      .toList();
    validFilteredMoves = filterDoubleMoves(validFilteredMoves, gameState);
    return validFilteredMoves.isEmpty() ? filteredMoves : validFilteredMoves;
  }

  public static List<Move> applyDetectiveFilters(List<Move> moves) {
    // Filter moves by unique destination first.
    List<Move> filteredMoves = filterMovesByDestination(moves);
    return filteredMoves;
  }

  public static List<Move> applyDetectiveFiltersWithoutOrdering(List<Move> moves, Map<Piece, Integer> shortestPath) {
    List<Move> filteredMoves = eliminateDetectiveMoveOrder(moves, shortestPath);
    return applyDetectiveFilters(filteredMoves);
  }

  public static List<Move> applyMTCSDetectiveFilters(List<Move> moves, MrXPossibleLocations mrXPossibleLocations) {
    return applyDetectiveFilters(eliminateMCTSDetectiveMoveOrder(moves, mrXPossibleLocations));
  }

  private static List<Move> eliminateDetectiveMoveOrder(List<Move> moves, Map<Piece, Integer> shortestPath) {
    // only allow the closest detective to move
    int closestDistance = Integer.MAX_VALUE;
    Piece closestDetective = null;

    // get a set of the closest detectives
    Set<Piece> detectivesInMoves = moves
      .stream()
      .map(Move::commencedBy)
      .collect(Collectors.toSet());


    for (Map.Entry<Piece, Integer> entry : shortestPath.entrySet()) {
      if (entry.getValue() < closestDistance &&
        detectivesInMoves.contains(entry.getKey())
      ) {
        closestDistance = entry.getValue();
        closestDetective = entry.getKey();
      }
    }

    List<Move> filteredMoves = new ArrayList<>();
    for (Move move : moves) {
      if (move.commencedBy().equals(closestDetective)) {
        filteredMoves.add(move);
      }
    }

    return filteredMoves;
  }

  private static List<Move> eliminateMCTSDetectiveMoveOrder(List<Move> moves,
                                                            MrXPossibleLocations mrXPossibleLocations)
  {
    // only allow the closest detective to move
    int closestDistance = Integer.MAX_VALUE;
    Set<Piece> closestDetectives = new HashSet<>();

    Dijkstra dijkstra = Dijkstra.getInstance();

    // get a set of the closest detectives
    Map<Piece, Integer> detectivesInMoves = new HashMap<>();

    for (Move move : moves) {
      if (detectivesInMoves.containsKey(move.commencedBy())) continue;

      Set<Integer> mrXLocations = mrXPossibleLocations.getLocations();
      int distanceSum = 0;
      int targetDetectiveLoc = move.source();
      for (int location : mrXLocations) {
        distanceSum += dijkstra.getDistance(targetDetectiveLoc, location);
      }
      detectivesInMoves.put(move.commencedBy(), distanceSum);
    }

    for (Map.Entry<Piece, Integer> entry : detectivesInMoves.entrySet()) {
      if (entry.getValue() < closestDistance) {
        closestDistance = entry.getValue();
        closestDetectives = new HashSet<>();
        closestDetectives.add(entry.getKey());
      } else if (entry.getValue() == closestDistance) {
        closestDetectives.add(entry.getKey());
      }
    }

    List<Move> filteredMoves = new ArrayList<>();
    for (Move move : moves) {
      if (closestDetectives.contains(move.commencedBy())) {
        filteredMoves.add(move);
      }
    }

    return filteredMoves;
  }

  private static int getMoveComparator(Move move) {
    return move.accept(new FunctionalVisitor<>(
      singleMove -> singleMove.ticket.ordinal(),
      doubleMove -> doubleMove.ticket1.ordinal() + doubleMove.ticket2.ordinal() + 10
    ));
  }

  private static List<Move> filterMovesByDestination(List<Move> moves) {
    // sort tickets
    // filter tickets if there are duplicated locations
    Set<Integer> visitedDestinations = new HashSet<>();

    return moves.stream()
      .sorted(Comparator.comparingInt(MoveFilter::getMoveComparator))
      .filter(move -> move.accept(new FunctionalVisitor<>(
        singleMove -> {
          if (singleMove.ticket.equals(Ticket.SECRET)) return true;
          return visitedDestinations.add(singleMove.destination);
        },
        doubleMove -> {
          if (doubleMove.source() == doubleMove.destination2) return false;
          return visitedDestinations.add(doubleMove.destination2);
        }
      )))
      .toList();
  }

  private static boolean validMrXMove(MyGameState gameState, Move move) {
    return move.accept(new FunctionalVisitor<>(
      singleMove -> {
        if (singleMove.ticket.equals(Ticket.SECRET)) {
          if (gameState.getMrXTravelLog().size() <= 2) return false;
          if (gameState.getSetup().moves.get(gameState.getMrXTravelLog().size())) return false;

          ImmutableValueGraph<Integer, ImmutableSet<Transport>> graph = gameState.getSetup().graph;
          boolean onlyTaxi = true;
          // Check if only taxi moves are available between the source and destination.
          for (Transport t : graph.edgeValueOrDefault(singleMove.source(), singleMove.destination, ImmutableSet.of())) {
            if (!t.requiredTicket().equals(Ticket.TAXI)) {
              onlyTaxi = false;
              break;
            }
          }
          if (onlyTaxi) return false;
        }
        return true;
      },
      doubleMove -> true
    ));
  }

  private static List<Move> filterDoubleMoves(List<Move> moves, MyGameState gameState) {
    List<Move> singleMoves = moves.stream().filter(move -> move instanceof SingleMove).toList();

    if (moves.isEmpty()) return moves;

    Set<Integer> allFutureDetectiveDestinations = gameState.advance(moves.get(0))
            .getAvailableMoves()
            .asList()
            .stream()
            .map(MoveFilter::getMoveDestination)
            .collect(Collectors.toSet());

    boolean mrXWillBeCaptured = singleMoves.stream()
            .map(MoveFilter::getMoveDestination)
            .allMatch(allFutureDetectiveDestinations::contains);

    if (mrXWillBeCaptured) {
      List<Move> doubleMoves = moves.stream().filter(move -> move instanceof DoubleMove).toList();
      return doubleMoves.isEmpty() ? singleMoves : doubleMoves;
    }
    return singleMoves;
  }

  public static int getMoveDestination(Move move) {
    return move.accept(new Move.FunctionalVisitor<>(
            singleMove -> singleMove.destination,
            doubleMove -> doubleMove.destination2
    ));
  }

  private static List<Move> filterUseAllRemainingSecretTickets(List<Move> moves, MyGameState gameState) {
    int totalMoveCount = gameState.getSetup().moves.size();
    int currentMoveCount = gameState.getMrXTravelLog().size();

    if (gameState.getSetup().moves.get(currentMoveCount)) {
        return moves;
    }

    int remainingHiddenMoveCount = (int) IntStream.range(currentMoveCount, totalMoveCount)
            .filter(i -> !gameState.getSetup().moves.get(i))
            .count();

    if (gameState.getMrX().hasAtLeast(Ticket.SECRET, remainingHiddenMoveCount)) {
        List<Move> allDoubleMoves = moves
                .stream()
                .filter(move -> move.accept(new FunctionalVisitor<>(
            singleMove -> singleMove.ticket.equals(Ticket.SECRET),
            doubleMove -> doubleMove.ticket1.equals(Ticket.SECRET) && doubleMove.ticket2.equals(Ticket.SECRET)
                )))
                .toList();
        return allDoubleMoves;
    }

    return moves;
  }
}
