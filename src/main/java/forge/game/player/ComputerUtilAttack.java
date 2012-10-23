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
package forge.game.player;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.google.common.base.Predicate;

import forge.Card;

import forge.CardLists;
import forge.CardPredicates;
import forge.Counters;
import forge.GameEntity;
import forge.Singletons;
import forge.card.cardfactory.CardFactoryUtil;
import forge.card.trigger.Trigger;
import forge.card.trigger.TriggerType;
import forge.game.GameState;
import forge.game.phase.Combat;
import forge.game.phase.CombatUtil;
import forge.game.zone.ZoneType;
import forge.util.MyRandom;


//doesHumanAttackAndWin() uses the global variable AllZone.getComputerPlayer()
/**
 * <p>
 * ComputerUtil_Attack2 class.
 * </p>
 * 
 * @author Forge
 * @version $Id$
 */
public class ComputerUtilAttack {

    // possible attackers and blockers
    private final List<Card> attackers;
    private final List<Card> blockers;

    private final Random random = MyRandom.getRandom();
    private final int randomInt = this.random.nextInt();

    private List<Card> humanList; // holds human player creatures
    private List<Card> computerList; // holds computer creatures

    private int aiAggression = 0; // added by Masher, how aggressive the ai
                                  // attack will be depending on circumstances

    /**
     * <p>
     * Constructor for ComputerUtil_Attack2.
     * </p>
     * 
     * @param possibleAttackers
     *            a {@link forge.CardList} object.
     * @param possibleBlockers
     *            a {@link forge.CardList} object.
     */
    public ComputerUtilAttack(final List<Card> possibleAttackers, final List<Card> possibleBlockers) {
        this.humanList = new ArrayList<Card>(possibleBlockers);
        this.humanList = CardLists.filter(this.humanList, CardPredicates.Presets.CREATURES);

        this.computerList = new ArrayList<Card>(possibleAttackers);
        this.computerList = CardLists.filter(this.computerList, CardPredicates.Presets.CREATURES);

        this.attackers = this.getPossibleAttackers(possibleAttackers);
        this.blockers = this.getPossibleBlockers(possibleBlockers, this.attackers);
    } // constructor

    /**
     * <p>
     * sortAttackers.
     * </p>
     * 
     * @param in
     *            a {@link forge.CardList} object.
     * @return a {@link forge.CardList} object.
     */
    public final List<Card> sortAttackers(final List<Card> in) {
        final List<Card> list = new ArrayList<Card>();

        // Cards with triggers should come first (for Battle Cry)
        for (final Card attacker : in) {
            for (final Trigger trigger : attacker.getTriggers()) {
                if (trigger.getMode() == TriggerType.Attacks) {
                    list.add(attacker);
                }
            }
        }

        for (final Card attacker : in) {
            if (!list.contains(attacker)) {
                list.add(attacker);
            }
        }

        return list;
    } // sortAttackers()

