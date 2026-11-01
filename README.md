# sparegamesystem

A small collection of games written in plain Java.

## Tetris

A complete Tetris implementation using Java Swing (JDK only, no third-party
dependencies). Features:

- Seven-bag randomizer and a three-piece next queue
- SRS-style wall kicks for rotations
- Ghost piece, hard drop and soft drop
- Hold slot (once per piece)
- Score, line count and level progression (gravity speeds up every 10 lines)
- Start menu (keyboard or mouse) with a how-to-play screen
- Animated line clears: completed rows flash, shrink and fade before collapsing
- Pause and restart

### Run

Requires JDK 17 or newer.

With Maven:

```bash
mvn package
java -jar target/tetris-1.0.0.jar
```

With the JDK only:

```bash
javac -d out $(find src/main/java -name "*.java")
java -cp out com.sparegames.tetris.Tetris
```

### Controls

| Key | Action |
| --- | --- |
| Up / Down (menu) | Move menu selection |
| Enter | Start / confirm (new game after game over) |
| Left / Right | Move |
| Down | Soft drop (hold to repeat) |
| Space | Hard drop |
| Up / X | Rotate clockwise |
| Z | Rotate counter-clockwise |
| C / Shift | Hold piece |
| P | Pause / resume |
| R | Restart |
| Esc | Return to main menu |
