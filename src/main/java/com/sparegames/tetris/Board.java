package com.sparegames.tetris;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * Pure game state: grid, active piece, spawn queue, scoring and line clears.
 * Contains no Swing or AWT types so it can be exercised headlessly.
 */
public final class Board {

    public static final int WIDTH = 10;
    public static final int VISIBLE_HEIGHT = 20;
    /** Rows above the visible playfield where pieces spawn. */
    public static final int HIDDEN_ROWS = 2;
    public static final int HEIGHT = VISIBLE_HEIGHT + HIDDEN_ROWS;

    private static final int NEXT_QUEUE_SIZE = 4;
    private static final int MAX_LEVEL = 20;

    /** Wall kick offsets tried in order when a rotation is blocked. */
    private static final int[][] KICKS = {
            {0, 0}, {-1, 0}, {1, 0}, {0, -1}, {0, 1}, {-2, 0}, {2, 0}};
    private static final int[][] KICKS_I = {
            {0, 0}, {-2, 0}, {2, 0}, {-1, 0}, {1, 0}, {0, -1}, {0, 1}};

    private final int[][] grid = new int[HEIGHT][WIDTH];
    private final Random random;
    private final Deque<Tetromino> bag = new ArrayDeque<>();
    private final Deque<Tetromino> queue = new ArrayDeque<>();

    private Piece current;
    private Tetromino holdType;
    private boolean holdUsed;
    private boolean gameOver;
    private int score;
    private int lines;
    private int level;

    /** Full rows waiting for the clear animation to finish before being removed. */
    private List<Integer> pendingClearRows = Collections.emptyList();

    public Board() {
        this(new Random());
    }

    public Board(Random random) {
        this.random = random;
        reset();
    }

    /** Clears the board and starts a new game. */
    public void reset() {
        for (int[] row : grid) {
            Arrays.fill(row, 0);
        }
        bag.clear();
        queue.clear();
        current = null;
        holdType = null;
        holdUsed = false;
        gameOver = false;
        score = 0;
        lines = 0;
        level = 1;
        pendingClearRows = Collections.emptyList();
        spawnNext();
    }

    // ------------------------------------------------------------- queries

    public Piece getCurrent() {
        return current;
    }

    public Tetromino getHoldType() {
        return holdType;
    }

    public boolean isHoldUsed() {
        return holdUsed;
    }

    public boolean isGameOver() {
        return gameOver;
    }

    public int getScore() {
        return score;
    }

    public int getLevel() {
        return level;
    }

    public int getLines() {
        return lines;
    }

    /** True while full rows are being animated out before they collapse. */
    public boolean isClearing() {
        return !pendingClearRows.isEmpty();
    }

    /** Rows that are currently playing the clear animation. */
    public List<Integer> clearingRows() {
        return pendingClearRows;
    }

    public boolean isClearingRow(int row) {
        return pendingClearRows.contains(row);
    }

    /** ARGB colour of the locked block at the given row/column, or 0 when empty. */
    public int cellColor(int row, int column) {
        if (row < 0 || row >= HEIGHT || column < 0 || column >= WIDTH) {
            return 0;
        }
        return grid[row][column];
    }

    /** Up to three upcoming pieces, in the order they will spawn. */
    public List<Tetromino> nextPieces() {
        List<Tetromino> next = new ArrayList<>(3);
        int count = 0;
        for (Tetromino type : queue) {
            if (count++ >= 3) {
                break;
            }
            next.add(type);
        }
        return next;
    }

    /** The current piece dropped as far as it can go without locking. */
    public Piece ghostPiece() {
        if (current == null || isClearing()) {
            return null;
        }
        Piece ghost = current;
        while (canPlace(ghost.moved(0, 1))) {
            ghost = ghost.moved(0, 1);
        }
        return ghost;
    }

    // ------------------------------------------------------------- actions

    /** Moves the current piece, returning false when blocked. */
    public boolean move(int dx, int dy) {
        if (gameOver || current == null || isClearing()) {
            return false;
        }
        Piece moved = current.moved(dx, dy);
        if (!canPlace(moved)) {
            return false;
        }
        current = moved;
        return true;
    }

    /**
     * Rotates the current piece, trying wall kicks when the plain rotation is
     * blocked.
     */
    public boolean rotate(boolean clockwise) {
        if (gameOver || current == null || isClearing()) {
            return false;
        }
        Piece rotated = current.rotated(current.rotation + (clockwise ? 1 : -1));
        for (int[] kick : kicksFor(current.type)) {
            Piece candidate = rotated.moved(kick[0], kick[1]);
            if (canPlace(candidate)) {
                current = candidate;
                return true;
            }
        }
        return false;
    }

    /** One gravity step: falls by one row, or locks when the piece has landed. */
    public void gravity() {
        if (gameOver || current == null || isClearing()) {
            return;
        }
        if (!move(0, 1)) {
            lock();
        }
    }

