package uk.ac.bris.cs.scotlandyard.ui.ai;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import uk.ac.bris.cs.scotlandyard.model.*;
import uk.ac.bris.cs.scotlandyard.model.Board.*;
import uk.ac.bris.cs.scotlandyard.model.ScotlandYard.*;
import uk.ac.bris.cs.scotlandyard.model.Move.*;
import uk.ac.bris.cs.scotlandyard.model.Piece.*;


import java.util.*;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

/**
 * cw-model
 * Stage 1: Complete this class
 */

public final class MyGameStateFactory implements Factory<GameState> {
  public final class MyGameState implements GameState {

    private final GameSetup setup;
    private final ImmutableSet<Piece> remaining;
    private final ImmutableList<LogEntry> log;
    private final Player mrX;
    private final List<Player> detectives;
    private final ImmutableSet<Move> moves;
    private final ImmutableSet<Piece> winner;

    private MyGameState(
            final GameSetup setup,
            final ImmutableSet<Piece> remaining,
            final ImmutableList<LogEntry> log,
            final Player mrX,
            final List<Player> detectives
    ) {
      // check argument values
      if (setup == null || remaining == null || log == null) throw new IllegalArgumentException();
      if (mrX == null || detectives == null) throw new NullPointerException();
      if (setup.graph.nodes().isEmpty() || setup.moves.isEmpty() || mrX.isDetective()) throw new IllegalArgumentException();

      Set<Integer> locations = new HashSet<>();
      for (Player detective : detectives) {
        if (detective.isMrX() ||
                detective.has(Ticket.DOUBLE) ||
                detective.has(Ticket.SECRET) ||
                !locations.add(detective.location()) // returns a boolean if added element not previously in set
        ) throw new IllegalArgumentException();
      }

      // define attributes
      this.remaining = remaining;
      this.setup = setup;
      this.log = log;
      this.mrX = mrX;
      this.detectives = detectives;

      this.moves = calculateAvailableMoves();
      this.winner = calculateWinner();
    }

    @Nonnull @Override public GameSetup getSetup() {
      return setup;
    }

    @Nonnull @Override public ImmutableSet<Piece> getPlayers() {
      Set<Piece> pieces = new HashSet<Piece>();
      pieces.add(mrX.piece());
      for (Player detective : detectives) {
        pieces.add(detective.piece());
      }
      return ImmutableSet.copyOf(pieces);
    }

    @Override public MyGameState advance(Move move) {
      if(!moves.contains(move)) throw new IllegalArgumentException("Illegal move: "+move);

      return move.accept(new Visitor<>() {
        @Override public MyGameState visit(SingleMove singleMove){
          if (singleMove.commencedBy().isMrX()) {
            // update log
            LogEntry logEntry;
            int size = log.size();

            logEntry = setup.moves.get(size) ?
                    LogEntry.reveal(singleMove.ticket, singleMove.destination) : LogEntry.hidden(singleMove.ticket);

            // add new log entry to log
            List<LogEntry> tmpLog = new ArrayList<LogEntry>(log);
            tmpLog.add(logEntry);

            // create new immutable list of log entries
            ImmutableList<LogEntry> newLog = ImmutableList.copyOf(tmpLog);

            // take the used ticket away from Mr X
            Player newMrX = mrX.use(singleMove.ticket);

            // move Mr X's position to their new destination
            newMrX = newMrX.at(singleMove.destination);

            // swap to the detectives turn
            Set<Piece> newRemaining = new HashSet<Piece>();
            for (Player detective : detectives) {
              newRemaining.add(detective.piece());
            }

            return new MyGameState(setup, ImmutableSet.copyOf(newRemaining), newLog, newMrX, detectives);
          }
          else {
            // Move the detective to their new destination
            Player detective = getPlayerFromPiece(singleMove.commencedBy());
            Player newDetective = detective.at(singleMove.destination);

            // Take the used ticket from the detective and give it to Mr X
            newDetective = newDetective.use(singleMove.ticket);
            Player newMrX = mrX.give(singleMove.ticket);

            // Update the detectives
            List<Player> newDetectives = new ArrayList<Player>(detectives);
            newDetectives.remove(detective);
            newDetectives.add(newDetective);

            // If there are no more possible detective moves, swap to Mr X's turn
            Set<Piece> newRemaining = new HashSet<Piece>(remaining);
            if (remaining.size() == 1) newRemaining = Set.of(mrX.piece());

              // remove the detective from remaining
            else newRemaining.remove(detective.piece());

            // if no detectives have more moves, pass turn to MrX
            boolean detectivesHaveMoves = false;
            for (Move move: moves) {
              if (!move.commencedBy().equals(singleMove.commencedBy())) {
                detectivesHaveMoves = true;
                break;
              }
            }

            if (!detectivesHaveMoves) newRemaining = Set.of(mrX.piece());

            return new MyGameState(setup, ImmutableSet.copyOf(newRemaining), log, newMrX, newDetectives);
          }
        }

        @Override public MyGameState visit(DoubleMove doubleMove){
          if (doubleMove.commencedBy().isMrX()) {
            // update log
            int size = log.size();
            List<LogEntry> tmpLog = new ArrayList<LogEntry>(log);

            // add new log entry to log
            if (setup.moves.get(size)) tmpLog.add(LogEntry.reveal(doubleMove.ticket1, doubleMove.destination1));
            else tmpLog.add(LogEntry.hidden(doubleMove.ticket1));

            if (setup.moves.get(size + 1)) tmpLog.add(LogEntry.reveal(doubleMove.ticket2, doubleMove.destination2));
            else tmpLog.add(LogEntry.hidden(doubleMove.ticket2));

            // create new immutable list of log entries
            ImmutableList<LogEntry> newLog = ImmutableList.copyOf(tmpLog);

            // take the used ticket away from Mr X
            Player newMrX = mrX.use(doubleMove.ticket1);
            newMrX = newMrX.use(doubleMove.ticket2);

            // move Mr X's position to their new destination
            newMrX = newMrX.at(doubleMove.destination2);

            // swap to the detectives turn
            Set<Piece> newRemaining = new HashSet<Piece>();
            for (Player detective : detectives) newRemaining.add(detective.piece());

            // decrement double move ticket
            newMrX = newMrX.use(Ticket.DOUBLE);

            return new MyGameState(setup, ImmutableSet.copyOf(newRemaining), newLog, newMrX, detectives);
          }

          return null;
        }
      });
    }

