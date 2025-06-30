package forge.alpha;

import com.google.common.collect.*;
import forge.LobbyPlayer;
//import forge.ai.ability.ProtectAi;
import forge.card.CardStateName;
import forge.card.ColorSet;
import forge.card.ICardFace;
import forge.card.MagicColor;
import forge.card.mana.ManaCost;
import forge.card.mana.ManaCostShard;
import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.game.*;
import forge.game.ability.AbilityUtils;
import forge.game.ability.ApiType;
import forge.game.ability.effects.CharmEffect;
import forge.game.ability.effects.RollDiceEffect;
import forge.game.card.*;
import forge.game.combat.Combat;
import forge.game.cost.Cost;
import forge.game.cost.CostEnlist;
import forge.game.cost.CostPart;
import forge.game.cost.CostPartMana;
import forge.game.cost.CostPayment;
import forge.game.keyword.Keyword;
import forge.game.keyword.KeywordInterface;
import forge.game.mana.Mana;
import forge.game.mana.ManaConversionMatrix;
import forge.game.mana.ManaCostBeingPaid;
import forge.game.phase.PhaseHandler;
import forge.game.phase.PhaseType;
import forge.game.player.*;
import forge.game.replacement.ReplacementEffect;
import forge.game.spellability.*;
import forge.game.staticability.StaticAbility;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;
import forge.game.trigger.WrappedAbility;
import forge.game.zone.PlayerZone;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;
import forge.util.*;
import forge.util.collect.FCollection;
import forge.util.collect.FCollectionView;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.function.Predicate;


/**
 * A prototype for AlphaMTG player controller class.
 * I'm leaving in references to other Ai*Controller classes so I remember what methods need implementation.
 *
 * Handles phase skips for now.
 */
public class PlayerControllerAlpha extends PlayerController {
    private final AlphaController brains;

    // private boolean pilotsNonAggroDeck = false;

    public PlayerControllerAlpha(Game game, Player p, LobbyPlayer lp) {
        super(game, p, lp);

        brains = new AlphaController(p, game);
    }

    @Override
    public SpellAbility getAbilityToPlay(Card hostCard, List<SpellAbility> abilities, ITriggerEvent triggerEvent) {
        if (abilities.isEmpty()) {
            return null;
        }
        return abilities.get(0);
    }

    public AlphaController getAi() { return brains; }

    @Override
    public boolean isAI() { return true; }

    // Given a maindeck and sideboard, have the AI choose cards to swap between main and sideboard
    // Return new maindeck
    // For now, no changes
    // TODO: AI sideboard logic
    // simulate games with various sideboard combinations against cards seen/inferred archetype?
    @Override
    public List<PaperCard> sideboard(Deck deck, GameType gameType, String message) {
        List<PaperCard> main = deck.get(DeckSection.Main).toFlatList();
        List<PaperCard> sideboard = deck.get(DeckSection.Sideboard).toFlatList();
        return null;
    }

    // distribute combat damage among blockers
    // TODO: implement AI damage distribution
    @Override
    public Map<Card, Integer> assignCombatDamage(Card attacker, CardCollectionView blockers, CardCollectionView remaining, int damageDealt, GameEntity defender, boolean overrideOrder) {
        return null;
    }

    // Something to do with shield counters?
    @Override
    public Map<GameEntity, Integer> divideShield(Card effectSource, Map<GameEntity, Integer> affected, int shieldAmount) {
        return new HashMap<>();
    }

    // specify combination of mana to use for spellability
    @Override
    public Map<Byte, Integer> specifyManaCombo(SpellAbility sa, ColorSet colorSet, int manaAmount, boolean different) {
        return new HashMap<>();
    }

    // unsure what the usecase for this function is
    @Override
    public Integer announceRequirements(SpellAbility ability, String announce) {
        return null;
    }
    // TODO: review

    @Override
    public CardCollectionView choosePermanentsToSacrifice(SpellAbility sa, int min, int max, CardCollectionView validTargets, String message) {
        return new CardCollection(validTargets.subList(0,min));
        //return ComputerUtil.choosePermanentsToSacrifice(player, validTargets, max, sa, false, min == 0);
    }
    // TODO: review

