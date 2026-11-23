# The Trie

*Two tries, one per lattice; training, reading, and the extrapolation setting.*

---

## 3. The trie

### 3.1 Layout

```
D4 root                                   D6 root
└─ box (short × long, or empty)           └─ box (the common extent)
   └─ rot180? / rot90? ...                   └─ rot60? ...
      └─ ... one level per symmetry             └─ ...
         └─ mass
            └─ joins
               └─ shape
                  └─ orientation
                     └─ pose
                        └─ corner   (leaf: the trained grids)
```

A shape is filed under its lattice's root for good. Every node has one parent, prefixes are shared, and every node keeps two counts: how many training grids labelled 0 and 1 passed through it. So every partial key (a box, a box plus some symmetry answers, a mass within those) is a node with its own evidence.

Everything above the trailing levels describes the shape alone: every turn, mirror and move of a shape shares that part of the path. The trailing levels come last so that a read only needs them where labels disagree across poses or positions.

### 3.2 Training

Training walks the grid's whole path, creating any missing nodes, adds one to the counts of every node passed, and stores the grid at the leaf. Nothing is sealed. `fit` inserts every grid in one pass; `accept` inserts one grid, and is legal at any time.

### 3.3 Reading

A read follows the grid's path until it reaches a leaf or breaks off at a missing child.

| Verdict | When |
|---|---|
| `POSITIVE` / `NEGATIVE` | the whole path was trained. The leaf answers: a grid stored there answers with its own majority, any other grid with the leaf's |
| `ASSUMED` | the path breaks off at a **pure** node (one label only, and not a root) that the extrapolation setting lets stand in for the missing child. The answer is that node's label |
| `UNSEEN` | the path breaks off where no stand-in is allowed: a mixed node, or a pure node above what the setting allows. Not a negative: an absence of evidence |

### 3.4 Extrapolation

`Extrapolate` sets how far a read may go beyond what was trained: the highest level a pure node may stand in at. Each setting allows everything the one before it does. The same setting governs slices ([§5](04-slices.md)).

| `Extrapolate` | A pure node stands in for |
|---|---|
| `NONE` | nothing |
| `PLACEMENT` | unseen orientations, poses and corners of trained shapes |
| `SYMMETRY` (default) | also unseen symmetry classes, provided the query's mass and joins occur together somewhere under the node |
| `EVERYTHING` | also unseen mass and joins values, and unseen symmetry classes whatever their mass and joins |

The root never stands in for a box, so a grid whose box was never trained is UNSEEN under every setting. Under the default, a fold over symmetry never crosses mass or joins: trained on the T (mass 4, joins 3), the L (`xxx/x..`, also 4 and 3) is ASSUMED 1, while the U (`x.x/xxx`, mass 5) and `x.x/x.x` (mass 4, joins 0) are UNSEEN. Under `EVERYTHING` both are ASSUMED 1.

A pure node stands in only when it holds at least `MIN_SUPPORT_SHARE` (0.001, `Plexus.MIN_SUPPORT_SHARE`) of its parent's training evidence, so a node seen once against a large branch is not taken as evidence for an unseen child.

A pure node's children record which values were actually seen, and the answer's context shows how deep the evidence goes. With only upright T's trained, a side T is ASSUMED from the `joins=3` node, whose children split on orientation: no side T was ever seen. With a side T also trained, a side T at a new corner is ASSUMED from `turned=yes → pose=0`: only the corner is new. Train a side T with label 0 instead and the orientation level separates them: side T's then read 0 and upright T's 1, for this shape only.

## Coverage

What a trained router answers for grids it never saw, measured on 4×4 frames (measured once with probe programs, which are not part of the test suite):

- **Held-out placements of trained shapes.** A shape trained at a quarter of its placements answers every one of the others under PLACEMENT and above (43,578 of 43,578).
- **Shapes never trained.** Answered 0 of 5,743 under every setting. A shape has its own path below the shape level, and no stand-in crosses shapes, so an unseen shape is UNSEEN. This is deliberate: an answer for a shape the router has never seen would be borrowed from another shape's label.
- **Labels that are not a function of the shape** (random labels). Almost every shape is contradicted, so almost every answer is UNSEEN; the router abstains rather than guessing.

The coverage figures of earlier designs, 40 to 55% on larger frames, came from the time when shapes shared a key. They do not hold now.