# t-plexus

![t-plexus](assets/plexus-logo.png)

**A symmetry-routed exact classifier — a router, not a network.**

Plexus has no weights. Its key has no thresholds; its one named threshold is documented in docs/02-trie.md. It files every binary grid by what its shape **is**, not where it sits:

```
D4 root / D6 root → box → symmetry walk → mass → joins → shape → orientation → pose → corner
```

The bounding box of the filled cells picks the lattice (square, or hex when a square box gains symmetry by shearing its rows). The shape is turned into a canonical pose and walked through one yes/no question per symmetry. Mass and joins follow, then how and where the shape arrived. Two tries, one per lattice, hold the counts; every partial key is a node with its own evidence.

A read answers **POSITIVE**, **NEGATIVE**, **ASSUMED** (a pure node stands in for something never seen, as far as the extrapolation setting allows) or **UNSEEN**. Different shapes are separated by their canonical shape, before any pose or place is read; where one shape is labelled differently by pose or place, the trailing levels separate it. Contradictions are kept exactly, never smoothed over.

The unifying idea is the **slice**: fix some coordinates of the key and enumerate everything that answers so. Classification runs the key forward; a slice runs it backward, and generates the grids it would answer for.

## Quickstart

Requires **Java 21+**. No dependencies — the JDK is the whole toolchain.

```sh
# compile (macOS/Linux)
javac -d out $(find src -name "*.java")
# compile (Windows PowerShell)
javac -d out (Get-ChildItem -Recurse src -Filter *.java).FullName

# train + drop into the REPL
java -cp out org.tervel.plexus.cli.Main data/conservation-t-orbit-3x3.txt
```

```
[5] score  [t] trie  [4] grid  [s] slice  [8] diagnose  [q] quit
```

As a library:

```java
final var plexus = new Plexus(3, 3);                 // the input frame
plexus.fit(grids, labels);
final var answer = plexus.read(grid);                // POSITIVE, NEGATIVE, ASSUMED or UNSEEN, with its context
plexus.slice(Slice.on(Root.D4).atLeast("short-axis mirror").label(1),
             (g, context, evidence) -> { ... });     // every matching grid, CONFIRMED or ASSUMED
```

## Using it

Maven, from JitPack (the artifact is built from this repository):

```xml
<repositories>
  <repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
  </repository>
</repositories>

<dependency>
  <groupId>com.github.noxidog</groupId>
  <artifactId>t-plexus</artifactId>
  <version>v2.0.3</version>
</dependency>
```

Build and test it (JDK 21 and Maven):

```sh
mvn test
```

Changes between releases are listed in [CHANGELOG.md](CHANGELOG.md).
## Sixty seconds of output

**The trie keeps shape and pose apart.** Trained on the eight T's of a 3×3 frame, `[t]` shows them sharing one shape path and splitting only at the trailing levels:

```
D4 root  [neg 0, pos 8]
  box=2x3  [neg 0, pos 8]
    rot180=no  [neg 0, pos 8]
      short-axis mirror=yes  [neg 0, pos 8]
        long-axis mirror=no  [neg 0, pos 8]
          mass=4  [neg 0, pos 8]
            joins=3  [neg 0, pos 8]
              turned=no  [neg 0, pos 4]
              ...
              turned=yes  [neg 0, pos 4]
              ...
```

**You choose how far it extrapolates.** On the same data, `[4]` with the L `xxx/x..` (same box, mass and joins as the T, but no mirror):

```
  RECT on D4, box 2x3, symmetries [], mass 4, joins 3, orientation 0, pose 0, corner (0,0)
  NONE       UNSEEN     D4 box=2x3 → rot180=no  [neg 0, pos 8]
  PLACEMENT  UNSEEN     D4 box=2x3 → rot180=no  [neg 0, pos 8]
  SYMMETRY   ASSUMED  label 1   D4 box=2x3 → rot180=no  [neg 0, pos 8]
  EVERYTHING ASSUMED  label 1   D4 box=2x3 → rot180=no  [neg 0, pos 8]
```

**It separates shapes by their shape.** On `data/separable-path-fork-5x5.txt` a path and a fork share box, symmetry, mass and joins; `[8]` shows that they split at their shape:

```
mixed nodes and what resolves them
  D4 box=2x4 → rot180=no → short-axis mirror=no → long-axis mirror=no → mass=5 → joins=4  [neg 1, pos 1]
    -> several shapes: separated by their shape
```

The behaviour is asserted, not just printed: the suite checks pose-free keys on every dataset, the stand-ins, and, on 3×3 datasets and on every 4×4 grid, that a slice lists exactly what reading answers. Frames above 16 cells are not checked: the shipped 5×5 dataset is too large to check exhaustively; see [Slices](docs/04-slices.md).

## Limits

Stated plainly, from what the code and the measurements show:

- **Binary cells.** Each cell is 0 or 1. Other values are refused, not read as shades.
- **One shape per grid.** A grid's filled cells, and their bounding box, are one shape. Two objects in one frame are read as one.
- **Exact shapes.** A shape with one cell changed is a new shape. Noise is not tolerated, and the router has no
  similarity measure across shapes.
- **Untrained shapes are unanswered.** A shape never seen answers UNSEEN; a seen shape answers its placements it was not trained at. [Coverage](docs/02-trie.md) has the measured figures.
- **Labels are what the data says.** A contradiction with equal evidence abstains, and a router keeps every distinct
  training grid, so memory grows with the data (about 1.6 kB per 6×6 example).
- **Frames.** Each side is at most 4096 cells, and a frame's cell count must fit an int.
- **Slices.** A node with more than 2²¹ fills to enumerate is skipped, and the count is in Slice.Result.skipped.
- **Fixed levels.** The order of questions the trie asks, the symmetry group and the feature set are fixed in the code.
  Only the lattices are chosen by the caller.
- **Noise.** The default is exact. `new Plexus(rows, cols, lattices, tolerance)` lets a branch with a small share of
  mislabelled examples keep answering, at the cost of answering some grids it was never pure for.

## What it is for

Plexus is for binary grids of one object where the question is "what shape is this, and where has it been seen?" The
answers are exact and can be listed (slices), so you can audit what the router says yes to. It is not a classifier for
noisy images or for several objects in one frame. The measured figures for coverage and accuracy are in
[The Trie](docs/02-trie.md), and the test suite checks the behaviour they rely on.

## Where to read more

- **[docs/](docs/README.md)** — the design as a document set: the descriptor, the trie and the extrapolation setting, slices and the interpreter. [Interpretation](docs/interpretation.md) reads the operations as conservation, fibers and symmetry breaking.
- **[data/README.md](data/README.md)** — the datasets and the question each one asks.
- **[Plexus: Intuitions from a Symmetry Machine](https://noxidog.substack.com/p/plexus)** — the companion essay series.