    private Player getPlayerFromPiece(Piece piece){
      for (Player detective : detectives) {
        if (detective.piece().equals(piece)) return detective;
      }
      return mrX;
    }

    @Nonnull @Override public Optional<Integer> getDetectiveLocation(Detective detective) {
      for (Player candiateDetective : detectives) {
        if (candiateDetective.piece().equals(detective)) {
          int location = candiateDetective.location();
          return Optional.of(location);
        }
      }
      return Optional.empty();
    }

    @Nonnull @Override public Optional<TicketBoard> getPlayerTickets(Piece piece) {
      Player player = getPlayerFromPiece(piece);
      // check whether player returned is really mrX or a player who is not in the list of detectives
      if (player.isMrX() && !mrX.piece().equals(piece)) return Optional.empty();

      TicketBoard ticketBoard = ticket -> player.tickets().get(ticket);

      return Optional.of(ticketBoard);
    }

    @Nonnull @Override public ImmutableList<LogEntry> getMrXTravelLog() {
      return log;
    }

    private ImmutableSet<Piece> getDetectivePieces() {
      Set<Piece> detectivePieces = new HashSet<>();

      for (Player detective : detectives) {
        detectivePieces.add(detective.piece());
      }
      return ImmutableSet.copyOf(detectivePieces);
    }

    private ImmutableSet<Piece> calculateWinner() {
      boolean mrXCaptured = false;

      // check if detective is on MrX
      for (Player detective : detectives) {
        if (detective.location() == mrX.location()) {
          mrXCaptured = true;
          break;
        }
      }

      boolean mrXCanMove = false;
      if (!moves.isEmpty()) {
        for (Move move : moves) {
          if (move.commencedBy().isMrX()) {
            mrXCanMove = true;
            break;
          }
        }
      }

      // check if detective have all run out of tickets
      boolean detectivesHaveNoTicket = true;
      for (Player detective : detectives) {
        if (detective.has(Ticket.TAXI) ||
                detective.has(Ticket.BUS) ||
                detective.has(Ticket.UNDERGROUND)
        ) detectivesHaveNoTicket = false;
      }
      if (detectivesHaveNoTicket) return ImmutableSet.of(mrX.piece());

      if (mrXCaptured || (!mrXCanMove && remaining.contains(mrX.piece()))) return getDetectivePieces();

      // check whether mrX finishes its last move without being captured
      if (log.size() == setup.moves.size() && remaining.contains(mrX.piece())) return ImmutableSet.of(mrX.piece());

      return ImmutableSet.of();
    }

    @Nonnull @Override public ImmutableSet<Piece> getWinner() {
      return winner;
    }

    // see if we can use lambdas
    @Nonnull @Override public ImmutableSet<Move> getAvailableMoves() {
      if (!winner.isEmpty()) return ImmutableSet.of();
      return moves;
    }

    private ImmutableSet<Move> calculateAvailableMoves() {
      Set<Move> allPlayerMoves = new HashSet<>();

      // get possible player moves
      for (Piece piece : remaining) {
        Player player = getPlayerFromPiece(piece);
        allPlayerMoves.addAll(makeSingleMoves(setup, detectives, player, player.location()));
        if (piece == mrX.piece() && log.size() + 1 != setup.moves.size() && mrX.hasAtLeast(Ticket.DOUBLE, 1)) {
          allPlayerMoves.addAll(makeDoubleMoves(setup, detectives, player, player.location()));
        }
      }
      return ImmutableSet.<Move>copyOf(allPlayerMoves);
    }

