package com.gl.logicraft.circuit;

import net.minecraft.nbt.NbtCompound;

/**
 * Abstract base class for all programmable logic components (AND, OR, NOT,
 * PassThrough…).
 *
 * Each component reads from a set of input slots (indices into
 * CircuitState.inputs)
 * and writes a result to a single output slot (index into
 * CircuitState.outputs).
 */
public abstract class LogicComponent {

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------

    /** Indices into CircuitState.inputs that this component reads from. */
    protected int[] inputSlots;

    /**
     * Indices into CircuitState.outputs where this component writes its results.
     */
    protected int[] outputSlots;

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    protected LogicComponent() {
    }

    protected LogicComponent(int[] inputSlots, int[] outputSlots) {
        this.inputSlots = inputSlots;
        this.outputSlots = outputSlots;
    }

    // -----------------------------------------------------------------------
    // Abstract API
    // -----------------------------------------------------------------------

    /**
     * Returns the TYPE string used to identify this component in NBT.
     * Each subclass must override this.
     */
    public abstract String getType();

    /**
     * Evaluates the component logic given the provided full inputs array.
     *
     * @param inputs full CircuitState.inputs array
     * @return boolean array of results to place in the output slots
     */
    public abstract boolean[] evaluateMulti(boolean[] inputs);

    // -----------------------------------------------------------------------
    // Evaluate into state
    // -----------------------------------------------------------------------

    /**
     * Runs evaluateMulti() and writes the results into
     * state.outputs[outputSlots[i]].
     */
    public void apply(CircuitState state) {
        boolean[] results = evaluateMulti(state.inputs);
        for (int i = 0; i < outputSlots.length && i < results.length; i++) {
            state.outputs[outputSlots[i]] = results[i];
        }
    }

