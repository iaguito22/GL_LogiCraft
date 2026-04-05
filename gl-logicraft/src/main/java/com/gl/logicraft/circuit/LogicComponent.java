package com.gl.logicraft.circuit;

import net.minecraft.nbt.NbtCompound;

/**
 * Abstract base class for all programmable logic components (AND, OR, NOT, PassThrough…).
 *
 * Each component reads from a set of input slots (indices into CircuitState.inputs)
 * and writes a result to a single output slot (index into CircuitState.outputs).
 */
public abstract class LogicComponent {

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------

    /** Indices into CircuitState.inputs that this component reads from. */
    protected int[] inputSlots;

    /** Index into CircuitState.outputs where this component writes its result. */
    protected int outputSlot;

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    protected LogicComponent() {}

    protected LogicComponent(int[] inputSlots, int outputSlot) {
        this.inputSlots = inputSlots;
        this.outputSlot = outputSlot;
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
     * @return boolean result to place in the output slot
     */
    public abstract boolean evaluate(boolean[] inputs);

    // -----------------------------------------------------------------------
    // Evaluate into state
    // -----------------------------------------------------------------------

    /**
     * Runs evaluate() and writes the result into state.outputs[outputSlot].
     */
    public void apply(CircuitState state) {
        state.outputs[outputSlot] = evaluate(state.inputs);
    }

    // -----------------------------------------------------------------------
    // NBT
    // -----------------------------------------------------------------------

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("type", getType());
        nbt.putIntArray("inputSlots", inputSlots);
        nbt.putInt("outputSlot", outputSlot);
        return nbt;
    }

    public void fromNbt(NbtCompound nbt) {
        inputSlots = nbt.getIntArray("inputSlots");
        outputSlot = nbt.getInt("outputSlot");
    }

    /**
     * Reconstructs a LogicComponent from NBT.
     * Add new types here as they are implemented.
     */
    public static LogicComponent fromNbtFull(NbtCompound nbt) {
        String type = nbt.getString("type");
        int[] slots = nbt.getIntArray("inputSlots");
        int out = nbt.getInt("outputSlot");

        LogicComponent comp = switch (type) {
            case AndGate.TYPE      -> new AndGate(slots, out);
            case OrGate.TYPE       -> new OrGate(slots, out);
            case NotGate.TYPE      -> new NotGate(slots[0], out);
            case PassThrough.TYPE  -> new PassThrough(slots[0], out);
            case XorGate.TYPE      -> new XorGate();
            case OneConstant.TYPE  -> new OneConstant();
            case ZeroConstant.TYPE -> new ZeroConstant();
            default -> throw new IllegalArgumentException("Unknown LogicComponent type: " + type);
        };
        comp.fromNbt(nbt);
        return comp;
    }

    // =======================================================================
    //  Built-in implementations
    // =======================================================================

    // ── AND ─────────────────────────────────────────────────────────────────

    /**
     * AND gate — true only when every input is true.
     */
    public static class AndGate extends LogicComponent {
        public static final String TYPE = "and";

        public AndGate(int[] inputSlots, int outputSlot) {
            super(inputSlots, outputSlot);
        }

        @Override public String getType() { return TYPE; }

        @Override
        public boolean evaluate(boolean[] inputs) {
            for (int slot : inputSlots) {
                if (!inputs[slot]) return false;
            }
            return true;
        }
    }

    // ── OR ──────────────────────────────────────────────────────────────────

    /**
     * OR gate — true when at least one input is true.
     */
    public static class OrGate extends LogicComponent {
        public static final String TYPE = "or";

        public OrGate(int[] inputSlots, int outputSlot) {
            super(inputSlots, outputSlot);
        }

        @Override public String getType() { return TYPE; }

        @Override
        public boolean evaluate(boolean[] inputs) {
            for (int slot : inputSlots) {
                if (inputs[slot]) return true;
            }
            return false;
        }
    }

    // ── NOT ─────────────────────────────────────────────────────────────────

    /**
     * NOT gate — inverts a single input.
     */
    public static class NotGate extends LogicComponent {
        public static final String TYPE = "not";

        public NotGate(int inputSlot, int outputSlot) {
            super(new int[]{inputSlot}, outputSlot);
        }

        @Override public String getType() { return TYPE; }

        @Override
        public boolean evaluate(boolean[] inputs) {
            return !inputs[inputSlots[0]];
        }
    }

    // ── PASS-THROUGH ────────────────────────────────────────────────────────

    /**
     * Pass-through — copies one input directly to the output unchanged.
     */
    public static class PassThrough extends LogicComponent {
        public static final String TYPE = "pass";

        public PassThrough(int inputSlot, int outputSlot) {
            super(new int[]{inputSlot}, outputSlot);
        }

        @Override public String getType() { return TYPE; }

        @Override
        public boolean evaluate(boolean[] inputs) {
            return inputs[inputSlots[0]];
        }
    }

    // ── XOR ──────────────────────────────────────────────────────────────────

    /**
     * XOR gate — true only when exactly one input is true (for 2 inputs).
     */
    public static class XorGate extends LogicComponent {
        public static final String TYPE = "XOR";
        public XorGate() {
            this.inputSlots = new int[]{0, 1};
            this.outputSlot = 0;
        }
        @Override
        public boolean evaluate(boolean[] inputs) {
            return inputs[0] ^ inputs[1];
        }
        @Override
        public String getType() { return TYPE; }
    }

    // ── CONSTANTS ────────────────────────────────────────────────────────────

    public static class OneConstant extends LogicComponent {
        public static final String TYPE = "1";
        public OneConstant() {
            this.inputSlots = new int[]{};
            this.outputSlot = 0;
        }
        @Override
        public boolean evaluate(boolean[] inputs) { return true; }
        @Override
        public String getType() { return TYPE; }
    }

    public static class ZeroConstant extends LogicComponent {
        public static final String TYPE = "0";
        public ZeroConstant() {
            this.inputSlots = new int[]{};
            this.outputSlot = 0;
        }
        @Override
        public boolean evaluate(boolean[] inputs) { return false; }
        @Override
        public String getType() { return TYPE; }
    }
}