    @Override
    public CardCollectionView choosePermanentsToDestroy(SpellAbility sa, int min, int max, CardCollectionView validTargets, String message) {
        return new CardCollection(validTargets.subList(0,min));
        //return ComputerUtil.choosePermanentsToSacrifice(player, validTargets, max, sa, true, min == 0);
    }
    // TODO: review

    @Override
    public CardCollectionView chooseCardsForEffect(CardCollectionView sourceList, SpellAbility sa, String title, int min, int max, boolean isOptional, Map<String, Object> params) {
        return new CardCollection(sourceList.subList(0, min));
        //return brains.chooseCardsForEffect(sourceList, sa, min, max, isOptional, params);
    }

    // TODO: review
    @Override
    public List<Card> chooseContraptionsToCrank(List<Card> contraptions) {
        return CardLists.filter(contraptions, c -> {
            Trigger crankTrigger = IterableUtil.find(c.getTriggers(), t -> t.getMode() == TriggerType.CrankContraption);
            return confirmTrigger(new WrappedAbility(crankTrigger, crankTrigger.getOverridingAbility(), player));
        });
    }

    @Override
    public boolean helpPayForAssistSpell(ManaCostBeingPaid cost, SpellAbility sa, int max, int requested) {
        return true;
    }

    @Override
    public Player choosePlayerToAssistPayment(FCollectionView<Player> optionList, SpellAbility sa, String title, int max) {
        return null;
    }

    // TODO: review
    @Override
    public <T extends GameEntity> T chooseSingleEntityForEffect(FCollectionView<T> optionList, DelayedReveal delayedReveal, SpellAbility sa, String title, boolean isOptional, Player targetedPlayer, Map<String, Object> params) {
        return null;
    }

    @Override
    public <T extends GameEntity> List<T> chooseEntitiesForEffect(
            FCollectionView<T> optionList, int min, int max, DelayedReveal delayedReveal, SpellAbility sa, String title,
            Player targetedPlayer, Map<String, Object> params) {
        return null;
    }

    @Override
    public List<SpellAbility> chooseSpellAbilitiesForEffect(List<SpellAbility> spells, SpellAbility sa, String title, int num, Map<String, Object> params) {
        return null;
    }

    // TODO: review
    @Override
    public SpellAbility chooseSingleSpellForEffect(List<SpellAbility> spells, SpellAbility sa, String title,
                                                   Map<String, Object> params) {
        return spells.getFirst();
        // return SpellApiToAi.Converter.get(sa).chooseSingleSpellAbility(player, sa, spells, params);
    }

    // TODO: review
    @Override
    public boolean confirmAction(SpellAbility sa, PlayerActionConfirmMode mode, String message, List<String> options, Card cardToShow, Map<String, Object> params) {
        return true;
        //return brains.confirmAction(sa, mode, message, params);
    }

    // TODO: review
    @Override
    public boolean confirmBidAction(SpellAbility sa, PlayerActionConfirmMode mode, String string,
                                    int bid, Player winner) {
        return true;
        //return brains.confirmBidAction(sa, mode, string, bid, winner);
    }

    // TODO: review
    @Override
    public boolean confirmStaticApplication(Card hostCard, PlayerActionConfirmMode mode, String message, String logic) {
        return true;
        //return brains.confirmStaticApplication(hostCard, logic);
    }

    // confirm Trigger
    @Override
    public boolean confirmTrigger(WrappedAbility wrapper) {
        return true;
    }

    @Override
    public boolean confirmPayment(CostPart costPart, String prompt, SpellAbility sa) {
        return true;
        //return brains.confirmPayment(costPart); // AI is expected to know what it is paying for at the moment (otherwise add another parameter to this method)
    }

    @Override
    public boolean confirmReplacementEffect(ReplacementEffect replacementEffect, SpellAbility effectSA, GameEntity affected, String question) {
        return true;
        //return brains.aiShouldRun(replacementEffect, effectSA, affected);
    }

    @Override
    public List<Card> exertAttackers(List<Card> attackers) {
        return Lists.newArrayList();
        //return AiAttackController.exertAttackers(attackers, brains.getAttackAggression());
    }

