package com.sparegames.tetris;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JFrame;

/** Top-level window hosting the game panel. */
public final class GameFrame extends JFrame {

    private static final long serialVersionUID = 1L;

    public GameFrame() {
        super("Tetris");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setContentPane(new GamePanel());
        pack();
        setResizable(false);
        setLocationRelativeTo(null);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent event) {
                getContentPane().requestFocusInWindow();
            }
        });
    }
}
