package com.sparegames.tetris;

/**
 * The seven tetromino shapes with their four pre-computed rotation states.
 * All matrices are square and rotated around their own centre, which keeps
 * wall-kick offsets simple.
 */
public enum Tetromino {

    I(0xFF00E5FF, new int[][]{
            {0, 0, 0, 0},
            {1, 1, 1, 1},
            {0, 0, 0, 0},
            {0, 0, 0, 0}}),

    O(0xFFFFD400, new int[][]{
            {1, 1},
            {1, 1}}),

    T(0xFF9B5DE5, new int[][]{
            {0, 1, 0},
            {1, 1, 1},
            {0, 0, 0}}),

    S(0xFF4CD964, new int[][]{
            {0, 1, 1},
            {1, 1, 0},
            {0, 0, 0}}),

    Z(0xFFFF4D4D, new int[][]{
            {1, 1, 0},
            {0, 1, 1},
            {0, 0, 0}}),

    J(0xFF4D7CFF, new int[][]{
            {1, 0, 0},
            {1, 1, 1},
            {0, 0, 0}}),

    L(0xFFFF9F0A, new int[][]{
            {0, 0, 1},
            {1, 1, 1},
            {0, 0, 0}});

    /** Packed ARGB colour used when this piece is rendered. */
    public final int rgb;

    private final int[][][] rotations = new int[4][][];

    Tetromino(int rgb, int[][] shape) {
        this.rgb = rgb;
        rotations[0] = shape;
        for (int rotation = 1; rotation < rotations.length; rotation++) {
            rotations[rotation] = rotateClockwise(rotations[rotation - 1]);
        }
    }

    /** Side length of the square matrix that holds this piece. */
    public int size() {
        return rotations[0].length;
    }

    /** Shape matrix for the given rotation state (0 = spawn, clockwise positive). */
    public int[][] matrix(int rotation) {
        return rotations[(rotation % 4 + 4) % 4];
    }

    private static int[][] rotateClockwise(int[][] matrix) {
        int size = matrix.length;
        int[][] rotated = new int[size][size];
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                rotated[column][size - 1 - row] = matrix[row][column];
            }
        }
        return rotated;
    }
}
