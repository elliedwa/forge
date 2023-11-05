package forge.game.ability.effects;

import com.google.common.collect.Maps;
import forge.game.ability.AbilityKey;
import forge.game.ability.SpellAbilityEffect;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardLists;
import forge.game.player.Player;
import forge.game.spellability.AbilitySub;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;

import java.util.Map;

public class UntapAllEffect extends SpellAbilityEffect {
    @Override
    protected String getStackDescription(SpellAbility sa) {
        if (sa instanceof AbilitySub) {
            return "Untap all valid cards.";
        }
        return sa.getParam("SpellDescription");
    }

    @Override
    public void resolve(SpellAbility sa) {
        final Card card = sa.getHostCard();
        final Player activator = sa.getActivatingPlayer();
        final String valid = sa.getParamOrDefault("ValidCards", "");
        Map<Player, CardCollection> untapMap = Maps.newHashMap();

        for (Player p : getTargetPlayers(sa)) {
            for (Card c : CardLists.getValidCards(p.getCardsIn(ZoneType.Battlefield), valid, activator, card, sa)) {
                if (c.untap(true))  {
                    untapMap.computeIfAbsent(p, i -> new CardCollection()).add(c);
                    if (sa.hasParam("RememberUntapped")) card.addRemembered(c);
                }
            }
        }

        if (!untapMap.isEmpty()) {
            final Map<AbilityKey, Object> runParams = AbilityKey.newMap();
            runParams.put(AbilityKey.Map, untapMap);
            activator.getGame().getTriggerHandler().runTrigger(TriggerType.UntapAll, runParams, false);
        }
    }
}
