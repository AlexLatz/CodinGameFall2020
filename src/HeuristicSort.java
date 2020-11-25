import java.util.*;
import java.io.*;
import java.math.*;
/*
* This version was my initial attempt at the problem. Using heuristics and comparators, it managed to get halfway through Bronze League.
* Its main drawback was its lack of foresight as it only thought one move at a time.
 */
/*
 WEIGHTS FOR TWEAKING: greediness (weight_price) based on lead
 will to learn (weight_learn) based on it versus others
 will to hoard (weight_spell) based on its inventory size
 will to rest (weight_used) based on its options
 will to use repeatables over other types (weight_repeatable)

 MAIN ISSUES:
 it rests too often
 */
class Player {
    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        // game loop
        while (true) {
            int actionCount = in.nextInt(); // the number of spells and recipes in play
            boolean action = false;
            ArrayList<Recipe> potions = new ArrayList<>();
            ArrayList<Spell> spells = new ArrayList<>();
            ArrayList<TomeSpell> tome = new ArrayList<>();
            for (int i = 0; i < actionCount; i++) {
                int actionId = in.nextInt(); // the unique ID of this spell or recipe
                String actionType = in.next(); // in the first league: BREW; later: CAST, OPPONENT_CAST, LEARN, BREW
                int delta0 = in.nextInt(); // tier-0 ingredient change
                int delta1 = in.nextInt(); // tier-1 ingredient change
                int delta2 = in.nextInt(); // tier-2 ingredient change
                int delta3 = in.nextInt(); // tier-3 ingredient change
                int price = in.nextInt(); // the price in rupees if this is a potion
                int tomeIndex = in.nextInt(); // in the first two leagues: always 0; later: the index in the tome if this is a tome spell, equal to the read-ahead tax
                int taxCount = in.nextInt(); // in the first two leagues: always 0; later: the amount of taxed tier-0 ingredients you gain from learning this spell
                boolean castable = in.nextInt() != 0; // in the first league: always 0; later: 1 if this is a castable player spell
                boolean repeatable = in.nextInt() != 0; // for the first two leagues: always 0; later: 1 if this is a repeatable player spell
                if (actionType.equals("BREW")) potions.add(new Recipe(actionId, price, delta0, delta1, delta2, delta3));
                else if (actionType.equals("CAST")) spells.add(new Spell(actionId, price, true, repeatable, !castable, delta0, delta1, delta2, delta3));
                else if (actionType.equals("LEARN")) tome.add(new TomeSpell(actionId, price, tomeIndex, repeatable, !castable, delta0, delta1, delta2, delta3));
            }
            double[] inv = new double[0];
            for (int i = 0; i < 2; i++) {
                int inv0 = in.nextInt(); // tier-0 ingredients in inventory
                int inv1 = in.nextInt();
                int inv2 = in.nextInt();
                int inv3 = in.nextInt();
                int score = in.nextInt(); // amount of rupees
                if (i == 0) {
                    inv = new double[]{inv0, inv1, inv2, inv3};
                }
            }
            RecipeComparator recipeComparator = new RecipeComparator(inv);
            Collections.sort(potions, recipeComparator);
            Recipe chosen = potions.get(0);
            if (RecipeComparator.priceDelta(inv, chosen) == 0) {
                action = true;
                System.out.println("BREW " + chosen.id);
            } else {
                SpellComparator spellComparator = new SpellComparator(chosen, inv);
                spells.addAll(tome);
                Collections.sort(spells, spellComparator);
                Spell bestSpell = spells.get(0);
                if (!bestSpell.learned) {
                    TomeSpell tomeSpell = findTomeSpell(bestSpell, tome);
                    if (tomeSpell.cost > inv[0]) {
                        chosen = new Recipe(0, Integer.MAX_VALUE, tomeSpell.cost, 0, 0, 0);
                        spellComparator.setRecipe(chosen);
                        ArrayList<Spell> toRemove = new ArrayList<>();
                        for (Spell spell : spells) if (!Player.canAfford(spell.delta, inv) || !spell.learned) toRemove.add(spell);
                        spells.removeAll(toRemove);
                        Collections.sort(spells);
                        bestSpell = spells.get(0);
                    } else {
                        action = true;
                        System.out.println("LEARN " + bestSpell.id);
                    }
                }
                if (!action) {
                    if (bestSpell.used) {
                        System.out.println("REST");
                    } else {
                        if (invFull(inv, bestSpell.delta) && inv[0] + bestSpell.delta[0] > 4) {
                            ArrayList<TomeSpell> toRemove = new ArrayList<>();
                            for (TomeSpell tomeSpell : tome) if (tomeSpell.cost < (inv[0] + bestSpell.delta[0]) - 4) toRemove.add(tomeSpell);
                            tome.removeAll(toRemove);
                            Collections.sort(tome, spellComparator);
                            System.out.println("LEARN " + tome.get(0).id);
                        } else {
                            int times = 1;
                            double[] tmpInv = Arrays.copyOf(inv, 4);
                            for (int j = 0; j < 4; j++) tmpInv[j] += bestSpell.delta[j];
                            while (bestSpell.repeatable && spells.get(0).equals(bestSpell)) {
                                for (int j = 0; j < 4; j++) tmpInv[j] += bestSpell.delta[j];
                                if (!Player.canAfford(bestSpell.delta, tmpInv) || Player.invFull(inv, bestSpell.delta)) break;
                                spellComparator.setInv(tmpInv);
                                Collections.sort(spells, spellComparator);
                                times++;
                            }
                            if (!bestSpell.repeatable) System.out.println("CAST " + bestSpell.id);
                            else System.out.println("CAST " + bestSpell.id + " " + times);
                        }
                    }
                }
            }
            // Write an action using System.out.println()
            // To debug: System.err.println("Debug messages...");
            // in the first league: BREW <id> | WAIT; later: BREW <id> | CAST <id> [<times>] | LEARN <id> | REST | WAIT } }
        }
    }
    public static boolean canAfford(double[] cost, double[] inv) {
        for (int i = 0; i < 4; i++) {
            if (inv[i]+cost[i]<0) return false;
        }
        return true;
    }
    public static double deltaCost(Recipe recipe, double[] inv, Spell spell) {
        //distance from achieving the recipe
        double WEIGHT_SPELL = 0.8;
        double WEIGHT_USED = 0.4;
        double WEIGHT_REPEAT = 0.2;
        double WEIGHT_FULL = 0.3;
        double[] invCopy = Arrays.copyOf(inv, 4);
        int count = 0;
        double sum = 0;
        for (int i = 0; i < 4; i++) {
            invCopy[i] += recipe.delta[i];
            if (spell.used) invCopy[i] += (spell.delta[i] * WEIGHT_USED);
            if (spell.repeatable) invCopy[i] -= (spell.delta[i] * WEIGHT_REPEAT);
            else invCopy[i] += spell.delta[i];
            if (invCopy[i] < 0 && inv[i] >= invCopy[i]) sum += (-1 * invCopy[i]);
            if (invCopy[i] > 0) sum += (WEIGHT_SPELL * invCopy[i]);
            count += invCopy[i];
        }
        sum += (count/10.0) * WEIGHT_FULL;
        return sum;
    }
    public static TomeSpell findTomeSpell(Spell spell, ArrayList<TomeSpell> tome) {
        for (TomeSpell tomeSpell : tome) {
            if (spell.equals(tomeSpell)) return tomeSpell;
        }
        return null;
    }
    public static boolean invFull(double[] inv, double[] delta) {
        double[] invCopy = Arrays.copyOf(inv, 4);
        int sum = 0;
        for (int i = 0; i < 4; i++) {
            invCopy[i] += delta[i];
            if (invCopy[i] > 0) sum += invCopy[i];
        }
        return sum >= 10;
    }
}

