# Interpretation

*How to read what the code computes, and what the computation is taken to mean. Sections headed **Code** describe existing code. Sections headed **Interpretation** are readings: they say what the code's operations correspond to, and the code they point toward is named in the caveat at the end.*

---

## Code: shape and placement

A grid holds one shape in a frame. `Descriptor` separates two parts of it:

- the **invariant**, what the shape is: its bounding box, its symmetry answers, its mass (filled cells), its joins (touching pairs) and its canonical pose;
- the **placement**, where it sits: the orientation it was read in, the pose that reaches the canonical form, and the corner it occupies in the frame.

The invariant is unchanged by turning, mirroring or moving the shape. The placement is what changes. The trie keys on the invariant first and records the placement in its last levels (`Level.ORIENTATION`, `Level.POSE`, `Level.POSITION`).

## Code: slices

A slice (`Slice`) fixes some of the answers the descriptor gives, and reads the grids that agree with them. What the slice holds fixed is the constraint you set: a box, a symmetry, a mass range, a joins range, a pose, a corner. What varies is the rest of the grid. `Slice.Per` decides whether each matching grid is listed, or folded into one entry per pose or per shape.

The default descriptor does not record placement in its key, so translation is folded away. Placement has its own level, so a slice can fix it with `corner(row, col)`.

## Code: fibers and counting

Each key collects the grids that share it. `Plexus` has no function that returns a key's fiber size. A slice lists the grids of a fiber that a read answers, and `Slice.Result` counts them (`confirmed`, `assumed`, `skipped`). Counting the entries of a slice is therefore the way to count the grids behind one set of constraints. Frames above 16 cells are not checked against reads (see [Slices](04-slices.md)).

## Code: orbits and stabilizers

`Descriptor.stabilizerSize()` returns how many of a shape's motions leave it unchanged: of the square's eight for a square box, of the rectangle's four for a rectangle, and of the hex lattice's twelve for a hex shape. `Descriptor.orbitSize()` divides the square's or the hex lattice's motion count by that stabilizer size, giving the number of distinct orientations the shape has on its own lattice. For a hex shape this is a count on the hex lattice, so it need not equal the number of distinct grids the shape takes in the square frame it was read from. For a rectangle the code divides the square's eight motions by the stabilizer, so a rectangle's orbit is counted against the square group.

## Code: sum over the group's motions

Each motion of the lattice's group is a possible transform of the shape. A motion the shape keeps is one under which its label does not change; a motion it does not keep is one that changes it. The motions a shape keeps are its stabilizer, and `Descriptor.symmetries()` returns their names. The walk asks each motion in a fixed order, one yes or no per motion; a motion answered `no` is one the shape does not keep.

`Slice.atLeast(...)` and `Slice.exactly(...)` select the grids whose stabilizer contains, or equals, the named motions. So a query over the stabilizer is a sum over the shapes whose allowed motions match. The moves are spatial transforms of the frame, so these are symmetry paths, not the time paths an action integral would sum.

## Code: symmetry breaking

The square's eight symmetries (D4, `Groups.D4`) include the transpose, so its two axes are interchangeable. A rectangle has four (D2, `Groups.D2`), because its axes differ in length and cannot be swapped. The rectangle's walk asks about the short-axis and long-axis mirrors separately. On the square, a turn by 90° turns `mirrorH` into `mirrorV`, so the walk asks both, and a mirror is spelled once, as `mirrorH`, in the canonical pose.

So breaking a symmetry makes properties separately nameable. A rectangle's walk asks its two mirrors as separate questions; a square's walk asks its mirrors as conjugate questions. A hex shape has the six turns and six mirrors of `Groups.D6`; the descriptor files a shape under the hex root only when the caller declares `Lattice.HEX_6`.

## Code: stand-ins and invariance

A read can answer for a grid it has not trained by reaching a pure node: a node whose evidence all has one label, or, with a tolerance, a node whose minority is within the tolerance (`Plexus(…, tolerance)`). The extrapolation setting (`Extrapolate`) says how far up the trie that stand-in may come from. `PLACEMENT` allows the placements of a trained shape. `SYMMETRY` also allows other symmetry classes with the same mass and joins. `EVERYTHING` allows any mass and joins. `NONE` allows no stand-in.

The descriptor is unchanged by the frame's own motions. The tests check this exhaustively on small frames and by sampling on larger ones (`ShapeIdentityTest`).