    private static Set<SingleMove> makeSingleMoves(GameSetup setup, List<Player> detectives, Player player, int source) {
      // create an empty HashSet to store all the SingleMove generated
      Set<SingleMove> singleMoves = new HashSet<SingleMove>();

      // checks whether adjacent node can be moved to
      outerloop:
      for(int destination : setup.graph.adjacentNodes(source)) {

        // checks whether a detective occupies the proposed destination
        for (Player detective : detectives) {
          if (detective.location() == destination) {
            continue outerloop;
          }
        }

        // if the player has the required tickets construct a SingleMove and add it the collection of moves to return
        for (Transport t : setup.graph.edgeValueOrDefault(source, destination, ImmutableSet.of()) ) {
          if (player.has(t.requiredTicket())) {
            Ticket ticket = t.requiredTicket();
            singleMoves.add(new SingleMove(player.piece(), source, ticket, destination));
          }
        }

        // add secret move to singleMoves if mrX has secret tickets
        if (player.isMrX() && player.has(Ticket.SECRET)) {
          singleMoves.add(new SingleMove(player.piece(), source, Ticket.SECRET, destination));
        }
      }

      return singleMoves;
    }

    private static Set<DoubleMove> makeDoubleMoves(GameSetup setup, List<Player> detectives, Player player, int source) {
      // store all the first moves in singleMoves
      Set<SingleMove> singleMoves = makeSingleMoves(setup, detectives, player, source);

      return singleMoves.parallelStream()
              .flatMap(move1 -> {
                        // new reference to mrX a move ahead - the old reference is preserved
                        Player futurePlayer = player.use(move1.ticket);

                        // use the end state of move1 as the start state of move2
                        Set<SingleMove> secondMoves = makeSingleMoves(setup, detectives, futurePlayer, move1.destination);

                        // add the new double move to the set of double moves
                        return secondMoves.parallelStream()
                                .map(move2 -> new DoubleMove(futurePlayer.piece(), source, move1.ticket, move1.destination, move2.ticket, move2.destination));
                      }
              ).collect(Collectors.toSet());
    }

    public ImmutableSet<Piece> getRemaining() {
      return remaining;
    }

    public Player getMrX() {
      return mrX;
    }
  }

  @Nonnull @Override public GameState build(
          GameSetup setup,
          Player mrX,
          ImmutableList<Player> detectives) {
    return new MyGameState(setup, ImmutableSet.of(MrX.MRX), ImmutableList.of(), mrX, detectives);
  }

  public MyGameState buildFromBoard(Board board, int mrXLocation, Piece detectivePiece) {
    List<Player> detectives = new ArrayList<>();
    Player mrX = null;

    for (Piece piece : board.getPlayers()) {
      TicketBoard ticketBoard = board.getPlayerTickets(piece).get();
      Map<Ticket, Integer> tickets = new HashMap<>();
      Set<Ticket> ticketTypes = Set.of(Ticket.TAXI, Ticket.BUS, Ticket.UNDERGROUND, Ticket.SECRET, Ticket.DOUBLE);

      for (Ticket ticketType : ticketTypes) {
        tickets.put(ticketType, ticketBoard.getCount(ticketType));
      }

      if (piece.equals(detectivePiece)) {
        detectives.add(
                new Player(piece, ImmutableMap.copyOf(tickets), board.getDetectiveLocation((Detective) piece).get()));
      } else if (piece instanceof MrX) {
        mrX = new Player(piece, ImmutableMap.copyOf(tickets), mrXLocation);
      }
    }

    Set<Piece> remaining = Set.of(detectivePiece);

    return new MyGameState(
            board.getSetup(), ImmutableSet.copyOf(remaining), board.getMrXTravelLog(), mrX, detectives
    );
  }

  public MyGameState buildFromBoard(Board board, int mrXLocation) {
    List<Player> detectives = new ArrayList<>();
    Player mrX = null;

    for (Piece piece : board.getPlayers()) {
      TicketBoard ticketBoard = board.getPlayerTickets(piece).get();
      Map<Ticket, Integer> tickets = new HashMap<>();
      Set<Ticket> ticketTypes = Set.of(Ticket.TAXI, Ticket.BUS, Ticket.UNDERGROUND, Ticket.SECRET, Ticket.DOUBLE);

      for (Ticket ticketType : ticketTypes) {
        tickets.put(ticketType, ticketBoard.getCount(ticketType));
      }

      if (piece instanceof Detective detective) {
        detectives.add(
                new Player(piece, ImmutableMap.copyOf(tickets), board.getDetectiveLocation(detective).get()));
      } else {
        mrX = new Player(piece, ImmutableMap.copyOf(tickets), mrXLocation);
      }
    }

    Set<Piece> remaining = board.getAvailableMoves()
            .stream()
            .map(Move::commencedBy)
            .collect(Collectors.toSet());

    return new MyGameState(
            board.getSetup(), ImmutableSet.copyOf(remaining), board.getMrXTravelLog(), mrX, detectives
    );
  }
}
