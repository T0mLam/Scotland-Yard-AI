package uk.ac.bris.cs.scotlandyard.model;


import com.google.common.collect.ImmutableList;

import javax.annotation.Nonnull;

import com.google.common.collect.ImmutableSet;
import uk.ac.bris.cs.scotlandyard.model.ScotlandYard.Factory;

import uk.ac.bris.cs.scotlandyard.model.Board.GameState;
import uk.ac.bris.cs.scotlandyard.model.MyGameStateFactory.*;
import uk.ac.bris.cs.scotlandyard.model.Model.*;

import java.util.HashSet;
import java.util.Set;

/**
 * cw-model
 * Stage 2: Complete this class
 */
public final class MyModelFactory implements Factory<Model> {
	class MyModel implements Model {
		private GameState gameState;
		Set<Observer> observerList = new HashSet<Observer>();

		MyModel(GameState gameState) {
			this.gameState = gameState;
		}

		public Board getCurrentBoard() {
			return gameState;
		};
		public void registerObserver(Observer observer) {
			if (observer == null) {
				throw new NullPointerException();
			}
			if (observerList.contains(observer)) {
				throw new IllegalArgumentException();
			}
			observerList.add(observer);
		};

		public void unregisterObserver(Observer observer) {
			if (observer == null) {
				throw new NullPointerException();
			}
			if (!observerList.contains(observer)) {
				throw new IllegalArgumentException();
			}
			observerList.remove(observer);
		};

		public ImmutableSet<Observer> getObservers() {
			return ImmutableSet.copyOf(observerList);
		};

		public void chooseMove(@Nonnull Move move) {
			// advance game state
			gameState = gameState.advance(move);

			// notify observers
			boolean hasWinner = !gameState.getWinner().isEmpty();
			for (Observer observer : observerList) {
				if (hasWinner) {
					observer.onModelChanged(gameState, Observer.Event.GAME_OVER);
				} else {
					observer.onModelChanged(gameState, Observer.Event.MOVE_MADE);
				}
			}
		};

	}

	@Nonnull @Override public Model build(GameSetup setup,
	                                      Player mrX,
	                                      ImmutableList<Player> detectives) {
		MyGameStateFactory myGameStateFactory = new MyGameStateFactory();
		return new MyModel(myGameStateFactory.build(setup, mrX, detectives));
	}
}