## Code: identity and twins

Two grids have the same key when they are the same shape up to the group, in the same frame. For trained shapes the key is exact: every trained grid reads back its own label. Two different shapes can share every invariant answer. The router then keeps both under one key, and it cannot separate them without a finer level. Those are the twins. `Plexus.diagnose()` lists the junctions where a shape and another shape meet without a separating level.

A grid with no filled cells is kind `EMPTY`. A grid the router has no answer for reads `UNSEEN`, and `score()` returns −1 for it.

## Code: the read is finite

A read walks the trie once, from the root to a leaf or a stand-in, and then stops. Training inserts a grid by the same walk. Nothing is updated when a read happens, and nothing in the router moves a grid to another grid. The router reads a grid and does not evolve it.

## Code: what is not modelled

The code has no time, no dynamics and no write-back. It has no metric: distance between shapes is not part of the key, and the guess method's distance is a count of cells that differ after the best of the square's eight motions (`Plexus.guess`). Placement is recorded exactly, so a rule about where a shape sits does not carry to positions it has not been trained at.

---

## Interpretation: frames as physics

This is a **coherence argument**, not a claim to have built physics. It asks whether the machine is a sound way to analyse the world, not only a classifier of one dataset. The argument runs by construction: nothing physical is put into the machine. It is a structural router: symmetry, a chain of invariants, and an exact residual. It has no equations of motion, no action, no Planck constant, no metric. Yet when a stream of grids is read as **frames of an object over time**, the scaffolding of physical law appears without being forced: conserved charges, entropy as the log of a fiber, least action as the intersection of slices, the sum over paths, the classical–quantum fold, and the renormalization flow.

A framework earns the name of a coherent ontology when **the side-effects we already expect from how we observe the world re-appear as consequences of its own structure**: when the world is recognised in it without having been inserted by hand. That recurrence is the argument. The same slice-and-find-the-constant logic that routes a shape under D4 is the logic by which conservation, causality and scale are read out of nature.

### Reading map

The examples span a small basis of about six axes. The physics examples cluster on the first. Read down a row for the coherence argument, or one example per row for the fastest collapse.

| # | Axis | What it fixes | Readings below |
|---|---|---|---|
| 1 | **Slice / conservation** | fix a symmetry's coordinates; what stays constant is the conserved quantity | Correspondence · Conservation · Time as cursor · Law as compression · Least action · Sum over paths · Classical↔quantum · Drive and gate · Relativity · Light cone |
| 2 | **Fiber / entropy** | the un-fixed remainder, `log\|fiber\|` | Entropy · Playing a position |
| 3 | **Volume = determinant** | the size of the collapse, as a determinant | The thread is the determinant |
| 4 | **Decision without the volume** | act under a utility or stopping rule | Utility and strategy · Playing a position |
| 5 | **Symmetry breaking** | decidability lives in the break | Symmetry breaking: the rectangle |
| 6 | **Change of scale** | the fold across scales | Change of scale |

Row 1 is sampled ten ways on purpose: the recurrence across physics is the argument. Rows 3, 5 and 6 are sampled once each, rows 2 and 4 twice.

### Correspondence

| Plexus | physics |
|---|---|
| invariant part of the descriptor (mass, connectivity, symmetry) | the **macrostate**, the conserved quantities |
| cropped pose (translation, rotation) | the configuration coordinates where motion lives |
| a slice (fix the coordinates naming a symmetry) | a conservation law; the slice cut is the law (Noether) |
| constant along the slice | a conserved quantity (constant of motion) |
| the law: a state-to-next-state rule independent of when | time-translation invariance, which is energy conservation |
| the descriptor fiber, all grids with one macrostate | the microstates; `log\|fiber\|` is the entropy |
| the label (`1`/`0`) read at each step | the alive / halt bit: survival under the dynamics |
| the irreducible symmetry twins | the death points no symmetry can argue away |
| the context trie | the Turing tape quotiented by the symmetry group |

### Interpretation: conservation is a constant along a slice

A conserved quantity is a constant of motion. Cut a slice, walk the frames, and whatever stays the same is conserved. Cut along translation and you get momentum; along rotation, angular momentum; along time with a fixed rule, energy; along the invariant, mass or identity.

