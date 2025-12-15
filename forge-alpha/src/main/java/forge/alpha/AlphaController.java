/*
 * Forge: Play Magic: the Gathering.
 * Copyright (C) 2011  Forge Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package forge.alpha;

import com.esotericsoftware.minlog.Log;
import com.google.common.collect.Lists;

// For now, let's ignore the card memory function and only focus on public zones
// TODO: incorporate revealed cards into private zones
//import forge.alpha.AiCardMemory.MemorySet;
// TODO: set up simulator
//import forge.alpha.simulation.GameStateEvaluator;
//import forge.alpha.simulation.SpellAbilityPicker;
import forge.game.*;
import forge.game.card.*;
import forge.game.combat.Combat;
import forge.game.combat.CombatUtil;
import forge.game.player.Player;
import forge.game.spellability.*;
import forge.game.zone.ZoneType;
import forge.util.collect.FCollectionView;

import java.util.*;

//import static forge.ai.ComputerUtilMana.getAvailableManaEstimate;
import static java.lang.Math.max;

/**
 * <p>
 * AiController class for the AlphaMTG AI variant. Minimum viable implementation.
 * </p>
 *
 * @author Bennett Garza
 * @version $Id$
 */
public class AlphaController {
    // Fields that are definitely required
    private final Player player;
    private final Game game;

    // Fields that might not be required
    //private final AiCardMemory memory;
    //private Combat predictedCombat;
    //private Combat predictedCombatNextTurn;
    private boolean useSimulation;
    //private SpellAbilityPicker simPicker;
    //private int lastAttackAggression;

    public AlphaController(final Player computerPlayer, final Game game0) {
        player = computerPlayer;
        game = game0;
        // memory = new AiCardMemory();
        // simPicker = new SpellAbilityPicker(game, player);
    }

    // used by PlayerControllerAI, do nothing here?
    public void allowCheatShuffle(boolean canCheatShuffle) {

    }

    public boolean usesSimulation() {
        return true;
    }

    public void setUseSimulation(boolean value) {
        this.useSimulation = value;
    }

    // Game Simulator asks the AI Player Controller for the simulator
    // only needed if we are doing simulations, which we will
    // we do this model-side however
//    public SpellAbilityPicker getSimulationPicker() {
//        return simPicker;
//    }

    public Game getGame() {
        return game;
    }

    public Player getPlayer() {
        return player;
    }


    // Three discard methods
    // TODO: combine these into one or two
    public CardCollection getCardsToDiscard(final int numDiscard, final String[] uTypes, final SpellAbility sa) {
        return getCardsToDiscard(numDiscard, uTypes, sa, CardCollection.EMPTY);
    }

    public CardCollection getCardsToDiscard(final int numDiscard, final String[] uTypes, final SpellAbility sa, final CardCollectionView exclude) {
        CardCollection hand = new CardCollection(player.getCardsIn(ZoneType.Hand));
        hand.removeAll(exclude);
        if (uTypes != null && sa != null) {
            hand = CardLists.getValidCards(hand, uTypes, sa.getActivatingPlayer(), sa.getHostCard(), sa);
        }
        return getCardsToDiscard(numDiscard, numDiscard, hand, sa);
    }

    public CardCollection getCardsToDiscard(int min, final int max, final CardCollection validCards, final SpellAbility sa) {
        if (validCards.size() <= min) {
            return validCards; //return all valid cards since they will be discarded without filtering needed
        }

        Card sourceCard = null;
        //final CardCollection discardList = new CardCollection();

        List<GameObject> discardList = this.chooseManyToOne(sa, validCards, min, max, "discard");
        final CardCollection discardCollection = new CardCollection();
        for (GameObject c : discardList) {
            discardCollection.add((Card) c);
        }

        return discardCollection;
    }

