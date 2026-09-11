// MazeSolverGUI.java —
//
// First interactive phase: a resizable Swing window that draws a maze
// graphically (walls as lines, start/end as colored cells) and lets you
// generate a new one with adjustable rows, columns, and seed. Only
// randomized backtracking exists so far.

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Random;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

public class MazeSolverGUI extends JFrame {
    private final MazePanel mazePanel = new MazePanel();
    private final JSpinner rowsSpinner = new JSpinner(new SpinnerNumberModel(15, 2, 80, 1));
    private final JSpinner colsSpinner = new JSpinner(new SpinnerNumberModel(20, 2, 80, 1));
    private final JTextField seedField = new JTextField(12);
    private final JLabel statusLabel = new JLabel(" ");

    MazeSolverGUI() {
        super("Maze Solver");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        statusLabel.setBorder(new EmptyBorder(4, 8, 8, 8));
        add(buildControlPanel(), BorderLayout.NORTH);
        add(mazePanel, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);

        seedField.setText(Long.toString(new Random().nextLong()));
        generate(); // also packs the window to fit the initial maze

        setLocationRelativeTo(null);
    }

    private JPanel buildControlPanel() {
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controls.setBorder(new EmptyBorder(8, 8, 8, 8));

        controls.add(new JLabel("Rows:"));
        controls.add(rowsSpinner);
        controls.add(new JLabel("Cols:"));
        controls.add(colsSpinner);
        controls.add(new JLabel("Seed:"));
        controls.add(seedField);

        JButton newSeedButton = new JButton("New Seed");
        newSeedButton.addActionListener(e -> seedField.setText(Long.toString(new Random().nextLong())));
        controls.add(newSeedButton);

        JButton generateButton = new JButton("Generate");
        generateButton.addActionListener(e -> generate());
        controls.add(generateButton);

        return controls;
    }

    private void generate() {
        int rows = (int) rowsSpinner.getValue();
        int cols = (int) colsSpinner.getValue();
        long seed;
        try {
            seed = Long.parseLong(seedField.getText().trim());
        } catch (NumberFormatException e) {
            seed = new Random().nextLong();
            seedField.setText(Long.toString(seed));
        }

        Maze maze = new Maze(rows, cols, seed);
        maze.generateBacktracking();
        mazePanel.setMaze(maze);
        pack();

        statusLabel.setText("Generated with: backtracking  (" + rows + "x" + cols + ", seed " + seed + ")");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MazeSolverGUI().setVisible(true));
    }
}

enum Direction {
    NORTH, SOUTH, EAST, WEST;

    Direction opposite() {
        switch (this) {
            case NORTH: return SOUTH;
            case SOUTH: return NORTH;
            case EAST:  return WEST;
            case WEST:  return EAST;
        }
        throw new IllegalStateException("unreachable");
    }
}

class Cell {
    final int row, col;
    final boolean[] wall = {true, true, true, true}; // indexed by Direction.ordinal()
    boolean visited = false; // used while generating

    Cell(int row, int col) {
        this.row = row;
        this.col = col;
    }
}

// A grid-adjacent neighbor in a given direction, ignoring walls. Used only
// during generation, since generation is what decides where walls go.
record Edge(Direction dir, Cell cell) {}

class Maze {
    private final int rows, cols;
    private final Cell[] cells;
    private final Random rng;

