# GL_LogiCraft

Compact programmable logic blocks for Minecraft.

## Project Structure

- `src/main/java/com/gl/logicraft/`
    - `GLLogiCraft.java`: Main mod entrypoint.
    - `block/`: Minecraft logic block classes.
    - `blockentity/`: Storage and logic processing for blocks.
    - `circuit/`: Core logic simulation (AND, OR, NOT, etc.).
    - `gui/`: Client and Server GUI handlers for programming blocks.
    - `registry/`: Centralized registration for blocks, items, and screen handlers.

- `src/main/resources/`
    - `fabric.mod.json`: Mod metadata and entrypoint declaration.
    - `gllogicraft.mixins.json`: Mixin configuration.
    - `assets/gllogicraft/`: Graphics, models, and translations.

## Environment
- **Minecraft:** 1.21.4
- **Fabric Loader:** Late stable
- **Java:** 21
- **Gradle:** 8.x

## Building
To build the mod, run:
```bash
./gradlew build
```
The compiled jar will be in `build/libs/`.