    // declares blockers for given defender in a given combat
    public void declareBlockersFor(Player defender, Combat combat) {
        // TODO: we only need one of these...
        List<Card> possibleBlockers = player.getCreaturesInPlay();
        List<GameObject> blockers = new ArrayList<GameObject>();

        for (Card a : combat.getAttackersOf(defender)) {
            if (CombatUtil.canBeBlocked(a, null, defender)) {
                List<GameObject> validBlockers = new ArrayList<GameObject>();
                for (Card b : possibleBlockers) {
                    if (CombatUtil.canBlock(b, combat)){
                        validBlockers.add(b);
                    }
                }

                validBlockers.removeAll(blockers);

                List<GameObject> selectedBlockers = this.chooseOneToMany(validBlockers, a, 0, validBlockers.size(), "block");

                for (GameObject b : selectedBlockers) {
                    possibleBlockers.remove((Card) b);
                    combat.addBlocker(a, (Card) b);
                }
            }
        }
    }

    public void declareAttackers(Player attacker, Combat combat) {
        final FCollectionView<GameEntity> defs = combat.getDefenders();
        List<Card> myCreatures = player.getCreaturesInPlay();
        List<GameObject> attackers = new ArrayList<GameObject>();
        for (GameEntity def : defs) {
            // generate a list of valid attackers of defender def
            List<GameObject> validAttackers = new ArrayList<GameObject>();
            for (Card c : myCreatures) {
                if (CombatUtil.canAttack(c, def)) {
                    validAttackers.add(c);
                }
            }

            // Remove all creatures already attacking another entity
            validAttackers.removeAll(attackers);

            List<GameObject> selectedAttackers = this.chooseOneToMany(validAttackers, def, 0, validAttackers.size(), "attack");

            for (GameObject a : selectedAttackers) {
                combat.addAttacker((Card) a, def);
                attackers.add(a);
            }

        }

        for (final Card element : combat.getAttackers()) {
            // tapping of attackers happens after Propaganda is paid for
            Log.debug("Computer just assigned " + element.getName() + " as an attacker.");
        }
    }

    private List<SpellAbility> singleSpellAbilityList(SpellAbility sa) {
        if (sa == null) {
            return null;
        }
        return Lists.newArrayList(sa);
    }

    public List<SpellAbility> chooseSpellAbilityToPlay() {
        CardCollectionView cards = AlphaUtilAbility.getAvailableCards(game, player);

        List<SpellAbility> spellAbilities = AlphaUtilAbility.getPlayableSpellAbilities(cards, player);

        SpellAbility selection = (SpellAbility) this.chooseOneToOne(null, spellAbilities, "playSpellAbility");
        return singleSpellAbilityList(selection);
    }

    /**
     * Selects the best set of choices to assign amongst a set of sources for an action.
     * Used in cases like:
     * - assigning attackers to a set of defenders (Players, Planeswalkers, Battles)
     * - assigning blockers to a set of attackers
     *
     * @param sources The source associated with the action.
     * @param choices The set of action choices to choose from.
     * @param mins A mapping of minimum choice selections for each source.
     * @param maxes A mapping of maximum choice selections for each source.
     * @param descriptor The action descriptor.
     * @return The assignment of choices to sources that maximizes the predicted Q-Value of the action.
     */
    public Map<GameObject, List<GameObject>> chooseManyToMany(
            final List<? extends GameObject> sources,
            final List<? extends GameObject> choices,
            Map<GameObject, Integer> mins, Map<GameObject, Integer> maxes,
            String descriptor
    ) {
        Map<GameObject, List<GameObject>> assignments = new HashMap<GameObject, List<GameObject>>();
        if (sources.size() == 1) {
            GameObject source = sources.get(0);
            int min = mins.get(source);
            int max = maxes.get(source);
            assignments.put(source, chooseManyToOne(source, choices, min, max, descriptor));
            return assignments;
        }

        // greedy: iterate over each source and find the argmaxQ, removing choices as they are used
        List<GameObject> options = new ArrayList<GameObject>(choices);

        // Each source needs to meet its minimum choice requirement.
        // The reserved count represents the remaining total of unmet minimums.
        int numReserved = 0;
        for (GameObject source : sources) {
            numReserved += mins.get(source);
        }

        // There must be at least enough choices to meet every source minimum.
        if (numReserved > choices.size()) {
            throw new IllegalArgumentException(
                    "There are not enough choices to meet the minimum selections required for all sources."
            );
        }

        for (GameObject source : sources) {
            int sourceMin = mins.get(source);
            int sourceMax = maxes.get(source);
            numReserved -= sourceMin;
            // Restrict the source maximum to meet all remaining source minimums
            if (sourceMax > options.size() - numReserved) sourceMax = options.size() - numReserved;
            // reduction to a many-to-one problem for a single source
            List<GameObject> selections = this.chooseManyToOne(source, options, sourceMin, sourceMax, descriptor);
            assignments.put(source, selections);
            options.removeAll(selections);
        }
        return assignments;

    }