    /** Player-initiated fast drop. Awards one point per row fallen. */
    public boolean softDrop() {
        if (gameOver || current == null || isClearing()) {
            return false;
        }
        if (move(0, 1)) {
            score += 1;
            return true;
        }
        lock();
        return false;
    }

    /** Drops the piece instantly and locks it. Awards two points per row dropped. */
    public int hardDrop() {
        if (gameOver || current == null || isClearing()) {
            return 0;
        }
        int distance = 0;
        while (move(0, 1)) {
            distance++;
        }
        score += distance * 2;
        lock();
        return distance;
    }

    /** Swaps the current piece with the hold slot; allowed once per piece. */
    public boolean hold() {
        if (gameOver || current == null || holdUsed || isClearing()) {
            return false;
        }
        Tetromino swapped = holdType;
        holdType = current.type;
        if (swapped == null) {
            spawnNext();
        } else {
            spawnFrom(swapped);
        }
        holdUsed = true;
        return true;
    }

    // ------------------------------------------------------------- internals

    private boolean canPlace(Piece piece) {
        for (Piece.Cell cell : piece.cells()) {
            if (cell.x() < 0 || cell.x() >= WIDTH || cell.y() < 0 || cell.y() >= HEIGHT) {
                return false;
            }
            if (grid[cell.y()][cell.x()] != 0) {
                return false;
            }
        }
        return true;
    }

    private void spawnNext() {
        fillQueue();
        spawnFrom(queue.pollFirst());
        holdUsed = false;
    }

    private void spawnFrom(Tetromino type) {
        current = new Piece(type, 0, (WIDTH - type.size()) / 2, 0);
        if (!canPlace(current)) {
            gameOver = true;
        }
    }

    private void fillQueue() {
        while (queue.size() < NEXT_QUEUE_SIZE) {
            queue.addLast(nextFromBag());
        }
    }

    /** Seven-bag randomizer: every piece appears once before any repeats. */
    private Tetromino nextFromBag() {
        if (bag.isEmpty()) {
            List<Tetromino> seven = new ArrayList<>(List.of(Tetromino.values()));
            Collections.shuffle(seven, random);
            bag.addAll(seven);
        }
        return bag.removeFirst();
    }

    private static int[][] kicksFor(Tetromino type) {
        return type == Tetromino.I ? KICKS_I : KICKS;
    }

    private void lock() {
        if (current == null || isClearing()) {
            return;
        }
        for (Piece.Cell cell : current.cells()) {
            if (cell.y() >= 0 && cell.y() < HEIGHT && cell.x() >= 0 && cell.x() < WIDTH) {
                grid[cell.y()][cell.x()] = current.type.rgb;
            }
        }
        current = null;

        List<Integer> fullRows = findFullRows();
        if (!fullRows.isEmpty()) {
            // Keep the completed rows on the grid so the renderer can animate
            // them away; the actual collapse happens in finishClear().
            pendingClearRows = fullRows;
        } else if (!gameOver) {
            spawnNext();
        }
    }

    /**
     * Called once the clear animation has played: removes the completed rows,
     * awards the score and spawns the next piece.
     */
    public void finishClear() {
        if (pendingClearRows.isEmpty()) {
            return;
        }
        int cleared = pendingClearRows.size();
        pendingClearRows = Collections.emptyList();
        collapseRows();
        addScore(cleared);
        if (!gameOver) {
            spawnNext();
        }
    }

    private List<Integer> findFullRows() {
        List<Integer> rows = new ArrayList<>(4);
        for (int row = HEIGHT - 1; row >= 0; row--) {
            if (isFull(row)) {
                rows.add(row);
            }
        }
        return rows;
    }

    /** Removes completed rows, shifting everything above them down. */
    private void collapseRows() {
        int writeRow = HEIGHT - 1;
        for (int readRow = HEIGHT - 1; readRow >= 0; readRow--) {
            if (isFull(readRow)) {
                continue;
            }
            if (writeRow != readRow) {
                System.arraycopy(grid[readRow], 0, grid[writeRow], 0, WIDTH);
            }
            writeRow--;
        }
        for (int row = writeRow; row >= 0; row--) {
            Arrays.fill(grid[row], 0);
        }
    }

    private boolean isFull(int row) {
        for (int column = 0; column < WIDTH; column++) {
            if (grid[row][column] == 0) {
                return false;
            }
        }
        return true;
    }

    private void addScore(int cleared) {
        int base = switch (cleared) {
            case 1 -> 100;
            case 2 -> 300;
            case 3 -> 500;
            case 4 -> 800;
            default -> 0;
        };
        score += base * level;
        lines += cleared;
        level = Math.min(MAX_LEVEL, lines / 10 + 1);
    }
}
