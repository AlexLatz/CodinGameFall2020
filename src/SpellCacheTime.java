import java.util.*;

/**
 * This variant is my favorite in terms of complexity and structure. It reuses Spell objects to save time, has input optimizations, and a fully functional A* with timer.
 * This did not end up being the most effective, as the timer was more and more offset per turn due to the Java GC struggling to recycle the GameStates from the priority queue.
 */
class Player {
    public static void main(final String[] args) {
        final Scanner in = new Scanner(System.in);
        Recipe lastRecipe = null;
        final HashMap<Integer, Spell> spells = new HashMap<>();
        int turn = 0;
        // game loop
        while (true) {
            final int actionCount = in.nextInt(); // the number of spells and recipes in play
            final boolean action = false;
            final HashMap<Integer, Recipe> potions = new HashMap<>();
            final HashMap<Integer, TomeSpell> tome = new HashMap<>();
            in.nextLine();
            final long start = System.nanoTime();
            for (int i = 0; i < actionCount; i++) {
                final String[] input = in.nextLine().split(" ");
                if (input[1].equals("BREW") && !potions.containsKey(Integer.parseInt(input[0])))
                    potions.put(Integer.parseInt(input[0]), new Recipe(input));
                else if (input[1].equals("CAST") && !spells.containsKey(Integer.parseInt(input[0])))
                    spells.put(Integer.parseInt(input[0]), new Spell(input));
                else if (input[1].equals("LEARN")) tome.put(Integer.parseInt(input[0]), new TomeSpell(input));
            }
            final String[] invStr = in.nextLine().split(" ");
            final int[] inv = new int[]{Integer.parseInt(invStr[0]), Integer.parseInt(invStr[1]), Integer.parseInt(invStr[2]), Integer.parseInt(invStr[3])};
            in.nextLine();
            final RecipeComparator recipeComparator = new RecipeComparator(inv);
            final ArrayList<Recipe> potionList = new ArrayList<>(potions.values());
            potionList.sort(recipeComparator);
            Recipe chosen = potionList.get(0);
            if (lastRecipe == null || (!lastRecipe.equals(chosen) && RecipeComparator.priceDelta(inv, lastRecipe) - RecipeComparator.priceDelta(inv, chosen) < -1))
                lastRecipe = chosen;
            else if (potions.containsValue(lastRecipe)) chosen = lastRecipe;
            if (Player.delta(chosen, null, inv) == 0) {
                potions.remove(chosen.id);
                lastRecipe = null;
                System.out.println("BREW " + chosen.id);
            } else {
                final PriorityQueue<GameState> pq = new PriorityQueue<>();
                pq.add(new GameState(chosen, spells, tome, inv, null, null, 0, 0, Player.delta(chosen, null, inv)));
                while (pq.peek().delta > 0 && System.nanoTime() - start < 35000000 - (1000 * pq.size())) {
                    final GameState min = pq.poll();
                    for (final GameState state : min.moves()) {
                        pq.add(state);
                    }
                }
                GameState solved = pq.poll();
                while (solved.moves != 1) {
                    solved = solved.previous;
                }
                if (solved.action.charAt(0) == 'C') spells.get(solved.id).setUsed(true);
                else if (solved.action.charAt(0) == 'L') tome.remove(solved.id);
                else if (solved.action.charAt(0) == 'R') for (final Spell spell : spells.values()) spell.setUsed(false);
                turn++;
                System.out.println(solved.action + " in " + (System.nanoTime() - start) / 1000000 + " size: " + pq.size());
            }
            // Write an action using System.out.println()
            // To debug: System.err.println("Debug messages...");
            // in the first league: BREW <id> | WAIT; later: BREW <id> | CAST <id> [<times>] | LEARN <id> | REST | WAIT } }
        }
    }

    public static boolean canAfford(final int[] cost, final int[] inv) {
        for (int i = 0; i < 4; i++) {
            if (inv[i] + cost[i] < 0) return false;
        }
        return true;
    }

    /* old heuristic
    public static double deltaCost(Recipe recipe, double[] inv, Spell spell) {
        //distance from achieving the recipe
        double WEIGHT_SPELL = 0.8;
        double WEIGHT_USED = 0.4;
        double WEIGHT_REPEAT = 0.2;
        double WEIGHT_FULL = 0.2;
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
     */
    public static boolean invFull(final int[] inv, final int[] delta) {
        final int[] invCopy = Arrays.copyOf(inv, 4);
        int sum = 0;
        for (int i = 0; i < 4; i++) {
            invCopy[i] += delta[i];
            if (invCopy[i] > 0) sum += invCopy[i];
        }
        return sum >= 10;
    }

    public static int delta(final Recipe recipe, final int[] spell, final int[] inv) {
        final int[] invCopy = Arrays.copyOf(inv, 4);
        int sum = 0;
        for (int i = 0; i < 4; i++) {
            invCopy[i] += recipe.delta[i];
            if (spell != null) invCopy[i] += spell[i];
            if (invCopy[i] < 0) sum += (-1 * invCopy[i]);
        }
        return sum;
    }

    public static int[] spellInv(final int[] spell, final int[] inv) {
        final int[] invCopy = Arrays.copyOf(inv, 4);
        for (int i = 0; i < 4; i++) {
            invCopy[i] += spell[i];
        }
        return invCopy;
    }
}

