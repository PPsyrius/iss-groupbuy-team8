# caveman

Token-efficient response style that removes filler while preserving technical accuracy, code, commands, symbols, numbers, units, and exact errors.

## Invoke

```text
/caveman              # full, default
/caveman lite         # concise full sentences
/caveman ultra        # shortest unambiguous fragments
/caveman wenyan       # classical Chinese
stop caveman          # normal prose
```

| Level | Style |
|---|---|
| `lite` | No filler or hedging; full sentences |
| `full` | Drop articles where safe; fragments allowed |
| `ultra` | Remove conjunctions and repetition where meaning remains clear |
| `wenyan-lite` | Light classical Chinese compression |
| `wenyan-full` | Strong classical Chinese compression |
| `wenyan-ultra` | Maximum unambiguous classical compression |

The mode persists until stopped. Security warnings, irreversible confirmations, and ambiguity-prone procedures temporarily use normal prose. See [`SKILL.md`](./SKILL.md) for complete instructions.
