package forge.alpha;

import com.google.common.collect.*;
import forge.LobbyPlayer;
import forge.StaticData;
//import forge.ai.GameState;
//import forge.ai.PlayerControllerAi;
import forge.card.*;
import forge.card.mana.ManaCost;
import forge.card.mana.ManaCostShard;
import forge.deck.CardPool;
import forge.deck.Deck;
import forge.deck.DeckRecognizer;
import forge.deck.DeckSection;
import forge.game.*;
import forge.game.ability.AbilityKey;
import forge.game.ability.AbilityUtils;
import forge.game.ability.ApiType;
import forge.game.ability.effects.RollDiceEffect;
import forge.game.card.*;
import forge.game.card.CardView.CardStateView;
import forge.game.combat.Combat;
import forge.game.combat.CombatUtil;
import forge.game.cost.Cost;
import forge.game.cost.CostPart;
import forge.game.cost.CostPartMana;
import forge.game.keyword.Keyword;
import forge.game.keyword.KeywordInterface;
import forge.game.mana.Mana;
import forge.game.mana.ManaConversionMatrix;
import forge.game.mana.ManaCostBeingPaid;
import forge.game.player.*;
import forge.game.replacement.ReplacementEffect;
import forge.game.replacement.ReplacementEffectView;
import forge.game.spellability.*;
import forge.game.staticability.StaticAbility;
import forge.game.staticability.StaticAbilityView;
import forge.game.trigger.Trigger;
import forge.game.trigger.WrappedAbility;
import forge.game.zone.MagicStack;
import forge.game.zone.PlayerZone;
import forge.game.zone.Zone;
import forge.game.zone.ZoneType;
// these commented out packages are in forge-gui
//import forge.gamemodes.match.NextGameDecision;
//import forge.gamemodes.match.input.*;
import forge.item.IPaperCard;
import forge.item.PaperCard;
//import forge.localinstance.achievements.AchievementCollection;
//import forge.localinstance.properties.ForgeConstants;
//import forge.localinstance.properties.ForgePreferences.FPref;
//import forge.model.FModel;
import forge.trackable.TrackableCollection;
import forge.util.*;
import forge.util.collect.FCollectionView;
import forge.util.collect.FCollection;
import io.sentry.Sentry;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.Range;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.io.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * A prototype for player controller class
 * <p>
 * Handles phase skips for now.
 */
public class PlayerControllerAlpha extends PlayerController {
    // localizing needed? not likely
    private final Localizer localizer = Localizer.getInstance();

    protected Map<SpellAbilityView, SpellAbility> spellViewCache = null;

    private final AlphaController brains;

    // private boolean pilotsNonAggroDeck = false;

    public PlayerControllerAlpha(Game game, Player p, LobbyPlayer lp) {
        super(game, p, lp);

        brains = new AlphaController(p, game);
    }

    //public PlayerView getLocalPlayerView() {
    //    return player == null ? null : player.getView();
    //}


    // TODO: Unsure if any of the tempShow functions are useful for the AI
    private final ArrayList<Card> tempShownCards = new ArrayList<>();

    public <T> void tempShow(final Iterable<T> objects) {
        for (final T t : objects) {
            // assume you may see any card passed through here
            if (t instanceof Card) {
                tempShowCard((Card) t);
            } else if (t instanceof CardView) {
                tempShowCard(getCard((CardView) t));
            }
        }
    }

    private void tempShowCard(final Card c) {
        if (c == null) {
            return;
        }
        tempShownCards.add(c);
        c.addMayLookTemp(player);
    }

    @Override
    public void tempShowCards(final Iterable<Card> cards) {
        for (final Card c : cards) {
            tempShowCard(c);
        }
    }

