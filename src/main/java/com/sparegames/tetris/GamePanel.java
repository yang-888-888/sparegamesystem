package com.sparegames.tetris;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.JPanel;
import javax.swing.Timer;

/** Renders the board and side panel, and owns the timing / input loop. */
@SuppressWarnings("serial")
public final class GamePanel extends JPanel implements ActionListener {

    private static final long serialVersionUID = 1L;

    private static final int CELL = 30;
    private static final int BOARD_X = 26;
    private static final int BOARD_Y = 26;
    private static final int BOARD_W = Board.WIDTH * CELL;
    private static final int BOARD_H = Board.VISIBLE_HEIGHT * CELL;
    private static final int SIDE_X = BOARD_X + BOARD_W + 28;
    private static final int SIDE_W = 204;
    private static final int PANEL_W = SIDE_X + SIDE_W + 26;
    private static final int PANEL_H = 660;

    private static final int PREVIEW_CELL = 18;

    private static final long FRAME_MS = 16;
    private static final long DAS_DELAY_NS = 160_000_000L;
    private static final long DAS_REPEAT_NS = 45_000_000L;
    private static final long SOFT_DROP_NS = 35_000_000L;

    private static final Color BG_TOP = new Color(0xFF14142A);
    private static final Color BG_BOTTOM = new Color(0xFF06060C);
    private static final Color BOARD_BG = new Color(0xFF05050A);
    private static final Color GRID = new Color(0xFF14141E);
    private static final Color BORDER = new Color(0xFF26263A);
    private static final Color PANEL_BG = new Color(0xFF12121C);
    private static final Color LABEL = new Color(0xFF8C8CA6);
    private static final Color TEXT = new Color(0xFFF0F5FF);
    private static final Color HINT = new Color(0xFFB9B9CC);
    private static final Color ACCENT = new Color(0xFF35E0FF);