"The same" lives at two orders. A scalar charge (mass, energy) is flat in value. A vector charge (momentum) is flat in rate: the pose advances at a constant step. A bend in the flat quantity localizes a force. The default descriptor is translation-invariant, so it folds momentum away; to read momentum, keep the pose and watch the placement advance uniformly.

### Interpretation: time is the cursor, not a category

A law of physics is the requirement that the state-to-next-state rule not depend on when, which is time-translation invariance and so energy conservation. Time is the playback cursor the rule is iterated along, not an axis the router sorts on. Make time a category and you get a flipbook, not laws.

### Interpretation: the law is a compression

A lawful trajectory needs only an initial frame and a rule; the rest regenerate by iteration. The descriptor does spatial compression: cropping the position folds the orbit to one code. The law does temporal compression: one rule instead of every frame. Like a video codec, the conserved quantities are the compressible skeleton, stored once. Entropy is the incompressible residual. A force is a keyframe: the surprise the predictor could not fold.

A lawful universe is a compressible one, and finding the laws is finding the best compression (Kolmogorov, minimum description length). The trie makes this operational: the depth at which a walk resolves a label is the description length of that label's dependence. Laws stop shallow; facts walk to the grid.

### Interpretation: entropy is the log of the fiber

The descriptor is the macrostate. Its fiber is the set of microstates sharing it, so entropy is `log|fiber|`, and the count of possibilities a slice lists is that number. Conservation enables entropy rather than opposing it. A conserving, volume-preserving flow cannot shrink the cloud of states, so it can only spread until it fills the surface the invariants allow. Maximum entropy is the largest fiber.

This gives the arrow. Conservation draws a reversible rail; entropy sets the direction along it, toward larger fibers, simply because there are more of them. Conservation says where you can go; entropy says which way you almost certainly will.

### Interpretation: least action is threading the intersection

Each conserved quantity confines the trajectory to its slice. Conserve several and the trajectory is pinned to their intersection: a thin residual channel, and time is motion along that channel. Free motion conserves the most, so it has the full intersection, a straight line: the geodesic. A force looks like broken conservation, but for a closed system (object plus field) everything is still conserved; the charge only flows between subsystems. The precise selector is to extremize the action; "most conserved" is the intuition for the free and closed cases.

### Interpretation: the thread is the determinant

The fiber a grid is pinned to once its conserved coordinates are fixed is the thread, and `log|fiber|` is its entropy. The slogan can be made an executable identity: the volume of the thread is a determinant. Several volumes could be computed at each node and checked against each other:

- the **combinatorial** volume, how many grids of the frame route to the node;
- the **rigid orbit** of a representative member, in the bounding-box frame, translation quotiented out;
- the **orbit-stabilizer index**, `|G|/|Stab|`, the group-theoretic determinant of the action;
- the **Gram determinant of the descriptor's Jacobian**, the discrete co-volume of the coordinates, which is the change-of-variables reading: the factor relating a count in the ambient frame to a measure along the thread;
- the **Matrix-Tree determinant** of the cell graph, the number of spanning trees, which is the graph-theoretic volume and the rigorous form of the path, tree and loop dial.

The identity to check is orbit-stabilizer: the rigid orbit equals `|G|/|Stab|`. The full-frame fiber is larger by the translational multiplicity, how many places the box fits in the grid. That is the residual entropy the rigid group cannot see. Agreement would certify a context that is one tight orbit; disagreement would flag a context inflated by translation or by a mix of shapes. The divergence is the handle on where, and by how much, structure under-counts the fiber. The code computes the orbit and stabilizer sizes (see *Code: orbits and stabilizers*); the other volumes are the reading.

### Interpretation: sum over paths

Least action picks one extremal trajectory. The path integral keeps all of them and sums. The machine has the discrete version: sweep the group, and each motion is a possible path. The motions under which the input keeps its label are the allowed paths; the rest are forbidden. The allowed paths are the input's stabilizer, so the stabilizer is the bundle of histories that keep the input, and summing over paths is summing over that orbit. A large stabilizer means many allowed paths, a long-lived history; the first forbidden motion names the symmetry the input lacks.

The honest seam: these are spatial transforms, so they are symmetry paths, not the time paths an action integral sums. The time drive is not in the code.

### Interpretation: utility and strategy, deciding without the volume

Summing over paths keeps every history, and the fiber counts them. But often a decision must be made without computing the volume, and that is a different, decision-theoretic layer.

