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
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;

import java.util.*;

/**
 * Block entity for the LogicChipBlock.
 *
 * Stores the circuit editor layout (GuiComponent + Wire lists) and evaluates
 * the circuit graph when inputs change. Results are written to
 * CircuitState.outputs.
 */
public class LogicChipBlockEntity extends BlockEntity {

    public static final int MAX_COMPONENTS = 8;

    // Fixed node IDs --------------------------------------------------------
    public static final String NODE_REDST_IN = "REDST_IN";
    public static final String NODE_REDST_OUT = "REDST_OUT";

    public static final String NODE_IN(int n) {
        return "IN_" + n;
    } // helper – not valid Java, see below

    // -----------------------------------------------------------------------
    // State
    // -----------------------------------------------------------------------

    private final CircuitState circuitState = new CircuitState();

    /** Visual layout — persisted and synced to player GUI. */
    private final List<GuiComponent> guiComponents = new ArrayList<>();
    private final List<Wire> wires = new ArrayList<>();

    /** Legacy execution-side list (kept for SignalPropagator compat). */
    private final List<LogicComponent> components = new ArrayList<>();

    boolean isPropagating = false;
    private boolean lastOutput = false;
    private boolean lastInputRedstone = false;
    public Direction lastSignalOrigin = null;
    public boolean pendingRedstoneInput = false;
    private final Map<String, LogicComponent.TFlipFlop> tffInstances = new HashMap<>();

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    public LogicChipBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LOGIC_CHIP_BLOCK_ENTITY, pos, state);
    }

    // -----------------------------------------------------------------------
    // Accessors & Sync
    // -----------------------------------------------------------------------

    @Override
    public BlockEntityUpdateS2CPacket toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        NbtCompound nbt = new NbtCompound();
        this.writeNbt(nbt, registries);
        return nbt;
    }

    public CircuitState getCircuitState() {
        return circuitState;
    }

    public List<GuiComponent> getGuiComponents() {
        return guiComponents;
    }

    public List<Wire> getWires() {
        return wires;
    }

    public List<LogicComponent> getLogicComponents() {
        return components;
    }

    // -----------------------------------------------------------------------
    // Graph evaluation
    // -----------------------------------------------------------------------

    /**
     * Evaluates the circuit by tracing the wire graph.
     * Input node signals → gate evaluations → output node signals.
     * If any circuit output changed, propagates to the world and neighbours.
     */
    public void serverEvaluate() {
        if (world == null)
            return;

        boolean[] outputsBefore = Arrays.copyOf(circuitState.outputs, CircuitState.SIGNAL_COUNT);

        // ------ Build signal map ------
        Map<String, boolean[]> sigs = new HashMap<>();

        // Fixed input nodes
        circuitState.inputs[0] = pendingRedstoneInput;
        sigs.put(NODE_REDST_IN, new boolean[] { circuitState.inputs[0] });
        for (int i = 0; i < 4; i++) {
            sigs.put("IN_" + i, new boolean[] { circuitState.inputs[i + 1] });
        }

        // Seed constants first
        for (GuiComponent gc : guiComponents) {
            if ("1".equals(gc.type) || "0".equals(gc.type)) {
                boolean[] vals = evalGateMulti(gc.id, gc.type, new boolean[0]);
                sigs.put(gc.id, vals);
            }
        }

        int maxIterations = 20;
        for (int p = 0; p < maxIterations; p++) {
            boolean changedInIter = false;
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
                boolean[] results = evalGateMulti(gc.id, gc.type, gateInputs);
                boolean[] prev = sigs.get(gc.id);
                if (prev == null || !Arrays.equals(prev, results)) {
                    sigs.put(gc.id, results);
                    changedInIter = true;
                }
            }
            if (!changedInIter)
                break;
        }

        // Read output nodes
        circuitState.outputs[0] = getSignal(sigs, NODE_REDST_OUT);
        for (int i = 0; i < 4; i++) {
            circuitState.outputs[i + 1] = getSignal(sigs, "OUT_" + i);
        }

        // ------ Propagate if changed ------
        boolean changed = !Arrays.equals(outputsBefore, circuitState.outputs);

        boolean currentOut = circuitState.outputs[0];
        if (currentOut != lastOutput) {
            lastOutput = currentOut;
            if (world instanceof net.minecraft.server.world.ServerWorld sw) {
                sw.updateNeighborsAlways(pos, getCachedState().getBlock());
                Direction facing = getCachedState().get(com.gl.logicraft.block.LogicChipBlock.FACING);
                BlockPos hostPos = pos.offset(facing.getOpposite());
                sw.updateNeighborsAlways(hostPos, sw.getBlockState(hostPos).getBlock());
            }
        }

        if (changed) {
            isPropagating = true;
            try {
                BlockPos originPos = this.lastSignalOrigin != null ? pos.offset(this.lastSignalOrigin) : pos;
                SignalPropagator.propagate(world, pos, circuitState.outputs, originPos);
            } finally {
                isPropagating = false;
            }
            markDirty();
            world.updateListeners(pos, getCachedState(), getCachedState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
        }
    }

    private boolean[] evalGateMulti(String id, String type, boolean[] inputs) {
        if ("tff".equalsIgnoreCase(type)) {
            LogicComponent.TFlipFlop tff = tffInstances.get(id);
            if (tff == null) {
                tff = new LogicComponent.TFlipFlop();
                tffInstances.put(id, tff);
            }
            return tff.evaluateMulti(inputs);
        }
        return switch (type.toLowerCase()) {
            case "and" -> {
                boolean r = true;
                for (boolean b : inputs)
                    r &= b;
                yield new boolean[] { r };
            }
            case "or" -> {
                boolean r = false;
                for (boolean b : inputs)
                    r |= b;
                yield new boolean[] { r };
            }
            case "xor" -> new boolean[] { inputs.length >= 2 && (inputs[0] ^ inputs[1]) };
            case "not" -> new boolean[] { inputs.length > 0 && !inputs[0] };
            case "split", "pass" -> new boolean[] { inputs.length > 0 && inputs[0], inputs.length > 0 && inputs[0] };
            case "1" -> new boolean[] { true };
            case "0" -> new boolean[] { false };
            default -> new boolean[] { inputs.length > 0 && inputs[0] };
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

    public void receiveSignals(boolean[] signals, Direction fromDirection) {
        if (isPropagating || world == null || world.isClient())
            return;

        Direction oldOrigin = this.lastSignalOrigin;
        this.lastSignalOrigin = fromDirection;

        // Re-calculate inputs 1-4 by ORing all neighbors
        boolean[] nextInputs = new boolean[CircuitState.SIGNAL_COUNT];
        System.arraycopy(circuitState.inputs, 0, nextInputs, 0, CircuitState.SIGNAL_COUNT);

        // Reset inter-chip inputs before merging
        for (int i = 1; i < 5; i++)
            nextInputs[i] = false;

        for (Direction dir : Direction.values()) {
            if (world.getBlockEntity(pos.offset(dir)) instanceof LogicChipBlockEntity neighbor) {
                boolean[] nOuts = neighbor.getCircuitState().outputs;
                for (int i = 1; i < 5; i++) {
                    nextInputs[i] |= nOuts[i];
                }
            }
        }

        boolean anyInterChip = false;
        for (int i = 1; i < 5; i++) {
            if (nextInputs[i])
                anyInterChip = true;
        }
        if (!anyInterChip) {
            this.lastSignalOrigin = null;
        }

        boolean changed = false;
        for (int i = 1; i < 5; i++) {
            if (circuitState.inputs[i] != nextInputs[i]) {
                circuitState.inputs[i] = nextInputs[i];
                changed = true;
            }
        }

        boolean originChanged = (oldOrigin != this.lastSignalOrigin);

        if (changed) {
            serverEvaluate();
        } else if (originChanged) {
            markDirty();
            world.updateListeners(pos, getCachedState(), getCachedState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
        }
    }

    // -----------------------------------------------------------------------
    // GUI data serialization (used for screen open data packet)
    // -----------------------------------------------------------------------

    public NbtCompound serializeGuiData() {
        NbtCompound nbt = new NbtCompound();

        NbtList gcList = new NbtList();
        for (GuiComponent gc : guiComponents)
            gcList.add(gc.toNbt());
        nbt.put("GuiComponents", gcList);

        NbtList wList = new NbtList();
        for (Wire w : wires)
            wList.add(w.toNbt());
        nbt.put("Wires", wList);

        byte[] ins = new byte[CircuitState.SIGNAL_COUNT];
        for (int i = 0; i < CircuitState.SIGNAL_COUNT; i++)
            ins[i] = (byte) (circuitState.inputs[i] ? 1 : 0);
        nbt.putByteArray("CircuitInputs", ins);

        // Include TFF states
        NbtCompound tffNbt = new NbtCompound();
        for (Map.Entry<String, LogicComponent.TFlipFlop> e : tffInstances.entrySet()) {
            NbtCompound t = new NbtCompound();
            t.putBoolean("state", e.getValue().state);
            t.putBoolean("lastClk", e.getValue().lastClk);
            tffNbt.put(e.getKey(), t);
        }
        nbt.put("TffStates", tffNbt);

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

        // Re-initialize TFF instances
        tffInstances.clear();
        for (GuiComponent gc : guiComponents) {
            if ("TFF".equalsIgnoreCase(gc.type)) {
                tffInstances.put(gc.id, new LogicComponent.TFlipFlop());
            }
        }

        // Restore states if present
        if (nbt.contains("TffStates")) {
            NbtCompound tffNbt = nbt.getCompound("TffStates");
            for (String id : tffNbt.getKeys()) {
                LogicComponent.TFlipFlop tff = tffInstances.get(id);
                if (tff != null) {
                    tff.state = tffNbt.getCompound(id).getBoolean("state");
                    tff.lastClk = tffNbt.getCompound(id).getBoolean("lastClk");
                }
            }
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
        if (lastSignalOrigin != null) {
            nbt.putInt("LastSignalOrigin", lastSignalOrigin.getId());
        } else {
            nbt.putInt("LastSignalOrigin", -1);
        }
        nbt.putBoolean("PendingRedstoneInput", pendingRedstoneInput);
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
        if (nbt.contains("LastSignalOrigin")) {
            int dirId = nbt.getInt("LastSignalOrigin");
            this.lastSignalOrigin = dirId >= 0 ? Direction.byId(dirId) : null;
        }
        if (nbt.contains("PendingRedstoneInput")) {
            this.pendingRedstoneInput = nbt.getBoolean("PendingRedstoneInput");
        }
    }

    public static void tick(World world, BlockPos pos, BlockState state, LogicChipBlockEntity be) {
        if (world.isClient)
            return;

        // Poll host block redstone every tick to catch removals neighborUpdate might
        // miss
        Direction facing = state.get(com.gl.logicraft.block.LogicChipBlock.FACING);
        BlockPos hostPos = pos.offset(facing.getOpposite());
        Direction chipDir = state.get(com.gl.logicraft.block.LogicChipBlock.FACING);
        int power = 0;
        for (Direction dir : Direction.values()) {
            if (dir == chipDir)
                continue;
            power = Math.max(power, world.getEmittedRedstonePower(hostPos.offset(dir), dir));
        }
        boolean currentInput = power > 0;

        if (currentInput != be.lastInputRedstone) {
            be.lastInputRedstone = currentInput;
            be.pendingRedstoneInput = currentInput;
            world.scheduleBlockTick(pos, state.getBlock(), 1);
        }
    }
}