    @Override
    public List<Card> enlistAttackers(List<Card> attackers) {
        return null;
    }

    @Override
    public CardCollection orderBlockers(Card attacker, CardCollection blockers) {
        return blockers;
    }

    @Override
    public CardCollection orderBlocker(Card attacker, Card blocker, CardCollection oldBlockers) {
        final CardCollection allBlockers = new CardCollection(oldBlockers);
        allBlockers.add(blocker);
        return allBlockers;
        //return AiBlockController.orderBlocker(attacker, blocker, oldBlockers);
    }

    @Override
    public CardCollection orderAttackers(Card blocker, CardCollection attackers) {
        return attackers;
        //return AiBlockController.orderAttackers(blocker, attackers);
    }


    // For now, we will leave memory implementation untouched
    // And not incorporate into model inputs
    @Override
    public void reveal(CardCollectionView cards, ZoneType zone, Player owner, String messagePrefix, boolean addSuffix) {
        for (Card c : cards) {
            //AiCardMemory.rememberCard(player, c, AiCardMemory.MemorySet.REVEALED_CARDS);
        }
    }

    @Override
    public void reveal(List<CardView> cards, ZoneType zone, PlayerView owner, String messagePrefix, boolean addSuffix) {
        for (CardView cv : cards) {
            //AiCardMemory.rememberCard(player, player.getGame().findByView(cv), AiCardMemory.MemorySet.REVEALED_CARDS);
        }
    }

    @Override
    public ImmutablePair<CardCollection, CardCollection> arrangeForScry(CardCollection topN) {
        CardCollection toBottom = new CardCollection();
        CardCollection toTop = new CardCollection();
        // For now, put all on top
        toTop.addAll(topN);
        return ImmutablePair.of(toTop, toBottom);
    }


    // For now, all on top
    @Override
    public ImmutablePair<CardCollection, CardCollection> arrangeForSurveil(CardCollection topN) {
        CardCollection toGraveyard = new CardCollection();
        CardCollection toTop = new CardCollection();
        toTop.addAll(topN);
        return ImmutablePair.of(toTop, toGraveyard);
    }

    // default to bottom
    @Override
    public boolean willPutCardOnTop(Card c) {
        return false;
    }

    // default to no changes
    @Override
    public CardCollectionView orderMoveToZoneList(CardCollectionView cards, ZoneType destinationZone, SpellAbility source) {
        return cards;
    }

    @Override
    public CardCollection chooseCardsToDiscardFrom(Player p, SpellAbility sa, CardCollection validCards, int min, int max) {
        if (p == player) {
            return brains.getCardsToDiscard(min, max, validCards, sa);
        }
        return validCards.subList(0, min);
    }

    @Override
    public void playSpellAbilityNoStack(SpellAbility effectSA, boolean canSetupTargets) {
        ;
    }

    // delve the first N cards found
    @Override
    public CardCollectionView chooseCardsToDelve(int genericAmount, CardCollection grave) {
        return grave.subList(0, genericAmount);
    }

    @Override
    public CardCollectionView chooseCardsToDiscardUnlessType(int num, CardCollectionView hand, String uType, SpellAbility sa) {
        // Iterable<Card> cardsOfType = IterableUtil.filter(hand, CardPredicates.restriction(uType.split(","), sa.getActivatingPlayer(), sa.getHostCard(), sa));
        return new CardCollection(hand.subList(0,num));
    }

    @Override
    public Mana chooseManaFromPool(List<Mana> manaChoices) {
        return manaChoices.get(0);
    }

    @Override
    public String chooseSomeType(String kindOfType, SpellAbility sa, Collection<String> validTypes, boolean isOptional) {
        return validTypes.iterator().next();
    }

    @Override
    public Object vote(SpellAbility sa, String prompt, List<Object> options, ListMultimap<Object, Player> votes, Player forPlayer, boolean optional) {
        return null;
    }

    @Override
    public String chooseSector(Card assignee, String ai, List<String> sectors){
        return Aggregates.random(sectors);
    }