The seam is already present. Where both labels reach one leaf, the leaf's majority is an implicit choice: it picks the label minimising error, the argmax under a hidden 0/1 utility. Making that valuation explicit gives a reusable utility: it answers how good an outcome is, where an invariant answers what coordinate it is.

On top of a utility sits optimal stopping, the secretary or Sultan's dowry problem. Outcomes arrive one at a time, scored by the utility, and each must be committed to or discarded on the spot, with no recall. You carry only a running benchmark and your position. The classic solution is the `1/e` rule: reject the first `⌊n/e⌋`, then take the first that beats them. It lands on the global best with probability about 0.368. It is deliberately volume-blind.

The rule factors into a harness and a strategy, the explore-versus-commit dial. Safe (greedy, risk-averse) takes the first outcome that beats the benchmark; neutral is the `1/e` rule, the fixed point of the two regrets; risky waits for the best. Neutral maximises the chance of the best outcome; both poles fall to `1/n`. Scaling riskiness moves the strategy toward the pole it leans toward.

Two seams. First, this is decision theory against indifferent chance; its multi-agent sibling is game theory, where a second chooser competes for the same stream and the threshold becomes a strategy that must best-respond. Second, the `1/e` rule is blind because the abstract problem cannot see the remaining volume. A Plexus can see it, so it could run the volume-aware rule: a declining threshold read off how much fiber remains. The residual where even that runs out, two outcomes tied on every admissible measurement, is the symmetry twin: a choice no rule can make, left to the stored evidence at a leaf.

### Interpretation: playing a position, the collapse accounting

The decision layer chooses when to commit. This reading is about what one move costs, learned from playing. Four laws, each an axis worn in a contest:

- **Felt uncertainty is an entropy meter, not a verdict meter.** Mid-contest dread measures how many ways the position could still go, not which way it goes. A pure context, where the label is effectively fixed by the matchup, feels calm or hopeless and is. A collision context, where both labels are live, feels like losing even while winning. The feeling is calibrated for the volume of the thread, never its direction. In pure contexts the structure decides; in collisions the structure is exhausted, and the leaf's majority decides.

- **Outcomes key on coverage, not drama.** Attention binds to the opponent's most salient threat, but the label is set by the coverage table: which of your answers touch which of their threats. The one-question audit is whether their big thing can touch my answers to it. Salience is a property of the threat; coverage is a property of the pair, and only pairs route.

- **A move's measure is its net collapse.** Playing a piece collapses three things at once. The resource: spending the conserved charge breaks the affordability symmetry, and the bank is how undecided you are allowed to remain. The availability: the piece is out of rotation until it returns. The information: the opponent's map updates, so a play is also a publication. Net collapse is what they lost minus what you lost. A good move is one whose net collapse is positive; a strong piece spent on a weak target collapses you for nothing.

- **A wrong prior mis-prunes.** The expensive failure is a trusted invariant outside its domain: a usually valid rule silently cuts the correct branch before evaluation. The search then feels complete while excluding the answer. The fix is prior repair, not depth. This is the player-level form of the invariance contract: an invariant applied where it does not hold shatters the orbit it usually protects.

The open-set discipline behind all four is closed under composition: the moves genuinely available are the ones no prior breaks, and two open moves whose combination breaks something were never both open. Unseen moves are unknown, not free.

### Interpretation: symmetry breaking, and sexual dimorphism

The square's symmetry makes its two axes interchangeable. Break that, make the frame a rectangle, and the transpose dies. The axes become incommensurable, two specialised dimensions. A tall box is then depth-first, a wide box breadth-first, and choosing between them is choosing which dimension gets the deep search. The aspect ratio is how lopsided that commitment is: square is agnostic, a thin rectangle means one axis is nearly everything.

Read biologically, this is sexual dimorphism. Isogamy, gametes all one size, is the square: the two roles are interchangeable. Anisogamy, two gamete sizes under disruptive selection, is the rectangle: the population splits into a depth specialist (the egg: few, large, provisioned, which exploits) and a breadth specialist (the sperm: many, small, motile, which explores). That is why the dominant strategy under incommensurable axes is two specialists rather than one compromise.

The honest limit: this is rigorous at the gamete level, where anisogamy really is a depth and breadth bifurcation. Above that it is a just-so story. "Male minds versus female minds" collapses an orbit that a single axis cannot carry.

