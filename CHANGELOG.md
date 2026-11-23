# Changelog

## Unreleased (after v2.0.0)

A review of the design and code. Breaking changes are marked **Breaking**.

### Breaking

- **The classes are in two packages.** The descriptor classes (`Descriptor`, `Kind`, `Lattice`, `Groups`, `CellSet`,
  `Fold`, `Root`) are in `org.tervel.plexus.descriptor`. The router (`Plexus`, `Level`, `Extrapolate`, `Context`,
  `Answer`, `Verdict`, `Slice`) is in `org.tervel.plexus.router`. Imports change to match.
- **The engine is no longer part of this library.** `Grid`, `Vocabulary`, `Template`, `Stage`, `Pipeline`, `Repeat`,
  `Periods`, `Fft` and `Frame` are in vanga's `vanga.engine` package. The interpreter's `[r]` command is gone. `Groups`,
  `CellSet` and `CellSet.Map` are public, because the engine uses the symmetry tables.
- **Self-extension removed.** `Plexus` no longer takes a `selfExtend` argument, and `extend()`, `extensions()` and
  `selfExtends()` are gone. Shapes are separated by a shape level in the key instead. Migrate by dropping the boolean
  from the four-argument constructor: `new Plexus(rows, cols, lattices)`.
- **`Primitive`, `Information` and `Expression` removed.** Nothing in the router or the engine used them after
  self-extension went. Use the `Descriptor` accessors and plain lambdas.
- **Stand-ins need evidence.** A pure node stands in for an unseen child only if it holds at least 0.1% of its
  parent's training evidence (`Plexus.MIN_SUPPORT_SHARE`).
- **Labels and cells are checked.** Labels other than 0 or 1, and cells other than 0 or 1, are refused with
  `IllegalArgumentException`. `fit` checks every example before storing any.
- **Slice filters are checked.** `Slice.label` accepts only 0 or 1; `mass` and `joins` refuse empty or negative ranges.
- **Hex is opt-in.** `Lattice.DEFAULT` is now the square lattice alone. A grid is filed as hex only when the caller
  passes `Lattice.HEX_6`, since the same cells have hex symmetry whatever they mean. Code that relied on the default
  filing hex must pass `Set.of(Lattice.SQUARE_4, Lattice.HEX_6)`.
- **Ties abstain.** A grid trained with both labels in equal numbers reads UNSEEN, not NEGATIVE, and slices do not
  deliver it.

### Added

- `Plexus.retract(grid, label)` removes one training example and undoes its counts.
- `Plexus.examples()` and `Plexus.clear()`: a model is saved as its examples and rebuilt with `fit`, which gives the
  same answers.
- `cli.Models.save(router, file)` and `cli.Models.load(file, lattices, tolerance)`: a model in the interpreter's data
  file format. The lattices and tolerance are not in the file, so the caller supplies them on load.
- A noise tolerance: `new Plexus(rows, cols, lattices, tolerance)`. A node stands in for an unseen grid while its
  minority label is within `tolerance` of its evidence. The default, `0`, is exact purity, as before.
- `Repeat` and `Periods` constants are named and documented; `docs/06-engine.md` lists them.
- The interpreter's `[r]` command reports how a typed grid repeats.
- `Context` has `splitsOn` and `queryValue`: the level an answer was decided on, and the query's value there. Breaking for anyone who builds a `Context` by hand.

### Fixed

- Stand-ins and unseen placements: a grid is answered only from a leaf or stand-in that holds its shape.
- `Repeat` returned steps that are not exact periods on exactly periodic grids.
- `Repeat` chose the lattice by how exact a shift was, so one stray cell changed the basis in about a third of runs on
  a 5×5 tile. It now scores each lattice by how much of the grid it explains (see the class description). A flipped
  cell or two no longer changes the basis of a tiling or a checkerboard.
- `Repeat` returned a half period on a tiling whose two halves differ in one cell; the true period is now returned.
- `Slice.exhaustive(true)` reads every grid that passes the query's constraints, so a slice on a frame of up to 30 cells
  lists every grid a read answers. Off by default.
- A slice on a frame of up to 16 cells lists every grid a read answers with its label and constraints, once each. It
  reads the whole frame to find them. Larger frames are not covered yet: see [Slices](docs/04-slices.md).
- A trained grid's evidence key is a copy of the grid, so changing the caller's array after training no longer
  corrupts the model.
- `Repeat` answered a board with an opened patch at some positions with a far shift instead of the every-cell repeat.
  It now answers the every-cell repeat wherever the patch sits.
- `Descriptor.equals` ignored the canonical shape and the frame.
- `Frame` accepted transposing moves on non-square frames.
- `Grid.of` allocated a slot per cell up front; a 20,000×20,000 grid ran out of memory before any read.
- `Grid.relabel` returned a new grid even when nothing changed.
- `Grid`'s oracle memo is safe to share between threads.
- `Template.covering` listed one physical placement once per pose.
- Slice generation was far slower than needed; the report's cases now take seconds, not minutes.
- Repeated examples are stored once per leaf, with a count.
- `jitpack.yml` runs the tests.
- A CI workflow (`.github/workflows/ci.yml`) runs `mvn verify` on every push and pull request.
- The release profile no longer auto-publishes to Maven Central: a release is staged and published by hand.
- The packaged jar names its main class, so `java -jar` runs the interpreter.
- **Breaking:** the artifact is renamed from `io.github.noxidog:plexus` to `io.github.noxidog:t-plexus`. The old name collided with the Codehaus Plexus framework, which Maven itself is built on. Change the dependency's `artifactId` to `t-plexus`; the Java package is still `org.tervel.plexus`.

## 2.0.0

The commit `344f8a5` replaced the 1.x design: the descriptor, the trie, the verdicts and the slices were rewritten, and
the 1.x test classes were removed. 114 files changed, with the source going from 49 files to 29. The public API is not
compatible with 1.x. To list what was removed, compare the tags:

```sh
git diff v1.1.0 v2.0.0 -- src
```

## 1.1.0

The last release of the 1.x design.