    // TODO: have choose*() methods also return the maximized Q Value to allow the Controller to compare action values

    /**
     * Selects the best set of choices for a given source.
     * Used in cases like:
     * - selecting multiple targets of a spell
     * - selecting creatures to convoke a spell
     * - assigning blockers to an attacker
     * - choosing cards in graveyard to exile for delve cost
     *
     * @param source The source associated with the action.
     * @param choices The set of action choices to choose from.
     * @param min The minimum number of choices to select.
     * @param max The maximum number of choices to select.
     * @param descriptor The action descriptor.
     * @return The set of choices that maximizes the predicted Q-Value of the action.
     */
    public List<GameObject> chooseManyToOne(
            final GameObject source,
            final List<? extends GameObject> choices,
            int min, int max,
            String descriptor
    ) {

        // idea: iterate over options and query the model for the avg Q-value of actions that include this option
        // greedy: choose the N best Q-value options ( O(N) )
        // semi-greedy: dynamic programming approach?

        // non-greedy: query the model for the Q-value of each action with combination of selections( O(N!) )
        // List<GameObject> selections = new ArrayList<GameObject>(choices.subList(0,min));

        // check that there are at least as many choices as the minimum required
        if (choices.size() < min) {
            throw new IllegalArgumentException("There are not enough choices to meet the minimum selections required.");
        }

        List<GameObject> bestSelections = new ArrayList<GameObject>();

        // If there are no choices, return an empty list
        if (choices.isEmpty()) {
            return bestSelections;
        }

        // if there is only one choice and min == max == 1, then just return the one option
        if (choices.size() == 1 && min == max && min == 1) {
            bestSelections.add(choices.get(0));
            return bestSelections;
        }

        // if min and max are one, this is just a one-to-one problem.
        if (min==max && min==1){
            bestSelections.add(this.chooseOneToOne(source, choices, descriptor));
            return bestSelections;
        }

        // semi-greedy: choose the best Q-value option and iterate again on options with best option set ( O(N^2) )

        // record the best selections for each possible # of selections from min to max
        List<List<GameObject>> selectionOptions = new ArrayList<> ();
        List<Double> selectionOptionQ = new ArrayList<> ();
        List<GameObject> selections = new ArrayList<GameObject>();
        List<GameObject> options = new ArrayList<>(choices);
        if (min == 0) {
            // TODO: query model with game state, no selections, and action descriptor
            double Q = 0.0;
            selectionOptions.add(new ArrayList<GameObject>());
            selectionOptionQ.add(Q);
        }
        while (selections.size() <= max) {

            // iterate on the remaining options and select the option that maximizes Q value
            double optMaxQ = 0;
            GameObject argmax = null;
            for (GameObject option : options) {
                // TODO: query model with game state, selections + option, and action descriptor
                double Q = 1.0;
                if (Q > optMaxQ) {
                    argmax = option;
                    optMaxQ = Q;
                }
            }
            // move the best choice from options to selections
            selections.add(argmax);
            options.remove(argmax);

            // if the selections are at least the minimum, record for comparison
            if (selections.size() >= min) {
                selectionOptions.add(selections);
                selectionOptionQ.add(optMaxQ);
            }

            // If there are no options (# of options < max choices), stop
            if (options.isEmpty()) {
                break;
            }
        }

        // choose the selection set from length min to max with the best Q value
        bestSelections = selectionOptions.get(0);
        double bestQ = selectionOptionQ.get(0);
        for (int i = 1; i < selectionOptions.size(); i++) {
            if (selectionOptionQ.get(i) > bestQ) {
                bestQ = selectionOptionQ.get(i);
                bestSelections = selectionOptions.get(i);
            }
        }

        return bestSelections;
    }