class GameState implements Comparable<GameState> {
    Recipe recipe;
    HashMap<Integer, Spell> spells;
    HashMap<Integer, TomeSpell> tome;
    int[] inv;
    GameState previous;
    String action;
    int id;
    int moves;
    int delta;

    public GameState(final Recipe recipe, final HashMap<Integer, Spell> spells, final HashMap<Integer, TomeSpell> tome, final int[] inv, final GameState previous, final String action, final int id, final int moves, final int delta) {
        this.recipe = recipe;
        this.spells = spells;
        this.tome = tome;
        this.inv = inv;
        this.previous = previous;
        this.action = action;
        this.id = id;
        this.moves = moves;
        this.delta = delta;
    }

    public Iterable<GameState> moves() {
        final ArrayList<GameState> options = new ArrayList<>();
        for (final Spell spell : spells.values()) {
            if (Player.canAfford(spell.delta, inv)) {
                final HashMap<Integer, Spell> newSpells = new HashMap<>(spells);
                newSpells.put(spell.id, new Spell(spell));
                if (!spell.used) {
                    newSpells.get(spell.id).setUsed(true);
                    if (!spell.repeatable) {
                        final int[] newInv = Player.spellInv(spell.delta, inv);
                        if (!Player.invFull(inv, spell.delta))
                            options.add(new GameState(recipe, newSpells, tome, newInv, this, "CAST " + spell.id, spell.id, moves + 1, Player.delta(recipe, spell.delta, newInv)));
                    } else {
                        int i = 1;
                        while (!Player.invFull(inv, spell.deltaCount(i)) && Player.canAfford(spell.deltaCount(i), inv)) {
                            final int[] newInv = Player.spellInv(spell.deltaCount(i), inv);
                            options.add(new GameState(recipe, newSpells, tome, newInv, this, "CAST " + spell.id + " " + i, spell.id, moves + 1, Player.delta(recipe, spell.deltaCount(i), newInv)));
                            i++;
                        }
                    }
                } else {
                    newSpells.get(spell.id).setUsed(false);
                    options.add(new GameState(recipe, newSpells, tome, Arrays.copyOf(inv, 4), this, "REST", 0, moves + 1, delta));
                }
            }
        }
        for (final TomeSpell tomeSpell : tome.values()) {
            if (Player.canAfford(new int[]{-1 * tomeSpell.cost, 0, 0, 0}, inv)) {
                final HashMap<Integer, TomeSpell> newTome = new HashMap<>(tome);
                final HashMap<Integer, Spell> newSpells = new HashMap<>(spells);
                newTome.remove(tomeSpell.id);
                newSpells.put(tomeSpell.id, tomeSpell);
                final int[] newInv = Player.spellInv(new int[]{-1 * tomeSpell.cost, 0, 0, 0}, inv);
                options.add(new GameState(recipe, newSpells, newTome, newInv, this, "LEARN " + tomeSpell.id, tomeSpell.id, moves + 1, delta));
            }
        }
        return options;
    }

    @Override
    public int compareTo(final GameState that) {
        return Integer.compare(this.moves + this.delta, that.moves + that.delta);
    }
}

class Recipe {
    int id;
    int price;
    int[] delta;

    public Recipe(final String[] input) {
        this.id = Integer.parseInt(input[0]);
        this.price = Integer.parseInt(input[6]);
        this.delta = new int[4];
        for (int i = 2; i < 6; i++) {
            this.delta[i - 2] = Integer.parseInt(input[i]);
        }
    }

    public Recipe(final int id, final int price, final int[] delta) {
        this.id = id;
        this.price = price;
        this.delta = delta;
    }
}

class Spell extends Recipe {
    boolean used;
    boolean repeatable;

    public Spell(final String[] input) {
        super(input);
        this.used = input[9].equals("0");
        this.repeatable = input[10].equals("1");
    }

    public Spell(final Spell spell) {
        super(spell.id, spell.price, spell.delta);
        this.used = spell.used;
        this.repeatable = spell.repeatable;
    }

    public int[] deltaCount(final int times) {
        if (repeatable) {
            final int[] deltaCopy = Arrays.copyOf(delta, 4);
            for (int i = 0; i < 4; i++) {
                deltaCopy[i] *= times;
            }
            return deltaCopy;
        } else return delta;
    }

    public void setUsed(final boolean used) {
        this.used = used;
    }

    @Override
    public boolean equals(final Object o) {
        final Spell that = (Spell) o;
        return this.id == that.id;
    }
}

class TomeSpell extends Spell {
    int cost;

    public TomeSpell(final String[] input) {
        super(input);
        this.cost = Integer.parseInt(input[7]);
    }
}

class RecipeComparator implements Comparator<Recipe> {
    int[] inv;

    public RecipeComparator(final int[] inv) {
        this.inv = inv;
    }

    public static double priceDelta(final int[] inv, final Recipe recipe) {
        double sum = 0;
        final int[] invCopy = Arrays.copyOf(inv, 4);
        for (int i = 0; i < 4; i++) {
            invCopy[i] += recipe.delta[i];
            if (invCopy[i] < 0) sum += (-1 * invCopy[i]);
        }
        return sum;
    }

    public int compare(final Recipe r1, final Recipe r2) {
        final double delta1 = priceDelta(inv, r1);
        final double delta2 = priceDelta(inv, r2);
        return Double.compare(delta1 + r1.price * -0.25, delta2 + r2.price * -0.25);
    }
}