# The Interpreter

*`cli.Main`: train on a data file, then query.*

---

## 6. The interpreter

```
java -cp out org.tervel.plexus.cli.Main data/conservation-t-orbit-3x3.txt
java -cp out org.tervel.plexus.cli.Main data/conservation-t-orbit-3x3.txt --lattice square8
```

`--lattice` picks the router's lattices, comma separated from `square4`, `square8` and `hex6`; the default is `square4`. A set without exactly one square lattice is refused with the reason.

`Main` reads the data file, trains a `Plexus` on it, prints one line with the example count, the frame, the lattices and the number of trie nodes, and opens a REPL:

```
t-plexus: 2 example(s) in a 5x5 frame, lattices [SQUARE_4], 16 node(s)
```

```
[5] score  [t] trie  [4] grid  [s] slice  [8] diagnose  [q] quit
```

| Command | What it does |
|---|---|
| `[5]` score | reads every training grid back under the default setting and counts those answered with their own label |
| `[t]` trie | draws both tries as indented paths, each node with its counts; leaves marked |
| `[4]` grid | reads a typed grid (`xxx/.x.`: rows separated by `/`, `x` or `1` filled, placed top-left in the frame): its descriptor, then its verdict under each setting with the controlling context |
| `[s]` slice | prompts for root, symmetries to keep at least, mass, joins, label, orientation (`0` / `1`, or `upright` / `turned`, `right` / `left`), pose (a number; 0 is the canonical pose), corner (`row,col`), what to show one entry per (`placement`, `pose` or `shape`), and extrapolation (`none`, `placement`, `symmetry` or `everything`), then prints every grid the slice produces with its evidence and context, and the confirmed and assumed counts. A blank answer, or `any`, `all`, `both` or `none`, leaves a prompt unconstrained. Mass and joins take a number or a range (`4-5`). Symmetry names ignore case, spaces and hyphens; an unknown name, a bad range, or an unknown root, label, orientation, pose, corner, show or extrapolation value is refused with a message saying what was expected |
| `[8]` diagnose | lists the mixed junctions and leaves and what resolves each |

### 6.1 Data files

The first line is `nxm`: rows by columns. Then one record per example: `n` rows of `m` cells (`x` filled, `.` or `_` empty), followed by a line holding the label, 0 or 1. Any other cell, a row of the wrong width, a label other than 0 or 1, a record cut short or a blank line is an error that names its line, and the file is not trained. Usage errors and unreadable files exit with a nonzero status.

```
3x3
xxx
_x_
___
1
```