The covering orders extend the reading. Equal mating types form a regular n-gon, and only the orders 1, 2, 3, 4 and 6 tile a periodic grid. Those are the symmetries the router can route over. Counter-intuitively, more equal sexes means more symmetry, not a stranger one. The non-covering orders, pentagon and beyond, cannot lie flat and periodic; their surplus symmetry escapes into curvature, aperiodicity, or a higher dimension.

### Interpretation: classical and quantum, the orbit versus its representative

Classical and quantum are the same bundle read two ways. The bundle is the orbit. Classical collapses it to its canonical representative: least action keeps the one stationary path. That collapse is the fold to the canonical stabilizer, and stationary phase is what performs it. Quantum keeps the whole bundle and lets its elements interfere, summing over paths with phase `e^{iS/ħ}`. The correspondence principle is the orbit fold, and the isotropy measures how much of the bundle collapses to a point.

The router is the classical shadow. It keeps the orbit as a set, a partition into allowed and forbidden, not a phased sum. There is no phase and no interference. Quantizing it would be one move: attach a phase to each orbit element and sum amplitudes instead of partitioning. Three knobs act on the one frame bundle: conservation fixes a coordinate, relativity chooses the foliation, and classical versus quantum chooses whether to fold the bundle or keep it interfering. Only the fold is in the code.

The symmetry twin earns a physical reading here. A twin shares every group invariant with its oppositely labelled partner and differs only in a discrete sign the algebra cannot derive: a Z₂ charge orthogonal to the geometry, kept as stored evidence at a leaf. That is the classical, decohered residue of a particle–antiparticle pair. Three seams bound the analogy. The operation relating twins is spatial, so it reads as parity before charge conjugation, which is internal. The majority and exception asymmetry has no electron and positron counterpart. And antiparticles originate in the very phase the router drops. The time drive, the T of CPT, is the other missing ingredient.

### Interpretation: drive and gate

Split the Turing analogy in two. The law is the drive: the transition rule that advances the state. The label is the gate: `1` advances and lives, `0` halts and dies. A configuration has a lifespan, the number of steps the dynamics keep it landing on `1`. The irreducible twins are the death points, the `0`s the symmetry algebra cannot smooth over.

As the code stands, the machine is finite and read-only: a read always halts in a fixed number of steps. It is not Turing-complete. Give it a write and unbounded memory, and "how long does it live, will it halt" becomes undecidable.

### Interpretation: relativity and the light cone

Push further and time becomes a foliation, not an index. Each observer is a way of slicing the frames into "now" surfaces. Observers reshuffle the order of spacelike frames while agreeing on the timelike ones. Conservation survives the shuffle because a constant is order-independent: the same charge sits on every observer's slice. What transforms between observers is the split. A boost mixes energy and momentum as components of one object, while the invariant the slice measures is shared.

The foliation need not be postulated. Take an ensemble of routers as observers, each fed the same frame stream reordered by propagation delay. The orderings that survive every observer form the causal partial order, and its frontier, the boundary between "all agree before or after" and "observers disagree", is the light cone. The cone is the invariant of the reshuffling. The charges (mass, connectivity, the symmetry verdict) are frame content, not frame order, so every reshuffled observer agrees on them; only the pose is observer-dependent, which is the part a boost mixes.

One ingredient is not given for free. A per-observer delay yields only a causal partial order, which could still be Galilean. The Lorentz cone, with time dilation and boosts, needs the propagation speed to be one invariant for all observers, `c`. That constraint makes the distance correction unshareable and forces the transformations between observers to be boosts that mix space and time.

### Interpretation: change of scale, renormalization

Alongside conservation and relativity there is a third knob on the same frame bundle: change of scale. Coarse-graining a grid, block decimation from side to side over k, is the renormalization-group flow. It is the same fold the descriptor runs, applied per block. Each k×k block collapses to one coarse cell, and the microstates it discards are the degrees of freedom integrated out. The block fold is the classical limit. The quantum move would keep those microstates with a phase and sum amplitudes.

The decimation needs k to divide the side, and that is where the frame's own arithmetic enters. A prime side has no such k except the trivial ones, so a prime frame is scale-rigid: there is no non-trivial coarse-graining at all. The translational action the router quotients away is the cyclic group of the side, whose subgroup lattice is the divisor lattice. Every non-constant orbit has the full size exactly when the side is prime; that is the rigidity test, and it forces Fermat's congruence `2^n ≡ 2 (mod n)` as a consequence. The distribution of primes, how scale-rigid sides thin out, is the analytic residual the symmetry points at but does not derive.

