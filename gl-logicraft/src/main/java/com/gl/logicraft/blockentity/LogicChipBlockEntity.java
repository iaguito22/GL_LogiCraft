package com.gl.logicraft.blockentity;

import com.gl.logicraft.circuit.CircuitState;
import com.gl.logicraft.circuit.GuiComponent;
import com.gl.logicraft.circuit.LogicComponent;
import com.gl.logicraft.circuit.SignalPropagator;
import com.gl.logicraft.circuit.Wire;
import com.gl.logicraft.registry.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * Block entity for the LogicChipBlock.
 *
 * Stores the circuit editor layout (GuiComponent + Wire lists) and evaluates
 * the circuit graph when inputs change. Results are written to CircuitState.outputs.
 */
public class LogicChipBlockEntity extends BlockEntity {

    public static final int MAX_COMPONENTS = 8;

    // Fixed node IDs --------------------------------------------------------
    public static final String NODE_REDST_IN  = "REDST_IN";
    public static final String NODE_REDST_OUT = "REDST_OUT";
    public static final String NODE_IN(int n) { return "IN_" + n; }  // helper – not valid Java, see below

    // -----------------------------------------------------------------------
    // State
    // -----------------------------------------------------------------------

    private final CircuitState circuitState = new CircuitState();

    /** Visual layout — persisted and synced to player GUI. */
    private final List<GuiComponent> guiComponents = new ArrayList<>();
    private final List<Wire>         wires         = new ArrayList<>();

    /** Legacy execution-side list (kept for SignalPropagator compat). */
    private final List<LogicComponent> components = new ArrayList<>();

    boolean isPropagating = false;

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    public LogicChipBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LOGIC_CHIP_BLOCK_ENTITY, pos, state);
    }

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------

    public CircuitState          getCircuitState()    { return circuitState; }
    public List<GuiComponent>    getGuiComponents()   { return guiComponents; }
    public List<Wire>            getWires()           { return wires; }
    public List<LogicComponent>  getLogicComponents() { return components; }

    // -----------------------------------------------------------------------
    // Graph evaluation
    // -----------------------------------------------------------------------

    /**
     * Evaluates the circuit by tracing the wire graph.
     * Input node signals → gate evaluations → output node signals.
     * If any circuit output changed, propagates to the world and neighbours.
     */
    public void evaluate() {
        if (world == null) return;

        boolean[] outputsBefore = Arrays.copyOf(circuitState.outputs, CircuitState.SIGNAL_COUNT);

        // ------ Build signal map ------
        Map<String, boolean[]> sigs = new HashMap<>();

        // Fixed input nodes
        sigs.put(NODE_REDST_IN, new boolean[]{circuitState.inputs[0]});
        for (int i = 0; i < 4; i++) {
            sigs.put("IN_" + i, new boolean[]{circuitState.inputs[i + 1]});
        }

        // Settle signals with up to (size+1) passes (handles any topological order)
        int passes = guiComponents.size() + 1;
        for (int p = 0; p < passes; p++) {
            for (GuiComponent gc : guiComponents) {
                int inCount = gc.getInputCount();
                boolean[] gateInputs = new boolean[inCount];
                for (Wire w : wires) {
                    if (w.toId.equals(gc.id) && w.toPort < inCount) {
                        boolean[] src = sigs.get(w.fromId);
                        if (src != null && w.fromPort < src.length) {
                            gateInputs[w.toPort] = src[w.fromPort];
                        }
                    }
                }
                boolean result = evalGate(gc.type, gateInputs);
                sigs.put(gc.id, new boolean[]{result});
            }
        }

        // Read output nodes
        circuitState.outputs[0] = getSignal(sigs, NODE_REDST_OUT);
        for (int i = 0; i < 4; i++) {
            circuitState.outputs[i + 1] = getSignal(sigs, "OUT_" + i);
        }

        // ------ Propagate if changed ------
        boolean changed = !Arrays.equals(outputsBefore, circuitState.outputs);
        if (changed) {
            world.updateNeighbors(pos, getCachedState().getBlock());
            isPropagating = true;
            try {
                SignalPropagator.propagate(world, pos, circuitState.outputs);
            } finally {
                isPropagating = false;
            }
            markDirty();
        }
    }

    private boolean evalGate(String type, boolean[] inputs) {
        return switch (type) {
            case "and"  -> { boolean r = true;  for (boolean b : inputs) r &= b; yield r; }
            case "or"   -> { boolean r = false; for (boolean b : inputs) r |= b; yield r; }
            case "not"  -> inputs.length > 0 && !inputs[0];
            default     -> inputs.length > 0 && inputs[0]; // pass
        };
    }

    private boolean getSignal(Map<String, boolean[]> sigs, String nodeId) {
        // Check if any wire delivers a signal to this output node
        for (Wire w : wires) {
            if (w.toId.equals(nodeId)) {
                boolean[] src = sigs.get(w.fromId);
                if (src != null && w.fromPort < src.length) {
                    return src[w.fromPort];
                }
            }
        }
        return false;
    }

    // -----------------------------------------------------------------------
    // Incoming neighbour signals
    // -----------------------------------------------------------------------

    public void receiveSignals(boolean[] signals) {
        if (isPropagating) return;
        boolean changed = false;
        int len = Math.min(signals.length, CircuitState.SIGNAL_COUNT);
        for (int i = 0; i < len; i++) {
            if (signals[i] && !circuitState.inputs[i]) {
                circuitState.inputs[i] = true;
                changed = true;
            }
        }
        if (changed) evaluate();
    }

    // -----------------------------------------------------------------------
    // GUI data serialization (used for screen open data packet)
    // -----------------------------------------------------------------------

    public NbtCompound serializeGuiData() {
        NbtCompound nbt = new NbtCompound();

        NbtList gcList = new NbtList();
        for (GuiComponent gc : guiComponents) gcList.add(gc.toNbt());
        nbt.put("GuiComponents", gcList);

        NbtList wList = new NbtList();
        for (Wire w : wires) wList.add(w.toNbt());
        nbt.put("Wires", wList);

        return nbt;
    }

    public void deserializeGuiData(NbtCompound nbt) {
        guiComponents.clear();
        wires.clear();

        NbtList gcList = nbt.getList("GuiComponents", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < gcList.size() && i < MAX_COMPONENTS; i++) {
            guiComponents.add(GuiComponent.fromNbt(gcList.getCompound(i)));
        }

        NbtList wList = nbt.getList("Wires", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < wList.size(); i++) {
            wires.add(Wire.fromNbt(wList.getCompound(i)));
        }
    }

    // -----------------------------------------------------------------------
    // NBT persistence
    // -----------------------------------------------------------------------

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        nbt.put("CircuitState", circuitState.toNbt());
        nbt.put("GuiData", serializeGuiData());
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        if (nbt.contains("CircuitState")) {
            circuitState.fromNbt(nbt.getCompound("CircuitState"));
        }
        if (nbt.contains("GuiData")) {
            deserializeGuiData(nbt.getCompound("GuiData"));
        }
    }
}
