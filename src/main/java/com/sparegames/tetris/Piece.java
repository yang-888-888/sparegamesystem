package com.sparegames.tetris;

import java.util.ArrayList;
import java.util.List;

/** An immutable position and rotation of a tetromino on the board. */
public final class Piece {

    public final Tetromino type;
    public final int rotation;
    public final int x;
    public final int y;

    public Piece(Tetromino type, int rotation, int x, int y) {
        this.type = type;
        this.rotation = (rotation % 4 + 4) % 4;
        this.x = x;
        this.y = y;
    }

    public Piece moved(int dx, int dy) {
        return new Piece(type, rotation, x + dx, y + dy);
    }

    public Piece rotated(int newRotation) {
        return new Piece(type, newRotation, x, y);
    }

    /** Absolute board coordinates of every filled cell of this piece. */
    public List<Cell> cells() {
        List<Cell> cells = new ArrayList<>(4);
        int[][] matrix = type.matrix(rotation);
        for (int row = 0; row < matrix.length; row++) {
            for (int column = 0; column < matrix.length; column++) {
                if (matrix[row][column] != 0) {
                    cells.add(new Cell(x + column, y + row));
                }
            }
        }
        return cells;
    }

    /** A single occupied board cell. */
    public record Cell(int x, int y) {
    }
}