The decimation must commute with the group. This is where the classical and quantum conflict is meant to surface, through renormalizability. Feed the coarse grid back to the collision diagnostic across two scales and watch whether the invariant chain closes. A finite fixed point means renormalizable: the classical fold and the quantum fiber reconcile. If each coarse step emits a new blueprint, the flow has unbounded counterterms and is non-renormalizable.

The two-scale loop compares the blueprint, the separating axes the diagnostic emits for separable collisions, and the floor, the count of provable twins. Three verdicts follow. Renormalizable closure: the fold preserved the floor and demanded no new counterterm. Soft non-renormalizability: the floor held, but the coarse scale demands a new reducible counterterm. Hard shattering: the fold turned a separable distinction into a twin, minting new irreducible exceptions and raising the floor, which is information no invariant can recover.

The illustrative case is the path-versus-fork collider scaled to 9×9. A T-tetromino fork and an L-tetromino path share their vertex and edge counts and a trivial canonical stabilizer, so they collide in one separable context with floor 0. Under the k = 3 fold each tetromino crushes to one or two coarse cells, and the reading is hard shattering: the path and fork distinction collapses onto identical coarse shapes, manufacturing irreducible twins. A side of at least 4 is needed, because the 3×3 and 5×5 reference sets are degenerate for k dividing the side.

What is not in the code is the phase sum. The loop would measure the discarded entropy but not sum amplitudes over it, so the conflict would surface as the floor rising rather than as interference.

### Interpretation: what the symmetry holds, and the one thing it cannot

The intuitions above reconcile with the machinery at three costs, set by how far each idea sits from the symmetry machinery.

**Held by the code now:**

- **Re-symmetrization gives an undecidable twin.** Breaking the transpose (the rectangle, D2) lets a structure collapse to an answer. Restoring it (the square, D4) makes the axes transpose-equivalent, a twin, which is undecidable. Decidability is a function of the broken symmetry.
- **Pose versus invariant.** The shape's pose is separate from the descriptor, which is the invariant. The invariance contract is why the structural read is robust: a non-invariant perturbation is discarded by contract.
- **The residual as an unhoused scalar.** The stored evidence at a leaf holds what no group-invariant can resolve. Any external scalar with no structural home is a residual of the same kind.
- **The diagnostic is the expert.** The collision diagnostic relocates a collision from underdetermination down to the irreducible twin floor, and names the separating axis where one exists.

**Held only at a different order.** The triad, three as the minimal closure (one is the null, two is an unstable duality, three is the first closed structure), is a ternary symmetry rather than D2. It needs a triangular lattice to be runnable. The middle term, the preserver, is the order-3 fixed locus: remove it and the closure degenerates to a line.

**Not held by any group: the framework's own residual.** The null, indifference, the collapse to zero. No group element represents it, because a symmetry group is a set of invertible bijections and nothing maps to nothing. The null is the universal fixed point, fixed by every transform because it is the absence of the object the group acts on, so it lies outside every orbit. The code contains it only as degenerate cases: the empty grid, an unseen key answered as unknown, the trivial stabilizer. It can be labelled but never enacted. It is the concept of non-collapse: the opposite of love is not hate, a flip, but absence, indifference, the zero.

---

## Caveat: what the code actually does

The implementation has the spatial groups (D4 on the square, D2 on a rectangle, D6 on hex when the caller declares it) and a plain frame index. It has the descriptor and its invariance, the trie, the extrapolation settings, slices with their counts, the stand-ins, the guess method, the collision diagnostic and the stored evidence at leaves. It has no metric, no light cone, no foliation, no entropy dynamics, no fiber counting function, no determinant volumes, no utility or stopping rule, no renormalization, no phase, no write-back, and no ensemble of observers. The sections headed *Interpretation* describe those as readings; they are not implemented.

Realising the relativistic extension would mean the ensemble construction: many routers fed delay-reordered frames, an invariant `c` shared across them, and a group that includes boosts, mixing the space and time axes. The slice-and-find-the-constant logic would not change. The causal structure is what is fixed under the boosted group, the same stabilizer and orbit machinery that routes a shape under D4. Only the group is larger.