class Recipe implements Comparable<Recipe>{
    final int id;
    final int price;
    final double[] delta;
    public Recipe(int id, int price, int d0, int d1, int d2, int d3) {
        this.id = id;
        this.price = price;
        this.delta = new double[]{d0, d1, d2, d3};
    }
    @Override
    public int compareTo(Recipe that) {
        return -1 * Integer.compare(this.price, that.price);
    }
}

class Spell extends Recipe {
    boolean used;
    boolean repeatable;
    boolean learned;
    public Spell(int id, int price, boolean learned, boolean repeatable, boolean used, int d0, int d1, int d2, int d3) {
        super(id, price, d0, d1, d2, d3);
        this.repeatable = repeatable;
        this.used = used;
        this.learned = learned;
    }
    public double[] deltaCount(int times) {
        if (repeatable) {
            double[] deltaCopy = Arrays.copyOf(delta, 4);
            for (int i = 0; i < 4; i++) {
                deltaCopy[i] *= times;
            }
            return deltaCopy;
        } else return delta;
    }
    @Override
    public boolean equals(Object o) {
        Spell that = (Spell)o;
        return this.id == that.id;
    }
}

class TomeSpell extends Spell {
    int cost;
    public TomeSpell(int id, int price, int cost, boolean repeatable, boolean used, int d0, int d1, int d2, int d3) {
        super(id, price, false, repeatable, used, d0, d1, d2, d3);
        this.cost = cost;
    }
}

class SpellComparator implements Comparator<Spell> {
    Recipe recipe;
    double[] inv;
    public SpellComparator(Recipe recipe, double[] inv) {
        this.recipe = recipe;
        this.inv = inv;
    }
    public void setInv(double[] inv) {
        this.inv = inv;
    }
    public void setRecipe(Recipe recipe) {
        this.recipe = recipe;
    }
    public int compare(Spell s1, Spell s2) {
        //compare the spells based off 2x used, dist from chosen recipe
        double delta1 = Player.deltaCost(recipe, inv, s1);
        double delta2 = Player.deltaCost(recipe, inv, s2);
        if (!Player.canAfford(s1.delta, inv) || Player.invFull(inv, s1.delta)) delta1 = Double.POSITIVE_INFINITY;
        if (!Player.canAfford(s2.delta, inv) || Player.invFull(inv, s2.delta)) delta2 = Double.MAX_VALUE;
        return Double.compare(delta1, delta2);
    }
}
class RecipeComparator implements Comparator<Recipe> {
    double WEIGHTED_PRICE = 0.4;
    double[] inv;
    public RecipeComparator(double[] inv) {
        this.inv = inv;
    }
    public static double priceDelta(double[] inv, Recipe recipe) {
        double sum = 0;
        double[] invCopy = Arrays.copyOf(inv, 4);
        for (int i = 0; i < 4; i++) {
            invCopy[i] += recipe.delta[i];
            if (invCopy[i] < 0) sum += (-1 * invCopy[i]);
        }
        return sum;
    }
    public int compare(Recipe r1, Recipe r2) {
        double delta1 = priceDelta(inv, r1);
        double delta2 = priceDelta(inv, r2);
        return Double.compare(delta1 + ((-1 * r1.price) * WEIGHTED_PRICE), delta2 + ((-1 * r2.price) * WEIGHTED_PRICE));
    }
}