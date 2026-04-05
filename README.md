# GL_LogiCraft

**A Fabric mod for Minecraft 1.21.4**

---

## What is it?

GL_LogiCraft lets you attach programmable logic circuits to any block in the game. No cables, no large redstone structures, no mess — just compact, modular logic built directly onto existing blocks.

## How it works

The mod adds two items:

**Logic Chip** — a nearly invisible attachment that sticks to any face of any block. Each chip contains a fully configurable logic circuit that reads and writes redstone signals from the block it's attached to.

**Logic Wrench** — the tool used to place, configure, and remove chips. Right-click a chip to open the circuit editor. Left-click to remove it.

## Circuit Editor

The editor is a Logisim-style interface with a 30×30 grid. You place logic components, connect them with wires by dragging between ports, and the circuit simulates in real time with color-coded feedback:

- **Gray wire** — signal is OFF
- **Green wire** — signal is ON  
- **Red wire** — disconnected port or error

## Components

| Component | Behavior |
|---|---|
| AND | ON only if all inputs are ON |
| OR | ON if any input is ON |
| NOT | Inverts the input |
| XOR | ON if inputs are different |
| PASS | Passes signal through. Useful to split one output into multiple inputs |
| 1 | Always outputs ON |
| 0 | Always outputs OFF |

## I/O System

Each chip has 5 inputs and 5 outputs:

- `redst_in` / `redst_out` — reads and writes redstone to/from the host block
- `in_1..4` / `out_1..4` — internal signals that connect automatically to adjacent chips by index

This lets you chain multiple chips together to build larger systems, each chip acting as a module.

## Crafting

**Logic Chip** — mid-game. Crafted with Gold Ingots, Redstone and Nether Quartz.

**Logic Wrench** — late-game. Crafted with Ender Pearls, Blaze Rods, Gold Blocks and a Netherite Ingot.

## Tips

- Hold the Logic Wrench to see a blue outline around all nearby chips
- Hover over items with Shift for detailed info
- Press `?` inside the circuit editor for a full reference of components, I/O nodes and controls
- Chips evaluate automatically — no need to reopen the editor when redstone changes

## Built with

- Fabric Loader 0.18.6
- Fabric API 0.119.4
- Minecraft 1.21.4
- Java 21

---

*GL_LogiCraft is a project by GL(iago).*

---