    @Override
    public int chooseSprocket(Card assignee, boolean forceDifferent) {
        int nextSprocket = (player.getCrankCounter() % 3) + 1;
        if(forceDifferent && nextSprocket == assignee.getSprocket())
            return (nextSprocket % 3) + 1;
        return nextSprocket;
    }

    @Override
    public PlanarDice choosePDRollToIgnore(List<PlanarDice> rolls) {
        //TODO create AI logic for this
        return Aggregates.random(rolls);
    }

    @Override
    public Integer chooseRollToIgnore(List<Integer> rolls) {
        //TODO create AI logic for this
        return Aggregates.random(rolls);
    }

    @Override
    public List<Integer> chooseDiceToReroll(List<Integer> rolls) {
        //TODO create AI logic for this
        return new ArrayList<>();
    }

    @Override
    public Integer chooseRollToModify(List<Integer> rolls) {
        //TODO create AI logic for this
        return Aggregates.random(rolls);
    }

    @Override
    public RollDiceEffect.DieRollResult chooseRollToSwap(List<RollDiceEffect.DieRollResult> rolls) {
        //TODO create AI logic for this
        return Aggregates.random(rolls);
    }

    @Override
    public String chooseRollSwapValue(List<String> swapChoices, Integer currentResult, int power, int toughness) {
        //TODO create AI logic for this
        return Aggregates.random(swapChoices);
    }

    @Override
    public boolean mulliganKeepHand(Player firstPlayer, int cardsToReturn) {
        return true;
    }

    @Override
    public CardCollectionView londonMulliganReturnCards(final Player mulliganingPlayer, int cardsToReturn) {
        CardCollection hand = new CardCollection(player.getCardsIn(ZoneType.Hand));
        return hand.subList(0, cardsToReturn);
    }

    @Override
    public void declareAttackers(Player attacker, Combat combat) {
        brains.declareAttackers(attacker, combat);
    }

    @Override
    public void declareBlockers(Player defender, Combat combat) {
        brains.declareBlockersFor(defender, combat);
    }

    @Override
    public List<SpellAbility> chooseSpellAbilityToPlay() {
        return brains.chooseSpellAbilityToPlay();
    }

    @Override
    public boolean playChosenSpellAbility(SpellAbility sa) {
        return true;
    }

    @Override
    public CardCollection chooseCardsToDiscardToMaximumHandSize(int numDiscard) {
        return brains.getCardsToDiscard(numDiscard, null, null);
    }

    @Override
    public CardCollection chooseCardsToRevealFromHand(int min, int max, CardCollectionView valid) {
        int numCardsToReveal = Math.min(max, valid.size());
        return numCardsToReveal == 0 ? new CardCollection() : (CardCollection)valid.subList(0, numCardsToReveal);
    }

    @Override
    public Player chooseStartingPlayer(boolean isFirstgame) {
        return this.player; // AI is brave :)
    }

    @Override
    public PlayerZone chooseStartingHand(List<PlayerZone> zones) {
        // Rate all the hands using the AI's hand evaluation function
        int bestScore = Integer.MIN_VALUE;
        PlayerZone bestZone = null;
        for (PlayerZone zone : zones) {
            int score = 1; // brains.scoreHand(zone.getCards(), this.player, 0);
            if (score > bestScore) {
                bestScore = score;
                bestZone = zone;
            }
        }

        return bestZone;
    }

    @Override
    public List<SpellAbility> chooseSaToActivateFromOpeningHand(List<SpellAbility> usableFromOpeningHand) {
        return null;
    }

    @Override
    public int chooseNumber(SpellAbility sa, String title, int min, int max) {
        return min;
    }

    @Override
    public int chooseNumber(SpellAbility sa, String string, int min, int max, Map<String, Object> params) {
        return min;
    }

    @Override
    public int chooseNumber(SpellAbility sa, String title, List<Integer> options, Player relatedPlayer) {
        return options.get(0);
    }

    @Override
    public boolean chooseFlipResult(SpellAbility sa, Player flipper, boolean[] results, boolean call) {
        return true;
    }