    // -----------------------------------------------------------------------
    // NBT
    // -----------------------------------------------------------------------

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("type", getType());
        nbt.putIntArray("inputSlots", inputSlots);
        nbt.putIntArray("outputSlots", outputSlots);
        return nbt;
    }

    public void fromNbt(NbtCompound nbt) {
        inputSlots = nbt.getIntArray("inputSlots");
        if (nbt.contains("outputSlots", 11)) { // 11 = int array type
            outputSlots = nbt.getIntArray("outputSlots");
        } else if (nbt.contains("outputSlot")) {
            outputSlots = new int[] { nbt.getInt("outputSlot") };
        }
    }

    /**
     * Reconstructs a LogicComponent from NBT.
     * Add new types here as they are implemented.
     */
    public static LogicComponent fromNbtFull(NbtCompound nbt) {
        String type = nbt.getString("type");
        int[] slots = nbt.getIntArray("inputSlots");
        int[] outs;
        if (nbt.contains("outputSlots", 11)) {
            outs = nbt.getIntArray("outputSlots");
        } else {
            outs = new int[] { nbt.getInt("outputSlot") };
        }

        LogicComponent comp = switch (type) {
            case "AND", "and" -> new AndGate(slots, outs);
            case "OR", "or" -> new OrGate(slots, outs);
            case "NOT", "not" -> new NotGate(slots[0], outs[0]);
            case "SPLIT", "PASS", "pass" -> new SplitGate(slots[0], outs);
            case "XOR" -> new XorGate();
            case "TFF" -> new TFlipFlop();
            case "1" -> new OneConstant();
            case "0" -> new ZeroConstant();
            default -> throw new IllegalArgumentException("Unknown LogicComponent type: " + type);
        };
        comp.fromNbt(nbt);
        return comp;
    }

    // =======================================================================
    // Built-in implementations
    // =======================================================================

    // ── AND ─────────────────────────────────────────────────────────────────

    /**
     * AND gate — true only when every input is true.
     */
    public static class AndGate extends LogicComponent {
        public static final String TYPE = "AND";

        public AndGate(int[] inputSlots, int[] outputSlots) {
            super(inputSlots, outputSlots);
        }

        public AndGate(int[] inputSlots, int outputSlot) {
            super(inputSlots, new int[] { outputSlot });
        }

        @Override
        public String getType() {
            return TYPE;
        }

        @Override
        public boolean[] evaluateMulti(boolean[] inputs) {
            boolean res = true;
            for (int slot : inputSlots) {
                if (!inputs[slot]) {
                    res = false;
                    break;
                }
            }
            return new boolean[] { res };
        }
    }

    // ── OR ──────────────────────────────────────────────────────────────────

    /**
     * OR gate — true when at least one input is true.
     */
    public static class OrGate extends LogicComponent {
        public static final String TYPE = "OR";

        public OrGate(int[] inputSlots, int[] outputSlots) {
            super(inputSlots, outputSlots);
        }

        public OrGate(int[] inputSlots, int outputSlot) {
            super(inputSlots, new int[] { outputSlot });
        }

        @Override
        public String getType() {
            return TYPE;
        }

        @Override
        public boolean[] evaluateMulti(boolean[] inputs) {
            boolean res = false;
            for (int slot : inputSlots) {
                if (inputs[slot]) {
                    res = true;
                    break;
                }
            }
            return new boolean[] { res };
        }
    }

    // ── NOT ─────────────────────────────────────────────────────────────────

    /**
     * NOT gate — inverts a single input.
     */
    public static class NotGate extends LogicComponent {
        public static final String TYPE = "NOT";

        public NotGate(int inputSlot, int outputSlot) {
            super(new int[] { inputSlot }, new int[] { outputSlot });
        }

        @Override
        public String getType() {
            return TYPE;
        }

        @Override
        public boolean[] evaluateMulti(boolean[] inputs) {
            return new boolean[] { !inputs[inputSlots[0]] };
        }
    }

    // ── PASS-THROUGH ────────────────────────────────────────────────────────

    /**
     * Pass-through — copies one input directly to the output unchanged.
     */
    /**
     * Split — copies one input directly to two outputs.
     */
    public static class SplitGate extends LogicComponent {
        public static final String TYPE = "SPLIT";

        public SplitGate(int inputSlot, int[] outputSlots) {
            super(new int[] { inputSlot }, outputSlots);
        }

        public SplitGate() {
            this.inputSlots = new int[] { 0 };
            this.outputSlots = new int[] { 0, 1 };
        }

        @Override
        public String getType() {
            return TYPE;
        }

        @Override
        public boolean[] evaluateMulti(boolean[] inputs) {
            boolean in = inputs[inputSlots[0]];
            return new boolean[] { in, in };
        }
    }

    // ── XOR ──────────────────────────────────────────────────────────────────

    /**
     * XOR gate — true only when exactly one input is true (for 2 inputs).
     */
    public static class XorGate extends LogicComponent {
        public static final String TYPE = "XOR";

        public XorGate() {
            this.inputSlots = new int[] { 0, 1 };
            this.outputSlots = new int[] { 0 };
        }

        @Override
        public boolean[] evaluateMulti(boolean[] inputs) {
            return new boolean[] { inputs[0] ^ inputs[1] };
        }

        @Override
        public String getType() {
            return TYPE;
        }
    }

    // ── CONSTANTS ────────────────────────────────────────────────────────────

    public static class OneConstant extends LogicComponent {
        public static final String TYPE = "1";

        public OneConstant() {
            this.inputSlots = new int[] {};
            this.outputSlots = new int[] { 0 };
        }

        @Override
        public boolean[] evaluateMulti(boolean[] inputs) {
            return new boolean[] { true };
        }

        @Override
        public String getType() {
            return TYPE;
        }
    }

    public static class ZeroConstant extends LogicComponent {
        public static final String TYPE = "0";

        public ZeroConstant() {
            this.inputSlots = new int[] {};
            this.outputSlots = new int[] { 0 };
        }

        @Override
        public boolean[] evaluateMulti(boolean[] inputs) {
            return new boolean[] { false };
        }

        @Override
        public String getType() {
            return TYPE;
        }
    }

    // ── T FLIP-FLOP ─────────────────────────────────────────────────────────

    public static class TFlipFlop extends LogicComponent {
        public static final String TYPE = "TFF";
        public boolean state = false;
        public boolean lastClk = false;

        public TFlipFlop() {
            this.inputSlots = new int[] { 0 };
            this.outputSlots = new int[] { 0 };
        }

        @Override
        public boolean[] evaluateMulti(boolean[] inputs) {
            boolean clk = inputs.length > 0 && inputs[0];
            if (clk && !lastClk) {
                state = !state;
            }
            lastClk = clk;
            return new boolean[] { state };
        }

        @Override
        public String getType() {
            return TYPE;
        }

        @Override
        public NbtCompound toNbt() {
            NbtCompound nbt = super.toNbt();
            nbt.putBoolean("state", state);
            nbt.putBoolean("lastClk", lastClk);
            return nbt;
        }

        @Override
        public void fromNbt(NbtCompound nbt) {
            super.fromNbt(nbt);
            state = nbt.getBoolean("state");
            lastClk = nbt.getBoolean("lastClk");
        }
    }
}