    // Is there any reward for attacking? (for 0/1 creatures there is not)
    /**
     * <p>
     * isEffectiveAttacker.
     * </p>
     * 
     * @param attacker
     *            a {@link forge.Card} object.
     * @param combat
     *            a {@link forge.game.phase.Combat} object.
     * @return a boolean.
     */
    public final boolean isEffectiveAttacker(final Player ai, final Card attacker, final Combat combat) {

        // if the attacker will die when attacking don't attack
        if ((attacker.getNetDefense() + CombatUtil.predictToughnessBonusOfAttacker(attacker, null, combat)) <= 0) {
            return false;
        }

        final Player opp = ai.getOpponent();
        if (CombatUtil.damageIfUnblocked(attacker, opp, combat) > 0) {
            return true;
        }
        if (CombatUtil.poisonIfUnblocked(attacker, opp, combat) > 0) {
            return true;
        }
        if (this.attackers.size() == 1 && attacker.hasKeyword("Exalted")) {
            return true;
        }

        final List<Card> controlledByCompy = ai.getCardsIn(ZoneType.STATIC_ABILITIES_SOURCE_ZONES);
        for (final Card c : controlledByCompy) {
            for (final Trigger trigger : c.getTriggers()) {
                if (CombatUtil.combatTriggerWillTrigger(attacker, null, trigger, combat)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * <p>
     * getPossibleAttackers.
     * </p>
     * 
     * @param in
     *            a {@link forge.CardList} object.
     * @return a {@link forge.CardList} object.
     */
    public final List<Card> getPossibleAttackers(final List<Card> in) {
        List<Card> list = new ArrayList<Card>(in);
        list = CardLists.filter(list, new Predicate<Card>() {
            @Override
            public boolean apply(final Card c) {
                return CombatUtil.canAttack(c);
            }
        });
        return list;
    } // getPossibleAttackers()

    /**
     * <p>
     * getPossibleBlockers.
     * </p>
     * 
     * @param blockers
     *            a {@link forge.CardList} object.
     * @param attackers
     *            a {@link forge.CardList} object.
     * @return a {@link forge.CardList} object.
     */
    public final List<Card> getPossibleBlockers(final List<Card> blockers, final List<Card> attackers) {
        List<Card> possibleBlockers = new ArrayList<Card>(blockers);
        possibleBlockers = CardLists.filter(possibleBlockers, new Predicate<Card>() {
            @Override
            public boolean apply(final Card c) {
                return canBlockAnAttacker(c, attackers);
            }
        });
        return possibleBlockers;
    } // getPossibleBlockers()

    /**
     * <p>
     * canBlockAnAttacker.
     * </p>
     * 
     * @param c
     *            a {@link forge.Card} object.
     * @param attackers
     *            a {@link forge.CardList} object.
     * @return a boolean.
     */
    public final boolean canBlockAnAttacker(final Card c, final List<Card> attackers) {
        final List<Card> attackerList = new ArrayList<Card>(attackers);
        if (!c.isCreature()) {
            return false;
        }
        for (final Card attacker : attackerList) {
            if (CombatUtil.canBlock(attacker, c)) {
                return true;
            }
        }
        return false;
    } // getPossibleBlockers()

    // this checks to make sure that the computer player
    // doesn't lose when the human player attacks
    // this method is used by getAttackers()
    /**
     * <p>
     * notNeededAsBlockers.
     * </p>
     * 
     * @param attackers
     *            a {@link forge.CardList} object.
     * @param combat
     *            a {@link forge.game.phase.Combat} object.
     * @return a {@link forge.CardList} object.
     */
    public final List<Card> notNeededAsBlockers(final Player ai, final List<Card> attackers, final Combat combat) {
        final List<Card> notNeededAsBlockers = new ArrayList<Card>(attackers);
        int fixedBlockers = 0;
        final List<Card> vigilantes = new ArrayList<Card>();
        //check for time walks
        if (Singletons.getModel().getGame().getPhaseHandler().getNextTurn().equals(ai)) {
            return attackers;
        }
        for (final Card c : this.computerList) {
            if (c.getName().equals("Masako the Humorless")) {
                // "Tapped creatures you control can block as though they were untapped."
                return attackers;
            }
            if (!attackers.contains(c)) { // this creature can't attack anyway
                if (canBlockAnAttacker(c, this.humanList)) {
                    fixedBlockers++;
                }
                continue;
            }
            if (c.hasKeyword("Vigilance")) {
                vigilantes.add(c);
                notNeededAsBlockers.remove(c); // they will be re-added later
                if (canBlockAnAttacker(c, this.humanList)) {
                    fixedBlockers++;
                }
            }
        }
        CardLists.sortAttackLowFirst(attackers);
        int blockersNeeded = this.humanList.size();

        // don't hold back creatures that can't block any of the human creatures
        final List<Card> list = this.getPossibleBlockers(attackers, this.humanList);

        //Calculate the amount of creatures necessary
        for (int i = 0; i < list.size(); i++) {
            if (!this.doesHumanAttackAndWin(ai, i)) {
                blockersNeeded = i;
                break;
            }
        }
        int blockersStillNeeded = blockersNeeded - fixedBlockers;
        blockersStillNeeded = Math.min(blockersNeeded, list.size());
        for (int i = 0; i < blockersStillNeeded; i++) {
            notNeededAsBlockers.remove(list.get(i));
        }

        // re-add creatures with vigilance
        notNeededAsBlockers.addAll(vigilantes);

        if (blockersNeeded > 1) {
            return notNeededAsBlockers;
        }

        final Player opp = ai.getOpponent();
        
        // Increase the total number of blockers needed by 1 if Finest Hour in
        // play
        // (human will get an extra first attack with a creature that untaps)
        // In addition, if the computer guesses it needs no blockers, make sure
        // that
        // it won't be surprised by Exalted
        final int humanExaltedBonus = this.countExaltedBonus(opp);

        if (humanExaltedBonus > 0) {
            final int nFinestHours = opp.getCardsIn(ZoneType.Battlefield, "Finest Hour").size();

            if (((blockersNeeded == 0) || (nFinestHours > 0)) && (this.humanList.size() > 0)) {
                //
                // total attack = biggest creature + exalted, *2 if Rafiq is in
                // play
                int humanBaseAttack = this.getAttack(this.humanList.get(0)) + humanExaltedBonus;
                if (nFinestHours > 0) {
                    // For Finest Hour, one creature could attack and get the
                    // bonus TWICE
                    humanBaseAttack = humanBaseAttack + humanExaltedBonus;
                }
                final int totalExaltedAttack = opp.isCardInPlay("Rafiq of the Many") ? 2 * humanBaseAttack
                        : humanBaseAttack;
                if (ai.getLife() - 3 <= totalExaltedAttack) {
                    // We will lose if there is an Exalted attack -- keep one
                    // blocker
                    if ((blockersNeeded == 0) && (notNeededAsBlockers.size() > 0)) {
                        notNeededAsBlockers.remove(0);
                    }

                    // Finest Hour allows a second Exalted attack: keep a
                    // blocker for that too
                    if ((nFinestHours > 0) && (notNeededAsBlockers.size() > 0)) {
                        notNeededAsBlockers.remove(0);
                    }
                }
            }
        }
        return notNeededAsBlockers;
    }

    // this uses a global variable, which isn't perfect
    /**
     * <p>
     * doesHumanAttackAndWin.
     * </p>
     * 
     * @param nBlockingCreatures
     *            a int.
     * @return a boolean.
     */
    public final boolean doesHumanAttackAndWin(final Player ai, final int nBlockingCreatures) {
        int totalAttack = 0;
        int totalPoison = 0;
        int blockersLeft = nBlockingCreatures;

        if (ai.cantLose()) {
            return false;
        }

        for (Card attacker : humanList) {
            if (!CombatUtil.canAttackNextTurn(attacker)) {
                continue;
            }
            if (CombatUtil.canBeBlocked(attacker) && blockersLeft > 0) {
                blockersLeft--;
                continue;
            }
            totalAttack += CombatUtil.damageIfUnblocked(attacker, ai, null);
            totalPoison += CombatUtil.poisonIfUnblocked(attacker, ai, null);
        }

        if (ai.getLife() <= totalAttack
                && !ai.cantLoseForZeroOrLessLife()
                && ai.canLoseLife()) {
            return true;
        }
        return ai.getPoisonCounters() + totalPoison > 9;
    }

    /**
     * <p>
     * doAssault.
     * </p>
     * 
     * @return a boolean.
     */
    private boolean doAssault(final Player ai) {
        // Beastmaster Ascension
        if (ai.isCardInPlay("Beastmaster Ascension")
                && (this.attackers.size() > 1)) {
            final List<Card> beastions = ai.getCardsIn(ZoneType.Battlefield, "Beastmaster Ascension");
            int minCreatures = 7;
            for (final Card beastion : beastions) {
                final int counters = beastion.getCounters(Counters.QUEST);
                minCreatures = Math.min(minCreatures, 7 - counters);
            }
            if (this.attackers.size() >= minCreatures) {
                return true;
            }
        }

        CardLists.sortAttack(this.attackers);

        final List<Card> unblockedAttackers = new ArrayList<Card>();
        final List<Card> remainingAttackers = new ArrayList<Card>(this.attackers);
        final List<Card> remainingBlockers = new ArrayList<Card>(this.blockers);
        final Player opp = ai.getOpponent();


        for (int i = 0; i < this.attackers.size(); i++) {
            if (!CombatUtil.canBeBlocked(this.attackers.get(i), this.blockers)
                    || this.attackers.get(i).hasKeyword("You may have CARDNAME assign its combat damage as though"
                            + " it weren't blocked.")) {
                unblockedAttackers.add(this.attackers.get(i));
            }
        }

        for (Card blocker : this.blockers) {
            if (blocker.hasKeyword("CARDNAME can block any number of creatures.")) {
                for (Card attacker : this.attackers) {
                    if (CombatUtil.canBlock(attacker, blocker)) {
                        remainingAttackers.remove(attacker);
                    }
                }
                remainingBlockers.remove(blocker);
            }
        }

        // presumes the Human will block
        for (Card blocker : remainingBlockers) {
            if (remainingAttackers.isEmpty()) {
                break;
            }
            if (blocker.hasKeyword("CARDNAME can block an additional creature.")) {
                remainingAttackers.remove(0);
                if (remainingAttackers.isEmpty()) {
                    break;
                }
            }
            remainingAttackers.remove(0);
        }
        unblockedAttackers.addAll(remainingAttackers);

        if ((CombatUtil.sumDamageIfUnblocked(remainingAttackers, opp) >= opp.getLife())
                && opp.canLoseLife()
                && !((opp.cantLoseForZeroOrLessLife() || ai.cantWin()) && (opp.getLife() < 1))) {
            return true;
        }

        if (CombatUtil.sumPoisonIfUnblocked(remainingAttackers, opp) >= (10 - opp.getPoisonCounters())) {
            return true;
        }

        return false;
    } // doAssault()

    /**
     * <p>
     * chooseDefender.
     * </p>
     * 
     * @param c
     *            a {@link forge.game.phase.Combat} object.
     * @param bAssault
     *            a boolean.
     */
    public final void chooseDefender(final Player ai, final Combat c, final boolean bAssault) {
        final List<GameEntity> defs = c.getDefenders();

        // Start with last planeswalker
        int n = defs.size() - 1;

        final Object entity = ai.getMustAttackEntity();
        if (null != entity) {
            final List<GameEntity> defenders = Singletons.getModel().getGame().getCombat().getDefenders();
            n = defenders.indexOf(entity);
            if (-1 == n) {
                System.out.println("getMustAttackEntity() returned something not in defenders.");
                c.setCurrentDefenderNumber(0);
            } else {
                c.setCurrentDefenderNumber(n);
            }
        } else {
            if (bAssault) {
                c.setCurrentDefenderNumber(0);
            } else {
                c.setCurrentDefenderNumber(n);
            }
        }

        return;
    }

    /**
     * <p>
     * Getter for the field <code>attackers</code>.
     * </p>
     * 
     * @return a {@link forge.game.phase.Combat} object.
     */
    public final Combat getAttackers(final Player ai) {
        // if this method is called multiple times during a turn,
        // it will always return the same value
        // randomInt is used so that the computer doesn't always
        // do the same thing on turn 3 if he had the same creatures in play
        // I know this is a little confusing
        GameState game = Singletons.getModel().getGame();
        
        this.random.setSeed(game.getPhaseHandler().getTurn() + this.randomInt);

        final Combat combat = new Combat();
        combat.setAttackingPlayer(game.getCombat().getAttackingPlayer());
        combat.setDefendingPlayer(game.getCombat().getDefendingPlayer());

        game.getCombat().initiatePossibleDefenders(game.getCombat().getDefendingPlayer());
        combat.setDefenders(game.getCombat().getDefenders());

        if (this.attackers.isEmpty()) {
            return combat;
        }

        final boolean bAssault = this.doAssault(ai);
        // Determine who will be attacked
        this.chooseDefender(ai, combat, bAssault);
        List<Card> attackersLeft = new ArrayList<Card>(this.attackers);
        // Attackers that don't really have a choice
        for (final Card attacker : this.attackers) {
            if (!CombatUtil.canAttack(attacker, combat)) {
                continue;
            }
            boolean mustAttack = false;
            for (String s : attacker.getKeyword()) {
                if (s.equals("CARDNAME attacks each turn if able.")
                        || s.equals("At the beginning of the end step, destroy CARDNAME.")
                        || s.equals("At the beginning of the end step, exile CARDNAME.")
                        || s.equals("At the beginning of the end step, sacrifice CARDNAME.")) {
                    mustAttack = true;
                    break;
                }
            }
            if (mustAttack || attacker.getSacrificeAtEOT() || attacker.getSirenAttackOrDestroy()
                    || attacker.getController().getMustAttackEntity() != null
                    || attacker.getSVar("MustAttack").equals("True")) {
                combat.addAttacker(attacker);
                attackersLeft.remove(attacker);
            }
        }
        if (attackersLeft.isEmpty()) {
            return combat;
        }
        if (bAssault) {
            System.out.println("Assault");
            CardLists.sortAttack(attackersLeft);
            for (Card attacker : attackersLeft) {
                if (CombatUtil.canAttack(attacker, combat) && this.isEffectiveAttacker(ai, attacker, combat)) {
                    combat.addAttacker(attacker);
                }
            }
            return combat;
        }

        // Exalted
        if (combat.getAttackers().isEmpty()) {
            boolean exalted = false;
            int exaltedCount = 0;
            for (Card c : ai.getCardsIn(ZoneType.Battlefield)) {
                if (c.getName().equals("Rafiq of the Many") || c.getName().equals("Battlegrace Angel")) {
                    exalted = true;
                    break;
                }
                if (c.getName().equals("Finest Hour")
                        && game.getPhaseHandler().isFirstCombat()) {
                    exalted = true;
                    break;
                }
                if (c.hasKeyword("Exalted")) {
                    exaltedCount++;
                    if (exaltedCount > 2) {
                        exalted = true;
                        break;
                    }
                }
            }
            if (exalted) {
                CardLists.sortAttack(this.attackers);
                System.out.println("Exalted");
                this.aiAggression = 6;
                for (Card attacker : this.attackers) {
                    if (CombatUtil.canAttack(attacker, combat) && this.shouldAttack(ai, attacker, this.blockers, combat)) {
                        combat.addAttacker(attacker);
                        return combat;
                    }
                }
            }
        }

        // *******************
        // Evaluate the creature forces
        // *******************

        int computerForces = 0;
        int humanForces = 0;
        int humanForcesForAttritionalAttack = 0;

        // examine the potential forces
        final List<Card> nextTurnAttackers = new ArrayList<Card>();
        int candidateCounterAttackDamage = 0;
        // int candidateTotalBlockDamage = 0;
        for (final Card pCard : this.humanList) {

            // if the creature can attack next turn add it to counter attackers
            // list
            if (CombatUtil.canAttackNextTurn(pCard)) {
                nextTurnAttackers.add(pCard);
                if (pCard.getNetCombatDamage() > 0) {
                    candidateCounterAttackDamage += pCard.getNetCombatDamage();
                    // candidateTotalBlockDamage += pCard.getNetCombatDamage();
                    humanForces += 1; // player forces they might use to attack
                }
            }
            // increment player forces that are relevant to an attritional
            // attack - includes walls
            if (CombatUtil.canBlock(pCard, true)) {
                humanForcesForAttritionalAttack += 1;
            }
        }

        // find the potential counter attacking damage compared to AI life total
        double aiLifeToPlayerDamageRatio = 1000000;
        if (candidateCounterAttackDamage > 0) {
            aiLifeToPlayerDamageRatio = (double) ai.getLife() / candidateCounterAttackDamage;
        }

        final Player opp = ai.getOpponent();
        // get the potential damage and strength of the AI forces
        final List<Card> candidateAttackers = new ArrayList<Card>();
        int candidateUnblockedDamage = 0;
        for (final Card pCard : this.computerList) {
            // if the creature can attack then it's a potential attacker this
            // turn, assume summoning sickness creatures will be able to
            if (CombatUtil.canAttackNextTurn(pCard)) {
                candidateAttackers.add(pCard);
                if (pCard.getNetCombatDamage() > 0) {
                    candidateUnblockedDamage += CombatUtil.damageIfUnblocked(pCard, opp, null);
                    computerForces += 1;
                }

            }
        }

        // find the potential damage ratio the AI can cause
        double humanLifeToDamageRatio = 1000000;
        if (candidateUnblockedDamage > 0) {
            humanLifeToDamageRatio = (double) opp.getLife() / candidateUnblockedDamage;
        }

        /*
         * System.out.println(String.valueOf(aiLifeToPlayerDamageRatio) +
         * " = ai life to player damage ratio");
         * System.out.println(String.valueOf(playerLifeToDamageRatio) +
         * " = player life ai player damage ratio");
         */

        // determine if the ai outnumbers the player
        final int outNumber = computerForces - humanForces;

        for (Card blocker : this.blockers) {
            if (blocker.hasKeyword("CARDNAME can block any number of creatures.")) {
                aiLifeToPlayerDamageRatio--;
            }
        }

        // compare the ratios, higher = better for ai
        final double ratioDiff = aiLifeToPlayerDamageRatio - humanLifeToDamageRatio;

        /*
         * System.out.println(String.valueOf(ratioDiff) +
         * " = ratio difference, higher = better for ai");
         * System.out.println(String.valueOf(outNumber) +
         * " = outNumber, higher = better for ai");
         */

        // *********************
        // if outnumber and superior ratio work out whether attritional all out
        // attacking will work
        // attritional attack will expect some creatures to die but to achieve
        // victory by sheer weight
        // of numbers attacking turn after turn. It's not calculate very
        // carefully, the accuracy
        // can probably be improved
        // *********************
        boolean doAttritionalAttack = false;
        // get list of attackers ordered from low power to high
        CardLists.sortAttackLowFirst(this.attackers);
        // get player life total
        int humanLife = opp.getLife();
        // get the list of attackers up to the first blocked one
        final List<Card> attritionalAttackers = new ArrayList<Card>();
        for (int x = 0; x < (this.attackers.size() - humanForces); x++) {
            attritionalAttackers.add(this.attackers.get(x));
        }
        // until the attackers are used up or the player would run out of life
        int attackRounds = 1;
        while (attritionalAttackers.size() > 0 && humanLife > 0 && attackRounds < 99) {
            // sum attacker damage
            int damageThisRound = 0;
            for (int y = 0; y < attritionalAttackers.size(); y++) {
                damageThisRound += attritionalAttackers.get(y).getNetCombatDamage();
            }
            // remove from player life
            humanLife -= damageThisRound;
            // shorten attacker list by the length of the blockers - assuming
            // all blocked are killed for convenience
            for (int z = 0; z < humanForcesForAttritionalAttack; z++) {
                if (attritionalAttackers.size() > 0) {
                    attritionalAttackers.remove(attritionalAttackers.size() - 1);
                }
            }
            attackRounds += 1;
            if (humanLife <= 0) {
                doAttritionalAttack = true;
            }
        }
        // System.out.println(doAttritionalAttack + " = do attritional attack");
        // *********************
        // end attritional attack calculation
        // *********************

        // *********************
        // see how long until unblockable attackers will be fatal
        // *********************
        double unblockableDamage = 0;
        double nextUnblockableDamage = 0;
        double turnsUntilDeathByUnblockable = 0;
        boolean doUnblockableAttack = false;
        for (final Card attacker : this.attackers) {
            boolean isUnblockableCreature = true;
            // check blockers individually, as the bulk canBeBlocked doesn't
            // check all circumstances
            for (final Card blocker : this.blockers) {
                if (CombatUtil.canBlock(attacker, blocker)) {
                    isUnblockableCreature = false;
                    break;
                }
            }
            if (isUnblockableCreature) {
                unblockableDamage += CombatUtil.damageIfUnblocked(attacker, opp, combat);
            }
        }
        for (final Card attacker : nextTurnAttackers) {
            boolean isUnblockableCreature = true;
            // check blockers individually, as the bulk canBeBlocked doesn't
            // check all circumstances
            for (final Card blocker : this.computerList) {
                if (CombatUtil.canBlock(attacker, blocker, true)) {
                    isUnblockableCreature = false;
                    break;
                }
            }
            if (isUnblockableCreature) {
                nextUnblockableDamage += CombatUtil.damageIfUnblocked(attacker, opp, null);
            }
        }
        if (unblockableDamage > 0 && !opp.cantLoseForZeroOrLessLife()
                && opp.canLoseLife()) {
            turnsUntilDeathByUnblockable = 1 + (opp.getLife() - unblockableDamage) / nextUnblockableDamage;
        }
        if (unblockableDamage > opp.getLife() && opp.canLoseLife()) {
            doUnblockableAttack = true;
        }
        // *****************
        // end see how long until unblockable attackers will be fatal
        // *****************

        // decide on attack aggression based on a comparison of forces, life
        // totals and other considerations
        // some bad "magic numbers" here, TODO replace with nice descriptive
        // variable names
        if (ratioDiff > 0 && doAttritionalAttack) { 
            this.aiAggression = 5; // attack at all costs
        } else if (ratioDiff >= 1 && (humanLifeToDamageRatio < 2 || outNumber > 0)) {
            this.aiAggression = 4; // attack expecting to trade or damage player.
        } else if ((humanLifeToDamageRatio < 2 && ratioDiff >= 0) || ratioDiff > 3
                || (ratioDiff > 0 && outNumber > 0)) {
            this.aiAggression = 3; // attack expecting to make good trades or damage player.
        } else if (ratioDiff >= 0 || ratioDiff + outNumber >= -1 || aiLifeToPlayerDamageRatio > 1) {
            // at 0 ratio expect to potentially gain an advantage by attacking first
            // if the ai has a slight advantage
            // or the ai has a significant advantage numerically but only a slight disadvantage damage/life
            this.aiAggression = 2; // attack expecting to destroy creatures/be unblockable
        } else if (doUnblockableAttack || (ratioDiff * -1 < turnsUntilDeathByUnblockable)) {
            this.aiAggression = 1;
            // look for unblockable creatures that might be
            // able to attack for a bit of fatal damage even if the player is significantly better
        } else {
            this.aiAggression = 0;
        } // stay at home to block
        System.out.println(String.valueOf(this.aiAggression) + " = ai aggression");

        // ****************
        // Evaluation the end
        // ****************

        System.out.println("Normal attack");

        attackersLeft = this.notNeededAsBlockers(ai, attackersLeft, combat);
        System.out.println(attackersLeft.size());

        attackersLeft = this.sortAttackers(attackersLeft);

        for (int i = 0; i < attackersLeft.size(); i++) {
            final Card attacker = attackersLeft.get(i);
            if (this.aiAggression < 5 && !attacker.hasFirstStrike() && !attacker.hasDoubleStrike()
                    && CombatUtil.getTotalFirstStrikeBlockPower(attacker, ai.getOpponent())
                    >= attacker.getKillDamage()) {
                continue;
            }

            if (this.shouldAttack(ai, attacker, this.blockers, combat)
                    && CombatUtil.canAttack(attacker, combat)) {
                combat.addAttacker(attacker);
                // check if attackers are enough to finish the attacked planeswalker 
                if (combat.getCurrentDefenderNumber() > 0) {
                    Card pw = (Card) combat.getDefender();
                    final int blockNum = this.blockers.size();
                    int attackNum = 0;
                    int damage = 0;
                    List<Card> attacking = combat.getAttackersByDefenderSlot(combat.getCurrentDefenderNumber());
                    CardLists.sortAttackLowFirst(attacking);
                    for (Card atta : attacking) {
                        if (attackNum >= blockNum || !CombatUtil.canBeBlocked(attacker, this.blockers)) {
                            damage += CombatUtil.damageIfUnblocked(atta, opp, null);
                        } else if (CombatUtil.canBeBlocked(attacker, this.blockers)) {
                            attackNum++;
                        }
                    }
                    // if enough damage: switch to next planeswalker or player
                    if (damage >= pw.getCounters(Counters.LOYALTY)) {
                        combat.setCurrentDefenderNumber(combat.getCurrentDefenderNumber() - 1);
                    }
                }
            }
        }

        return combat;
    } // getAttackers()

    /**
     * <p>
     * countExaltedBonus.
     * </p>
     * 
     * @param player
     *            a {@link forge.game.player.Player} object.
     * @return a int.
     */
    public final int countExaltedBonus(final Player player) {
        List<Card> list = player.getCardsIn(ZoneType.Battlefield);
        list = CardLists.filter(list, new Predicate<Card>() {
            @Override
            public boolean apply(final Card c) {
                return c.hasKeyword("Exalted");
            }
        });

        return list.size();
    }

    /**
     * <p>
     * getAttack.
     * </p>
     * 
     * @param c
     *            a {@link forge.Card} object.
     * @return a int.
     */
    public final int getAttack(final Card c) {
        int n = c.getNetCombatDamage();

        if (c.hasKeyword("Double Strike")) {
            n *= 2;
        }

        return n;
    }

    /**
     * <p>
     * shouldAttack.
     * </p>
     * 
     * @param attacker
     *            a {@link forge.Card} object.
     * @param defenders
     *            a {@link forge.CardList} object.
     * @param combat
     *            a {@link forge.game.phase.Combat} object.
     * @return a boolean.
     */
    public final boolean shouldAttack(final Player ai, final Card attacker, final List<Card> defenders, final Combat combat) {
        boolean canBeKilledByOne = false; // indicates if the attacker can be
                                          // killed by a single blocker
        boolean canKillAll = true; // indicates if the attacker can kill all
                                   // single blockers
        boolean canKillAllDangerous = true; // indicates if the attacker can
                                            // kill all single blockers with
                                            // wither or infect
        boolean isWorthLessThanAllKillers = true;
        boolean canBeBlocked = false;
        int numberOfPossibleBlockers = 0;

        if (!this.isEffectiveAttacker(ai, attacker, combat)) {
            return false;
        }
        boolean hasAttackEffect = attacker.getSVar("HasAttackEffect").equals("TRUE") || attacker.hasStartOfKeyword("Annihilator");
        boolean hasCombatEffect = attacker.getSVar("HasCombatEffect").equals("TRUE"); // is there a gain in attacking even when the blocker is not killed (Lifelink, Wither,...)
        if (!hasCombatEffect) {
            for (String keyword : attacker.getKeyword()) {
                if (keyword.equals("Wither") || keyword.equals("Infect") || keyword.equals("Lifelink") ) {
                    hasCombatEffect = true;
                    break;
                }
            }
        }

        // look at the attacker in relation to the blockers to establish a
        // number of factors about the attacking
        // context that will be relevant to the attackers decision according to
        // the selected strategy
        for (final Card defender : defenders) {
            if (CombatUtil.canBlock(attacker, defender)) { 
                numberOfPossibleBlockers += 1;
                if (CombatUtil.canDestroyAttacker(attacker, defender, combat, false)
                        && !(attacker.hasKeyword("Undying") && attacker.getCounters(Counters.P1P1) == 0)) {
                    canBeKilledByOne = true; // there is a single creature on
                                             // the battlefield that can kill
                                             // the creature
                    // see if the defending creature is of higher or lower
                    // value. We don't want to attack only to lose value
                    if (isWorthLessThanAllKillers && attacker.getSVar("SacMe").equals("") 
                            && CardFactoryUtil.evaluateCreature(defender) <= CardFactoryUtil.evaluateCreature(attacker)) {
                        isWorthLessThanAllKillers = false;
                    }
                }
                // see if this attacking creature can destroy this defender, if
                // not record that it can't kill everything
                if (!CombatUtil.canDestroyBlocker(defender, attacker, combat, false)) {
                    canKillAll = false;
                    if (!canKillAllDangerous) {
                        continue;
                    }
                    if (defender.getSVar("HasCombatEffect").equals("TRUE")) {
                        canKillAllDangerous = false;
                    } else {
                        for (String keyword : defender.getKeyword()) {
                            if (keyword.equals("Wither") || keyword.equals("Infect") || keyword.equals("Lifelink") ) {
                                canKillAllDangerous = false;
                                break;
                                // there is a creature that can survive an attack from this creature 
                                // and combat will have negative effects
                            }
                        }
                    }
                }
            }
        }

        // if the creature cannot block and can kill all opponents they might as
        // well attack, they do nothing staying back
        if (canKillAll && !CombatUtil.canBlock(attacker) && isWorthLessThanAllKillers) {
            System.out.println(attacker.getName()
                    + " = attacking because they can't block, expecting to kill or damage player");
            return true;
        }

        if (numberOfPossibleBlockers > 1
                || (numberOfPossibleBlockers == 1 
                    && !attacker.hasKeyword("CARDNAME can't be blocked except by two or more creatures."))) {
            canBeBlocked = true;
        }
        /*System.out.println(attacker + " canBeKilledByOne: " + canBeKilledByOne + " canKillAll: "
                + canKillAll + " isWorthLessThanAllKillers: " + isWorthLessThanAllKillers + " canBeBlocked: " + canBeBlocked);*/
        // decide if the creature should attack based on the prevailing strategy
        // choice in aiAggression
        switch (this.aiAggression) {
        case 6: // Exalted: expecting to at least kill a creature of equal value or not be blocked
            if ((canKillAll && isWorthLessThanAllKillers) || !canBeBlocked) {
                System.out.println(attacker.getName() + " = attacking expecting to kill creature, or is unblockable");
                return true;
            }
            break;
        case 5: // all out attacking
            System.out.println(attacker.getName() + " = all out attacking");
            return true;
        case 4: // expecting to at least trade with something
            if (canKillAll || (canKillAllDangerous && !canBeKilledByOne) || !canBeBlocked) {
                System.out.println(attacker.getName() + " = attacking expecting to at least trade with something");
                return true;
            }
            break;
        case 3: // expecting to at least kill a creature of equal value, not be
                // blocked
            if ((canKillAll && isWorthLessThanAllKillers) 
                    || ((canKillAllDangerous || hasAttackEffect || hasCombatEffect) && !canBeKilledByOne)
                    || !canBeBlocked) {
                System.out.println(attacker.getName()
                        + " = attacking expecting to kill creature or cause damage, or is unblockable");
                return true;
            }
            break;
        case 2: // attack expecting to attract a group block or destroying a
                // single blocker and surviving
            if (((canKillAll || hasAttackEffect || hasCombatEffect) && !canBeKilledByOne) || !canBeBlocked) {
                System.out.println(attacker.getName() + " = attacking expecting to survive or attract group block");
                return true;
            }
            break;
        case 1: // unblockable creatures only
            if (!canBeBlocked || (numberOfPossibleBlockers == 1 && canKillAll && !canBeKilledByOne)) {
                System.out.println(attacker.getName() + " = attacking expecting not to be blocked");
                return true;
            }
            break;
        default:
            break;
        }
        return false; // don't attack
    }

} // end class ComputerUtil_Attack2
