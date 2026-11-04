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
import java.awt.geom.RoundRectangle2D;
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

    private static final Font MENU_TITLE_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 54);
    private static final Font MENU_SUBTITLE_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 14);
    private static final Font MENU_ITEM_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 17);
    private static final Font MENU_ITEM_FOCUS_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 18);
    private static final Font MENU_HEADER_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 22);
    private static final Font MENU_TEXT_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 14);

    private static final int MENU_ITEM_W = 300;
    private static final int MENU_ITEM_H = 52;
    private static final int MENU_ITEM_GAP = 14;
    private static final int MENU_ITEM_TOP = 300;

    private static final String[] MENU_ITEMS = {"开始游戏", "操作说明", "退出游戏"};
    private static final String MENU_TITLE = "俄罗斯方块";

    /** How long the line-clear animation runs before the rows collapse. */
    private static final long CLEAR_ANIM_NS = 340_000_000L;

    private final Board board = new Board();
    private final Set<Integer> pressed = new HashSet<>();
    private final Timer timer;

    private boolean menu = true;
    private boolean menuHelp;
    private int menuSelection;
    private long menuAnimNs;
    private long clearElapsedNs;
    private boolean clearingActive;

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
                if (menu) {
                    handleMenuClick(e);
                }
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

        if (menu) {
            menuAnimNs += delta;
            repaint();
            return;
        }

        if (board.isClearing()) {
            // Freeze the playfield while the completed rows play their animation.
            if (!clearingActive) {
                clearingActive = true;
                clearElapsedNs = 0;
                resetDropTimers();
            }
            clearElapsedNs += delta;
            if (clearElapsedNs >= CLEAR_ANIM_NS) {
                board.finishClear();
                clearingActive = false;
                clearElapsedNs = 0;
                resetDropTimers();
            }
            repaint();
            return;
        }
        clearingActive = false;

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
            if (menu) {
                handleMenuKey(code);
                return;
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
                case KeyEvent.VK_ENTER -> {
                    if (board.isGameOver()) {
                        startGame();
                    }
                }
                case KeyEvent.VK_P -> togglePause();
                case KeyEvent.VK_R -> restart();
                case KeyEvent.VK_ESCAPE -> returnToMenu();
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
        return !menu && !paused && !board.isGameOver() && !board.isClearing();
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
        if (board.isGameOver() || board.isClearing()) {
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
        clearingActive = false;
        clearElapsedNs = 0;
        resetDropTimers();
        lastTickNs = System.nanoTime();
    }

    // ------------------------------------------------------------- menu

    private void startGame() {
        board.reset();
        menu = false;
        menuHelp = false;
        paused = false;
        pressed.clear();
        repeatDir = 0;
        clearingActive = false;
        clearElapsedNs = 0;
        resetDropTimers();
        lastTickNs = System.nanoTime();
        requestFocusInWindow();
    }

    private void returnToMenu() {
        board.reset();
        menu = true;
        menuHelp = false;
        menuSelection = 0;
        paused = false;
        pressed.clear();
        repeatDir = 0;
        clearingActive = false;
        clearElapsedNs = 0;
        resetDropTimers();
        lastTickNs = System.nanoTime();
    }

    private void handleMenuKey(int code) {
        if (menuHelp) {
            if (code == KeyEvent.VK_ENTER || code == KeyEvent.VK_ESCAPE || code == KeyEvent.VK_SPACE) {
                menuHelp = false;
            }
            return;
        }
        switch (code) {
            case KeyEvent.VK_UP, KeyEvent.VK_W ->
                    menuSelection = (menuSelection + MENU_ITEMS.length - 1) % MENU_ITEMS.length;
            case KeyEvent.VK_DOWN, KeyEvent.VK_S ->
                    menuSelection = (menuSelection + 1) % MENU_ITEMS.length;
            case KeyEvent.VK_ENTER, KeyEvent.VK_SPACE -> activateMenuItem();
            case KeyEvent.VK_ESCAPE -> {
            }
            default -> {
            }
        }
    }

    private void activateMenuItem() {
        switch (menuSelection) {
            case 0 -> startGame();
            case 1 -> menuHelp = true;
            case 2 -> System.exit(0);
            default -> {
            }
        }
    }

    private void handleMenuClick(MouseEvent event) {
        if (menuHelp) {
            menuHelp = false;
            return;
        }
        for (int i = 0; i < MENU_ITEMS.length; i++) {
            int itemY = menuItemY(i);
            int itemX = menuItemX();
            if (event.getX() >= itemX && event.getX() < itemX + MENU_ITEM_W
                    && event.getY() >= itemY && event.getY() < itemY + MENU_ITEM_H) {
                menuSelection = i;
                activateMenuItem();
                return;
            }
        }
    }

    private int menuItemY(int index) {
        return MENU_ITEM_TOP + index * (MENU_ITEM_H + MENU_ITEM_GAP);
    }

    private int menuItemX() {
        return (getWidth() - MENU_ITEM_W) / 2;
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

            if (menu) {
                drawMenu(g);
            } else {
                if (paused && !board.isGameOver()) {
                    drawPauseOverlay(g);
                }
                if (board.isGameOver()) {
                    drawGameOverOverlay(g);
                }
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
            if (board.isClearingRow(row)) {
                continue; // drawn by the clear animation instead
            }
            for (int column = 0; column < Board.WIDTH; column++) {
                int color = board.cellColor(row, column);
                if (color != 0) {
                    drawBlock(g, cellX(column), cellY(row), CELL, color, false);
                }
            }
        }

        if (board.isClearing()) {
            drawClearEffect(g, Math.min(1f, clearElapsedNs / (float) CLEAR_ANIM_NS));
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

    /**
     * Line-clear animation: the blocks first flash white, then shrink towards
     * their centre and fade out while a bright bar sweeps across the row.
     */
    private void drawClearEffect(Graphics2D g, float progress) {
        List<Integer> rows = board.clearingRows();
        if (rows.isEmpty()) {
            return;
        }

        float flash = Math.min(1f, progress / 0.42f);
        float fade = progress <= 0.42f ? 0f : (progress - 0.42f) / 0.58f;
        float scale = 1f - fade * 0.92f;
        int alpha = Math.round(255 * (1f - fade));

        for (int row : rows) {
            int top = cellY(row);
            if (top < BOARD_Y || top + CELL > BOARD_Y + BOARD_H) {
                continue;
            }

            for (int column = 0; column < Board.WIDTH; column++) {
                int rgb = board.cellColor(row, column);
                if (rgb != 0) {
                    drawClearingBlock(g, cellX(column), top, rgb, flash, scale, alpha);
                }
            }

            int centerY = top + CELL / 2;
            int beamHeight = Math.max(3, Math.round(CELL * 0.22f * (1f - fade * 0.6f)));
            g.setColor(new Color(255, 255, 255, Math.round(210 * (1f - fade))));
            g.fillRoundRect(BOARD_X + 1, centerY - beamHeight / 2,
                    BOARD_W - 2, beamHeight, beamHeight, beamHeight);
        }
    }

    private void drawClearingBlock(Graphics2D g, int x, int y, int rgb,
            float flash, float scale, int alpha) {
        float size = CELL * scale;
        if (size < 2.5f || alpha <= 0) {
            return;
        }
        float bx = x + (CELL - size) / 2f;
        float by = y + (CELL - size) / 2f;
        float arc = Math.max(2f, size / 4f);
        float inset = Math.min(1f, size / 6f);

        Color base = new Color(rgb, true);
        g.setColor(new Color(
                Math.round(base.getRed() + (255 - base.getRed()) * flash * 0.85f),
                Math.round(base.getGreen() + (255 - base.getGreen()) * flash * 0.85f),
                Math.round(base.getBlue() + (255 - base.getBlue()) * flash * 0.85f),
                alpha));
        g.fill(new RoundRectangle2D.Float(bx + inset, by + inset,
                size - inset * 2, size - inset * 2, arc, arc));

        if (flash > 0.02f) {
            g.setColor(new Color(255, 255, 255, Math.round(alpha * flash * 0.55f)));
            g.fill(new RoundRectangle2D.Float(bx + inset, by + inset,
                    size - inset * 2, size - inset * 2, arc, arc));
        }
    }

    private void drawSidePanel(Graphics2D g) {
        g.setFont(LABEL_FONT);
        g.setColor(LABEL);
        g.drawString("暂存", SIDE_X, 36);

        drawPreviewBox(g, SIDE_X, 46, SIDE_W, 78, board.getHoldType());

        g.setFont(LABEL_FONT);
        g.setColor(LABEL);
        g.drawString("下一个", SIDE_X, 156);

        List<Tetromino> next = board.nextPieces();
        for (int i = 0; i < next.size(); i++) {
            drawPreviewBox(g, SIDE_X, 166 + i * 88, SIDE_W, 78, next.get(i));
        }

        drawStat(g, "得分", String.valueOf(board.getScore()), 458);
        drawStat(g, "等级", String.valueOf(board.getLevel()), 490);
        drawStat(g, "消除行数", String.valueOf(board.getLines()), 522);

        g.setFont(LABEL_FONT);
        g.setColor(LABEL);
        g.drawString("操作", SIDE_X, 560);

        g.setFont(HINT_FONT);
        g.setColor(HINT);
        String[] controls = {
                "\u2190\u2192 移动    \u2193 软降",
                "SPACE 硬降",
                "\u2191/X 旋转    Z 反向旋转",
                "C 暂存  P 暂停  R 重开"};
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

    // ------------------------------------------------------------- menu

    private void drawMenu(Graphics2D g) {
        int width = getWidth();
        int centerX = width / 2;
        float seconds = menuAnimNs / 1_000_000_000f;

        g.setColor(new Color(3, 3, 9, 246));
        g.fillRect(0, 0, width, getHeight());

        drawMenuDecor(g, seconds);

        // Title with a soft neon glow.
        g.setFont(MENU_TITLE_FONT);
        FontMetrics titleMetrics = g.getFontMetrics();
        int titleWidth = titleMetrics.stringWidth(MENU_TITLE);
        int titleX = centerX - titleWidth / 2;
        int titleBaseline = 236;

        g.setColor(new Color(53, 224, 255, 55));
        g.drawString(MENU_TITLE, titleX + 2, titleBaseline + 2);
        g.drawString(MENU_TITLE, titleX - 2, titleBaseline - 2);
        g.setPaint(new GradientPaint(titleX, titleBaseline - 42,
                new Color(0xFFB6F8FF), titleX, titleBaseline, ACCENT));
        g.drawString(MENU_TITLE, titleX, titleBaseline);

        float pulse = (float) (0.5 + 0.5 * Math.sin(seconds * 2.6));
        g.setColor(new Color(53, 224, 255, (int) (90 + 130 * pulse)));
        g.fillRoundRect(centerX - titleWidth / 2 - 24, titleBaseline + 16, titleWidth + 48, 3, 3, 3);

        g.setFont(MENU_SUBTITLE_FONT);
        g.setColor(LABEL);
        drawCentered(g, "TETRIS · 经典方块消除 · 挑战更高分", centerX, titleBaseline + 42);

        int itemX = menuItemX();
        for (int i = 0; i < MENU_ITEMS.length; i++) {
            boolean selected = i == menuSelection;
            int itemY = menuItemY(i);

            if (selected) {
                g.setColor(new Color(53, 224, 255, 26));
                g.fillRoundRect(itemX - 6, itemY - 6, MENU_ITEM_W + 12, MENU_ITEM_H + 12, 18, 18);
            }

            g.setColor(selected ? new Color(20, 20, 32, 225) : new Color(13, 13, 21, 205));
            g.fillRoundRect(itemX, itemY, MENU_ITEM_W, MENU_ITEM_H, 14, 14);

            g.setStroke(new BasicStroke(selected ? 2f : 1f));
            g.setColor(selected ? ACCENT : BORDER);
            g.drawRoundRect(itemX, itemY, MENU_ITEM_W, MENU_ITEM_H, 14, 14);

            g.setFont(selected ? MENU_ITEM_FOCUS_FONT : MENU_ITEM_FONT);
            g.setColor(selected ? TEXT : HINT);
            FontMetrics metrics = g.getFontMetrics();
            int textX = centerX - metrics.stringWidth(MENU_ITEMS[i]) / 2;
            int textY = itemY + (MENU_ITEM_H + metrics.getAscent() - metrics.getDescent()) / 2;
            g.drawString(MENU_ITEMS[i], textX, textY);

            if (selected) {
                g.setColor(ACCENT);
                g.fillOval(itemX + 16, itemY + MENU_ITEM_H / 2 - 3, 6, 6);
            }
        }

        g.setFont(HINT_FONT);
        g.setColor(LABEL);
        drawCentered(g, "↑ ↓ 选择      ENTER 确认      也可用鼠标点击", centerX, 560);

        if (menuHelp) {
            drawMenuHelp(g);
        }
    }

    /** Small animated row of tetromino colours above the title. */
    private void drawMenuDecor(Graphics2D g, float seconds) {
        Tetromino[] types = Tetromino.values();
        int blockSize = 18;
        int gap = 12;
        int total = types.length * blockSize + (types.length - 1) * gap;
        int startX = getWidth() / 2 - total / 2;
        int baseY = 150;

        for (int i = 0; i < types.length; i++) {
            int bob = Math.round((float) Math.sin(seconds * 2.4f + i * 0.55f) * 5f);
            int x = startX + i * (blockSize + gap);
            int y = baseY + bob;

            g.setColor(new Color(types[i].rgb, true));
            g.fillRoundRect(x, y, blockSize, blockSize, 5, 5);
            g.setColor(new Color(255, 255, 255, 60));
            g.fillRoundRect(x + 3, y + 3, blockSize - 6, 3, 2, 2);
        }
    }

    private void drawMenuHelp(Graphics2D g) {
        int width = getWidth();
        int height = getHeight();

        g.setColor(new Color(3, 3, 8, 228));
        g.fillRect(0, 0, width, height);

        int boxX = 62;
        int boxY = 92;
        int boxW = width - boxX * 2;
        int boxH = height - boxY * 2 - 52;

        g.setColor(new Color(16, 16, 26, 246));
        g.fillRoundRect(boxX, boxY, boxW, boxH, 18, 18);
        g.setColor(ACCENT);
        g.setStroke(new BasicStroke(1.4f));
        g.drawRoundRect(boxX, boxY, boxW, boxH, 18, 18);

        g.setFont(MENU_HEADER_FONT);
        g.setColor(TEXT);
        drawCentered(g, "操作说明", width / 2, boxY + 50);

        g.setColor(ACCENT);
        g.fillRoundRect(width / 2 - 36, boxY + 62, 72, 2, 2, 2);

        String[] lines = {
                "← →  左右移动",
                "↓  软降（每格 +1 分）",
                "SPACE  硬降（每格 +2 分）",
                "↑ / X  顺时针旋转    Z  逆时针旋转",
                "C / SHIFT  暂存方块",
                "P  暂停 / 继续      R  重新开始",
                "ESC  返回主菜单",
                "消除整行即可得分，一次消除多行分数更高"};
        g.setFont(MENU_TEXT_FONT);
        g.setColor(HINT);
        int textX = boxX + 34;
        for (int i = 0; i < lines.length; i++) {
            g.drawString(lines[i], textX, boxY + 104 + i * 36);
        }

        g.setFont(HINT_FONT);
        g.setColor(LABEL);
        drawCentered(g, "按 ENTER 或 ESC 返回", width / 2, boxY + boxH - 24);
    }

    private void drawPauseOverlay(Graphics2D g) {
        int centerX = BOARD_X + BOARD_W / 2;
        int centerY = BOARD_Y + BOARD_H / 2;

        g.setColor(new Color(3, 3, 8, 185));
        g.fillRoundRect(BOARD_X, BOARD_Y, BOARD_W, BOARD_H, 8, 8);

        g.setFont(TITLE_FONT);
        g.setColor(TEXT);
        drawCentered(g, "已暂停", centerX, centerY - 2);

        g.setFont(HINT_FONT);
        g.setColor(LABEL);
        drawCentered(g, "按 P 继续  ·  ESC 返回菜单", centerX, centerY + 28);
    }

    private void drawGameOverOverlay(Graphics2D g) {
        int centerX = BOARD_X + BOARD_W / 2;
        int centerY = BOARD_Y + BOARD_H / 2;

        g.setColor(new Color(3, 3, 8, 205));
        g.fillRoundRect(BOARD_X, BOARD_Y, BOARD_W, BOARD_H, 8, 8);

        g.setFont(TITLE_FONT);
        g.setColor(new Color(0xFFFF6B6B));
        drawCentered(g, "游戏结束", centerX, centerY - 22);

        g.setFont(OVERLAY_SCORE_FONT);
        g.setColor(TEXT);
        drawCentered(g, "得分  " + board.getScore(), centerX, centerY + 14);

        g.setFont(HINT_FONT);
        g.setColor(LABEL);
        drawCentered(g, "R 重新开始  ·  ENTER 新对局", centerX, centerY + 44);
        drawCentered(g, "ESC 返回菜单", centerX, centerY + 62);
    }

    private void drawCentered(Graphics2D g, String text, int centerX, int baselineY) {
        FontMetrics metrics = g.getFontMetrics();
        g.drawString(text, centerX - metrics.stringWidth(text) / 2, baselineY);
    }
}