    @Override
    public Pair<SpellAbilityStackInstance, GameObject> chooseTarget(SpellAbility saSrc, List<Pair<SpellAbilityStackInstance, GameObject>> allTargets) {
        // TODO Teach AI how to determine the most damaging subability when retargeting a spell
        // with multiple targets (Arc Lightning, Cone of Flame, etc.) with Spellskite
        // (currently simply always returns the first valid target ability)
        return allTargets.get(0);
    }

    @Override
    public void notifyOfValue(SpellAbility saSource, GameObject realtedTarget, String value) {
        // AI should take into consideration creature types, numbers and other information (mostly choices) arriving through this channel
    }

    @Override
    public boolean chooseBinary(SpellAbility sa, String question, BinaryChoiceType kindOfChoice, Boolean defaultVal) {
        switch (kindOfChoice) {
            case TapOrUntap: return true;
            case UntapOrLeaveTapped: return true;
            case LeftOrRight: return true;
            case OddsOrEvens: return true;
            default:
                return true;
        }
    }

    @Override
    public boolean chooseBinary(SpellAbility sa, String question, BinaryChoiceType kindOfChoice, Map<String, Object> params) {
        return true;
    }

    @Override
    public List<AbilitySub> chooseModeForAbility(SpellAbility sa, List<AbilitySub> possible, int min, int num, boolean allowRepeat) {
        return possible.subList(0,min);
    }

    @Override
    public byte chooseColorAllowColorless(String message, Card card, ColorSet colors) {
        return Iterables.getFirst(colors, (byte)0);
    }

    @Override
    public byte chooseColor(String message, SpellAbility sa, ColorSet colors) {
        return Iterables.getFirst(colors, (byte)0);
    }

    @Override
    public List<String> chooseColors(String message, SpellAbility sa, int min, int max, List<String> options) {
        return options.subList(0,min);
    }

    @Override
    public CounterType chooseCounterType(List<CounterType> options, SpellAbility sa, String prompt,
                                         Map<String, Object> params) {
        return Iterables.getFirst(options, null);
    }

    @Override
    public String chooseKeywordForPump(final List<String> options, final SpellAbility sa, final String prompt, final Card tgtCard) {
        return Iterables.getFirst(options, null);
    }


    @Override
    public ReplacementEffect chooseSingleReplacementEffect(List<ReplacementEffect> possibleReplacers) {
        return possibleReplacers.getFirst();
    }

    @Override
    public StaticAbility chooseSingleStaticAbility(String prompt, List<StaticAbility> possibleStatics) {
        // only matters in corner cases
        return Iterables.getFirst(possibleStatics, null);
    }

    @Override
    public String chooseProtectionType(String string, SpellAbility sa, List<String> choices) {
        return Iterables.getFirst(choices, null);
    }

    @Override
    public boolean payManaCost(ManaCost toPay, CostPartMana costPartMana, SpellAbility sa, String prompt /* ai needs hints as well */, ManaConversionMatrix matrix, boolean effect) {
        return true;
    }

    @Override
    public boolean payCombatCost(Card c, Cost cost, SpellAbility sa, String prompt) {
        return true;
    }

    @Override
    public boolean payCostToPreventEffect(Cost cost, SpellAbility sa, boolean alreadyPaid, FCollectionView<Player> allPayers) {
        return true;
    }

    public boolean payCostDuringRoll(final Cost cost, final SpellAbility sa, final FCollectionView<Player> allPayers) {
        // TODO logic for AI to pay rerolls and modification costs
        return false;
    }

    @Override
    public void orderAndPlaySimultaneousSa(List<SpellAbility> activePlayerSAs) {
        ;
    }

    @Override
    public boolean playTrigger(Card host, WrappedAbility wrapperAbility, boolean isMandatory) {
        return false;
    }

    @Override
    public boolean playSaFromPlayEffect(SpellAbility tgtSA) {
        return true;
    }

    @Override
    public boolean chooseTargetsFor(SpellAbility currentAbility) {
        return true;
    }

    @Override
    public TargetChoices chooseNewTargetsFor(SpellAbility ability, Predicate<GameObject> filter, boolean optional) {
        // AI currently can't do this. But when it can it will need to be based on Ability API
        return null;
    }

    @Override
    public boolean chooseCardsPile(SpellAbility sa, CardCollectionView pile1, CardCollectionView pile2, String faceUp) {
        return false;
    }

