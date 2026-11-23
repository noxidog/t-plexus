# Plexus — Design Documentation

*A symmetry-routed exact classifier: it files every shape by what it is, not where it sits.*

| § | Document | Contents |
|---|---|---|
| 0–1 | [Design Overview](design.md) | The task, the contradictions that make it hard, and the shape of the key. |
| 2 | [The Descriptor](01-descriptor.md) | Bounding box, lattice pick, the symmetry walk, the canonical pose, invariants and trailing coordinates. |
| 3 | [The Trie](02-trie.md) | Two tries, training, the four verdicts, and the extrapolation setting. |
| 4 | [Slices](04-slices.md) | Constraints per level, the callback, and constructive generation. |
| 5 | [The Interpreter](05-cli.md) | `cli.Main` and the data-file format. |

## Interpretation

The reading of these operations as conservation, fibers and symmetry breaking is in [Interpretation](interpretation.md).

## Reading orders

- **To use it:** [Design Overview](design.md) → [The Interpreter](05-cli.md) → `../data/README.md`.
- **To understand the key:** [The Descriptor](01-descriptor.md) → [The Trie](02-trie.md).
- **To query and generate:** [The Trie](02-trie.md) → [Slices](04-slices.md).
- **To see how a shape is filed:** [The Descriptor](01-descriptor.md) → [The Trie](02-trie.md).
- **To judge it:** the [Limits](../README.md#limits) in the README, then the measured figures in [The Trie](02-trie.md) and the [tests](../test/org/tervel/plexus/).