    @Override
    public void endTempShowCards() {
        if (tempShownCards.isEmpty()) {
            return;
        }

        for (final Card c : tempShownCards) {
            c.removeMayLookTemp(player);
        }
        tempShownCards.clear();
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
    public SpellAbility getAbilityToPlay(final Card hostCard, final List<SpellAbility> abilities,
                                         final ITriggerEvent triggerEvent) {
        return  (SpellAbility) brains.chooseOneToOne(hostCard, abilities, "getAbility");
    }

    public AlphaController getAi() { return brains; }

    // TODO: figure out what this does and how we modify for Alpha
    @Override
    public void playSpellAbilityNoStack(final SpellAbility effectSA, final boolean canSetupTargets) {
        HumanPlay.playSpellAbilityNoStack(this, player, effectSA, !canSetupTargets);
    }

    // TODO: review greedy approach. Can we reduce the search space by combining duplicate cards?
    @Override
    public List<PaperCard> sideboard(final Deck deck, final GameType gameType, String message) {
        if (!deck.has(DeckSection.Sideboard)) return null;

        Map<PaperCard, PaperCard> sideboardPlan = Maps.newHashMap();
        List<PaperCard> main = deck.get(DeckSection.Main).toFlatList();
        List<PaperCard> sideboard = deck.get(DeckSection.Sideboard).toFlatList();

        // previous game. currently not in use
        Game lastGame = brains.getGame();
        // current match. currently not in use
        Match match = lastGame.getMatch();

        // greedy approach, since we cannot consider all 75 choose 60 different deck configurations
        // choose the cards from the sideboard the AI wants
        List<PaperCard> sideIn = (PaperCard) brains.chooseManyToOne(null, sideboard, 0, sideboard.size(), "sideboardIn");
        // for each card siding in, choose a card from the maindeck to side out
        // List<PaperCard> mainOut = new ArrayList<>();
        for (PaperCard c : sideIn) {
            PaperCard mainOut = (PaperCard) brains.chooseOneToOne(c, main, "sideboardOut");
            if (mainOut == null) {
                continue;
            } else {
                sideboard.remove(c);
                sideboard.add(mainOut);
                main.add(c);
                main.remove(mainOut);
            }
        }

        return main;
    }

    // TODO: do we need a new function in AlphaController to choose numbers as we see here?
    @Override
    public Map<Card, Integer> assignCombatDamage(final Card attacker, final CardCollectionView blockers, final CardCollectionView remaining,
                                                 final int damageDealt, final GameEntity defender, final boolean overrideOrder) {
        // Attacker is a poor name here, since the creature assigning damage
        // could just as easily be the blocker.
        CardCollection numberCards = new CardCollection();
        Map<Card, Integer> map = new HashMap<Card, Integer>();
        // TODO: review greedy combat damage assignment. can we instead search all damage assignment combinations?
        for (Card blocker : blockers) {
            map.put(blocker, (Integer) brains.chooseOneToOne(blocker, numberCards, "assignCombatDamage"));
            // TODO: reduce remaining damage via numberCards
        }

        return map;
    }

    @Override
    public Map<GameEntity, Integer> divideShield(Card effectSource, Map<GameEntity, Integer> affected, int shieldAmount) {
        final CardView vSource = CardView.get(effectSource);
        final Map<Object, Integer> vAffected = new HashMap<>(affected.size());
        for (Map.Entry<GameEntity, Integer> e : affected.entrySet()) {
            vAffected.put(GameEntityView.get(e.getKey()), e.getValue());
        }
        final Map<Object, Integer> vResult = getGui().assignGenericAmount(vSource, vAffected, shieldAmount, false,
                localizer.getMessage("lblShield"));
        Map<GameEntity, Integer> result = new HashMap<>();
        if (vResult != null) { //fix for netplay
            for (Map.Entry<GameEntity, Integer> e : affected.entrySet()) {
                if (vResult.containsKey(GameEntityView.get(e.getKey()))) {
                    result.put(e.getKey(), vResult.get(GameEntityView.get(e.getKey())));
                }
            }
        }
        return result;
    }

    @Override
    public Map<Byte, Integer> specifyManaCombo(SpellAbility sa, ColorSet colorSet, int manaAmount, boolean different) {
        final CardView vSource = CardView.get(sa.getHostCard());
        final Map<Object, Integer> vAffected = new LinkedHashMap<>(manaAmount);
        Integer maxAmount = different ? 1 : manaAmount;
        for (Byte color : colorSet) {
            vAffected.put(color, maxAmount);
        }
        final Map<Object, Integer> vResult = getGui().assignGenericAmount(vSource, vAffected, manaAmount, false,
                localizer.getMessage("lblMana").toLowerCase());
        Map<Byte, Integer> result = new HashMap<>();
        if (vResult != null) { //fix for netplay
            for (Byte color : colorSet) {
                if (vResult.containsKey(color)) {
                    result.put(color, vResult.get(color));
                }
            }
        }
        return result;
    }

    @Override
    public Integer announceRequirements(final SpellAbility ability, final String announce) {
        final Card host = ability.getHostCard();
        int max = Integer.MAX_VALUE;
        int xMin = 0;
        final boolean abXMin = ability.hasParam("XMin");
        Cost cost = ability.getPayCosts();

        if ("X".equals(announce)) {
            if (abXMin) xMin = Integer.parseInt(ability.getParam("XMin"));
            if (ability.hasParam("XMaxLimit")) {
                max = Math.min(max, AbilityUtils.calculateAmount(host, ability.getParam("XMaxLimit"), ability));
            }
            if (cost != null) {
                Integer costX = cost.getMaxForNonManaX(ability, player, false);
                if (costX != null && !player.getController().isFullControl(FullControlFlag.AllowPaymentStartWithMissingResources)) {
                    max = Math.min(max, costX);
                }
                if (cost.hasManaCost() && !abXMin) {
                    xMin = cost.getCostMana().getXMin();
                }
            }
        }
        final int min = xMin;

        if (ability.hasParam("AnnounceMax")) {
            max = Math.min(max, AbilityUtils.calculateAmount(host, ability.getParam("AnnounceMax"), ability));
        }

        if (ability.usesTargeting()) {
            // if announce is used as min targets, check what the max possible number would be
            if (announce.equals(ability.getTargetRestrictions().getMinTargets())) {
                max = Math.min(max, CardUtil.getValidCardsToTarget(ability).size());
            }
        }
        if (min > max) {
            return null;
        }

        String announceTitle = "X".equals(announce) ? ability.getParamOrDefault("XAnnounceTitle", announce) :
                ability.getParamOrDefault("AnnounceTitle", announce);
        if (cost.isMandatory()) {
            return chooseNumber(ability, localizer.getMessage("lblChooseAnnounceForCard", announceTitle,
                    CardTranslation.getTranslatedName(host.getName())), min, max);
        }
        if ("NumTimes".equals(announce)) {
            return getGui().getInteger(localizer.getMessage("lblHowManyTimesToPay", ability.getPayCosts().getTotalMana(),
                    CardTranslation.getTranslatedName(host.getName())), min, max, min + 9);
        }
        return getGui().getInteger(localizer.getMessage("lblChooseAnnounceForCard", announceTitle,
                CardTranslation.getTranslatedName(host.getName())), min, max, min + 9);
    }

    @Override
    public CardCollectionView choosePermanentsToSacrifice(final SpellAbility sa, final int min, final int max,
                                                          final CardCollectionView valid, final String message) {
        return choosePermanentsTo(min, max, valid, message, localizer.getMessage("lblSacrifice").toLowerCase(), sa);
    }

    @Override
    public CardCollectionView choosePermanentsToDestroy(final SpellAbility sa, final int min, final int max,
                                                        final CardCollectionView valid, final String message) {
        return choosePermanentsTo(min, max, valid, message, localizer.getMessage("lblDestroy"), sa);
    }

    private CardCollectionView choosePermanentsTo(final int min, int max, final CardCollectionView valid,
                                                  final String message, final String action, final SpellAbility sa) {
        max = Math.min(max, valid.size());
        if (max <= 0) {
            return CardCollection.EMPTY;
        }

        String inpMessage = localizer.getMessage((min == 0 ? "lblSelectUpToNumTargetToAction" :
                "lblSelectNumTargetToAction"), message, action);

        final InputSelectCardsFromList inp = new InputSelectCardsFromList(this, min, max, valid, sa);
        inp.setMessage(inpMessage);
        inp.setCancelAllowed(min == 0);
        inp.showAndWait();
        return new CardCollection(inp.getSelected());
    }

    private boolean useSelectCardsInput(final FCollectionView<? extends GameEntity> sourceList, final SpellAbility sa) {
        //this can be used to stop zone select GUI when certain APIs would reveal illegal zone information
        //initially created for HeistEffect which showed library placement
        if (sa != null && ApiType.Heist.equals(sa.getApi())) return false;
        return useSelectCardsInput(sourceList);
    }

    private boolean useSelectCardsInput(final FCollectionView<? extends GameEntity> sourceList) {
        // can't use InputSelect from GUI thread (e.g., DevMode Tutor)
        if (FThreads.isGuiThread()) {
            return false;
        }

        // if UI_SELECT_FROM_CARD_DISPLAYS not set use InputSelect only for battlefield and player hand
        // if UI_SELECT_FROM_CARD_DISPLAYS set and using desktop GUI use InputSelect for any zone that can be shown
        for (final GameEntity c : sourceList) {
            if (c instanceof Player) {
                continue;
            }

            if (!(c instanceof Card)) {
                return false;
            }
            final Zone cz = ((Card) c).getZone();
            // Don't try to draw the UI point of a card if it doesn't exist in any zone.
            if (cz == null) {
                return false;
            }

            final boolean useUiPointAtCard =
                    (FModel.getPreferences().getPrefBoolean(FPref.UI_SELECT_FROM_CARD_DISPLAYS) && (!GuiBase.getInterface().isLibgdxPort())) ?
                            (cz.is(ZoneType.Battlefield) || cz.is(ZoneType.Hand) || cz.is(ZoneType.Library) ||
                                    cz.is(ZoneType.Graveyard) || cz.is(ZoneType.Exile) || cz.is(ZoneType.Flashback) ||
                                    cz.is(ZoneType.Command) || cz.is(ZoneType.Sideboard)) :
                            (cz.is(ZoneType.Hand, player) || cz.is(ZoneType.Battlefield));
            if (!useUiPointAtCard) {
                return false;
            }
        }
        return true;
    }

    @Override
    public CardCollectionView chooseCardsForEffect(final CardCollectionView sourceList, final SpellAbility sa,
                                                   final String title, final int min, final int max, final boolean isOptional, Map<String, Object> params) {
        // If only one card to choose, use a dialog box.
        // Otherwise, use the order dialog to be able to grab multiple cards in one shot

        if (min == 1 && max == 1) {
            final Card singleChosen = chooseSingleEntityForEffect(sourceList, sa, title, isOptional, params);
            return singleChosen == null ? CardCollection.EMPTY : new CardCollection(singleChosen);
        }

        final CardCollection choices = new CardCollection();
        if (sourceList.isEmpty()) {
            return choices;
        }

        getGui().setPanelSelection(CardView.get(sa.getHostCard()));

        if (useSelectCardsInput(sourceList)) {
            tempShowCards(sourceList);
            final InputSelectCardsFromList sc = new InputSelectCardsFromList(this, min, max, sourceList, sa);
            sc.setMessage(title);
            sc.setCancelAllowed(isOptional);
            sc.showAndWait();
            endTempShowCards();
            return new CardCollection(sc.getSelected());
        }

        tempShowCards(sourceList);
        GameEntityViewMap<Card, CardView> gameCachechoose = GameEntityView.getMap(sourceList);
        List<CardView> views = getGui().many(title, localizer.getMessage("lblChosenCards"), min, max,
                gameCachechoose.getTrackableKeys(), CardView.get(sa.getHostCard()));
        endTempShowCards();
        gameCachechoose.addToList(views, choices);
        return choices;
    }

    /**
     * IDs of Contraptions that have been cranked previously, and will default to the "cranked" column next time their
     * sprocket is cranked.
     */
    private final Set<Integer> savedCrankedIDs = new HashSet<>();

    @Override
    public List<Card> chooseContraptionsToCrank(List<Card> contraptions) {
        if(contraptions.isEmpty())
            return contraptions;

        tempShowCards(contraptions);
        GameEntityViewMap<Card, CardView> gameCacheChoose = GameEntityView.getMap(contraptions);
        TrackableCollection<CardView> viewList = gameCacheChoose.getTrackableKeys();

        //Contraptions that were cranked previously will start in the cranked column when the dialog is shown.
        List<CardView> cranked = new ArrayList<>(), uncranked = new ArrayList<>();
        for(CardView c : viewList) {
            int id = c.getId();
            (savedCrankedIDs.contains(id) ? cranked : uncranked).add(c);
        }

        List<CardView> views = getGui().many(localizer.getMessage("lblChooseCrank"),
                localizer.getMessage("lblCranked"), -1, -1, uncranked, cranked, null);
        endTempShowCards();

        //If any were on the saved cranked list before but aren't cranked now, remove them from the saved list.
        cranked.stream().filter(v -> !views.contains(v)).map(CardView::getId).forEach(savedCrankedIDs::remove);
        //Add any that were cranked this time to the saved list.
        views.stream().map(CardView::getId).forEach(savedCrankedIDs::add);

        List<Card> choices = new CardCollection();
        gameCacheChoose.addToList(views, choices);

        return choices;
    }


    @Override
    public boolean helpPayForAssistSpell(ManaCostBeingPaid cost, SpellAbility sa, int max, int requested) {
        // This is like a mini-announce X
        String title = String.format("%s trying to cast (%s) How much would you like to help pay for Assist? (Max: %s)", sa.getActivatingPlayer(), sa, max);
        int willPay = chooseNumber(sa, title, 0, max);

        if (willPay <= 0) {
            // Just because you choose not to help, doesn't mean we should cancel the spell
            return true;
        }

        ManaCost manaCost = ManaCost.get(willPay);
        ManaCostBeingPaid assistCost = new ManaCostBeingPaid(manaCost);

        InputPayMana inpPayment = new InputPayManaOfCostPayment(this, assistCost, sa, this.getPlayer(), null, true);
        inpPayment.setMessagePrefix("Paying for assist - ");
        inpPayment.showAndWait();

        if (inpPayment.isPaid()) {
            // Apply payments from assistCost to cost
            // If cost is canceled, how do we make sure mana gets undone?

            cost.decreaseGenericMana(willPay);
            return true;
        } else if (sa.getHostCard().getGame().EXPERIMENTAL_RESTORE_SNAPSHOT) {
            // Let's roll it back!
            return false;
        } else {
            System.out.println("Assist rollback may not work well without experimental restore snapshot enabled");
            return false;
        }
    }

    @Override
    public Player choosePlayerToAssistPayment(FCollectionView<Player> optionList, SpellAbility sa, String title, int max) {
        return chooseSingleEntityForEffect(optionList, null, sa, title, true, null, null);
    }

    @Override
    public <T extends GameEntity> T chooseSingleEntityForEffect(final FCollectionView<T> optionList,
                                                                final DelayedReveal delayedReveal, final SpellAbility sa, final String title, final boolean isOptional,
                                                                final Player targetedPlayer, Map<String, Object> params) {
        // Human is supposed to read the message and understand from it what to choose
        if (optionList.isEmpty()) {
            if (delayedReveal != null) {
                reveal(delayedReveal.getCards(), delayedReveal.getZone(), delayedReveal.getOwner(),
                        delayedReveal.getMessagePrefix());
            }
            return null;
        }
        if (!isOptional && optionList.size() == 1) {
            if (delayedReveal != null) {
                reveal(delayedReveal.getCards(), delayedReveal.getZone(), delayedReveal.getOwner(),
                        delayedReveal.getMessagePrefix());
            }
            return Iterables.getFirst(optionList, null);
        }

        tempShow(optionList);
        if (delayedReveal != null) {
            tempShow(delayedReveal.getCards());
        }

        if (useSelectCardsInput(optionList, sa)) {
            final InputSelectEntitiesFromList<T> input = new InputSelectEntitiesFromList<>(this, isOptional ? 0 : 1, 1,
                    optionList, sa);
            input.setCancelAllowed(isOptional);
            input.setMessage(MessageUtil.formatMessage(title, player, targetedPlayer));
            input.showAndWait();
            endTempShowCards();
            return Iterables.getFirst(input.getSelected(), null);
        }

        GameEntityViewMap<T, GameEntityView> gameCacheChoose = GameEntityView.getMap(optionList);
        final GameEntityView result = getGui().chooseSingleEntityForEffect(title,
                gameCacheChoose.getTrackableKeys(), delayedReveal, isOptional);
        endTempShowCards();

        if (result == null || !gameCacheChoose.containsKey(result)) {
            return null;
        }
        return gameCacheChoose.get(result);
    }

    @Override
    public <T extends GameEntity> List<T> chooseEntitiesForEffect(final FCollectionView<T> optionList, final int min, final int max,
                                                                  final DelayedReveal delayedReveal, final SpellAbility sa, final String title, final Player targetedPlayer, Map<String, Object> params) {
        // useful details for debugging problems with the mass select logic
        Sentry.setExtra("Card", sa.getCardView().toString());
        Sentry.setExtra("SpellAbility", sa.toString());

        // Human is supposed to read the message and understand from it what to choose
        if (optionList.isEmpty()) {
            if (delayedReveal != null) {
                reveal(delayedReveal.getCards(), delayedReveal.getZone(), delayedReveal.getOwner(),
                        delayedReveal.getMessagePrefix());
            }
            return Lists.newArrayList();
        }

        if (delayedReveal != null) {
            tempShow(delayedReveal.getCards());
        }

        tempShow(optionList);
        if (useSelectCardsInput(optionList)) {
            final InputSelectEntitiesFromList<T> input = new InputSelectEntitiesFromList<>(this, min, max, optionList,
                    sa);
            input.setCancelAllowed(min == 0);
            input.setMessage(MessageUtil.formatMessage(title, player, targetedPlayer));
            input.showAndWait();
            endTempShowCards();
            return (List<T>) input.getSelected();
        }

        GameEntityViewMap<T, GameEntityView> gameCacheEntity = GameEntityView.getMap(optionList);
        final List<GameEntityView> views = getGui().chooseEntitiesForEffect(title, gameCacheEntity.getTrackableKeys(), min, max, delayedReveal);
        endTempShowCards();

        List<T> results = Lists.newArrayList();

        if (views != null) {
            gameCacheEntity.addToList(views, results);
        }

        return results;
    }

    @Override
    public int chooseNumber(final SpellAbility sa, final String title, final int min, final int max) {
        if (min >= max) {
            return min;
        }
        // todo check for X cost or any max value for optional costs like multikicker, etc to determine the correct max value,
        // fixes crash for word of command OutOfMemoryError when selecting a card with announce X or Multikicker since
        // it will build from 0 to Integer.MAX_VALUE...
        if (max == Integer.MAX_VALUE) {
            Integer choice = getGui().getInteger(title, min, max, 9);
            if (choice != null)
                return choice;
            else
                return 0;
        } else {
            final ImmutableList.Builder<Integer> choices = ImmutableList.builder();
            int size = max - min;
            for (int i = 0; i <= size; i++) {
                choices.add(i + min);
            }
            return getGui().one(title, choices.build());
        }
    }

    @Override
    public int chooseNumber(final SpellAbility sa, final String title, final List<Integer> choices,
                            final Player relatedPlayer) {
        return getGui().one(title, choices);
    }

    @Override
    public SpellAbility chooseSingleSpellForEffect(final List<SpellAbility> spells, final SpellAbility sa,
                                                   final String title, Map<String, Object> params) {
        if (spells.size() < 2) {
            return Iterables.getFirst(spells, null);
        }

        // Show the card that asked for this choice
        getGui().setCard(CardView.get(sa.getHostCard()));

        // create a mapping between a spell's view and the spell itself
        Map<SpellAbilityView, SpellAbility> spellViewCache = SpellAbilityView.getMap(spells);
        Object choice = getGui().one(title, Lists.newArrayList(spellViewCache.keySet()));

        // Human is supposed to read the message and understand from it what to choose
        return spellViewCache.get(choice);
    }

    @Override
    public List<SpellAbility> chooseSpellAbilitiesForEffect(List<SpellAbility> spells, SpellAbility sa, String title, int num, Map<String, Object> params) {
        List<SpellAbility> result = Lists.newArrayList();
        // create a mapping between a spell's view and the spell itself
        Map<SpellAbilityView, SpellAbility> spellViewCache = SpellAbilityView.getMap(spells);

        if(sa.hasParam("ShowCurrentCard"))
        {
            Card current = Iterables.getFirst(AbilityUtils.getDefinedCards(sa.getHostCard(), sa.getParam("ShowCurrentCard"), sa), null);
            if(current != null) {
                String promptCurrent = localizer.getMessage("lblCurrentCard") + ": " + current;
                title = title + "\n" + promptCurrent;
            }
        }

        //override generic
        List<SpellAbilityView> chosen = getGui().getChoices(title, num, num, Lists.newArrayList(spellViewCache.keySet()));

        for (SpellAbilityView view : chosen) {
            if (spellViewCache.containsKey(view)) {
                result.add(spellViewCache.get(view));
            }
        }
        return result;
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * forge.game.player.PlayerController#confirmAction(forge.gui.card.spellability.
     * SpellAbility, java.lang.String, java.lang.String)
     */
    @Override
    public boolean confirmAction(final SpellAbility sa, final PlayerActionConfirmMode mode, final String message,
                                 List<String> options, Card cardToShow, Map<String, Object> params) {
        // Another card should be displayed in the prompt on mouse over rather than the SA source
        if (cardToShow != null) {
            tempShowCard(cardToShow);
            boolean result = options.isEmpty() ? InputConfirm.confirm(this, cardToShow.getView(), sa, message)
                    : InputConfirm.confirm(this, cardToShow.getView(), message, true, options);
            endTempShowCards();
            return result;
        }

        // The general case: display the source of the SA in the prompt on mouse over
        return options.isEmpty() ? InputConfirm.confirm(this, sa, message) :
                InputConfirm.confirm(this, sa.getHostCard().getView(), sa, message, true, options);
    }

    @Override
    public boolean confirmBidAction(final SpellAbility sa, final PlayerActionConfirmMode bidlife, final String string,
                                    final int bid, final Player winner) {
        return InputConfirm.confirm(this, sa, string + " " + localizer.getMessage("lblHighestBidder") + " " + winner);
    }

    @Override
    public boolean confirmStaticApplication(final Card hostCard, PlayerActionConfirmMode mode, final String message, final String logic) {
        return InputConfirm.confirm(this, CardView.get(hostCard), message);
    }

    @Override
    public boolean confirmTrigger(final WrappedAbility wrapper) {
        final SpellAbility sa = wrapper.getWrappedAbility();
        final Trigger regtrig = wrapper.getTrigger();
        if (getGui().shouldAlwaysAcceptTrigger(regtrig.getId())) {
            return true;
        }
        if (getGui().shouldAlwaysDeclineTrigger(regtrig.getId())) {
            return false;
        }

        // triggers with costs can always be declined by not paying the cost
        if (sa.hasParam("Cost") && !sa.getParam("Cost").equals("0")) {
            return true;
        }

        final StringBuilder buildQuestion = new StringBuilder(localizer.getMessage("lblUseTriggeredAbilityOf") + " ");
        buildQuestion.append(regtrig.getHostCard().toString()).append("?");
        if (!FModel.getPreferences().getPrefBoolean(FPref.UI_COMPACT_PROMPT)
                && !FModel.getPreferences().getPrefBoolean(FPref.UI_DETAILED_SPELLDESC_IN_PROMPT)) {
            // append trigger description unless prompt is compact or detailed descriptions are on
            buildQuestion.append("\n(");
            buildQuestion.append(regtrig.toString());
            buildQuestion.append(")");
        }
        final Map<AbilityKey, Object> tos = sa.getTriggeringObjects();
        if (tos.containsKey(AbilityKey.Attacker)) {
            buildQuestion.append("\n").append(localizer.getMessage("lblAttacker")).append(": ").append(tos.get(AbilityKey.Attacker));
        }
        if (tos.containsKey(AbilityKey.Card)) {
            final Card card = (Card) tos.get(AbilityKey.Card);
            if (card != null && (card.getController() == player || getGame().getZoneOf(card) == null
                    || getGame().getZoneOf(card).getZoneType().isKnown())) {
                buildQuestion.append("\n").append(localizer.getMessage("lblTriggeredby")).append(": ").append(tos.get(AbilityKey.Card));
            }
        }
        if (GuiBase.getInterface().isLibgdxPort()) {
            CardView cardView;
            SpellAbilityView spellAbilityView = wrapper.getView();
            if (spellAbilityView != null) //updated view
                cardView = spellAbilityView.getHostCard();
            else
                cardView = wrapper.getCardView();
            return this.getGui().confirm(cardView, buildQuestion.toString().replaceAll("\n", " "));
        } else {
            final InputConfirm inp = new InputConfirm(this, buildQuestion.toString(), wrapper);
            inp.showAndWait();
            return inp.getResult();
        }
    }

    @Override
    public Player chooseStartingPlayer(final boolean isFirstGame) {
        String prompt = null;
        if (isFirstGame) {
            prompt = localizer.getMessage("lblYouHaveWonTheCoinToss", player.getName());
        } else {
            prompt = localizer.getMessage("lblYouLostTheLastGame", player.getName());
        }

        if (getGame().getPlayers().size() == 2) {
            prompt += "\n\n" + localizer.getMessage("lblWouldYouLiketoPlayorDraw");
            final InputConfirm inp = new InputConfirm(this, prompt, localizer.getMessage("lblPlay"), localizer.getMessage("lblDraw"));
            inp.showAndWait();
            return inp.getResult() ? this.player : this.player.getOpponents().get(0);
        }

        prompt += "\n\n" + localizer.getMessage("lblWhoWouldYouLiketoStartthisGame");
        final InputSelectEntitiesFromList<Player> input = new InputSelectEntitiesFromList<>(this, 1, 1, getGame().getPlayersInTurnOrder());
        input.setMessage(prompt);
        input.showAndWait();
        return input.getFirstSelected();
    }

    @Override
    public CardCollection orderBlockers(final Card attacker, final CardCollection blockers) {
        GameEntityViewMap<Card, CardView> gameCacheBlockers = GameEntityView.getMap(blockers);
        final CardView vAttacker = CardView.get(attacker);
        getGui().setPanelSelection(vAttacker);
        List<CardView> chosen = getGui().order(localizer.getMessage("lblChooseDamageOrderFor", CardTranslation.getTranslatedName(vAttacker.getName())), localizer.getMessage("lblDamagedFirst"),
                gameCacheBlockers.getTrackableKeys(), vAttacker);
        CardCollection chosenCards = new CardCollection();
        gameCacheBlockers.addToList(chosen, chosenCards);
        return chosenCards;
    }

    @Override
    public List<Card> exertAttackers(List<Card> attackers) {
        GameEntityViewMap<Card, CardView> gameCacheExert = GameEntityView.getMap(attackers);
        List<CardView> chosen = getGui().order(localizer.getMessage("lblExertAttackersConfirm"), localizer.getMessage("lblExerted"),
                0, gameCacheExert.size(), gameCacheExert.getTrackableKeys(), null, null, false);

        List<Card> chosenCards = new CardCollection();
        gameCacheExert.addToList(chosen, chosenCards);
        return chosenCards;
    }

    @Override
    public List<Card> enlistAttackers(List<Card> attackers) {
        GameEntityViewMap<Card, CardView> gameCacheExert = GameEntityView.getMap(attackers);
        List<CardView> chosen = getGui().order(localizer.getMessage("lblEnlistAttackersConfirm"), localizer.getMessage("lblEnlisted"),
                0, gameCacheExert.size(), gameCacheExert.getTrackableKeys(), null, null, false);

        List<Card> chosenCards = new CardCollection();
        gameCacheExert.addToList(chosen, chosenCards);
        return chosenCards;
    }

    @Override
    public List<CostPart> orderCosts(List<CostPart> costs) {
        if (!isFullControl(FullControlFlag.ChooseCostOrder) || costs.size() < 2) {
            return costs;
        }
        List<CostPart> chosen = getGui().order(localizer.getMessage("lblOrderCosts"), localizer.getMessage("lblPayFirst"),
                0, 0, costs, null, null, false);
        return chosen;
    }

    @Override
    public CardCollection orderBlocker(final Card attacker, final Card blocker, final CardCollection oldBlockers) {
        GameEntityViewMap<Card, CardView> gameCacheBlockers = GameEntityView.getMap(oldBlockers);
        final CardView vAttacker = CardView.get(attacker);
        getGui().setPanelSelection(vAttacker);
        List<CardView> chosen = getGui().insertInList(
                localizer.getMessage("lblChooseBlockerAfterWhichToPlaceAttackert", CardTranslation.getTranslatedName(vAttacker.getName())),
                CardView.get(blocker), CardView.getCollection(oldBlockers));
        CardCollection chosenCards = new CardCollection();
        gameCacheBlockers.addToList(chosen, chosenCards);
        return chosenCards;
    }

    @Override
    public CardCollection orderAttackers(final Card blocker, final CardCollection attackers) {
        GameEntityViewMap<Card, CardView> gameCacheAttackers = GameEntityView.getMap(attackers);
        final CardView vBlocker = CardView.get(blocker);
        getGui().setPanelSelection(vBlocker);
        List<CardView> chosen = getGui().order(localizer.getMessage("lblChooseDamageOrderFor", CardTranslation.getTranslatedName(vBlocker.getName())), localizer.getMessage("lblDamagedFirst"),
                CardView.getCollection(attackers), vBlocker);
        CardCollection chosenCards = new CardCollection();
        gameCacheAttackers.addToList(chosen, chosenCards);
        return chosenCards;
    }

    @Override
    public void reveal(final CardCollectionView cards, final ZoneType zone, final Player owner, String message, boolean addSuffix) {
        reveal(cards, zone, PlayerView.get(owner), message, addSuffix);
    }

    @Override
    public void reveal(final List<CardView> cards, final ZoneType zone, final PlayerView owner, String message, boolean addSuffix) {
        reveal(getCardList(cards), zone, owner, message, addSuffix);
    }

    protected void reveal(final CardCollectionView cards, final ZoneType zone, final PlayerView owner, String message, boolean addSuffix) {
        if (StringUtils.isBlank(message)) {
            message = localizer.getMessage("lblLookCardInPlayerZone", "{player's}", zone.getTranslatedName().toLowerCase());
        } else {
            if (addSuffix) message += " " + localizer.getMessage("lblPlayerZone", "{player's}", zone.getTranslatedName().toLowerCase());
        }
        final String fm = MessageUtil.formatMessage(message, getLocalPlayerView(), owner);
        if (!cards.isEmpty()) {
            tempShowCards(cards);
            TrackableCollection<CardView> collection = CardView.getCollection(cards);
            getGui().reveal(fm, collection);
            getGui().updateRevealedCards(collection);
            endTempShowCards();
        } else {
            getGui().message(MessageUtil.formatMessage(localizer.getMessage("lblThereNoCardInPlayerZone", "{player's}", zone.getTranslatedName().toLowerCase()),
                    getLocalPlayerView(), owner), fm);
        }
    }

    public List<Card> manipulateCardList(final String title, final Iterable<Card> cards, final Iterable<Card> manipulable, final boolean toTop, final boolean toBottom, final boolean toAnywhere) {
        GameEntityViewMap<Card, CardView> gameCacheManipulate = GameEntityView.getMap(cards);
        gameCacheManipulate.putAll(manipulable);
        List<CardView> views = getGui().manipulateCardList(title, CardView.getCollection(cards), CardView.getCollection(manipulable), toTop, toBottom, toAnywhere);
        return gameCacheManipulate.addToList(views, new CardCollection());
    }

    public ImmutablePair<CardCollection, CardCollection> arrangeForMove(final String title, final FCollectionView<Card> cards, final List<Card> manipulable, final boolean topOK, final boolean bottomOK) {
        List<Card> result = manipulateCardList(title, cards, manipulable, topOK, bottomOK, false);
        CardCollection toBottom = new CardCollection();
        CardCollection toTop = new CardCollection();
        for (int i = 0; i < cards.size() && manipulable.contains(result.get(i)); i++) {
            toTop.add(result.get(i));
        }
        if (toTop.size() < cards.size()) { // the top isn't everything
            for (int i = result.size() - 1; i >= 0 && manipulable.contains(result.get(i)); i--) {
                toBottom.add(result.get(i));
            }
        }
        return ImmutablePair.of(toTop, toBottom);
    }

    @Override
    public ImmutablePair<CardCollection, CardCollection> arrangeForScry(final CardCollection topN) {
        CardCollection toBottom = null;
        CardCollection toTop = null;

        tempShowCards(topN);
        if (FModel.getPreferences().getPrefBoolean(FPref.UI_SELECT_FROM_CARD_DISPLAYS) &&
                (!GuiBase.getInterface().isLibgdxPort()) && (!GuiBase.isNetworkplay())) { //prevent crash for desktop vs mobile port it will crash the netplay since mobile doesnt have manipulatecardlist, send the alternate below
            CardCollectionView cardList = player.getCardsIn(ZoneType.Library);
            ImmutablePair<CardCollection, CardCollection> result =
                    arrangeForMove(localizer.getMessage("lblMoveCardstoToporBbottomofLibrary"), cardList, topN, true, true);
            toTop = result.getLeft();
            toBottom = result.getRight();
        } else {
            if (topN.size() == 1) {
                if (willPutCardOnTop(topN.get(0))) {
                    toTop = topN;
                } else {
                    toBottom = topN;
                }
            } else {
                GameEntityViewMap<Card, CardView> cardCacheScry = GameEntityView.getMap(topN);

                toBottom = new CardCollection();
                List<CardView> views = getGui().many(localizer.getMessage("lblSelectCardsToBeOutOnTheBottomOfYourLibrary"),
                        localizer.getMessage("lblCardsToPutOnTheBottom"), -1, cardCacheScry.getTrackableKeys(), null);
                cardCacheScry.addToList(views, toBottom);

                topN.removeAll(toBottom);
                if (topN.isEmpty()) {
                    toTop = null;
                } else if (topN.size() == 1) {
                    toTop = topN;
                } else {
                    GameEntityViewMap<Card, CardView> cardCacheOrder = GameEntityView.getMap(topN);
                    toTop = new CardCollection();
                    views = getGui().order(localizer.getMessage("lblArrangeCardsToBePutOnTopOfYourLibrary"),
                            localizer.getMessage("lblTopOfLibrary"), cardCacheOrder.getTrackableKeys(), null);
                    cardCacheOrder.addToList(views, toTop);
                }
            }
        }
        endTempShowCards();
        return ImmutablePair.of(toTop, toBottom);
    }

    @Override
    public ImmutablePair<CardCollection, CardCollection> arrangeForSurveil(final CardCollection topN) {
        CardCollection toGrave = null;
        CardCollection toTop = null;

        tempShowCards(topN);
        if (topN.size() == 1) {
            final Card c = topN.getFirst();
            final CardView view = CardView.get(c);

            tempShowCard(c);
            getGui().setCard(view);
            boolean result = false;
            result = InputConfirm.confirm(this, view, localizer.getMessage("lblPutCardsOnTheTopLibraryOrGraveyard", CardTranslation.getTranslatedName(view.getName())),
                    true, ImmutableList.of(localizer.getMessage("lblLibrary"), localizer.getMessage("lblGraveyard")));
            if (result) {
                toTop = topN;
            } else {
                toGrave = topN;
            }
        } else {
            GameEntityViewMap<Card, CardView> gameCacheSurveil = GameEntityView.getMap(topN);
            toGrave = new CardCollection();
            List<CardView> views = getGui().many(localizer.getMessage("lblSelectCardsToBePutIntoTheGraveyard"),
                    localizer.getMessage("lblCardsToPutInTheGraveyard"), -1, gameCacheSurveil.getTrackableKeys(), null);
            gameCacheSurveil.addToList(views, toGrave);
            topN.removeAll(toGrave);
            if (topN.isEmpty()) {
                toTop = null;
            } else if (topN.size() == 1) {
                toTop = topN;
            } else {
                GameEntityViewMap<Card, CardView> cardCacheOrder = GameEntityView.getMap(topN);
                toTop = new CardCollection();
                views = getGui().order(localizer.getMessage("lblArrangeCardsToBePutOnTopOfYourLibrary"),
                        localizer.getMessage("lblTopOfLibrary"), cardCacheOrder.getTrackableKeys(), null);
                cardCacheOrder.addToList(views, toTop);
            }
        }
        endTempShowCards();
        return ImmutablePair.of(toTop, toGrave);
    }

    @Override
    public boolean willPutCardOnTop(final Card c) {
        // get the predicted Q-values of putting the card on top and bottom of library and choose
        CardCollection library = new CardCollection(player.getZone(ZoneType.Library).getCards());
        CardCollection libraryCardOnTop = (CardCollection) library.clone();
        CardCollection libraryCardOnBottom = (CardCollection) library.clone().subList(1, library.size());
        libraryCardOnBottom.add(library.get(0));

        List<CardCollection> scryChoices = new ArrayList<CardCollection>();
        scryChoices.add(libraryCardOnTop);
        scryChoices.add(libraryCardOnBottom);
        FCollection<? extends GameObject> selection = brains.chooseOneCollection(null, scryChoices,  "scry");

        // TODO: test that this actually works, or if the typing causes issues
        return selection.equals(libraryCardOnTop);
    }

    @Override
    public CardCollectionView orderMoveToZoneList(final CardCollectionView cards, final ZoneType destinationZone, final SpellAbility source) {

        tempShowCards(cards);
        GameEntityViewMap<Card, CardView> gameCacheMove = GameEntityView.getMap(cards);
        List<CardView> choices = gameCacheMove.getTrackableKeys();

        boolean topOfDeck = destinationZone.isDeck()
                && (source == null
                || !source.hasParam("LibraryPosition")
                || AbilityUtils.calculateAmount(source.getHostCard(), source.getParam("LibraryPosition"), source) >= 0);

        switch (destinationZone) {
            case Library:
                choices = getGui().order(localizer.getMessage("lblChooseOrderCardsPutIntoLibrary"), localizer.getMessage(topOfDeck ? "lblClosestToTop" : "lblClosestToBottom"), choices, null);
                break;
            case Battlefield:
                choices = getGui().order(localizer.getMessage("lblChooseOrderCardsPutOntoBattlefield"), localizer.getMessage("lblPutFirst"), choices, null);
                break;
            case Graveyard:
                choices = getGui().order(localizer.getMessage("lblChooseOrderCardsPutIntoGraveyard"), localizer.getMessage("lblClosestToBottom"), choices, null);
                break;
            case Exile:
                choices = getGui().order(localizer.getMessage("lblChooseOrderCardsPutIntoExile"), localizer.getMessage("lblPutFirst"), choices, null);
                break;
            case PlanarDeck:
                choices = getGui().order(localizer.getMessage("lblChooseOrderCardsPutIntoPlanarDeck"), localizer.getMessage(topOfDeck ? "lblClosestToTop" : "lblClosestToBottom"), choices, null);
                break;
            case SchemeDeck:
                choices = getGui().order(localizer.getMessage("lblChooseOrderCardsPutIntoSchemeDeck"), localizer.getMessage(topOfDeck ? "lblClosestToTop" : "lblClosestToBottom"), choices, null);
                break;
            case AttractionDeck:
            case ContraptionDeck:
                choices = getGui().order(localizer.getMessage("lblChooseOrderCardsPutIntoExtraDeck"), localizer.getMessage(topOfDeck ? "lblClosestToTop" : "lblClosestToBottom"), choices, null);
            case Stack:
                choices = getGui().order(localizer.getMessage("lblChooseOrderCopiesCast"), localizer.getMessage("lblPutFirst"), choices, null);
                break;
            case None: //for when we want to order but don't really want to move the cards
                choices = getGui().order(localizer.getMessage("lblChooseOrderCards"), localizer.getMessage("lblPutFirst"), choices, null);
                break;
            default:
                System.out.println("ZoneType " + destinationZone + " - Not Ordered");
                endTempShowCards();
                return cards;
        }
        endTempShowCards();
        if(topOfDeck)
            Collections.reverse(choices);
        CardCollection result = new CardCollection();
        gameCacheMove.addToList(choices, result);
        return result;
    }

    @Override
    public CardCollectionView chooseCardsToDiscardFrom(final Player p, final SpellAbility sa,
                                                       final CardCollection valid, final int min, final int max) {
        boolean optional = min == 0;
        String descriptor = "selfDiscard";

        if (p != player) {
            descriptor = "opponentDiscard";
        }
        List<GameObject> selection = brains.chooseManyToOne(sa, new ArrayList<GameObject>(valid), min, max, descriptor);
        CardCollection cardsToDiscard = new CardCollection();
        for (GameObject c : selection) {
            cardsToDiscard.add((Card) c);
        }

        return cardsToDiscard;
    }

    @Override
    public CardCollectionView chooseCardsToDelve(final int genericAmount, final CardCollection grave) {
        final int maxToDelve = Math.min(genericAmount, grave.size());
        if (maxToDelve == 0) {
            return CardCollection.EMPTY;
        }

        List<GameObject> selection = brains.chooseManyToOne(null, grave, 0, maxToDelve, "delve");
        CardCollection cardsToDelve = new CardCollection();
        for (GameObject c : selection) {
            cardsToDelve.add((Card) c);
        }
        return cardsToDelve;
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * forge.game.player.PlayerController#chooseCardsToDiscardUnlessType(int,
     * java.lang.String, forge.gui.card.spellability.SpellAbility)
     */
    @Override
    public CardCollectionView chooseCardsToDiscardUnlessType(final int num, final CardCollectionView hand,
                                                             final String uType, final SpellAbility sa) {
        Iterable<Card> cardsOfType = IterableUtil.filter(hand, CardPredicates.restriction(uType.split(","), sa.getActivatingPlayer(), sa.getHostCard(), sa));
        List<GameObject> discardType = new ArrayList<GameObject>();
        if (!Iterables.isEmpty(cardsOfType)) {
            List<GameObject> cardListOfType = new ArrayList<GameObject>();
            for (Card c : cardsOfType) {
                cardListOfType.add(c);
            }
            discardType.add(brains.chooseOneToOne(sa, cardListOfType, "selfDiscard"));
        }
        List<GameObject> handList = new ArrayList<GameObject>();
        for (Card c : hand) {
            handList.add(c);
        }
        List<GameObject> discardAny = brains.chooseManyToOne(sa, handList, num, num, "selfDiscard");

        // TODO: compare the Q-values of choosing discardType vs discardAny and choose the better option
        CardCollection cardsToDiscard = new CardCollection();
        for (GameObject c : discardAny) {
            cardsToDiscard.add((Card) c);
        }
        return cardsToDiscard;
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * forge.game.player.PlayerController#chooseManaFromPool(java.util.List)
     */
    @Override
    public Mana chooseManaFromPool(final List<Mana> manaChoices) {
        final List<String> options = Lists.newArrayList();
        for (int i = 0; i < manaChoices.size(); i++) {
            final Mana m = manaChoices.get(i);
            options.add(localizer.getMessage("lblNColorManaFromCard", String.valueOf(1 + i), MagicColor.toLongString(m.getColor()), CardTranslation.getTranslatedName(m.getSourceCard().getName())));
        }
        final String chosen = getGui().one(localizer.getMessage("lblPayManaFromManaPool"), options);
        final String idx = TextUtil.split(chosen, '.')[0];
        return manaChoices.get(Integer.parseInt(idx) - 1);
    }

    /*
     * (non-Javadoc)
     *
     * @see forge.game.player.PlayerController#chooseSomeType(java.lang.String,
     * java.lang.String, java.util.List, java.util.List, java.lang.String)
     */
    @Override
    public String chooseSomeType(final String kindOfType, final SpellAbility sa, final Collection<String> validTypes, final boolean isOptional) {
        final List<String> types = Lists.newArrayList(validTypes);
        if (kindOfType.equals("Creature")) {
            sortCreatureTypes(types);
        }
        if (isOptional) {
            return getGui().oneOrNone(localizer.getMessage("lblChooseATargetType", kindOfType.toLowerCase()), types);
        }
        return getGui().one(localizer.getMessage("lblChooseATargetType", kindOfType.toLowerCase()), types);
    }

    @Override
    public String chooseSector(Card assignee, String ai, List<String> sectors) {
        String prompt;
        if (assignee != null) {
            String creature = CardTranslation.getTranslatedName(assignee.getName()) + " (" + assignee.getId() + ")";
            prompt = Localizer.getInstance().getMessage("lblAssignSectorCreature", creature);
        } else {
            prompt = Localizer.getInstance().getMessage("lblChooseSectorEffect");
        }
        return getGui().one(prompt, sectors);
    }

    @Override
    public int chooseSprocket(Card assignee, boolean forceDifferent) {
        String cardName = CardTranslation.getTranslatedName(assignee.getName()) + " (" + assignee.getId() + ")";
        String prompt = Localizer.getInstance().getMessage("lblAssignSprocket", cardName);
        List<Integer> options = Lists.newArrayList(1, 2, 3);
        if(forceDifferent)
            options.remove(Integer.valueOf(assignee.getSprocket()));
        int crankedNextTurn = (player.getCrankCounter() % 3) + 1;
        getGui().setCard(assignee.getView());
        List<Integer> choices = getGui().getChoices(prompt, 1, 1, options, null, (sprocket) -> {
            //Add some info about each sprocket.
            StringBuilder label = new StringBuilder();
            label.append(sprocket);
            int currentCount = CardLists.count(player.getCardsIn(ZoneType.Battlefield), CardPredicates.isContraptionOnSprocket(sprocket));
            if(currentCount > 0)
                label.append(' ').append(Localizer.getInstance().getMessage("lblAssignSprocketCurrentCount", currentCount));
            if(sprocket == crankedNextTurn)
                label.append(' ').append(Localizer.getInstance().getMessage("lblAssignSprocketNextTurn"));
            return label.toString();
        });
        assert choices.size() == 1;
        return choices.get(0);
    }

    @Override
    public PlanarDice choosePDRollToIgnore(List<PlanarDice> rolls) {
        return getGui().one(Localizer.getInstance().getMessage("lblChooseRollIgnore"), rolls);
    }

    @Override
    public Integer chooseRollToIgnore(List<Integer> rolls) {
        return getGui().one(Localizer.getInstance().getMessage("lblChooseRollIgnore"), rolls);
    }

    @Override
    public List<Integer> chooseDiceToReroll(List<Integer> rolls) {
        return getGui().many(Localizer.getInstance().getMessage("lblChooseDiceToRerollTitle"),
                Localizer.getInstance().getMessage("lblChooseDiceToRerollCaption"),0, rolls.size(), rolls, null);
    }

    @Override
    public Integer chooseRollToModify(List<Integer> rolls) {
        return getGui().oneOrNone(Localizer.getInstance().getMessage("lblChooseRollToModify"), rolls);
    }

    @Override
    public RollDiceEffect.DieRollResult chooseRollToSwap(List<RollDiceEffect.DieRollResult> rolls) {
        return getGui().oneOrNone(Localizer.getInstance().getMessage("lblChooseRollToSwap"), rolls);
    }

    @Override
    public String chooseRollSwapValue(List<String> swapChoices, Integer currentResult, int power, int toughness) {
        return getGui().oneOrNone(Localizer.getInstance().getMessage("lblChooseSwapPT", currentResult, power, toughness), swapChoices);
    }

    @Override
    public Object vote(final SpellAbility sa, final String prompt, final List<Object> options,
                       final ListMultimap<Object, Player> votes, Player forPlayer, boolean optional) {
        if (optional) {
            return getGui().oneOrNone(prompt, options);
        }
        return getGui().one(prompt, options);
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * forge.game.player.PlayerController#confirmReplacementEffect(forge.gui.card.
     * replacement.ReplacementEffect, forge.gui.card.spellability.SpellAbility,
     * java.lang.String)
     */
    @Override
    public boolean confirmReplacementEffect(final ReplacementEffect replacementEffect, final SpellAbility effectSA,
                                            GameEntity affected, final String question) {
        if (GuiBase.getInterface().isLibgdxPort()) {
            CardView cardView;
            SpellAbilityView spellAbilityView = effectSA == null ? null : effectSA.getView();
            if (spellAbilityView != null) //updated view
                cardView = spellAbilityView.getHostCard();
            else //fallback
                cardView = effectSA == null ? null : effectSA.getCardView();
            return this.getGui().confirm(cardView, question.replaceAll("\n", " "));
        } else {
            final InputConfirm inp = new InputConfirm(this, question, effectSA);
            inp.showAndWait();
            return inp.getResult();
        }
    }

    @Override
    public boolean mulliganKeepHand(final Player mulliganingPlayer, int cardsToReturn) {
        // TODO we should be passing tuckCards into Confirmation Dialog
        final InputConfirmMulligan inp = new InputConfirmMulligan(this, player, mulliganingPlayer);
        inp.showAndWait();
        return inp.isKeepHand();
    }

    @Override
    public CardCollectionView londonMulliganReturnCards(final Player mulliganingPlayer, int cardsToReturn) {
        final InputLondonMulligan inp = new InputLondonMulligan(this, player, cardsToReturn);
        inp.showAndWait();
        return inp.getSelectedCards();
    }

    @Override
    public void declareAttackers(final Player attackingPlayer, final Combat combat) {
        if (mayAutoPass()) {
            if (CombatUtil.validateAttackers(combat)) {
                return; // don't prompt to declare attackers if user chose to
                // end the turn and not attacking is legal
            }
            // otherwise: cancel auto pass because of this unexpected attack
            autoPassCancel();
        }

        // This input should not modify combat object itself, but should return user choice
        final InputAttack inpAttack = new InputAttack(this, attackingPlayer, combat);
        inpAttack.showAndWait();
    }

    @Override
    public void declareBlockers(final Player defender, final Combat combat) {
        // This input should not modify combat object itself, but should return user choice
        final InputBlock inpBlock = new InputBlock(this, defender, combat);
        inpBlock.showAndWait();
        getGui().updateAutoPassPrompt();
    }

    @Override
    public List<SpellAbility> chooseSpellAbilityToPlay() {
        final MagicStack stack = getGame().getStack();

        if (mayAutoPass()) {
            // avoid prompting for input if current phase is set to be
            // auto-passed instead posing a short delay if needed to
            // prevent the game jumping ahead too quick
            int delay = 0;
            if (stack.isEmpty()) {
                // make sure to briefly pause at phases you're not set up to skip
                if (!getGui().isUiSetToSkipPhase(getGame().getPhaseHandler().getPlayerTurn().getView(),
                        getGame().getPhaseHandler().getPhase())) {
                    delay = FControlGamePlayback.phasesDelay;
                }
            } else {
                // pause slightly longer for spells and abilities on the stack resolving
                delay = FControlGamePlayback.resolveDelay;
            }
            if (delay > 0) {
                try {
                    Thread.sleep(delay);
                } catch (final InterruptedException e) {
                    e.printStackTrace();
                }
            }
            return null;
        }

        if (stack.isEmpty()) {
            if (getGui().isUiSetToSkipPhase(getGame().getPhaseHandler().getPlayerTurn().getView(),
                    getGame().getPhaseHandler().getPhase())) {
                return null; // avoid prompt for input if stack is empty and
                // player is set to skip the current phase
            }
        } else {
            final SpellAbility ability = stack.peekAbility();
            if (ability != null && ability.isAbility() && getGui().shouldAutoYield(ability.yieldKey())) {
                // avoid prompt for input if top ability of stack is set to auto-yield
                try {
                    Thread.sleep(FControlGamePlayback.resolveDelay);
                } catch (final InterruptedException e) {
                    e.printStackTrace();
                }
                return null;
            }
        }

        final InputPassPriority defaultInput = new InputPassPriority(this);
        defaultInput.showAndWait();
        return defaultInput.getChosenSa();
    }

    @Override
    public boolean playChosenSpellAbility(final SpellAbility chosenSa) {
        return HumanPlay.playSpellAbility(this, player, chosenSa);
    }

    @Override
    public CardCollection chooseCardsToDiscardToMaximumHandSize(final int nDiscard) {
        final int max = player.getMaxHandSize();

        if (GuiBase.getInterface().isLibgdxPort()) {
            tempShowCards(player.getCardsIn(ZoneType.Hand));
            GameEntityViewMap<Card, CardView> gameCacheDiscard = GameEntityView.getMap(player.getCardsIn(ZoneType.Hand));
            List<CardView> views = getGui().many(String.format(localizer.getMessage("lblChooseMinCardToDiscard"), nDiscard),
                    localizer.getMessage("lblDiscarded"), nDiscard, nDiscard, gameCacheDiscard.getTrackableKeys(), null);
            endTempShowCards();
            final CardCollection choices = new CardCollection();
            gameCacheDiscard.addToList(views, choices);
            return choices;
        }

        @SuppressWarnings("serial") final InputSelectCardsFromList inp = new InputSelectCardsFromList(this, nDiscard, nDiscard,
                player.getZone(ZoneType.Hand).getCards()) {
            @Override
            protected boolean allowAwaitNextInput() {
                return true; // prevent Cleanup message getting stuck during
                // opponent's next turn
            }
        };
        final String message = localizer.getMessage("lblCleanupPhase") + "\n"
                + localizer.getMessage("lblSelectCardsToDiscardHandDownMaximum", String.valueOf(nDiscard), String.valueOf(max));
        inp.setMessage(message);
        inp.setCancelAllowed(false);
        inp.showAndWait();
        return new CardCollection(inp.getSelected());
    }

    @Override
    public CardCollectionView chooseCardsToRevealFromHand(int min, int max, final CardCollectionView valid) {
        max = Math.min(max, valid.size());
        min = Math.min(min, max);
        final InputSelectCardsFromList inp = new InputSelectCardsFromList(this, min, max, valid);
        inp.setMessage(localizer.getMessage("lblChooseWhichCardstoReveal"));
        inp.showAndWait();
        return new CardCollection(inp.getSelected());
    }

    @Override
    public boolean payCombatCost(final Card c, final Cost cost, final SpellAbility sa, final String prompt) {
        if (cost.isOnlyManaCost() && cost.getTotalMana().isZero() && isFullControl(FullControlFlag.NoFreeCombatCostHandling)) {
            return true;
        }
        return HumanPlay.payCostDuringAbilityResolve(this, player, c, cost, sa, prompt);
    }

    @Override
    public List<SpellAbility> chooseSaToActivateFromOpeningHand(final List<SpellAbility> usableFromOpeningHand) {
        final CardCollection srcCards = new CardCollection();
        for (final SpellAbility sa : usableFromOpeningHand) {
            srcCards.add(sa.getHostCard());
        }
        final List<SpellAbility> result = Lists.newArrayList();
        if (srcCards.isEmpty()) {
            return result;
        }
        GameEntityViewMap<Card, CardView> gameCacheOpenHand = GameEntityView.getMap(srcCards);

        final List<CardView> chosen = getGui().many(localizer.getMessage("lblChooseCardsActivateOpeningHandandOrder"),
                localizer.getMessage("lblActivateFirst"), -1, CardView.getCollection(srcCards), null);
        for (final CardView view : chosen) {
            if (!gameCacheOpenHand.containsKey(view)) {
                continue;
            }
            final Card c = gameCacheOpenHand.get(view);
            for (final SpellAbility sa : usableFromOpeningHand) {
                if (sa.getHostCard() == c) {
                    result.add(sa);
                    break;
                }
            }
        }
        return result;
    }

    @Override
    public PlayerZone chooseStartingHand(List<PlayerZone> zones) {
        // Create new zone objects in the UI temporarily.
        // Spawn a new input dialog, it works by selecting a card in the zone you want and clicking OK
        // The card will then extract the PlayerZone via the card that is chosen and return it to this function
        // Which will then return the PlayerZone to the caller
        player.updateZoneForView(player.getZone(ZoneType.Hand));
        final InputChooseStartingHand inp = new InputChooseStartingHand(this, player);
        inp.showAndWait();
        return inp.getSelectedHand();
    }

    @Override
    public boolean chooseBinary(final SpellAbility sa, final String question, final BinaryChoiceType kindOfChoice,
                                final Boolean defaultVal) {
        final List<String> labels;
        switch (kindOfChoice) {
            case HeadsOrTails:
                labels = ImmutableList.of(localizer.getMessage("lblHeads"), localizer.getMessage("lblTails"));
                break;
            case TapOrUntap:
                labels = ImmutableList.of(StringUtils.capitalize(localizer.getMessage("lblTap")),
                        localizer.getMessage("lblUntap"));
                break;
            case OddsOrEvens:
                labels = ImmutableList.of(localizer.getMessage("lblOdds"), localizer.getMessage("lblEvens"));
                break;
            case UntapOrLeaveTapped:
                labels = ImmutableList.of(localizer.getMessage("lblUntap"), localizer.getMessage("lblLeaveTapped"));
                break;
            case PlayOrDraw:
                labels = ImmutableList.of(localizer.getMessage("lblPlay"), localizer.getMessage("lblDraw"));
                break;
            case LeftOrRight:
                labels = ImmutableList.of(localizer.getMessage("lblLeft"), localizer.getMessage("lblRight"));
                break;
            case AddOrRemove:
                labels = ImmutableList.of(localizer.getMessage("lblAddCounter"), localizer.getMessage("lblRemoveCounter"));
                break;
            case IncreaseOrDecrease:
                labels = ImmutableList.of(localizer.getMessage("lblIncrease"), localizer.getMessage("lblDecrease"));
                break;
            default:
                labels = ImmutableList.copyOf(kindOfChoice.toString().split("Or"));
        }

        return InputConfirm.confirm(this, sa, question, defaultVal == null || defaultVal, labels);
    }

    @Override
    public boolean chooseFlipResult(final SpellAbility sa, final Player flipper, final boolean[] results,
                                    final boolean call) {
        final String[] labelsSrc = call ? new String[]{localizer.getMessage("lblHeads"), localizer.getMessage("lblTails")}
                : new String[]{localizer.getMessage("lblWinTheFlip"), localizer.getMessage("lblLoseTheFlip")};
        final List<String> sortedResults = new ArrayList<String>();
        for (boolean result : results) {
            sortedResults.add(labelsSrc[result ? 0 : 1]);
        }

        Collections.sort(sortedResults);
        if (!call) {
            Collections.reverse(sortedResults);
        }
        return getGui().one(sa.getHostCard().getName() + " - " + localizer.getMessage("lblChooseAResult"), sortedResults).equals(labelsSrc[0]);
    }

    @Override
    public Pair<SpellAbilityStackInstance, GameObject> chooseTarget(final SpellAbility saSpellskite,
                                                                    final List<Pair<SpellAbilityStackInstance, GameObject>> allTargets) {
        if (allTargets.size() < 2) {
            return Iterables.getFirst(allTargets, null);
        }

        final List<Pair<SpellAbilityStackInstance, GameObject>> chosen = getGui()
                .getChoices(saSpellskite.getHostCard().getName(), 1, 1, allTargets, null, new FnTargetToString());
        return Iterables.getFirst(chosen, null);
    }

    private final static class FnTargetToString
            implements Function<Pair<SpellAbilityStackInstance, GameObject>, String>, Serializable {
        private static final long serialVersionUID = -4779137632302777802L;

        @Override
        public String apply(final Pair<SpellAbilityStackInstance, GameObject> targ) {
            return targ.getRight().toString() + " - " + targ.getLeft().getStackDescription();
        }
    }

    @Override
    public void notifyOfValue(final SpellAbility sa, final GameObject realtedTarget, final String value) {
        final String message = MessageUtil.formatNotificationMessage(sa, player, realtedTarget, value);
        if (sa != null && sa.isManaAbility()) {
            getGame().getGameLog().add(GameLogEntryType.LAND, message);
        } else {
            if (sa != null && sa.getHostCard() != null && GuiBase.getInterface().isLibgdxPort()) {
                CardView cardView;
                IPaperCard iPaperCard = sa.getHostCard().getPaperCard();
                if (iPaperCard != null)
                    cardView = CardView.getCardForUi(iPaperCard);
                else
                    cardView = sa.getHostCard().getView();
                getGui().confirm(cardView, message, ImmutableList.of(localizer.getMessage("lblOK")));
            } else {
                getGui().message(message, sa == null || sa.getHostCard() == null ? "" : CardView.get(sa.getHostCard()).toString());
            }
        }
    }

    // end of not related candidates for move.

    /*
     * (non-Javadoc)
     *
     * @see forge.game.player.PlayerController#chooseModeForAbility(forge.gui.card.
     * spellability.SpellAbility, java.util.List, int, int)
     */
    @Override
    public List<AbilitySub> chooseModeForAbility(final SpellAbility sa, List<AbilitySub> possible, final int min, final int num,
                                                 boolean allowRepeat) {
        boolean trackerFrozen = getGame().getTracker().isFrozen();
        if (trackerFrozen) {
            // The view tracker needs to be unfrozen to update the SpellAbilityViews at this point, or it may crash
            getGame().getTracker().unfreeze();
        }
        Map<SpellAbilityView, AbilitySub> spellViewCache = SpellAbilityView.getMap(possible);
        if (trackerFrozen) {
            getGame().getTracker().freeze(); // refreeze if the tracker was frozen prior to this update
        }
        final String modeTitle = localizer.getMessage("lblPlayerActivatedCardChooseMode", sa.getActivatingPlayer().toString(), CardTranslation.getTranslatedName(sa.getHostCard().getName()));
        final List<AbilitySub> chosen = Lists.newArrayListWithCapacity(num);
        int chosenPawprint = 0;
        for (int i = 0; i < num; i++) {
            if (sa.hasParam("Pawprint")) {
                final int tmpPaw = chosenPawprint;
                spellViewCache.values().removeIf(ab -> Integer.parseInt(ab.getParam("Pawprint")) > num - tmpPaw);
            }
            final List<SpellAbilityView> choices = Lists.newArrayList(spellViewCache.keySet());

            SpellAbilityView a;
            if (i < min) {
                a = getGui().one(modeTitle, choices);
            } else {
                a = getGui().oneOrNone(modeTitle, choices);
            }
            if (a == null) {
                break;
            }

            AbilitySub sp = spellViewCache.get(a);
            if (!allowRepeat) {
                spellViewCache.remove(a);
            }
            if (sp.hasParam("Pawprint")) {
                chosenPawprint += AbilityUtils.calculateAmount(sp.getHostCard(), sp.getParam("Pawprint"), sp);
            }
            chosen.add(sp);
        }
        return chosen;
    }

    @Override
    public List<String> chooseColors(final String message, final SpellAbility sa, final int min, final int max,
                                     List<String> options) {
        options = options.stream().map(DeckRecognizer::getLocalisedMagicColorName).collect(Collectors.toList());
        List<String> choices = getGui().getChoices(message, min, max, options);
        return choices.stream().map(DeckRecognizer::getColorNameByLocalisedName).collect(Collectors.toList());
    }

    @Override
    public byte chooseColor(final String message, final SpellAbility sa, final ColorSet colors) {
        final int cntColors = colors.countColors();
        switch (cntColors) {
            case 0:
                return 0;
            case 1:
                return colors.getColor();
            default:
                return chooseColorCommon(message, sa == null ? null : sa.getHostCard(), colors, false);
        }
    }

    @Override
    public byte chooseColorAllowColorless(final String message, final Card c, final ColorSet colors) {
        final int cntColors = 1 + colors.countColors();
        switch (cntColors) {
            case 1:
                return 0;
            default:
                return chooseColorCommon(message, c, colors, true);
        }
    }

    private byte chooseColorCommon(final String message, final Card c, final ColorSet colors,
                                   final boolean withColorless) {
        final ImmutableList.Builder<String> colorNamesBuilder = ImmutableList.builder();
        if (withColorless) {
            colorNamesBuilder.add(MagicColor.toLongString(MagicColor.COLORLESS));
        }
        for (final Byte b : colors) {
            colorNamesBuilder.add(MagicColor.toLongString(b));
        }
        final ImmutableList<String> colorNames = colorNamesBuilder.build();
        if (colorNames.size() > 2) {
            return MagicColor.fromName(getGui().one(message, colorNames));
        }

        boolean confirmed = false;
        confirmed = InputConfirm.confirm(this, CardView.get(c), message, true, colorNames);
        final int idxChosen = confirmed ? 0 : 1;
        return MagicColor.fromName(colorNames.get(idxChosen));
    }

    @Override
    public ICardFace chooseSingleCardFace(final SpellAbility sa, final String message, final Predicate<ICardFace> cpp,
                                          final String name) {
        List<CardFaceView> choices = FModel.getMagicDb().getCommonCards().streamAllFaces()
                .filter(cpp)
                .map(cardFace -> new CardFaceView(CardTranslation.getTranslatedName(cardFace.getName()), cardFace.getName()))
                .sorted()
                .collect(Collectors.toList());
        CardFaceView cardFaceView = getGui().one(message, choices);
        return StaticData.instance().getCommonCards().getFaceByName(cardFaceView.getOracleName());
    }

    @Override
    public ICardFace chooseSingleCardFace(SpellAbility sa, List<ICardFace> faces, String message) {
        return getGui().one(message, faces);
    }

    @Override
    public CounterType chooseCounterType(final List<CounterType> options, final SpellAbility sa, final String prompt,
                                         Map<String, Object> params) {
        if (options.size() <= 1) {
            return Iterables.getFirst(options, null);
        }
        return getGui().one(prompt, options);
    }

    @Override
    public CardState chooseSingleCardState(SpellAbility sa, List<CardState> states, String message, Map<String, Object> params) {
        if (states.size() <= 1) {
            return Iterables.getFirst(states, null);
        }
        Map<CardStateView, CardState> cache = CardView.getStateMap(states);
        CardStateView chosen = getGui().one(message, Lists.newArrayList(cache.keySet()));
        return cache.get(chosen);
    }

    @Override
    public String chooseKeywordForPump(final List<String> options, final SpellAbility sa, final String prompt, final Card tgtCard) {
        if (options.size() <= 1) {
            return Iterables.getFirst(options, null);
        }
        return getGui().one(prompt, options);
    }

    @Override
    public boolean confirmPayment(final CostPart costPart, final String question, SpellAbility sa) {
        if (GuiBase.getInterface().isLibgdxPort()) {
            CardView cardView;
            try {
                cardView = CardView.getCardForUi(ImageUtil.getPaperCardFromImageKey(sa.getView().getHostCard().getCurrentState().getTrackableImageKey()));
            } catch (Exception e) {
                SpellAbilityView spellAbilityView = sa.getView();
                if (spellAbilityView != null) //updated view
                    cardView = spellAbilityView.getHostCard();
                else //fallback
                    cardView = sa.getCardView();
            }
            return this.getGui().confirm(cardView, question.replaceAll("\n", " "));
        } else {
            final InputConfirm inp = new InputConfirm(this, question, sa);
            inp.showAndWait();
            return inp.getResult();
        }
    }

    @Override
    public ReplacementEffect chooseSingleReplacementEffect(final List<ReplacementEffect> possibleReplacers) {
        final ReplacementEffect first = possibleReplacers.get(0);
        if (possibleReplacers.size() == 1) {
            return first;
        }
        final List<String> res = possibleReplacers.stream().map(ReplacementEffect::toString).collect(Collectors.toList());
        final String firstStr = res.get(0);
        final String prompt = localizer.getMessage("lblChooseFirstApplyReplacementEffect");
        for (int i = 1; i < res.size(); i++) {
            // prompt user if there are multiple different options
            if (!res.get(i).equals(firstStr)) {
                if (!GuiBase.isNetworkplay()) //non network game don't need serialization
                    return getGui().one(prompt, possibleReplacers);
                ReplacementEffectView rev = getGui().one(prompt, possibleReplacers.stream().map(ReplacementEffect::getView).collect(Collectors.toList()));
                return possibleReplacers.stream().filter(re -> re.getId() == rev.getId()).findAny().orElse(first);
            }
        }
        // return first option without prompting if all options are the same
        return first;
    }

    @Override
    public StaticAbility chooseSingleStaticAbility(final String prompt, final List<StaticAbility> possibleStatics) {
        final StaticAbility first = possibleStatics.get(0);
        if (possibleStatics.size() == 1 || !isFullControl(FullControlFlag.ChooseCostOrder)) {
            return first;
        }
        final List<String> sts = possibleStatics.stream().map(StaticAbility::toString).collect(Collectors.toList());
        final String firstStr = sts.get(0);
        for (int i = 1; i < sts.size(); i++) {
            // prompt user if there are multiple different options
            if (!sts.get(i).equals(firstStr)) {
                if (!GuiBase.isNetworkplay()) //non network game don't need serialization
                    return getGui().one(prompt, possibleStatics);
                StaticAbilityView stv = getGui().one(prompt, possibleStatics.stream().map(StaticAbility::getView).collect(Collectors.toList()));
                return possibleStatics.stream().filter(st -> st.getId() == stv.getId()).findAny().orElse(first);
            }
        }
        // return first option without prompting if all options are the same
        return first;
    }

    @Override
    public String chooseProtectionType(final String string, final SpellAbility sa, final List<String> choices) {
        return getGui().one(string, choices);
    }

    @Override
    public boolean payCostToPreventEffect(final Cost cost, final SpellAbility sa, final boolean alreadyPaid, final FCollectionView<Player> allPayers) {
        // if it's paid by the AI already the human can pay, but it won't change anything
        String prompt = null;
        if (sa.isKeyword(Keyword.ECHO)) {
            prompt = Localizer.getInstance().getMessage("lblPayEcho");
        } else if (sa.isKeyword(Keyword.CUMULATIVE_UPKEEP)) {
            prompt = "Cumulative upkeep for " + sa.getHostCard();
        }
        return HumanPlay.payCostDuringAbilityResolve(this, player, sa.getHostCard(), cost, sa, prompt);
    }

    @Override
    public boolean payCostDuringRoll(final Cost cost, final SpellAbility sa, final FCollectionView<Player> allPayers) {
        // if it's paid by the AI already the human can pay, but it won't change anything
        return HumanPlay.payCostDuringAbilityResolve(this, player, sa.getHostCard(), cost, sa, null);
    }

    // stores saved order for different sets of SpellAbilities
    private final Map<String, List<Integer>> orderedSALookup = Maps.newHashMap();

    @Override
    public void orderAndPlaySimultaneousSa(final List<SpellAbility> activePlayerSAs) {
        List<SpellAbility> orderedSAs = activePlayerSAs;
        if (activePlayerSAs.size() > 1) {
            final String firstStr = activePlayerSAs.get(0).toString();
            boolean needPrompt = !activePlayerSAs.get(0).isTrigger();

            // for the purpose of pre-ordering, no need for extra granularity
            int idxAdditionalInfo = firstStr.indexOf(" [");
            StringBuilder saLookupKey = new StringBuilder(idxAdditionalInfo > 0 ? firstStr.substring(0, idxAdditionalInfo - 1) : firstStr);

            char delim = (char) 5;
            for (int i = 1; i < activePlayerSAs.size(); i++) {
                SpellAbility currentSa = activePlayerSAs.get(i);
                String saStr = currentSa.toString();

                // if current SA isn't a trigger and it uses Targeting, try to show prompt
                if (currentSa.isTrigger()) {
                    needPrompt |= currentSa.getTrigger().hasParam("OrderDuplicates");
                } else if (currentSa.usesTargeting()) {
                    needPrompt = true;
                }
                if (!needPrompt && !saStr.equals(firstStr)) {
                    // prompt by default unless all abilities are the same
                    needPrompt = true;
                }

                saLookupKey.append(delim).append(saStr);
                idxAdditionalInfo = saLookupKey.indexOf(" [");
                if (idxAdditionalInfo > 0) {
                    saLookupKey = new StringBuilder(saLookupKey.substring(0, idxAdditionalInfo - 1));
                }
            }
            if (needPrompt) {
                List<Integer> savedOrder = orderedSALookup.get(saLookupKey.toString());
                List<SpellAbilityView> orderedSAVs = Lists.newArrayList();

                // create a mapping between a spell's view and the spell itself
                Map<SpellAbilityView, SpellAbility> spellViewCache = SpellAbilityView.getMap(orderedSAs);

                if (savedOrder != null) {
                    orderedSAVs = Lists.newArrayList();
                    for (Integer index : savedOrder) {
                        orderedSAVs.add(activePlayerSAs.get(index).getView());
                    }
                } else {
                    for (SpellAbility spellAbility : orderedSAs) {
                        orderedSAVs.add(spellAbility.getView());
                    }
                }
                if (savedOrder != null) {
                    boolean preselect = FModel.getPreferences()
                            .getPrefBoolean(FPref.UI_PRESELECT_PREVIOUS_ABILITY_ORDER);
                    orderedSAVs = getGui().order(localizer.getMessage("lblReorderSimultaneousAbilities"), localizer.getMessage("lblResolveFirst"), 0, 0,
                            preselect ? Lists.newArrayList() : orderedSAVs,
                            preselect ? orderedSAVs : Lists.newArrayList(), null, false);
                } else {
                    orderedSAVs = getGui().order(localizer.getMessage("lblSelectOrderForSimultaneousAbilities"), localizer.getMessage("lblResolveFirst"), orderedSAVs,
                            null);
                }
                orderedSAs = Lists.newArrayList();
                for (SpellAbilityView spellAbilityView : orderedSAVs) {
                    orderedSAs.add(spellViewCache.get(spellAbilityView));
                }
                // save order to avoid needing to prompt a second time to order
                // the same abilities
                savedOrder = Lists.newArrayListWithCapacity(activePlayerSAs.size());
                for (SpellAbility sa : orderedSAs) {
                    savedOrder.add(activePlayerSAs.indexOf(sa));
                }
                orderedSALookup.put(saLookupKey.toString(), savedOrder);
            }
        }
        for (int i = orderedSAs.size() - 1; i >= 0; i--) {
            final SpellAbility next = orderedSAs.get(i);
            if (next.isTrigger() && !next.isCopied()) {
                HumanPlay.playSpellAbility(this, player, next);
            } else {
                if (next.isCopied()) {
                    if (next.isSpell()) {
                        // copied spell always add to stack
                        if (!next.getHostCard().isInZone(ZoneType.Stack)) {
                            next.setHostCard(player.getGame().getAction().moveToStack(next.getHostCard(), next));
                        } else {
                            player.getGame().getStackZone().add(next.getHostCard());
                        }
                    }
                    // TODO check if static abilities needs to be run for things affecting the copy?
                    if (next.isMayChooseNewTargets()) {
                        next.setupNewTargets(player);
                    }
                }
                player.getGame().getStack().add(next);
            }
        }
    }

    @Override
    public boolean playTrigger(final Card host, final WrappedAbility wrapperAbility, final boolean isMandatory) {
        return HumanPlay.playSpellAbilityNoStack(this, player, wrapperAbility);
    }

    @Override
    public boolean playSaFromPlayEffect(final SpellAbility tgtSA) {
        return HumanPlay.playSpellAbility(this, player, tgtSA);
    }

    @Override
    public boolean chooseTargetsFor(final SpellAbility currentAbility) {
        final TargetSelection select = new TargetSelection(this, currentAbility);
        boolean canFilterMustTarget = true;

        // Can't filter MustTarget if any parent ability is also targeting
        SpellAbility checkSA = currentAbility.getParent();
        while (checkSA != null) {
            if (checkSA.usesTargeting()) {
                canFilterMustTarget = false;
                break;
            }
            checkSA = checkSA.getParent();
        }
        // Can't filter MustTarget is any SubAbility is also targeting
        checkSA = currentAbility.getSubAbility();
        while (checkSA != null) {
            if (checkSA.usesTargeting()) {
                canFilterMustTarget = false;
                break;
            }
            checkSA = checkSA.getSubAbility();
        }

        boolean result = select.chooseTargets(null, null, null, false, canFilterMustTarget);

        final Iterable<GameEntity> targets = currentAbility.getTargets().getTargetEntities();
        final int size = Iterables.size(targets);
        int amount = currentAbility.getStillToDivide();

        // assign divided as you choose values
        if (result && size > 0 && amount > 0) {
            if (currentAbility.hasParam("DividedUpTo")) {
                amount = chooseNumber(currentAbility, localizer.getMessage("lblHowMany"), size, amount);
            }
            if (size == 1) {
                currentAbility.addDividedAllocation(Iterables.get(targets, 0), amount);
            } else if (size == amount) {
                for (GameEntity e : targets) {
                    currentAbility.addDividedAllocation(e, 1);
                }
            } else if (amount == 0) {
                for (GameEntity e : targets) {
                    currentAbility.addDividedAllocation(e, 0);
                }
            } else if (size > amount) {
                return false;
            } else {
                String label = "lblDamage";
                if (currentAbility.getApi() == ApiType.PreventDamage) {
                    label = "lblShield";
                } else if (currentAbility.getApi() == ApiType.PutCounter) {
                    label = "lblCounters";
                }
                label = localizer.getMessage(label).toLowerCase();
                final CardView vSource = CardView.get(currentAbility.getHostCard());
                final Map<Object, Integer> vTargets = new HashMap<>(size);
                for (GameEntity e : targets) {
                    vTargets.put(GameEntityView.get(e), amount);
                }
                final Map<Object, Integer> vResult = getGui().assignGenericAmount(vSource, vTargets, amount, true, label);
                for (GameEntity e : targets) {
                    currentAbility.addDividedAllocation(e, vResult.get(GameEntityView.get(e)));
                }
                if (currentAbility.getStillToDivide() > 0) {
                    return false;
                }
            }
        }

        return result;
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * forge.game.player.PlayerController#chooseTargets(forge.gui.card.spellability.
     * SpellAbility, forge.gui.card.spellability.SpellAbilityStackInstance)
     */
    @Override
    public TargetChoices chooseNewTargetsFor(final SpellAbility ability, Predicate<GameObject> filter, boolean optional) {
        final SpellAbility sa = ability.isWrapper() ? ((WrappedAbility) ability).getWrappedAbility() : ability;
        if (!sa.usesTargeting()) {
            return null;
        }
        final TargetChoices oldTarget = sa.getTargets();
        final TargetSelection select = new TargetSelection(this, sa);
        sa.clearTargets();
        if (select.chooseTargets(oldTarget.size(), sa.isDividedAsYouChoose() ? Lists.newArrayList(oldTarget.getDividedValues()) : null, filter, optional, false)) {
            return sa.getTargets();
        } else {
            sa.setTargets(oldTarget);
            // Return old target, since we had to reset them above
            return null;
        }
    }

    @Override
    public boolean chooseCardsPile(final SpellAbility sa, final CardCollectionView pile1,
                                   final CardCollectionView pile2, final String faceUp) {
        final String p1Str = TextUtil.concatNoSpace("-- Pile 1 (", String.valueOf(pile1.size()), " cards) --");
        final String p2Str = TextUtil.concatNoSpace("-- Pile 2 (", String.valueOf(pile2.size()), " cards) --");

        /*
         * if (faceUp.equals("True")) { final List<String> possibleValues =
         * ImmutableList.of(p1Str , p2Str); return
         * getGui().confirm(CardView.get(sa.getHostCard()), "Choose a Pile",
         * possibleValues); }
         */

        final List<CardView> cards = Lists.newArrayListWithCapacity(pile1.size() + pile2.size() + 2);
        final CardView pileView1 = new CardView(Integer.MIN_VALUE, null, p1Str);

        cards.add(pileView1);
        if (faceUp.equals("False")) {
            tempShowCards(pile1);
            cards.addAll(CardView.getCollection(pile1));
        }

        final CardView pileView2 = new CardView(Integer.MIN_VALUE + 1, null, p2Str);
        cards.add(pileView2);
        if (!faceUp.equals("True")) {
            tempShowCards(pile2);
            cards.addAll(CardView.getCollection(pile2));
        }

        // make sure Pile 1 or Pile 2 is clicked on
        boolean result;
        while (true) {
            final CardView chosen = getGui().one(localizer.getMessage("lblChooseaPile"), cards);
            if (chosen.equals(pileView1)) {
                result = true;
                break;
            }
            if (chosen.equals(pileView2)) {
                result = false;
                break;
            }
        }

        endTempShowCards();
        return result;
    }

    @Override
    public void revealAnte(final String message, final Multimap<Player, PaperCard> removedAnteCards) {
        for (final Player p : removedAnteCards.keySet()) {
            getGui().reveal(localizer.getMessage("lblActionFromPlayerDeck", message, Lang.getInstance().getPossessedObject(MessageUtil.mayBeYou(player, p), "")),
                    ImmutableList.copyOf(removedAnteCards.get(p)));
        }
    }

    @Override
    public void revealAISkipCards(final String message, final Map<Player, Map<DeckSection, List<? extends PaperCard>>> unplayable) {
        if (GuiBase.getInterface().isLibgdxPort()) {
            //restore old functionality for mobile version since list of card names can't be zoomed to display the cards
            for (Player p : unplayable.keySet()) {
                final Map<DeckSection, List<? extends PaperCard>> removedUnplayableCards = unplayable.get(p);
                final List<PaperCard> labels = new ArrayList<>();
                for (final DeckSection s : new TreeSet<>(removedUnplayableCards.keySet())) {
                    if (DeckSection.Sideboard.equals(s))
                        continue;
                    labels.addAll(removedUnplayableCards.get(s));
                }
                if (!labels.isEmpty())
                    getGui().reveal(localizer.getMessage("lblActionFromPlayerDeck", message, Lang.getInstance().getPossessedObject(MessageUtil.mayBeYou(player, p), "")),
                            ImmutableList.copyOf(labels));
            }
            return;
        }
        for (Player p : unplayable.keySet()) {
            final Map<DeckSection, List<? extends PaperCard>> removedUnplayableCards = unplayable.get(p);
            final List<Object> labels = new ArrayList<>();
            for (final DeckSection s : new TreeSet<>(removedUnplayableCards.keySet())) {
                labels.add("=== " + s.getLocalizedName() + " ===");
                labels.addAll(removedUnplayableCards.get(s));
            }
            getGui().reveal(localizer.getMessage("lblActionFromPlayerDeck", message, Lang.getInstance().getPossessedObject(MessageUtil.mayBeYou(player, p), "")),
                    ImmutableList.copyOf(labels));
        }
    }

    @Override
    public List<PaperCard> chooseCardsYouWonToAddToDeck(final List<PaperCard> losses) {
        return getGui().many(localizer.getMessage("lblSelectCardstoAddtoYourDeck"), localizer.getMessage("lblAddTheseToMyDeck"), 0, losses.size(), losses, null);
    }

    @Override
    public boolean payManaCost(final ManaCost toPay, final CostPartMana costPartMana, final SpellAbility sa,
                               final String prompt, ManaConversionMatrix matrix, final boolean effect) {
        return HumanPlay.payManaCost(this, toPay, costPartMana, sa, player, prompt, matrix, effect);
    }

    @Override
    public Map<Card, ManaCostShard> chooseCardsForConvokeOrImprovise(final SpellAbility sa, final ManaCost manaCost,
                                                                     final CardCollectionView untappedCards, boolean improvise) {
        final InputSelectCardsForConvokeOrImprovise inp = new InputSelectCardsForConvokeOrImprovise(this, player,
                manaCost, untappedCards, improvise, sa);
        inp.showAndWait();
        return inp.getConvokeMap();
    }

    @Override
    public String chooseCardName(final SpellAbility sa, final Predicate<ICardFace> cpp, final String valid,
                                 final String message) {
        while (true) {
            final ICardFace cardFace = chooseSingleCardFace(sa, message, cpp, sa.getHostCard().getName());
            final PaperCard cp = FModel.getMagicDb().getCommonCards().getCard(cardFace.getName());
            // the Card instance for test needs a game to be tested
            final Card instanceForPlayer = Card.fromPaperCard(cp, player);
            // TODO need the valid check be done against the CardFace?
            if (instanceForPlayer.isValid(valid, sa.getHostCard().getController(), sa.getHostCard(), sa)) {
                // it need to return name for card face
                return cardFace.getName();
            }
        }
    }

    @Override
    public Card chooseSingleCardForZoneChange(final ZoneType destination, final List<ZoneType> origin,
                                              final SpellAbility sa, final CardCollection fetchList, final DelayedReveal delayedReveal,
                                              final String selectPrompt, final boolean isOptional, final Player decider) {
        return chooseSingleEntityForEffect(fetchList, delayedReveal, sa, selectPrompt, isOptional, decider, null);
    }

    public List<Card> chooseCardsForZoneChange(final ZoneType destination, final List<ZoneType> origin,
                                               final SpellAbility sa, final CardCollection fetchList, final int min, final int max, final DelayedReveal delayedReveal,
                                               final String selectPrompt, final Player decider) {
        return chooseEntitiesForEffect(fetchList, min, max, delayedReveal, sa, selectPrompt, decider, null);
    }

    @Override
    public boolean isGuiPlayer() {
        return lobbyPlayer == GamePlayerUtil.getGuiPlayer();
    }

    public void updateAchievements() {
        AchievementCollection.updateAll(this);
    }

    public boolean canUndoLastAction() {
        if (!getGame().stack.canUndo(player)) {
            return false;
        }
        final Player priorityPlayer = getGame().getPhaseHandler().getPriorityPlayer();
        return priorityPlayer != null && priorityPlayer == player;
    }

    @Override
    public void resetAtEndOfTurn() {
        // Not used by the human controller
    }

    // Dev Mode cheat functions
    private boolean canPlayUnlimitedLands;

    @Override
    public boolean canPlayUnlimitedLands() {
        return canPlayUnlimitedLands;
    }


//    public boolean hasCheated() {
//        return cheats != null;
//    }

    // redirect to chooseSingleCardFace
    @Override
    public String chooseCardName(SpellAbility sa, List<ICardFace> faces, String message) {
        ICardFace face = chooseSingleCardFace(sa, faces, message);
        return face == null ? "" : face.getName();
    }

    // TODO
    @Override
    public Card chooseDungeon(Player player, List<PaperCard> dungeonCards, String message) {
        // psuedocode: have AI decide what dungeon is best in current state
        PaperCard dungeon =  brains.chooseDungeon(dungeonCards);
        return Card.fromPaperCard(dungeon, player);
    }

    // Pre: cards contains only cards with the splice ability
    @Override
    public List<Card> chooseCardsForSplice(SpellAbility sa, List<Card> cards) {
        return brains.chooseCardsForSplice(sa, cards);
    }

    /*
     * (non-Javadoc)
     *
     * @see forge.game.player.PlayerController#chooseOptionalCosts(forge.game.
     * spellability.SpellAbility, java.util.List)
     */
    @Override
    public List<OptionalCostValue> chooseOptionalCosts(SpellAbility choosen, List<OptionalCostValue> optionalCost) {
        return getGui().many(localizer.getMessage("lblChooseOptionalCosts"), localizer.getMessage("lblOptionalCosts"), 0, optionalCost.size(),
                optionalCost, choosen.getHostCard().getView());
    }

    @Override
    public boolean confirmMulliganScry(Player p) {
        return InputConfirm.confirm(this, (SpellAbility) null, localizer.getMessage("lblDoYouWanttoScry"));
    }

    @Override
    public int chooseNumberForKeywordCost(SpellAbility sa, Cost cost, KeywordInterface keyword, String prompt, int max) {
        if (max <= 0) {
            return 0;
        }
        if (max == 1) {
            return InputConfirm.confirm(this, sa, prompt) ? 1 : 0;
        }

        Integer v = getGui().getInteger(prompt, 0, max, 9);
        return v == null ? 0 : v;
    }

    @Override
    public int chooseNumberForCostReduction(final SpellAbility sa, final int min, final int max) {
        if (isFullControl(FullControlFlag.ChooseCostReductionOrderAndVariableAmount)) {
            return chooseNumber(sa, localizer.getMessage("lblChooseAmountCostReduction"), min, max);
        }
        return max;
    }

    @Override
    public CardCollection chooseCardsForEffectMultiple(Map<String, CardCollection> validMap, SpellAbility sa, String title, boolean isOptional) {
        CardCollection result = new CardCollection();
        for (Map.Entry<String, CardCollection> e : validMap.entrySet()) {
            result.addAll(chooseCardsForEffect(e.getValue(), sa, title + " (" + e.getKey() + ")", 0, 1, isOptional, null));
        }
        return result;
    }

    public Card getCard(final CardView cardView) {
        return getGame().findByView(cardView);
    }

    public CardCollection getCardList(Iterable<CardView> cardViews) {
        CardCollection result = new CardCollection();
        for (CardView cardView : cardViews) {
            final Card c = this.getCard(cardView);
            if (c != null) {
                result.add(c);
            }
        }
        return result;
    }

    @Override
    public boolean isOrderedZone() {
        return FModel.getPreferences().getPrefBoolean(FPref.UI_ORDER_HAND);
    }

}