    @Override
    public void revealAnte(String message, Multimap<Player, PaperCard> removedAnteCards) {
        // Ai won't understand that anyway
    }

    @Override
    public void revealAISkipCards(String message, Map<Player, Map<DeckSection, List<? extends PaperCard>>> deckCards) {
        // Ai won't understand that anyway
    }

    @Override
    public Map<DeckSection, List<? extends PaperCard>> complainCardsCantPlayWell(Deck myDeck) {
        return null;
    }

    @Override
    public CardCollectionView cheatShuffle(CardCollectionView list) {
        return list;
    }

    @Override
    public List<PaperCard> chooseCardsYouWonToAddToDeck(List<PaperCard> losses) {
        // TODO AI takes all by default
        return losses;
    }

    @Override
    public Map<Card, ManaCostShard> chooseCardsForConvokeOrImprovise(SpellAbility sa, ManaCost manaCost, CardCollectionView untappedCards, boolean improvise) {
        return null;
    }

    @Override
    public String chooseCardName(SpellAbility sa, List<ICardFace> faces, String message) {
        return "Tarmogoyf";
    }


    @Override
    public String chooseCardName(SpellAbility sa, Predicate<ICardFace> cpp, String valid, String message) {
        return "Tarmogoyf";
    }

    @Override
    public Card chooseSingleCardForZoneChange(ZoneType destination,
                                              List<ZoneType> origin, SpellAbility sa, CardCollection fetchList, DelayedReveal delayedReveal,
                                              String selectPrompt, boolean isOptional, Player decider) {
        return fetchList.get(0);
    }

    @Override
    public List<Card> chooseCardsForZoneChange(
            ZoneType destination, List<ZoneType> origin, SpellAbility sa, CardCollection fetchList, int min, int max,
            DelayedReveal delayedReveal, String selectPrompt, Player decider) {
        // this isn't used
        return null;
    }

    @Override
    public void resetAtEndOfTurn() {
        // TODO - if card memory is ever used to remember something for longer than a turn, make sure it's not reset here.
        //getAi().getCardMemory().clearAllRemembered();
    }

    @Override
    public void autoPassCancel() {
        // Do nothing
    }

    @Override
    public void awaitNextInput() {
        // Do nothing
    }
    @Override
    public void cancelAwaitNextInput() {
        // Do nothing
    }

    @Override
    public ICardFace chooseSingleCardFace(SpellAbility sa, List<ICardFace> faces, String message) {
        return faces.getFirst();
    }

    @Override
    public ICardFace chooseSingleCardFace(SpellAbility sa, String message, Predicate<ICardFace> cpp, String name) {
        throw new UnsupportedOperationException("Should not be called for AI"); // or implement it if you know how
    }

    @Override
    public CardState chooseSingleCardState(SpellAbility sa, List<CardState> states, String message, Map<String, Object> params) {
        return states.getFirst();
    }

    @Override
    public Card chooseDungeon(Player ai, List<PaperCard> dungeonCards, String message) {
        return Card.fromPaperCard(dungeonCards.getFirst(), ai);
    }

    @Override
    public List<Card> chooseCardsForSplice(SpellAbility sa, List<Card> cards) {
        return Lists.newArrayList();
    }

    @Override
    public List<OptionalCostValue> chooseOptionalCosts(SpellAbility chosen, List<OptionalCostValue> optionalCostValues) {
        return Lists.newArrayList();
    }

    @Override
    public boolean confirmMulliganScry(Player p) {
        // Always true?
        return true;
    }

    @Override
    public int chooseNumberForKeywordCost(SpellAbility sa, Cost cost, KeywordInterface keyword, String prompt, int max) {
        return max;
    }

    @Override
    public int chooseNumberForCostReduction(final SpellAbility sa, final int min, final int max) {
        return max;
    }

    @Override
    public List<CostPart> orderCosts(List<CostPart> costs) {
        return costs;
    }

    @Override
    public CardCollection chooseCardsForEffectMultiple(Map<String, CardCollection> validMap, SpellAbility sa, String title, boolean isOptional) {
        CardCollection choices = new CardCollection();
        return choices;
    }

}