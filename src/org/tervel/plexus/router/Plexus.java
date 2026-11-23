package org.tervel.plexus.router;

import org.tervel.plexus.descriptor.*;
import org.tervel.plexus.router.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Plexus: a symmetry-routed exact classifier that files every shape by what it is, not where it sits.
 * Two tries, one per lattice:
 *
 * <pre>
 * D4 root / D6 root → box → symmetry walk → mass → joins → shape → orientation → pose → corner
 * </pre>
 *
 * <ul>
 *   <li>The <b>box</b> (short × long, or the hex extent) and the <b>lattice</b> come from the bounding box of
 *       the filled cells ({@link Descriptor}).</li>
 *   <li>The <b>symmetry walk</b> asks one question per symmetry, yes or no, in the lattice's fixed order. Every
 *       partial symmetry is therefore a node, and a slice over symmetries is a walk.</li>
 *   <li><b>Mass</b> and <b>joins</b>: filled cells, and adjacent pairs on the shape's own lattice.</li>
 *   <li><b>Shape</b>: the canonical shape, interned to an id, so different shapes never share a path below it.</li>
 *   <li>The <b>trailing</b> levels (orientation, pose, corner) are last, so a read only needs them where labels
 *       disagree across poses or positions.</li>
 * </ul>
 *
 * <p>Training walks the whole path, adding one to every node passed, and keeps the grid at the leaf. Reading
 * walks until the path breaks off; a pure node may then stand in for the missing child if the {@link Extrapolate}
 * setting allows that level (an {@link Verdict#ASSUMED} answer). Slices walk the same tries under constraints and
 * stream every matching grid, trained or assumed, to a callback.
 *
 * <p>All grids share one input frame ({@code inRows × inCols}), row-major. It is how a grid's cells are read
 * into rows and columns, and where slices place the grids they generate.
 */
public final class Plexus {

    // ---- the trie --------------------------------------------------------------------------------

    /** What a node's children split on: a level, plus the walk index for a symmetry. */
    record Coordinate(Level level, int index) {
        static final Coordinate BOX = new Coordinate(Level.BOX, 0);
        static Coordinate symmetry(int i) { return new Coordinate(Level.SYMMETRY, i); }
        static final Coordinate MASS = new Coordinate(Level.MASS, 0);
        static final Coordinate JOINS = new Coordinate(Level.JOINS, 0);
        static final Coordinate SHAPE = new Coordinate(Level.SHAPE, 0);
        static final Coordinate ORIENTATION = new Coordinate(Level.ORIENTATION, 0);
        static final Coordinate POSE = new Coordinate(Level.POSE, 0);
        static final Coordinate POSITION = new Coordinate(Level.POSITION, 0);
    }

    /** One trained grid, kept at its leaf so exact exceptions and subtree rebuilds have the evidence. */
    record Member(int[] grid, int label, CellSet canonical) { }

    /** A grid as a map key: by its contents, without making a string of them. */
    record GridKey(int[] cells) {
        @Override public boolean equals(Object o) { return o instanceof GridKey k && Arrays.equals(cells, k.cells); }
        @Override public int hashCode() { return Arrays.hashCode(cells); }
    }

    static final class Node {
        Coordinate splitOn;                                   // null at a leaf
        final Map<Long, Node> children = new LinkedHashMap<>();
        final long[] counts = new long[2];
        final List<Member> members = new ArrayList<>();      // leaves only: one per distinct grid and label
        final Map<GridKey, long[]> evidence = new HashMap<>(); // leaves only: [negatives, positives] per distinct grid
        final Node parent;
        final Coordinate edge;                                // the parent's splitOn (null at a root)
        final long value;                                     // the edge value
        final Kind kind;                                      // null at a root
        final int depth;

        Node(Coordinate splitOn, Node parent, Coordinate edge, long value, Kind kind) {
            this.splitOn = splitOn; this.parent = parent; this.edge = edge; this.value = value; this.kind = kind;
            this.depth = parent == null ? 0 : parent.depth + 1;
        }

        int answer() { return counts[1] > counts[0] ? 1 : 0; }
    }

    private final int inRows;
    private final int inCols;
    private final Set<Lattice> lattices;
    private final double tolerance;
    private final Node d4 = new Node(Coordinate.BOX, null, null, 0, null);
    private final Node d6 = new Node(Coordinate.BOX, null, null, 0, null);
    /** Each canonical shape the router has trained, with its id: the key of the SHAPE level. */
    private final Map<CellSet, Long> shapeIds = new HashMap<>();
    /** The shapes each box enumerates, by box, symmetry answers and mass range: kept for one slice only. */
    private final Map<String, Enumerated> shapeMemo = new HashMap<>();

    /** A router over {@code inRows × inCols} input grids: the default lattices. */
    public Plexus(int inRows, int inCols) { this(inRows, inCols, Lattice.DEFAULT); }

    /**
     * A router over {@code inRows × inCols} input grids on these lattices. For example
     * {@code new Plexus(8, 8, Lattice.SQUARE_8)} keeps every shape square with 8 neighbours, and
     * {@code new Plexus(8, 8, Lattice.SQUARE_8, Lattice.HEX_6)} also tries hex.
     *
     * @throws IllegalArgumentException if the lattices are not a legal set ({@link Lattice#checked(Set)})
     */
    public Plexus(int inRows, int inCols, Lattice... lattices) { this(inRows, inCols, Lattice.checked(lattices)); }

    /**
     * A router over {@code inRows × inCols} input grids on {@code lattices}. The lattices are fixed for the router's
     * life: they define the key, so every grid it ever describes must be described the same way.
     *
     * @throws IllegalArgumentException if the lattices are not a legal set ({@link Lattice#checked(Set)})
     */
    public Plexus(int inRows, int inCols, Set<Lattice> lattices) { this(inRows, inCols, lattices, 0); }

    /**
     * As {@link #Plexus(int, int, Set)}, with a noise tolerance. A node stands in for an unseen grid only if its
     * minority label is at most {@code tolerance} of the evidence it holds. {@code 0} (the default) is exact: a node
     * stands in only when every example in it has the same label. A positive tolerance lets a node with a few
     * mislabelled examples keep answering for the rest of its branch, at the cost of answering some grids it was
     * never pure for. It must be below one half, so the majority is always the label that stands in.
     *
     * @throws IllegalArgumentException if the tolerance is not in {@code [0, 0.5)}
     */
    public Plexus(int inRows, int inCols, Set<Lattice> lattices, double tolerance) {
        checkFrame(inRows, inCols);
        if (!(tolerance >= 0 && tolerance < 0.5))
            throw new IllegalArgumentException("tolerance " + tolerance + " must be in [0, 0.5)");
        this.inRows = inRows; this.inCols = inCols;
        this.lattices = Lattice.checked(lattices);
        this.tolerance = tolerance;
    }

    /** The noise tolerance this router was built with; see {@link #Plexus(int, int, Set, double)}. */
    public double tolerance() { return tolerance; }

    /** Whether a node is pure enough to stand in: its minority label is within the tolerance of its evidence. */
    private boolean pure(Node n) {
        return Math.min(n.counts[0], n.counts[1]) <= tolerance * (n.counts[0] + n.counts[1]);
    }

    /** The lattices this router was built on. */
    public Set<Lattice> lattices() { return lattices; }

    public int inRows() { return inRows; }
    public int inCols() { return inCols; }

    /** The descriptor this router reads off {@code grid}. */
    public Descriptor describe(int[] grid) { return Descriptor.of(grid, inRows, inCols, lattices); }

    private Node rootOf(Root r) { return r == Root.D6 ? d6 : d4; }

    // ---- training -----------------------------------------------------------------------------------

    /**
     * The largest side a frame may have. Keys pack a row or column count into 4096 places, so a wider frame would
     * alias two different shapes onto one key.
     */
    public static final int MAX_SIDE = 4096;

    static void checkFrame(int rows, int cols) {
        if (rows < 1 || cols < 1 || rows > MAX_SIDE || cols > MAX_SIDE)
            throw new IllegalArgumentException("frame " + rows + "x" + cols + " must have each side in 1.." + MAX_SIDE);
    }

    /** A label is 0 or 1: anything else is a data error, refused rather than stored as 0. */
    static void checkLabel(int label) {
        if (label != 0 && label != 1) throw new IllegalArgumentException("label " + label + " must be 0 or 1");
    }

    /** Train on one grid with a label. Legal at any time. */
    public synchronized void accept(int[] grid, int label) {
        checkLabel(label);
        describe(grid);                                 // refuses a wrong size or a cell value other than 0 or 1
        insert(grid, label);
    }

    /**
     * Remove one training example, undoing what {@link #accept} added: its counts come off every node on its path,
     * and nodes left with no evidence are dropped. A contradiction it was part of is undone with it.
     *
     * @throws IllegalArgumentException if that grid was never trained with that label
     */
    public synchronized void retract(int[] grid, int label) {
        checkLabel(label);
        final var d = describe(grid);
        final var root = rootOf(d.root());
        final var path = new ArrayList<Node>();
        var node = root;
        while (node.splitOn != null) {
            node = node.children.get(value(node.splitOn, d, false));
            if (node == null) throw new IllegalArgumentException("that grid was never trained");
            path.add(node);
        }
        final var key = new GridKey(grid);
        final var own = node.evidence.get(key);
        if (own == null || own[label] == 0) throw new IllegalArgumentException("that grid was never trained with label " + label);
        if (--own[label] == 0) {
            node.members.removeIf(m -> m.label() == label && Arrays.equals(m.grid(), grid));
            if (own[0] + own[1] == 0) node.evidence.remove(key);
        }
        root.counts[label]--;
        for (final var n : path) n.counts[label]--;
        for (var i = path.size() - 1; i >= 0; i--) {                     // drop nodes with no evidence left
            final var n = path.get(i);
            if (n.counts[0] + n.counts[1] > 0) break;
            final var parent = i == 0 ? root : path.get(i - 1);
            parent.children.remove(n.value);
        }
    }

    /** One training example, as {@link #examples} returns it: a copy of the grid and its label. */
    public record Example(int[] grid, int label) { }

    /**
     * Every example the model holds, each repeated as often as it was trained, in no particular order. Rebuilding a
     * model with {@link #fit} on these gives back the same model, so this is how a model is saved.
     */
    public synchronized List<Example> examples() {
        final var out = new ArrayList<Example>();
        collect(d4, out);
        collect(d6, out);
        return out;
    }

    private static void collect(Node n, List<Example> out) {
        if (n.splitOn != null) {
            for (final var child : n.children.values()) collect(child, out);
            return;
        }
        for (final var m : n.members) {
            final var own = n.evidence.get(new GridKey(m.grid()));
            for (long k = 0; k < own[m.label()]; k++) out.add(new Example(m.grid().clone(), m.label()));
        }
    }

    /** Forgets every example: the model is as it was when constructed. */
    public synchronized void clear() {
        for (final var root : List.of(d4, d6)) {
            root.children.clear();
            root.counts[0] = root.counts[1] = 0;
            root.splitOn = Coordinate.BOX;
            root.members.clear();
            root.evidence.clear();
        }
        shapeIds.clear();
        shapeMemo.clear();
    }

    /**
     * A guess for a grid the router cannot answer exactly: the label of the trained grid nearest to it, and how far
     * away that grid is. The distance is the number of cells in one shape and not the other, after the best of the
     * square's eight turns and mirrors, with both shapes moved to their top-left corner. So a copy of a trained shape
     * anywhere in the frame is at distance 0. {@code label} is -1 when the nearest trained grids disagree, and
     * {@code distance} is -1 when nothing is trained. A guess is not an answer: {@link #read} still says UNSEEN for a
     * grid it has not trained.
     */
    public record Guess(int label, int distance, int[] nearest) { }

    /** The nearest trained grid to {@code grid}, as a {@link Guess}. */
    public synchronized Guess guess(int[] grid) {
        describe(grid);                                  // refuses a wrong size or a cell value other than 0 or 1
        final var query = cellsOf(grid, inCols);
        var best = -1;
        var ones = 0;
        var zeros = 0;
        int[] nearest = null;
        for (final var e : examples()) {
            final var d = shapeDistance(query, cellsOf(e.grid(), inCols));
            if (best < 0 || d < best) { best = d; ones = 0; zeros = 0; nearest = e.grid(); }
            if (d == best) { if (e.label() == 1) ones++; else zeros++; }
        }
        if (best < 0) return new Guess(-1, -1, null);
        return new Guess(ones == zeros ? -1 : ones > zeros ? 1 : 0, best, nearest);
    }

    /** The filled cells of a grid as {row, column} pairs, moved so the top-left filled cell is at (0, 0). */
    private static List<int[]> cellsOf(int[] grid, int cols) {
        final var cells = new ArrayList<int[]>();
        for (var i = 0; i < grid.length; i++) if (grid[i] == 1) cells.add(new int[]{i / cols, i % cols});
        return normalized(cells);
    }

    /** {@code cells} moved so that their smallest row and smallest column are both 0. */
    private static List<int[]> normalized(List<int[]> cells) {
        var minR = Integer.MAX_VALUE;
        var minC = Integer.MAX_VALUE;
        for (final var c : cells) { minR = Math.min(minR, c[0]); minC = Math.min(minC, c[1]); }
        final var out = new ArrayList<int[]>();
        for (final var c : cells) out.add(new int[]{c[0] - minR, c[1] - minC});
        return out;
    }

    /** The smallest number of cells in one of {@code a}, {@code b} but not the other, over the square's eight motions of {@code b}. */
    private static int shapeDistance(List<int[]> a, List<int[]> b) {
        final var ka = keys(a);
        var best = Integer.MAX_VALUE;
        for (var k = 0; k < 8; k++) {
            final var moved = new ArrayList<int[]>();
            for (final var c : b) {
                var r = c[0];
                var col = c[1];
                if ((k & 4) != 0) { final var t = r; r = col; col = t; }
                if ((k & 1) != 0) r = -r;
                if ((k & 2) != 0) col = -col;
                moved.add(new int[]{r, col});
            }
            final var kb = keys(normalized(moved));
            var common = 0;
            for (final var x : kb) if (ka.contains(x)) common++;
            best = Math.min(best, ka.size() + kb.size() - 2 * common);
        }
        return best;
    }

    private static Set<Long> keys(List<int[]> cells) {
        final var out = new HashSet<Long>();
        for (final var c : cells) out.add(c[0] * 65536L + c[1]);
        return out;
    }

    /** Train on a labelled dataset in one pass. */
    public synchronized void fit(List<int[]> grids, List<Integer> labels) {
        if (grids.size() != labels.size())
            throw new IllegalArgumentException(grids.size() + " grids but " + labels.size() + " labels");
        for (var i = 0; i < grids.size(); i++) {        // every example checked before any is stored: fit is all or nothing
            checkLabel(labels.get(i));
            describe(grids.get(i));
        }
        for (var i = 0; i < grids.size(); i++) insert(grids.get(i), labels.get(i));
    }

    private void insert(int[] grid, int label) {
        final var d = describe(grid);
        final var root = rootOf(d.root());
        root.counts[label == 1 ? 1 : 0]++;
        descend(root, d, grid, label == 1 ? 1 : 0);
    }

    /** Walk {@code m} down from {@code node} (whose counts already include it), creating nodes as needed. */
    private void descend(Node node, Descriptor d, int[] grid, int label) {
        while (node.splitOn != null) {
            final var v = value(node.splitOn, d, true);
            final var kind = node.kind == null ? d.kind() : node.kind;
            final var from = node;
            node = node.children.computeIfAbsent(v, k -> new Node(next(from.splitOn, kind), from, from.splitOn, k, kind));
            node.counts[label]++;
        }
        // A repeated example adds to the leaf's count for its grid, not another stored copy.
        final var own = node.evidence.computeIfAbsent(new GridKey(grid.clone()), k -> new long[2]);
        if (own[label]++ == 0) node.members.add(new Member(grid.clone(), label, d.canonical()));
    }

    /** The coordinate a node splits on after {@code c}, in the default order for {@code kind}. */
    static Coordinate next(Coordinate c, Kind kind) {
        final var walk = Groups.walkFor(kind).size();
        return switch (c.level()) {
            case BOX -> kind == Kind.EMPTY ? null : walk > 0 ? Coordinate.symmetry(0) : Coordinate.MASS;
            case SYMMETRY -> c.index() + 1 < walk ? Coordinate.symmetry(c.index() + 1) : Coordinate.MASS;
            case MASS -> Coordinate.JOINS;
            case JOINS -> Coordinate.SHAPE;
            case SHAPE -> kind == Kind.SQUARE ? Coordinate.POSE : Coordinate.ORIENTATION;
            case ORIENTATION -> Coordinate.POSE;
            case POSE -> Coordinate.POSITION;
            case POSITION -> null;
        };
    }

    /**
     * The key value of {@code d} at {@code c}. A shape's id is assigned the first time it is trained ({@code intern});
     * a shape never trained has none, -1, so a read stops at the shape level rather than borrowing another shape's path.
     */
    private long value(Coordinate c, Descriptor d, boolean intern) {
        return switch (c.level()) {
            case BOX -> d.kind() == Kind.EMPTY ? 0 : d.boxShort() * 4096L + d.boxLong();
            case SYMMETRY -> d.answers()[c.index()] ? 1 : 0;
            case MASS -> d.mass();
            case JOINS -> d.joins();
            case SHAPE -> intern ? shapeIds.computeIfAbsent(d.canonical(), k -> (long) shapeIds.size())
                    : shapeIds.getOrDefault(d.canonical(), -1L);
            case ORIENTATION -> d.orientation();
            case POSE -> d.pose();
            case POSITION -> d.cornerRow() * 4096L + d.cornerCol();
        };
    }

    /**
     * The mixed nodes that matter, and what resolves each. At a shape node (where the trie splits by shape): one shape
     * split by orientation, pose or corner, or several shapes, each on its own path. At a leaf: the same grid trained
     * with both labels. Other mixed nodes (above a shape node, or between it and the leaves) are not listed.
     */
    public synchronized String diagnose() {
        final var sb = new StringBuilder("mixed nodes and what resolves them\n");
        var found = 0;
        for (final var root : List.of(d4, d6)) {
            final var nodes = new ArrayList<Node>();
            collectAll(root, nodes);
            for (final var n : nodes) {
                if (pure(n) || n.parent == null) continue;
                final String verdict;
                if (n.splitOn != null && n.splitOn.level() == Level.SHAPE) {
                    final var shapes = membersUnder(n).stream().map(Member::canonical).distinct().count();
                    verdict = shapes == 1 ? "one shape: separated by orientation, pose or corner"
                            : "several shapes: separated by their shape";
                } else if (n.splitOn == null) {
                    verdict = "the same grid trained with both labels";
                } else continue;
                found++;
                sb.append("  ").append(context(n)).append('\n').append("    -> ").append(verdict).append('\n');
            }
        }
        if (found == 0) sb.append("  none: every node is pure\n");
        return sb.toString();
    }

    private static void collectAll(Node n, List<Node> out) {
        out.add(n);
        for (final var c : n.children.values()) collectAll(c, out);
    }

    private static List<Member> membersUnder(Node n) {
        final var out = new ArrayList<Member>();
        gather(n, out);
        return out;
    }

    private static void gather(Node n, List<Member> out) {
        out.addAll(n.members);
        for (final var c : n.children.values()) gather(c, out);
    }

    // ---- reading ------------------------------------------------------------------------------------

    /** Read {@code grid} under the default setting ({@link Extrapolate#SYMMETRY}). */
    public synchronized Answer read(int[] grid) { return read(grid, Extrapolate.SYMMETRY); }

    /** Read {@code grid}: follow its path; where it breaks off, a pure node may stand in if the extrapolation setting allows. */
    public synchronized Answer read(int[] grid, Extrapolate extrapolate) {
        final var d = describe(grid);
        var node = rootOf(d.root());
        while (node.splitOn != null) {
            final var child = node.children.get(value(node.splitOn, d, false));
            if (child == null) {
                if (standsIn(node, d, extrapolate)) return new Answer(Verdict.ASSUMED, node.answer(), context(node, d));
                return new Answer(Verdict.UNSEEN, -1, context(node, d));
            }
            node = child;
        }
        if (node.counts[0] + node.counts[1] == 0 || !holdsShape(node, d)) return new Answer(Verdict.UNSEEN, -1, context(node, d));
        final var label = resolve(node, grid);
        if (label < 0) return new Answer(Verdict.UNSEEN, -1, context(node, d));   // contradicted with equal evidence
        return new Answer(label == 1 ? Verdict.POSITIVE : Verdict.NEGATIVE, label, context(node, d));
    }

    /**
     * Whether a stored grid under {@code node} has {@code d}'s shape: the same canonical class, whatever its placement.
     * A leaf, or a stand-in for a placement the shape has not been seen at, answers only for a shape it holds; two
     * shapes that share a box, symmetry, mass and joins otherwise share a leaf and borrow each other's labels.
     */
    private static boolean holdsShape(Node node, Descriptor d) {
        for (final var m : membersUnder(node)) if (m.canonical().equals(d.canonical())) return true;
        return false;
    }

    /**
     * Whether pure {@code node} may answer for {@code d}, whose child there is missing. The setting must allow the
     * level. Under {@link Extrapolate#SYMMETRY} a stand-in for an unseen symmetry class also needs the
     * query's mass and joins to occur together somewhere under the node, so the fold never crosses mass or joins.
     */
    private boolean standsIn(Node node, Descriptor d, Extrapolate extrapolate) {
        if (node.parent == null || !pure(node) || node.counts[0] + node.counts[1] == 0) return false;
        // Support: the node's evidence must be a real share of the evidence of the branch it sits in, its parent.
        if (node.counts[0] + node.counts[1] < MIN_SUPPORT_SHARE * (node.parent.counts[0] + node.parent.counts[1])) return false;
        if (!extrapolate.allows(node.splitOn.level())) return false;
        final var level = node.splitOn.level();
        if ((level == Level.ORIENTATION || level == Level.POSE || level == Level.POSITION) && !holdsShape(node, d)) return false;
        return level != Level.SYMMETRY || extrapolate == Extrapolate.EVERYTHING || invariantsUnder(node, d.mass(), d.joins());
    }

    /**
     * The least share of its branch's training evidence (its parent's) a pure node must hold before it may stand in for a
     * child it never saw. Measured on 4x4 frames: at 1% most held-out placements of trained shapes lost their answer (42%
     * answered); at 0.1% all were answered.
     */
    static final double MIN_SUPPORT_SHARE = 0.001;                   // THRESHOLD: documented in docs/02-trie.md §3.4

    private static boolean invariantsUnder(Node n, int mass, int joins) {
        if (n.edge != null && n.edge.level() == Level.JOINS && n.value == joins
                && n.parent.edge != null && n.parent.edge.level() == Level.MASS && n.parent.value == mass) return true;
        for (final var c : n.children.values()) if (invariantsUnder(c, mass, joins)) return true;
        return false;
    }

    /**
     * The binary score: 1 for POSITIVE or an ASSUMED 1 under the default setting, 0 for NEGATIVE or an ASSUMED 0, and
     * -1 for UNSEEN, where the router has no answer. UNSEEN is not a negative, so it is not reported as 0.
     */
    public synchronized int score(int[] grid) {
        final var a = read(grid);
        if (a.verdict() == Verdict.UNSEEN) return -1;
        return (a.verdict() == Verdict.POSITIVE || (a.verdict() == Verdict.ASSUMED && a.label() == 1)) ? 1 : 0;
    }

    /**
     * At a leaf: a grid seen there answers with its own majority; any other grid with the leaf's. Equal evidence for
     * both labels is a contradiction with no majority, so it abstains: -1.
     */
    private static int resolve(Node leaf, int[] grid) {
        final var own = leaf.evidence.get(new GridKey(grid));
        if (own == null || own[0] + own[1] == 0) return leaf.answer();
        if (own[0] == own[1]) return -1;
        return own[1] > own[0] ? 1 : 0;
    }

    private Context context(Node node) { return context(node, null); }

    /** The context of {@code node}; with {@code d}, also the query's value at the level the node splits on. */
    private Context context(Node node, Descriptor d) {
        final var steps = new ArrayList<Context.Step>();
        var root = node;
        for (var n = node; n.parent != null; n = n.parent) { steps.add(0, step(n)); root = n.parent; }
        if (node.parent == null) root = node;
        final var boundary = node.parent != null && !pure(node.parent);
        return new Context(root == d6 ? Root.D6 : Root.D4, List.copyOf(steps), node.counts[0], node.counts[1],
                node.answer(), node.depth, boundary, node.splitOn == null ? null : node.splitOn.level(),
                d == null || node.splitOn == null ? -1 : value(node.splitOn, d, false));
    }

    private static Context.Step step(Node n) {
        final var c = n.edge;
        final var v = n.value;
        return switch (c.level()) {
            case BOX -> new Context.Step(Level.BOX, "box", v, n.kind == Kind.EMPTY ? "empty"
                    : n.kind == Kind.HEX ? "hex " + (v % 4096) : (v / 4096) + "x" + (v % 4096));
            case SYMMETRY -> new Context.Step(Level.SYMMETRY, Groups.walkFor(n.kind).get(c.index()).name(), v, v == 1 ? "yes" : "no");
            case MASS -> new Context.Step(Level.MASS, "mass", v, Long.toString(v));
            case JOINS -> new Context.Step(Level.JOINS, "joins", v, Long.toString(v));
            case SHAPE -> new Context.Step(Level.SHAPE, "shape", v, "#" + v);
            case ORIENTATION -> new Context.Step(Level.ORIENTATION, n.kind == Kind.HEX ? "shift" : "turned", v,
                    n.kind == Kind.HEX ? (v == 0 ? "right" : "left") : (v == 1 ? "yes" : "no"));
            case POSE -> new Context.Step(Level.POSE, "pose", v, Long.toString(v));
            case POSITION -> new Context.Step(Level.POSITION, "corner", v, "(" + (v / 4096) + "," + (v % 4096) + ")");
        };
    }

    // ---- slices and generation ----------------------------------------------------------------------

    /** Where a grid's path ends: the node it reached, and whether it broke off there (a missing child). */
    private record Stop(Node node, boolean missing) { }

    private Stop stop(int[] grid) { return stopOf(describe(grid)); }

    private Stop stopOf(Descriptor d) {
        var node = rootOf(d.root());
        while (node.splitOn != null) {
            final var child = node.children.get(value(node.splitOn, d, false));
            if (child == null) return new Stop(node, true);
            node = child;
        }
        return new Stop(node, false);
    }

    private record Pending(int[] grid, Context context) { }

    /** Largest number of whole-orbit fills (or subsets) a single node may enumerate. */
    static final long GENERATION_LIMIT = 1L << 21;

    /**
     * Walk the slice's root(s) under its constraints. Trained grids are delivered as they are found
     * (CONFIRMED); grids assumed by a pure node within the setting are generated, then delivered deepest node
     * first and, at equal depth, nodes next to disagreement first (ASSUMED).
     *
     * <p>With {@link Slice.Per#POSE} or {@link Slice.Per#SHAPE} the matching grids are folded into one entry
     * per distinct pose or shape. The entry is a trained grid if the group has one (CONFIRMED), and among
     * those the one nearest the canonical pose and the frame's top-left: not turned, lowest pose code, lowest
     * corner. So a shape trained in its canonical pose is shown in it.
     */
    public synchronized Slice.Result slice(Slice q, Slice.Callback callback) {
        shapeMemo.clear();                                  // the trie may have changed since the last slice
        if (q.per == Slice.Per.PLACEMENT) return placements(q, callback);

        // Folding by pose or by shape: gather every matching grid, then keep one per group.
        record Found(int[] grid, Context context, Slice.Evidence evidence, Descriptor d) { }
        final var found = new ArrayList<Found>();
        final var all = placements(q, (g, c, e) -> found.add(new Found(g, c, e, describe(g))));
        final Comparator<Found> preferred = Comparator
                .comparing((Found f) -> f.evidence() != Slice.Evidence.CONFIRMED)   // a trained grid first
                .thenComparingInt(f -> Math.max(0, f.d().orientation()))
                .thenComparingInt(f -> f.d().pose())
                .thenComparingInt(f -> f.d().cornerRow())
                .thenComparingInt(f -> f.d().cornerCol());
        final var groups = new LinkedHashMap<List<Object>, Found>();
        for (final var f : found) {
            final List<Object> key = q.per == Slice.Per.SHAPE
                    ? List.of(f.d().root(), f.d().canonical())
                    : List.of(f.d().root(), f.d().canonical(), f.d().orientation(), f.d().pose());
            groups.merge(key, f, (a, b) -> preferred.compare(a, b) <= 0 ? a : b);
        }
        var confirmed = 0;
        for (final var f : groups.values()) {
            if (f.evidence() == Slice.Evidence.CONFIRMED) confirmed++;
            callback.accept(f.grid(), f.context(), f.evidence());
        }
        return new Slice.Result(confirmed, groups.size() - confirmed, all.skipped());
    }

    /**
     * Every matching grid, one entry per placement: the walk's grids, then, for a frame of at most
     * {@link #EXHAUSTIVE_CELLS} cells, every other grid a read answers with the query's label and constraints. So on
     * such a frame the slice lists exactly what reading answers; on a larger one the walk's list may be short.
     */
    private Slice.Result placements(Slice q, Slice.Callback callback) {
        final var sent = new HashSet<GridKey>();
        final Slice.Callback out = (g, c, e) -> { sent.add(new GridKey(g.clone())); callback.accept(g, c, e); };
        final var walked = walkPlacements(q, out);
        final var extra = completePlacements(q, out, sent);
        return new Slice.Result(walked.confirmed() + extra.confirmed(), walked.assumed() + extra.assumed(), walked.skipped());
    }

    /** The largest frame, in cells, whose every grid the slice reads: 2^16 reads at most. */
    static final int EXHAUSTIVE_CELLS = 16;

    /** Every grid of the frame that a read answers and the query admits, not already delivered by the walk. */
    private Slice.Result completePlacements(Slice q, Slice.Callback out, Set<GridKey> sent) {
        final var cells = inRows * inCols;
        if (cells > EXHAUSTIVE_CELLS && !q.exhaustive) return new Slice.Result(0, 0, 0);
        if (cells > 30) throw new IllegalArgumentException("an exhaustive slice reads 2^" + cells + " grids: the frame has at most 30 cells");
        final var stored = new HashSet<GridKey>();
        for (final var e : examples()) stored.add(new GridKey(e.grid()));
        final Set<Root> roots = q.root == Root.BOTH ? Set.of(Root.D4, Root.D6) : Set.of(q.root);
        final var g = new int[cells];
        var confirmed = 0;
        var assumed = 0;
        for (long mask = 0; mask < (1L << cells); mask++) {
            for (var i = 0; i < cells; i++) g[i] = (int) ((mask >>> i) & 1);
            final var key = new GridKey(g);
            if (sent.contains(key)) continue;
            final var d = describe(g);
            if (!roots.contains(d.root()) || !q.admits(d)) continue;
            final var a = read(g, q.extrapolate);
            if (a.verdict() == Verdict.UNSEEN || (q.label != null && a.label() != q.label)) continue;
            final var evidence = stored.contains(key) ? Slice.Evidence.CONFIRMED : Slice.Evidence.ASSUMED;
            out.accept(g.clone(), a.context(), evidence);
            if (evidence == Slice.Evidence.CONFIRMED) confirmed++; else assumed++;
        }
        return new Slice.Result(confirmed, assumed, 0);
    }

    /** The walk's grids, one entry per placement. */
    private Slice.Result walkPlacements(Slice q, Slice.Callback callback) {
        final var roots = q.root == Root.BOTH ? List.of(d4, d6) : List.of(rootOf(q.root));
        final var seen = new HashSet<String>();
        final var pending = new ArrayList<Pending>();
        final var tally = new int[3];                          // confirmed, assumed, skipped
        for (final var root : roots) walk(root, q, callback, seen, pending, tally);
        pending.sort(Comparator.comparingInt((Pending p) -> -p.context().depth())
                .thenComparing(p -> !p.context().boundary()));
        for (final var p : pending) callback.accept(p.grid(), p.context(), Slice.Evidence.ASSUMED);
        return new Slice.Result(tally[0], pending.size(), tally[2]);
    }

    private void walk(Node node, Slice q, Slice.Callback cb, Set<String> seen, List<Pending> pending, int[] tally) {
        if (node.splitOn == null) {
            final var ctx = context(node);
            final var done = new HashSet<String>();
            for (final var m : node.members) {
                final var key = Arrays.toString(m.grid());
                if (!done.add(key)) continue;
                seen.add(key);                                    // a stored grid is never generated again, whatever its label
                final var label = resolve(node, m.grid());
                if (label < 0 || (q.label != null && q.label != label)) continue;   // a contradiction gives no verdict
                tally[0]++;
                cb.accept(m.grid().clone(), ctx, Slice.Evidence.CONFIRMED);
            }
            // Read answers a leaf's label for every grid whose full path ends there, trained or not.
            if (node.parent != null && (q.label == null || q.label == node.answer())) generate(node, q, seen, pending, tally);
            return;
        }
        for (final var e : node.children.entrySet())
            if (passes(node, e.getKey(), e.getValue(), q)) walk(e.getValue(), q, cb, seen, pending, tally);
        if (node.parent != null && pure(node) && node.counts[0] + node.counts[1] > 0
                && q.extrapolate.allows(node.splitOn.level()) && (q.label == null || q.label == node.answer()))
            generate(node, q, seen, pending, tally);
    }

    private boolean passes(Node node, long v, Node child, Slice q) {
        return switch (node.splitOn.level()) {
            case BOX -> {
                if (q.boxShort != null && (v / 4096 != q.boxShort || v % 4096 != q.boxLong)) yield false;
                if (child.kind == Kind.EMPTY) yield q.required.isEmpty() && q.massMin == 0;
                final var names = Groups.walkFor(child.kind).stream().map(CellSet.Map::name).toList();
                yield names.containsAll(q.required);
            }
            case SYMMETRY -> {
                final var name = Groups.walkFor(node.kind).get(node.splitOn.index()).name();
                if (q.required.contains(name)) yield v == 1;
                yield !q.exact || v == 0;
            }
            case SHAPE -> true;
            case MASS -> v >= q.massMin && v <= q.massMax;
            case JOINS -> v >= q.joinsMin && v <= q.joinsMax;
            case ORIENTATION -> q.orientation == null || v == q.orientation;
            case POSE -> q.pose == null || v == q.pose;
            case POSITION -> q.cornerRow == null || v == q.cornerRow * 4096L + q.cornerCol;
        };
    }

    /** Generate the grids a pure node would answer for but never saw, keeping those the slice admits. */
    private void generate(Node node, Slice q, Set<String> seen, List<Pending> pending, int[] tally) {
        // Every shape the node's box holds, at every level below it: read answers for any shape that reaches the node,
        // so generation must propose any of them, not only the shapes already stored under it.
        final var crops = shapes(node, q, tally);
        final var leaf = node.splitOn == null;
        final var ctx = context(node);
        for (final var crop : crops)
            for (final var t : Groups.D4) {
                final var posed = crop.map(t);
                if (posed.extentA > inRows || posed.extentB > inCols) continue;
                // Every placement of a posed shape has the same descriptor except its corner, which only the last level reads:
                // describe the shape once, and walk each placement's corner through the trie.
                final var d = describe(place(posed, 0, 0));
                if (!q.admitsShape(d)) continue;
                if ((!leaf && !standsIn(node, d, q.extrapolate)) || (leaf && !holdsShape(node, d))) continue;
                for (var r = 0; r + posed.extentA <= inRows; r++)
                    for (var c = 0; c + posed.extentB <= inCols; c++) {
                        if (!q.admitsCorner(r, c)) continue;
                        final var s = stopAt(d, r, c);
                        if (s.node() != node || s.missing() == leaf) continue;   // a pure node: broken off; a leaf: reached
                        final var g = place(posed, r, c);
                        final var key = Arrays.toString(g);
                        if (seen.contains(key)) continue;
                        seen.add(key);
                        pending.add(new Pending(g, ctx));
                    }
            }
    }

    /** Where the path of {@code d}, with its corner moved to {@code (row, col)}, stops: the same walk as {@link #stopOf}. */
    private Stop stopAt(Descriptor d, int row, int col) {
        var node = rootOf(d.root());
        while (node.splitOn != null) {
            final long v = node.splitOn.level() == Level.POSITION ? row * 4096L + col : value(node.splitOn, d, false);
            final var child = node.children.get(v);
            if (child == null) return new Stop(node, true);
            node = child;
        }
        return new Stop(node, false);
    }

    /**
     * Shapes in this node's box with at least the symmetries already answered yes on the path. On the square
     * lattice they are built from whole orbits of those symmetries (one choice per orbit); on the hex root the
     * candidates are the subsets of the square input box, which the descriptor then files.
     */
    private Set<CellSet> shapes(Node node, Slice q, int[] tally) {
        Node box = node;
        while (box.edge == null || box.edge.level() != Level.BOX) box = box.parent;
        final var kind = box.kind;
        if (kind == Kind.EMPTY) return Set.of();
        final int h = (int) (box.value / 4096), w = (int) (box.value % 4096);
        final var yes = new ArrayList<Integer>();
        for (var n = node; n != box.parent; n = n.parent)
            if (n.edge != null && n.edge.level() == Level.SYMMETRY && n.value == 1) yes.add(n.edge.index());

        if (kind == Kind.HEX) return hexCandidates(h, q, tally);

        // Every node under one box with the same yes-answers enumerates the same shapes: enumerate once per slice.
        // A node below a mass or joins split holds only shapes of that mass and joins: any other never reaches it.
        final var mass = pathValue(node, Level.MASS);
        final var joins = pathValue(node, Level.JOINS);
        // A pure node splitting on a symmetry, with one answer already trained, is missing only the other answer: a shape
        // that stops there must carry that answer.
        final var symIndex = node.splitOn != null && node.splitOn.level() == Level.SYMMETRY ? node.splitOn.index() : -1;
        final var symAnswer = symIndex < 0 ? -1 : missingAnswer(node);
        final var key = kind + ":" + box.value + ":" + yes + ":" + mass + ":" + joins + ":" + symIndex + ":" + symAnswer
                + ":" + q.massMin + ":" + q.massMax;
        final var memo = shapeMemo.computeIfAbsent(key,
                k -> enumerateShapes(kind, h, w, yes, mass, joins, symIndex, symAnswer, Lattice.square(lattices), q));
        if (memo.tooLarge()) tally[2]++;
        return memo.shapes();
    }

    /** The shapes of an {@code h×w} box with the yes-symmetries, one per D4 class; {@code tooLarge} if not enumerable. */
    private record Enumerated(Set<CellSet> shapes, boolean tooLarge) { }

    /** The value the path to {@code node} fixes at {@code level} (the node's own edge included), or -1 if none does. */
    private static long pathValue(Node node, Level level) {
        for (var n = node; n != null; n = n.parent)
            if (n.edge != null && n.edge.level() == level) return n.value;
        return -1;
    }

    /** Adjacent pairs of a filled {@code h×w} box on the forward offsets: the descriptor's joins for a square shape. */
    private static int adjacentPairs(int[] fill, int h, int w, int[][] forward) {
        var e = 0;
        for (var r = 0; r < h; r++)
            for (var c = 0; c < w; c++) {
                if (fill[r * w + c] == 0) continue;
                for (final var d : forward) {
                    final var nr = r + d[0];
                    final var nc = c + d[1];
                    if (nr >= 0 && nc >= 0 && nr < h && nc < w && fill[nr * w + nc] == 1) e++;
                }
            }
        return e;
    }

    /** The answer (0 or 1) a symmetry node's missing child needs: the one value its children lack, or -1 if both or neither. */
    private static int missingAnswer(Node node) {
        final var have = new HashSet<Long>(node.children.keySet());
        final var missing = new ArrayList<Integer>();
        for (var v = 0; v <= 1; v++) if (!have.contains((long) v)) missing.add(v);
        return missing.size() == 1 ? missing.get(0) : -1;
    }

    /** Whether a filled box is unchanged by a cell permutation. */
    private static boolean invariant(int[] fill, int[] perm) {
        for (var i = 0; i < fill.length; i++) if (fill[perm[i]] != fill[i]) return false;
        return true;
    }

    private static Enumerated enumerateShapes(Kind kind, int h, int w, List<Integer> yes, long fixedMass, long fixedJoins,
                                              int symIndex, int symAnswer, Lattice square, Slice q) {
        final var symPerm = symIndex >= 0 ? boxPermutation(kind, symIndex, h, w) : null;
        final var maps = new ArrayList<int[]>();                      // each: a permutation of the h×w cells
        for (final var i : yes) maps.add(boxPermutation(kind, i, h, w));
        final var orbit = orbits(h * w, maps);
        final var groups = Arrays.stream(orbit).distinct().count();
        if ((1L << Math.min(groups, 62)) > GENERATION_LIMIT) return new Enumerated(Set.of(), true);
        final var members = new HashMap<Integer, List<Integer>>();
        for (var i = 0; i < orbit.length; i++) members.computeIfAbsent(orbit[i], k -> new ArrayList<>()).add(i);
        final var list = new ArrayList<>(members.values());
        final var out = new LinkedHashSet<CellSet>();
        for (long mask = 1; mask < (1L << list.size()); mask++) {
            final var fill = new int[h * w];
            var count = 0;
            for (var k = 0; k < list.size(); k++)
                if ((mask & (1L << k)) != 0) for (final var cell : list.get(k)) { fill[cell] = 1; count++; }
            if (count < q.massMin || count > q.massMax || (fixedMass >= 0 && count != fixedMass) || !tight(fill, h, w)) continue;
            if (fixedJoins >= 0 && adjacentPairs(fill, h, w, square.forward()) != fixedJoins) continue;
            if (symPerm != null && symAnswer >= 0 && invariant(fill, symPerm) != (symAnswer == 1)) continue;
            out.add(canonicalCrop(cropOfFill(fill, w)));
        }
        return new Enumerated(out, false);
    }

    private Set<CellSet> hexCandidates(int s, Slice q, int[] tally) {
        final var cells = s * s;
        if (cells >= 62 || (1L << cells) > GENERATION_LIMIT) { tally[2]++; return Set.of(); }
        final var out = new LinkedHashSet<CellSet>();
        for (long mask = 1; mask < (1L << cells); mask++) {
            final var mass = Long.bitCount(mask);
            if (mass < q.massMin || mass > q.massMax) continue;
            final var fill = new int[cells];
            for (var i = 0; i < cells; i++) if ((mask & (1L << i)) != 0) fill[i] = 1;
            if (!tight(fill, s, s)) continue;
            out.add(cropOfFill(fill, s));
        }
        return out;
    }

    /**
     * The least image of a crop under the square's eight motions: one representative per class. Generation applies all
     * eight motions to each representative, so the output is the same as for every mask, at an eighth of the work.
     */
    private static CellSet canonicalCrop(CellSet crop) {
        var best = crop;
        for (final var t : Groups.D4) {
            final var m = crop.map(t);
            if (m.compareTo(best) < 0) best = m;
        }
        return best;
    }

    private static CellSet cropOfFill(int[] fill, int w) {
        final var as = new ArrayList<Integer>();
        final var bs = new ArrayList<Integer>();
        for (var i = 0; i < fill.length; i++) if (fill[i] == 1) { as.add(i / w); bs.add(i % w); }
        return CellSet.of(as.stream().mapToInt(Integer::intValue).toArray(), bs.stream().mapToInt(Integer::intValue).toArray());
    }

    /** The permutation of an {@code h×w} box's cells (row-major) that walk symmetry {@code i} performs. */
    private static int[] boxPermutation(Kind kind, int i, int h, int w) {
        final var p = new int[h * w];
        for (var a = 0; a < h; a++)
            for (var b = 0; b < w; b++) {
                final int[] to = switch (kind) {
                    case RECT -> switch (i) {
                        case 0 -> new int[]{h - 1 - a, w - 1 - b};      // rot180
                        case 1 -> new int[]{a, w - 1 - b};              // short-axis mirror
                        default -> new int[]{h - 1 - a, b};             // long-axis mirror
                    };
                    default -> switch (i) {                             // square box, s = h = w
                        case 0 -> new int[]{b, h - 1 - a};              // rot90
                        case 1 -> new int[]{h - 1 - a, h - 1 - b};      // rot180
                        case 2 -> new int[]{h - 1 - a, b};              // mirrorH
                        case 3 -> new int[]{a, h - 1 - b};              // mirrorV
                        case 4 -> new int[]{b, a};                      // diagMain
                        default -> new int[]{h - 1 - b, h - 1 - a};     // diagAnti
                    };
                };
                p[a * w + b] = to[0] * w + to[1];
            }
        return p;
    }

    /** Orbit label per cell under the group generated by {@code maps} (union-find). */
    private static int[] orbits(int n, List<int[]> maps) {
        final var parent = new int[n];
        for (var i = 0; i < n; i++) parent[i] = i;
        for (final var m : maps)
            for (var i = 0; i < n; i++) {
                final int a = find(parent, i), b = find(parent, m[i]);
                if (a != b) parent[a] = b;
            }
        final var out = new int[n];
        for (var i = 0; i < n; i++) out[i] = find(parent, i);
        return out;
    }

    private static int find(int[] p, int i) { while (p[i] != i) { p[i] = p[p[i]]; i = p[i]; } return i; }

    private static boolean tight(int[] fill, int h, int w) {
        boolean top = false, bottom = false, left = false, right = false;
        for (var a = 0; a < h; a++)
            for (var b = 0; b < w; b++)
                if (fill[a * w + b] == 1) {
                    if (a == 0) top = true;
                    if (a == h - 1) bottom = true;
                    if (b == 0) left = true;
                    if (b == w - 1) right = true;
                }
        return top && bottom && left && right;
    }

    private int[] place(CellSet s, int row, int col) {
        final var g = new int[inRows * inCols];
        for (var i = 0; i < s.size(); i++) g[(row + s.a(i)) * inCols + col + s.b(i)] = 1;
        return g;
    }

    private CellSet cropOf(int[] grid) {
        final var as = new ArrayList<Integer>();
        final var bs = new ArrayList<Integer>();
        for (var i = 0; i < grid.length; i++) if (grid[i] == 1) { as.add(i / inCols); bs.add(i % inCols); }
        return CellSet.of(as.stream().mapToInt(Integer::intValue).toArray(), bs.stream().mapToInt(Integer::intValue).toArray());
    }

    // ---- inspection ---------------------------------------------------------------------------------

    /** Both tries drawn as indented paths, each node with its counts and leaves marked. */
    public synchronized String render() {
        final var sb = new StringBuilder();
        for (final var root : List.of(d4, d6)) {
            sb.append(root == d4 ? "D4 root" : "D6 root")
              .append(String.format("  [neg %d, pos %d]%n", root.counts[0], root.counts[1]));
            for (final var c : root.children.values()) draw(c, "  ", sb);
        }
        return sb.toString();
    }

    private static void draw(Node n, String indent, StringBuilder sb) {
        sb.append(indent).append(step(n)).append(String.format("  [neg %d, pos %d]%s%n",
                n.counts[0], n.counts[1], n.splitOn == null ? "  leaf" : ""));
        for (final var c : n.children.values()) draw(c, indent + "  ", sb);
    }

    /** Nodes in both tries (roots included). */
    public int size() { return count(d4) + count(d6); }

    private static int count(Node n) {
        var k = 1;
        for (final var c : n.children.values()) k += count(c);
        return k;
    }

}
