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
//    public SpellAbilityPicker getSimulationPicker() {
//        return simPicker;
//    }

    public Game getGame() {
        return game;
    }

    public Player getPlayer() {
        return player;
    }

    // Unsure what this is used for
//    public AiPlayDecision canPlaySa(SpellAbility sa) {
//        if (!checkAiSpecificRestrictions(sa)) {
//            return AiPlayDecision.CantPlayAi;
//        }
//        if (sa instanceof WrappedAbility) {
//            return canPlaySa(((WrappedAbility) sa).getWrappedAbility());
//        }
//
//        if (!sa.canCastTiming(player)) {
//            return AiPlayDecision.AnotherTime;
//        }
//
//        final Card card = sa.getHostCard();
//
//        // Trying to play a card that has Buyback without a Buyback cost, look for possible additional considerations
//        if (getBooleanProperty(AiProps.TRY_TO_PRESERVE_BUYBACK_SPELLS)) {
//            if (card.hasKeyword(Keyword.BUYBACK) && !sa.isBuyback() && !canPlaySpellWithoutBuyback(card, sa)) {
//                return AiPlayDecision.NeedsToPlayCriteriaNotMet;
//            }
//        }
//
//        // When processing a new SA, clear the previously remembered cards that have been marked to avoid re-entry
//        // which might potentially cause a stack overflow.
//        memory.clearMemorySet(AiCardMemory.MemorySet.MARKED_TO_AVOID_REENTRY);
//
//        // TODO before suspending some spells try to predict if relevant targets can be expected
//        if (sa.getApi() != null) {
//
//            String msg = "AiController:canPlaySa: AI checks for if can PlaySa";
//            Breadcrumb bread = new Breadcrumb(msg);
//            bread.setData("Api", sa.getApi().toString());
//            bread.setData("Card", card.getName());
//            bread.setData("SA", sa.toString());
//            Sentry.addBreadcrumb(bread);
//
//            // add Extra for debugging
//            Sentry.setExtra("Card", card.getName());
//            Sentry.setExtra("SA", sa.toString());
//
//            boolean canPlay = SpellApiToAi.Converter.get(sa).canPlayAIWithSubs(player, sa);
//
//            // remove added extra
//            Sentry.removeExtra("Card");
//            Sentry.removeExtra("SA");
//
//            if (!canPlay) {
//                return AiPlayDecision.CantPlayAi;
//            }
//        } else {
//            Cost payCosts = sa.getPayCosts();
//            if (payCosts != null) {
//                ManaCost mana = payCosts.getTotalMana();
//                if (mana != null) {
//                    if (mana.countX() > 0) {
//                        // Set PayX here to maximum value.
//                        final int xPay = ComputerUtilCost.getMaxXValue(sa, player, sa.isTrigger());
//                        if (xPay <= 0) {
//                            return AiPlayDecision.CantAffordX;
//                        }
//                        sa.setXManaCostPaid(xPay);
//                    } else if (mana.isZero()) {
//                        // if mana is zero, but card mana cost does have X, then something is wrong
//                        ManaCost cardCost = card.getManaCost();
//                        if (cardCost != null && cardCost.countX() > 0) {
//                            return AiPlayDecision.CantPlayAi;
//                        }
//                    }
//                }
//            }
//        }
//        if (checkCurseEffects(sa)) {
//            return AiPlayDecision.CurseEffects;
//        }
//        // TODO maybe other location for this?
//        if (!sa.isLegalAfterStack()) {
//            return AiPlayDecision.AnotherTime;
//        }
//        Card spellHost = card;
//        if (sa.isSpell()) {
//            spellHost = CardCopyService.getLKICopy(spellHost);
//            spellHost.setLKICMC(-1); // to reset the cmc
//            spellHost.setLastKnownZone(game.getStackZone()); // need to add to stack to make check Restrictions respect stack cmc
//            spellHost.setCastFrom(card.getZone());
//        }
//        if (!sa.checkRestrictions(spellHost, player)) {
//            return AiPlayDecision.AnotherTime;
//        }
//        if (sa.usesTargeting()) {
//            if (!sa.isTargetNumberValid() && sa.getTargetRestrictions().getNumCandidates(sa, true) == 0) {
//                return AiPlayDecision.TargetingFailed;
//            }
//            if (!StaticAbilityMustTarget.meetsMustTargetRestriction(sa)) {
//                return AiPlayDecision.TargetingFailed;
//            }
//        }
//        if (sa instanceof Spell) {
//            if (sa.getApi() == ApiType.PermanentCreature || sa.getApi() == ApiType.PermanentNoncreature) {
//                return canPlayFromEffectAI((Spell) sa, false, true);
//            }
//            if (!player.cantLoseForZeroOrLessLife() && player.canLoseLife() &&
//                    ComputerUtil.getDamageForPlaying(player, sa) >= player.getLife()) {
//                return AiPlayDecision.CurseEffects;
//            }
//            return canPlaySpellBasic(card, sa);
//        }
//
//        return AiPlayDecision.WillPlay;
//    }

    // Three discard methods
    // TODO: combine these into one or two
    public CardCollection getCardsToDiscard(final int numDiscard, final String[] uTypes, final SpellAbility sa) {
        return getCardsToDiscard(numDiscard, uTypes, sa, CardCollection.EMPTY);
    }

    public CardCollection getCardsToDiscard(final int numDiscard, final String[] uTypes, final SpellAbility sa, final CardCollectionView exclude) {
        boolean noFiltering = sa != null && "DiscardCMCX".equals(sa.getParam("AILogic")); // list AI logic for which filtering is taken care of elsewhere
        CardCollection hand = new CardCollection(player.getCardsIn(ZoneType.Hand));
        hand.removeAll(exclude);
        if (uTypes != null && sa != null && !noFiltering) {
            hand = CardLists.getValidCards(hand, uTypes, sa.getActivatingPlayer(), sa.getHostCard(), sa);
        }
        return getCardsToDiscard(numDiscard, numDiscard, hand, sa);
    }

    public CardCollection getCardsToDiscard(int min, final int max, final CardCollection validCards, final SpellAbility sa) {
        if (validCards.size() <= min) {
            return validCards; //return all valid cards since they will be discarded without filtering needed
        }

        Card sourceCard = null;
        final CardCollection discardList = new CardCollection();

        // A simple demonstration of choosing cards to discard
        // this implementation is purely random
        Random discardGenerator = new Random();
        int numToDiscard = min + discardGenerator.nextInt(max - min + 1);
        int numCards = validCards.size();
        for (int i = 0; i < numToDiscard; i++) {
            discardList.add(validCards.get(discardGenerator.nextInt(numCards)));
        }

        // TODO: have AI predict Q-value for each/some sets of cards to discard and choose the best set

        return discardList;
    }

    // declares blockers for given defender in a given combat
    public void declareBlockersFor(Player defender, Combat combat) {
        // A simple demo of assigning blockers to each attacker randomly
        Random blockerGenerator = new Random();
        List<Card> possibleBlockers = player.getCreaturesInPlay();
        for (Card a : combat.getAttackersOf(defender)) {
            if (CombatUtil.canBeBlocked(a, null, defender)) {
                int numFreeBlockers = possibleBlockers.size();
                int blockerNum = blockerGenerator.nextInt(numFreeBlockers + 1);
                // this number indicates no block
                if (blockerNum == numFreeBlockers) {
                    continue;
                }
                Card b = possibleBlockers.get(blockerNum);
                if (CombatUtil.canBlock(b, combat)) {
                    possibleBlockers.remove(blockerNum);
                    combat.addBlocker(a, b);
                }
            }
        }

        // TODO: have AI predict Q-value for each/some sets of blocker assignments and choose the best
    }

    public void declareAttackers(Player attacker, Combat combat) {
        // basic demo of assigning attackers
        Random attackerGenerator = new Random();
        final FCollectionView<GameEntity> defs = combat.getDefenders();
        List<Card> myCreatures = player.getCreaturesInPlay();
        List<Card> attackers = new ArrayList<Card>();
        for (GameEntity def : defs) {
            // generate a list of valid attackers of defender def
            List<Card> validAttackers = new ArrayList<Card>();
            for (Card c : myCreatures) {
                if (CombatUtil.canAttack(c, def)) {
                    validAttackers.add(c);
                }
            }

            // choose a random attacker (or none)
            int attackerNum = attackerGenerator.nextInt(validAttackers.size()+1);
            if (attackerNum==validAttackers.size()) {
                continue;
            }
            Card a = validAttackers.get(attackerNum);
            if (attackers.contains(a)) {
                continue;
            }
            combat.addAttacker(a, def);
            attackers.add(a);

        }

        for (final Card element : combat.getAttackers()) {
            // tapping of attackers happens after Propaganda is paid for
            Log.debug("Computer just assigned " + element.getName() + " as an attacker.");
        }

        // TODO: have AI predict Q-value for some/each set of attacker assignments and choose best one
    }

    private List<SpellAbility> singleSpellAbilityList(SpellAbility sa) {
        if (sa == null) {
            return null;
        }
        return Lists.newArrayList(sa);
    }

    public List<SpellAbility> chooseSpellAbilityToPlay() {


        if (useSimulation) {
            ;
        }

        // Basic demo of choosing a spell-ability to play while having priority

        Random spellAbilityGenerator = new Random();

        CardCollection playBeforeLand = CardLists.filter(
                player.getCardsIn(ZoneType.Hand), CardPredicates.hasSVar("PlayBeforeLandDrop")
        );
        if (!playBeforeLand.isEmpty()) {
            List<SpellAbility> spellAbilityBeforeLand =
                    AlphaUtilAbility.getSpellAbilities(playBeforeLand, player);

            int spellAbilityToPlay = spellAbilityGenerator.nextInt(spellAbilityBeforeLand.size() + 1);
            if (spellAbilityToPlay < spellAbilityBeforeLand.size()) {
                return singleSpellAbilityList(spellAbilityBeforeLand.get(spellAbilityToPlay));
            }
        }

        CardCollection landsPlayable = AlphaUtilAbility.getAvailableLandsToPlay(game, player);
        if (landsPlayable != null) {
            int landToPlay = spellAbilityGenerator.nextInt(landsPlayable.size() + 1);
            if (landToPlay < landsPlayable.size()) {
                Card land = landsPlayable.get(landToPlay);

                final List<SpellAbility> abilities = land.getAllPossibleAbilities(player, true);
                // skip non Land Abilities
                abilities.removeIf(sa -> !sa.isLandAbility());

                if (!abilities.isEmpty()) {
                    // TODO extend this logic to evaluate MDFC with both sides land
                    return abilities;
                }
            }
        }

        return null;

        // TODO: have AI predict Q-value of all spell abilities (and passing) and choose the best one
    }
}
