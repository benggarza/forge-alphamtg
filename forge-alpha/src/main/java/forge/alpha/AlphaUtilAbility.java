package forge.alpha;

import com.google.common.collect.Lists;
import forge.card.CardStateName;
import forge.game.Game;
import forge.game.GameActionUtil;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardCollectionView;
import forge.game.card.CardLists;
import forge.game.cost.CostPart;
import forge.game.cost.CostPayEnergy;
import forge.game.cost.CostPutCounter;
import forge.game.cost.CostRemoveCounter;
import forge.game.keyword.Keyword;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.OptionalCost;
import forge.game.spellability.OptionalCostValue;
import forge.game.spellability.SpellAbility;
import forge.game.spellability.SpellAbilityStackInstance;
import forge.game.zone.ZoneType;

import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

public class AlphaUtilAbility {
    public static CardCollection getAvailableLandsToPlay(final Game game, final Player player) {
        if (!game.getStack().isEmpty() || !game.getPhaseHandler().getPhase().isMain()) {
            return null;
        }
        CardCollection landList = new CardCollection(player.getCardsIn(ZoneType.Hand));

        //filter out cards that can't be played
        landList = CardLists.filter(landList, c -> {
            if (!c.hasPlayableLandFace()) {
                return false;
            }
            return player.canPlayLand(c, false, c.getFirstSpellAbility());
        });

        final CardCollection landsNotInHand = new CardCollection(player.getCardsIn(ZoneType.Graveyard));
        landsNotInHand.addAll(game.getCardsIn(ZoneType.Exile));
        if (!player.getCardsIn(ZoneType.Library).isEmpty()) {
            landsNotInHand.add(player.getCardsIn(ZoneType.Library).get(0));
        }
        for (final Card crd : landsNotInHand) {
            if (!(crd.hasPlayableLandFace() || (crd.isFaceDown() && crd.getState(CardStateName.Original).getType().isLand()))) {
                continue;
            }
            if (!crd.mayPlay(player).isEmpty()) {
                landList.add(crd);
            }
        }
        if (landList.isEmpty()) {
            return null;
        }
        return landList;
    }

    public static CardCollection getAvailableCards(final Game game, final Player player) {
        CardCollection all = new CardCollection(player.getCardsIn(ZoneType.Hand));

        all.addAll(player.getCardsIn(ZoneType.Graveyard));
        for (Player p : game.getPlayers()) {
            if (!p.getCardsIn(ZoneType.Library).isEmpty()) {
                all.add(p.getCardsIn(ZoneType.Library).get(0));
            }
        }
        all.addAll(game.getCardsIn(ZoneType.Command));
        all.addAll(game.getCardsIn(ZoneType.Exile));
        all.addAll(game.getCardsIn(ZoneType.Battlefield));
        return all;
    }
    public static List<SpellAbility> getPlayableSpellAbilities(final CardCollectionView l, final Player player) {
        final List<SpellAbility> spellAbilities = Lists.newArrayList();
        for (final Card c : l) {
            spellAbilities.addAll(c.getAllPossibleAbilities(player, true));
        }
        return spellAbilities;
    }

    public static List<SpellAbility> getSpellAbilities(final CardCollectionView l, final Player player) {
        final List<SpellAbility> spellAbilities = Lists.newArrayList();
        for (final Card c : l) {
            spellAbilities.addAll(c.getAllPossibleAbilities(player, false));
        }
        return spellAbilities;
    }

    public static List<SpellAbility> getOriginalAndAltCostAbilities(final List<SpellAbility> originList, final Player player) {
        final List<SpellAbility> newAbilities = Lists.newArrayList();

        List<SpellAbility> originListWithAddCosts = Lists.newArrayList();
        for (SpellAbility sa : originList) {
            // If this spell has alternative additional costs, add them instead of the unmodified SA itself
            sa.setActivatingPlayer(player);
            originListWithAddCosts.addAll(GameActionUtil.getAdditionalCostSpell(sa));
        }

        for (SpellAbility sa : originListWithAddCosts) {
            // determine which alternative costs are cheaper than the original and prioritize them
            List<SpellAbility> saAltCosts = GameActionUtil.getAlternativeCosts(sa, player, false);
            List<SpellAbility> priorityAltSa = Lists.newArrayList();
            List<SpellAbility> otherAltSa = Lists.newArrayList();
            for (SpellAbility altSa : saAltCosts) {
                if (sa.getPayCosts().isOnlyManaCost()
                        && altSa.getPayCosts().isOnlyManaCost() && sa.getPayCosts().getTotalMana().compareTo(altSa.getPayCosts().getTotalMana()) == 1) {
                    // the alternative cost is strictly cheaper, so why not? (e.g. Omniscience etc.)
                    priorityAltSa.add(altSa);
                } else {
                    otherAltSa.add(altSa);
                }
            }

            // add alternative costs as additional spell abilities
            newAbilities.addAll(priorityAltSa);
            newAbilities.add(sa);
            newAbilities.addAll(otherAltSa);
        }

        final List<SpellAbility> result = Lists.newArrayList();
        for (SpellAbility sa : newAbilities) {
            sa.setActivatingPlayer(player);

            // Optional cost selection through the AI controller
            boolean choseOptCost = false;
            List<OptionalCostValue> list = GameActionUtil.getOptionalCostValues(sa);
            if (!list.isEmpty()) {
                // still add base spell in case of Promise Gift
                if (list.stream().anyMatch(ocv -> ocv.getType().equals(OptionalCost.PromiseGift))) {
                    result.add(sa);
                }
                list = player.getController().chooseOptionalCosts(sa, list);
                if (!list.isEmpty()) {
                    choseOptCost = true;
                    result.add(GameActionUtil.addOptionalCosts(sa, list));
                }
            }

            // Add only one ability: either the one with preferred optional costs, or the original one if there are none
            if (!choseOptCost) {
                result.add(sa);
            }
        }

        return result;
    }

    public static SpellAbility getTopSpellAbilityOnStack(Game game, SpellAbility sa) {
        Iterator<SpellAbilityStackInstance> it = game.getStack().iterator();

        if (!it.hasNext()) {
            return null;
        }

        SpellAbility tgtSA = it.next().getSpellAbility();
        // Grab the topmost spellability that isn't this SA and use that for comparisons
        if (sa.equals(tgtSA) && game.getStack().size() > 1) {
            if (!it.hasNext()) {
                return null;
            }
            tgtSA = it.next().getSpellAbility();
        }
        return tgtSA;
    }

    public static SpellAbility getFirstCopySASpell(List<SpellAbility> spells) {
        SpellAbility sa = null;
        for (SpellAbility spell : spells) {
            if (spell.getApi() == ApiType.CopySpellAbility) {
                sa = spell;
                break;
            }
        }
        return sa;
    }

    public static Card getAbilitySource(SpellAbility sa) {
        return sa.getOriginalHost() != null ? sa.getOriginalHost() : sa.getHostCard();
    }

    public static String getAbilitySourceName(SpellAbility sa) {
        final Card c = getAbilitySource(sa);
        return c != null ? c.getName() : "";
    }

    public static boolean isFullyTargetable(SpellAbility sa) {
        SpellAbility sub = sa;
        while (sub != null) {
            if (sub.usesTargeting() && sub.getTargetRestrictions().getNumCandidates(sub, true) < sub.getMinTargets()) {
                return false;
            }
            sub = sub.getSubAbility();
        }
        return true;
    }

}
