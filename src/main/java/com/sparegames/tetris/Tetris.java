package com.sparegames.tetris;

import javax.swing.SwingUtilities;

/** Application entry point. */
public final class Tetris {

    private Tetris() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            GameFrame frame = new GameFrame();
            frame.setVisible(true);
        });
    }
}