    Maze(int rows, int cols, long seed) {
        this.rows = rows;
        this.cols = cols;
        this.rng = new Random(seed);
        this.cells = new Cell[rows * cols];
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++)
                cells[r * cols + c] = new Cell(r, c);
    }

    int getRows() { return rows; }
    int getCols() { return cols; }
    Cell at(int r, int c) { return cells[r * cols + c]; }
    Cell startCell() { return at(0, 0); }
    Cell endCell() { return at(rows - 1, cols - 1); }

    List<Edge> gridNeighbors(Cell cell) {
        int[] dr = {-1, 1, 0, 0};
        int[] dc = {0, 0, 1, -1};
        Direction[] dirs = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
        List<Edge> result = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            int nr = cell.row + dr[i], nc = cell.col + dc[i];
            if (nr >= 0 && nr < rows && nc >= 0 && nc < cols)
                result.add(new Edge(dirs[i], at(nr, nc)));
        }
        return result;
    }

    void removeWall(Cell a, Cell b, Direction dir) {
        a.wall[dir.ordinal()] = false;
        b.wall[dir.opposite().ordinal()] = false;
    }

    // Randomized DFS: walk to a random unvisited neighbor, knock down the
    // wall, repeat; back up when stuck. Tends to produce long, winding
    // corridors with relatively few dead ends.
    void generateBacktracking() {
        resetGenerationState();
        Deque<Cell> stack = new ArrayDeque<>();
        Cell start = startCell();
        start.visited = true;
        stack.push(start);
        while (!stack.isEmpty()) {
            Cell current = stack.peek();
            List<Edge> options = new ArrayList<>();
            for (Edge e : gridNeighbors(current))
                if (!e.cell().visited) options.add(e);

            if (options.isEmpty()) {
                stack.pop();
                continue;
            }
            Edge pick = options.get(rng.nextInt(options.size()));
            removeWall(current, pick.cell(), pick.dir());
            pick.cell().visited = true;
            stack.push(pick.cell());
        }
    }

    private void resetGenerationState() {
        for (Cell cell : cells) {
            Arrays.fill(cell.wall, true);
            cell.visited = false;
        }
    }
}

// Draws the maze with Graphics2D: a filled square per cell, then wall
// segments on top. Each cell only ever draws its own East and South edges
// (plus the outer North/West border), mirroring how the old ASCII renderer
// avoided drawing shared walls twice.
class MazePanel extends JPanel {
    private static final int CELL_SIZE = 24;
    private static final Color WALL_COLOR = new Color(33, 33, 33);
    private static final Color BACKGROUND_COLOR = Color.WHITE;
    private static final Color START_COLOR = new Color(76, 175, 80);
    private static final Color END_COLOR = new Color(244, 67, 54);

    private Maze maze;

    MazePanel() {
        setBackground(BACKGROUND_COLOR);
    }

    void setMaze(Maze maze) {
        this.maze = maze;
        setPreferredSize(new Dimension(maze.getCols() * CELL_SIZE + 1, maze.getRows() * CELL_SIZE + 1));
        revalidate();
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (maze == null) return;
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int rows = maze.getRows(), cols = maze.getCols();

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                Color fill = BACKGROUND_COLOR;
                if (r == 0 && c == 0) fill = START_COLOR;
                else if (r == rows - 1 && c == cols - 1) fill = END_COLOR;
                g2.setColor(fill);
                g2.fillRect(c * CELL_SIZE, r * CELL_SIZE, CELL_SIZE, CELL_SIZE);
            }
        }

        g2.setColor(WALL_COLOR);
        g2.setStroke(new BasicStroke(2f));
        for (int c = 0; c < cols; c++)
            if (maze.at(0, c).wall[Direction.NORTH.ordinal()])
                g2.drawLine(c * CELL_SIZE, 0, (c + 1) * CELL_SIZE, 0);
        for (int r = 0; r < rows; r++)
            if (maze.at(r, 0).wall[Direction.WEST.ordinal()])
                g2.drawLine(0, r * CELL_SIZE, 0, (r + 1) * CELL_SIZE);

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                Cell cell = maze.at(r, c);
                int x = c * CELL_SIZE, y = r * CELL_SIZE;
                if (cell.wall[Direction.EAST.ordinal()])
                    g2.drawLine(x + CELL_SIZE, y, x + CELL_SIZE, y + CELL_SIZE);
                if (cell.wall[Direction.SOUTH.ordinal()])
                    g2.drawLine(x, y + CELL_SIZE, x + CELL_SIZE, y + CELL_SIZE);
            }
        }
    }
}