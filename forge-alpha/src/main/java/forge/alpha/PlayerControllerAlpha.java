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
 * A prototype for AlphaMTG player controller class
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
        return ComputerUtil.choosePermanentsToSacrifice(player, validTargets, max, sa, false, min == 0);
    }
    // TODO: review

    @Override
    public CardCollectionView choosePermanentsToDestroy(SpellAbility sa, int min, int max, CardCollectionView validTargets, String message) {
        return ComputerUtil.choosePermanentsToSacrifice(player, validTargets, max, sa, true, min == 0);
    }
    // TODO: review

    @Override
    public CardCollectionView chooseCardsForEffect(CardCollectionView sourceList, SpellAbility sa, String title, int min, int max, boolean isOptional, Map<String, Object> params) {
        return brains.chooseCardsForEffect(sourceList, sa, min, max, isOptional, params);
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
        return SpellApiToAi.Converter.get(sa).chooseSingleSpellAbility(player, sa, spells, params);
    }

    // TODO: review
    @Override
    public boolean confirmAction(SpellAbility sa, PlayerActionConfirmMode mode, String message, List<String> options, Card cardToShow, Map<String, Object> params) {
        return getAi().confirmAction(sa, mode, message, params);
    }

    // TODO: review
    @Override
    public boolean confirmBidAction(SpellAbility sa, PlayerActionConfirmMode mode, String string,
                                    int bid, Player winner) {
        return getAi().confirmBidAction(sa, mode, string, bid, winner);
    }

    // TODO: review
    @Override
    public boolean confirmStaticApplication(Card hostCard, PlayerActionConfirmMode mode, String message, String logic) {
        return getAi().confirmStaticApplication(hostCard, logic);
    }

    // confirm Trigger

    }