    private static final Font LABEL_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 12);
    private static final Font VALUE_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 20);
    private static final Font HINT_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    private static final Font TITLE_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 28);
    private static final Font OVERLAY_SCORE_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 18);

    private final Board board = new Board();
    private final Set<Integer> pressed = new HashSet<>();
    private final Timer timer;

    private boolean paused;
    private int repeatDir;
    private long nextRepeatAtNs;
    private long lastTickNs;
    private long gravityAccNs;
    private long softDropAccNs;

    public GamePanel() {
        setPreferredSize(new Dimension(PANEL_W, PANEL_H));
        setBackground(BG_BOTTOM);
        setFocusable(true);
        setOpaque(true);

        Handler handler = new Handler();
        addKeyListener(handler);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
            }
        });
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                pressed.clear();
                repeatDir = 0;
            }
        });

        timer = new Timer((int) FRAME_MS, this);
        timer.setCoalesce(true);
        lastTickNs = System.nanoTime();
        timer.start();
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        long now = System.nanoTime();
        long delta = Math.min(now - lastTickNs, 50_000_000L);
        lastTickNs = now;

        if (!paused && !board.isGameOver()) {
            updateHorizontalRepeat(now);
            gravityAccNs += delta;
            if (gravityAccNs >= gravityIntervalNs()) {
                gravityAccNs = 0;
                board.gravity();
            }
            if (isDown(KeyEvent.VK_DOWN)) {
                softDropAccNs += delta;
                while (softDropAccNs >= SOFT_DROP_NS && !board.isGameOver()) {
                    softDropAccNs -= SOFT_DROP_NS;
                    board.softDrop();
                }
            } else {
                softDropAccNs = 0;
            }
        }
        repaint();
    }

    // ------------------------------------------------------------- input

    private final class Handler extends KeyAdapter {

        @Override
        public void keyPressed(KeyEvent event) {
            int code = event.getKeyCode();
            if (!pressed.add(code)) {
                return; // OS auto-repeat, handled by our own DAS timer instead
            }
            switch (code) {
                case KeyEvent.VK_LEFT -> {
                    if (canPlay()) {
                        startHorizontal(-1);
                    }
                }
                case KeyEvent.VK_RIGHT -> {
                    if (canPlay()) {
                        startHorizontal(1);
                    }
                }
                case KeyEvent.VK_DOWN -> {
                    if (canPlay()) {
                        board.softDrop();
                    }
                }
                case KeyEvent.VK_SPACE -> {
                    if (canPlay()) {
                        board.hardDrop();
                        resetDropTimers();
                    }
                }
                case KeyEvent.VK_UP, KeyEvent.VK_X -> {
                    if (canPlay()) {
                        board.rotate(true);
                    }
                }
                case KeyEvent.VK_Z -> {
                    if (canPlay()) {
                        board.rotate(false);
                    }
                }
                case KeyEvent.VK_C, KeyEvent.VK_SHIFT -> {
                    if (canPlay()) {
                        board.hold();
                        resetDropTimers();
                    }
                }
                case KeyEvent.VK_P -> togglePause();
                case KeyEvent.VK_R -> restart();
                default -> {
                }
            }
        }

        @Override
        public void keyReleased(KeyEvent event) {
            pressed.remove(event.getKeyCode());
        }
    }

    private boolean canPlay() {
        return !paused && !board.isGameOver();
    }

    private boolean isDown(int keyCode) {
        return pressed.contains(keyCode);
    }

    private void startHorizontal(int dir) {
        board.move(dir, 0);
        repeatDir = dir;
        nextRepeatAtNs = System.nanoTime() + DAS_DELAY_NS;
    }

    private void updateHorizontalRepeat(long now) {
        boolean left = isDown(KeyEvent.VK_LEFT);
        boolean right = isDown(KeyEvent.VK_RIGHT);
        int dir = 0;
        if (repeatDir < 0 && left) {
            dir = -1;
        } else if (repeatDir > 0 && right) {
            dir = 1;
        } else if (left) {
            dir = -1;
        } else if (right) {
            dir = 1;
        }

        if (dir == 0) {
            repeatDir = 0;
            return;
        }
        if (dir != repeatDir) {
            repeatDir = dir;
            nextRepeatAtNs = now + DAS_DELAY_NS;
            return;
        }
        if (now >= nextRepeatAtNs) {
            board.move(dir, 0);
            nextRepeatAtNs = now + DAS_REPEAT_NS;
        }
    }

    private void togglePause() {
        if (board.isGameOver()) {
            return;
        }
        paused = !paused;
        resetDropTimers();
        lastTickNs = System.nanoTime();
        if (paused) {
            pressed.clear();
        }
    }

    private void restart() {
        board.reset();
        paused = false;
        pressed.clear();
        repeatDir = 0;
        resetDropTimers();
        lastTickNs = System.nanoTime();
    }

    private void resetDropTimers() {
        gravityAccNs = 0;
        softDropAccNs = 0;
    }

    private long gravityIntervalNs() {
        long millis = Math.max(60L, 800L - (board.getLevel() - 1) * 60L);
        return millis * 1_000_000L;
    }

    // ------------------------------------------------------------- painting

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setPaint(new GradientPaint(0, 0, BG_TOP, 0, getHeight(), BG_BOTTOM));
            g.fillRect(0, 0, getWidth(), getHeight());

            drawBoard(g);
            drawSidePanel(g);

            if (paused) {
                drawPauseOverlay(g);
            }
            if (board.isGameOver()) {
                drawGameOverOverlay(g);
            }
        } finally {
            g.dispose();
        }
    }

    private void drawBoard(Graphics2D g) {
        for (int i = 3; i >= 1; i--) {
            g.setColor(new Color(53, 224, 255, 12));
            g.fillRoundRect(BOARD_X - i * 2, BOARD_Y - i * 2,
                    BOARD_W + i * 4, BOARD_H + i * 4, 12, 12);
        }

        g.setColor(BOARD_BG);
        g.fillRoundRect(BOARD_X, BOARD_Y, BOARD_W, BOARD_H, 8, 8);

        g.setColor(GRID);
        for (int column = 1; column < Board.WIDTH; column++) {
            int x = BOARD_X + column * CELL;
            g.drawLine(x, BOARD_Y, x, BOARD_Y + BOARD_H);
        }
        for (int row = 1; row < Board.VISIBLE_HEIGHT; row++) {
            int y = BOARD_Y + row * CELL;
            g.drawLine(BOARD_X, y, BOARD_X + BOARD_W, y);
        }

        for (int row = Board.HIDDEN_ROWS; row < Board.HEIGHT; row++) {
            for (int column = 0; column < Board.WIDTH; column++) {
                int color = board.cellColor(row, column);
                if (color != 0) {
                    drawBlock(g, cellX(column), cellY(row), CELL, color, false);
                }
            }
        }

        if (!board.isGameOver()) {
            Piece ghost = board.ghostPiece();
            if (ghost != null) {
                drawPiece(g, ghost, true);
            }
            drawPiece(g, board.getCurrent(), false);
        }

        g.setColor(BORDER);
        g.setStroke(new BasicStroke(1.4f));
        g.drawRoundRect(BOARD_X, BOARD_Y, BOARD_W, BOARD_H, 8, 8);
    }

    private void drawPiece(Graphics2D g, Piece piece, boolean ghost) {
        if (piece == null) {
            return;
        }
        for (Piece.Cell cell : piece.cells()) {
            if (cell.y() < Board.HIDDEN_ROWS) {
                continue;
            }
            drawBlock(g, cellX(cell.x()), cellY(cell.y()), CELL, piece.type.rgb, ghost);
        }
    }

    private int cellX(int column) {
        return BOARD_X + column * CELL;
    }

    private int cellY(int row) {
        return BOARD_Y + (row - Board.HIDDEN_ROWS) * CELL;
    }

    private void drawBlock(Graphics2D g, int x, int y, int size, int rgb, boolean ghost) {
        Color color = new Color(rgb, true);
        if (ghost) {
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 38));
            g.fillRoundRect(x + 2, y + 2, size - 4, size - 4, 6, 6);
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 185));
            g.setStroke(new BasicStroke(1.5f));
            g.drawRoundRect(x + 2, y + 2, size - 4, size - 4, 6, 6);
            return;
        }
        g.setColor(color);
        g.fillRoundRect(x + 1, y + 1, size - 2, size - 2, 7, 7);
        g.setColor(new Color(255, 255, 255, 70));
        g.fillRoundRect(x + 4, y + 4, size - 8, Math.max(3, size / 6), 3, 3);
        g.setColor(new Color(0, 0, 0, 120));
        g.setStroke(new BasicStroke(1f));
        g.drawRoundRect(x + 1, y + 1, size - 2, size - 2, 7, 7);
    }

    private void drawSidePanel(Graphics2D g) {
        g.setFont(LABEL_FONT);
        g.setColor(LABEL);
        g.drawString("HOLD", SIDE_X, 36);

        drawPreviewBox(g, SIDE_X, 46, SIDE_W, 78, board.getHoldType());

        g.setFont(LABEL_FONT);
        g.setColor(LABEL);
        g.drawString("NEXT", SIDE_X, 156);

        List<Tetromino> next = board.nextPieces();
        for (int i = 0; i < next.size(); i++) {
            drawPreviewBox(g, SIDE_X, 166 + i * 88, SIDE_W, 78, next.get(i));
        }

        drawStat(g, "SCORE", String.valueOf(board.getScore()), 458);
        drawStat(g, "LEVEL", String.valueOf(board.getLevel()), 490);
        drawStat(g, "LINES", String.valueOf(board.getLines()), 522);

        g.setFont(LABEL_FONT);
        g.setColor(LABEL);
        g.drawString("CONTROLS", SIDE_X, 560);

        g.setFont(HINT_FONT);
        g.setColor(HINT);
        String[] controls = {
                "\u2190\u2192 move    \u2193 soft drop",
                "SPACE hard drop",
                "\u2191/X rotate    Z back",
                "C hold   P pause   R restart"};
        for (int i = 0; i < controls.length; i++) {
            g.drawString(controls[i], SIDE_X, 582 + i * 18);
        }
    }

    private void drawPreviewBox(Graphics2D g, int x, int y, int width, int height, Tetromino type) {
        g.setColor(PANEL_BG);
        g.fillRoundRect(x, y, width, height, 12, 12);
        g.setColor(BORDER);
        g.setStroke(new BasicStroke(1f));
        g.drawRoundRect(x, y, width, height, 12, 12);

        if (type == null) {
            return;
        }

        int[][] matrix = type.matrix(0);
        int minRow = matrix.length;
        int maxRow = -1;
        int minColumn = matrix.length;
        int maxColumn = -1;
        for (int row = 0; row < matrix.length; row++) {
            for (int column = 0; column < matrix.length; column++) {
                if (matrix[row][column] != 0) {
                    minRow = Math.min(minRow, row);
                    maxRow = Math.max(maxRow, row);
                    minColumn = Math.min(minColumn, column);
                    maxColumn = Math.max(maxColumn, column);
                }
            }
        }

        int pieceWidth = (maxColumn - minColumn + 1) * PREVIEW_CELL;
        int pieceHeight = (maxRow - minRow + 1) * PREVIEW_CELL;
        int originX = x + (width - pieceWidth) / 2;
        int originY = y + (height - pieceHeight) / 2;

        for (int row = minRow; row <= maxRow; row++) {
            for (int column = minColumn; column <= maxColumn; column++) {
                if (matrix[row][column] != 0) {
                    drawBlock(g, originX + (column - minColumn) * PREVIEW_CELL,
                            originY + (row - minRow) * PREVIEW_CELL,
                            PREVIEW_CELL, type.rgb, false);
                }
            }
        }
    }

    private void drawStat(Graphics2D g, String label, String value, int baseline) {
        g.setFont(LABEL_FONT);
        g.setColor(LABEL);
        g.drawString(label, SIDE_X, baseline);

        g.setFont(VALUE_FONT);
        g.setColor(TEXT);
        FontMetrics metrics = g.getFontMetrics();
        g.drawString(value, SIDE_X + SIDE_W - metrics.stringWidth(value), baseline);
    }

    private void drawPauseOverlay(Graphics2D g) {
        int centerX = BOARD_X + BOARD_W / 2;
        int centerY = BOARD_Y + BOARD_H / 2;

        g.setColor(new Color(3, 3, 8, 185));
        g.fillRoundRect(BOARD_X, BOARD_Y, BOARD_W, BOARD_H, 8, 8);

        g.setFont(TITLE_FONT);
        g.setColor(TEXT);
        drawCentered(g, "PAUSED", centerX, centerY - 2);

        g.setFont(HINT_FONT);
        g.setColor(LABEL);
        drawCentered(g, "Press P to resume", centerX, centerY + 28);
    }

    private void drawGameOverOverlay(Graphics2D g) {
        int centerX = BOARD_X + BOARD_W / 2;
        int centerY = BOARD_Y + BOARD_H / 2;

        g.setColor(new Color(3, 3, 8, 205));
        g.fillRoundRect(BOARD_X, BOARD_Y, BOARD_W, BOARD_H, 8, 8);

        g.setFont(TITLE_FONT);
        g.setColor(new Color(0xFFFF6B6B));
        drawCentered(g, "GAME OVER", centerX, centerY - 22);

        g.setFont(OVERLAY_SCORE_FONT);
        g.setColor(TEXT);
        drawCentered(g, "Score  " + board.getScore(), centerX, centerY + 14);

        g.setFont(HINT_FONT);
        g.setColor(LABEL);
        drawCentered(g, "Press R to restart", centerX, centerY + 44);
    }

    private void drawCentered(Graphics2D g, String text, int centerX, int baselineY) {
        FontMetrics metrics = g.getFontMetrics();
        g.drawString(text, centerX - metrics.stringWidth(text) / 2, baselineY);
    }
}