    /**
     * Selects the singular best choice (via model-predicted Q-Value) for a given source.
     * Used in cases like:
     * - Choosing the target of a single-target spell (Lightning Bolt)
     * - Choosing the next SpellAbility (or pass priority) to use
     * - Choosing a color during resolution of a spell like Prismatic Strands
     *
     * @param source The source of the action.
     * @param choices The target or choice of the action.
     * @param descriptor The name of the action.
     * @return The choice that maximizes the predicted Q-value.
     */
    public GameObject chooseOneToOne(
            final GameObject source,
            final List<? extends GameObject> choices,
            String descriptor
    ) {
        GameObject selection = null;
        double maxQ = 0;
        for (GameObject choice : choices) {
            // TODO :query the model with state, option, and descriptor
            double Q = 1;
            if (Q > maxQ) {
                maxQ = Q;
                selection = choice;
            }
        }
        return selection;
    }

    /**
     * Selects the best set of sources to "link" to the given choice. It is unclear whether this will ever be used,
     * or if chooseManyToOne will always be used instead.
     *
     * @param sources The set of candidate sources to choose from.
     * @param choice The single choice to associate the sources with.
     * @param min The minimum number of sources to select.
     * @param max The maximum number of sources to select.
     * @param descriptor The action descriptor.
     * @return The set of sources that maximizes the predicted Q-Value.
     */
    public List<GameObject> chooseOneToMany(
            final List<GameObject> sources,
            final GameObject choice,
            int min, int max,
            String descriptor
    ) {
        // idea: iterate over options and query the model for the avg Q-value of actions that include this option
        // greedy: choose the N best Q-value options ( O(N) )
        // semi-greedy: dynamic programming approach?

        // non-greedy: query the model for the Q-value of each action with combination of selections( O(N!) )
        // List<GameObject> selections = new ArrayList<GameObject>(choices.subList(0,min));

        // check that there are at least as many choices as the minimum required
        if (sources.size() < min) {
            throw new IllegalArgumentException("There are not enough sources to meet the minimum selections required.");
        }

        List<GameObject> bestSelections = new ArrayList<GameObject>();

        // If there are no choices, return an empty list
        if (sources.isEmpty()) {
            return bestSelections;
        }

        // if there is only one choice and min == max == 1, then just return the one option
        if (sources.size() == 1 && min == max && min == 1) {
            bestSelections.add(sources.get(0));
            return bestSelections;
        }

        // if min and max are one, this is a (backward) one-to-one problem.
        if (min==max && min==1){
            double maxQ = 0;
            GameObject argmax = null;
            for (GameObject source : sources) {
                double Q = 1.0;
                if (Q > maxQ) {
                    maxQ = Q;
                    argmax = source;
                }
            }
            bestSelections.add(argmax);
            return bestSelections;
        }

        // semi-greedy: choose the best Q-value option and iterate again on options with best option set ( O(N^2) )

        // record the best selections for each possible # of selections from min to max
        List<List<GameObject>> selectionOptions = new ArrayList<> ();
        List<Double> selectionOptionQ = new ArrayList<> ();
        List<GameObject> selections = new ArrayList<GameObject>();
        List<GameObject> options = new ArrayList<>(sources);
        if (min == 0) {
            // TODO: query model with game state, no selections, and action descriptor
            double Q = 0.0;
            selectionOptions.add(new ArrayList<GameObject>());
            selectionOptionQ.add(Q);
        }
        while (selections.size() <= max) {

            // iterate on the remaining options and select the option that maximizes Q value
            double optMaxQ = 0;
            GameObject argmax = null;
            for (GameObject option : options) {
                // TODO: query model with game state, selections + option, and action descriptor
                double Q = 1.0;
                if (Q > optMaxQ) {
                    argmax = option;
                    optMaxQ = Q;
                }
            }
            // move the best choice from options to selections
            selections.add(argmax);
            options.remove(argmax);

            // if the selections are at least the minimum, record for comparison
            if (selections.size() >= min) {
                selectionOptions.add(selections);
                selectionOptionQ.add(optMaxQ);
            }
        }

        // choose the selection set from length min to max with the best Q value
        bestSelections = selectionOptions.get(0);
        double bestQ = selectionOptionQ.get(0);
        for (int i = 1; i < selectionOptions.size(); i++) {
            if (selectionOptionQ.get(i) > bestQ) {
                bestQ = selectionOptionQ.get(i);
                bestSelections = selectionOptions.get(i);
            }
        }

        return bestSelections;
    }
}
