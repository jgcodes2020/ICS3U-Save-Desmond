/* =========================================
 * Save Desmond
 * Author: Jacky Guo
 * Due: Jan. 20, 2023
 * Programming Language: Java 1.8
 * =========================================
 * INPUT: menu options, player commands
 * PROCESSING:
 * - movement of player, Desmond and enemies
 * - parsing and execution of commands
 * - Spawning of Desmond and enemies
 * - collision between player, Desmond, and enemies
 * OUTPUT:
 * - occluded game board
 * - neat tables for the leaderboard
 * - terrible MIDI music??
 * =========================================
 * Overview of classes:
 * 
 * UTILITY METHODS
 * Utils - general utility methods
 * GenericUtils - utilities for extracting arguments from Object arrays
 * 
 * COMMAND ENGINE
 * CommandException (extends Exception) - A generic exception in the command engine.
 * CommandParsingException (extends CommandException) - Exception caused by command parsing.
 * CommandExecutionException (extends CommandException) - Exception wrapping one thrown from a running command.
 * CommandParser - Parses and dispatches commands.
 * 
 * GAME ENGINE PRIMITIVES (base classes)
 * Point - 2D integer point.
 * GameEntity - Moving object within the game that has a position.
 * CollisionMap - Handles collision between entities and walls.
 * 
 * GAME OBJECTS
 * Player - the player. According to lore, it's a robot, but I haven't bothered renaming it.
 * Desmond - Desmond, the child that needs saving.
 * Zombie - a zombie.
 * 
 * AUDIO SYSTEM
 * MidiUtils - utility methods for MIDI
 * MusicEngine (implements AutoCloseable) - main music handler
 *   MusicEngine.FaderThread (extends Thread) - Thread handling fading layers
 * 
 * GameState - primary global game state and implementation of commands
 * SaveDesmond - main class. Calls into the other classes to do most of its work.
 */

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.function.*;
import javax.sound.midi.*;


// UTILITY METHODS
// ==========================

/**
 * General utility methods.
 */
class Utils {
  /**
   * This class should not be constructed.
   */
  private Utils() {}
  
  // Global RNG object
  private static Random rng = new Random();
  // Global reader for System.in
  private static BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
  
  /**
   * Returns a random integer which is at least {@code min} and less than {@code max}.
   * @param min the lower bound
   * @param max the upper bound
   * @return the random value
   */
  public static int randomInt(int min, int max) {
    // Random.nextInt returns a value in the range [0, n).
    // Our range is [min, max), so the maximum difference from min is max - min.
    // We add min to move it into the correct range.
    return rng.nextInt(max - min) + min;
  }
  
  /**
   * Returns true {@code num/den} of the time.
   * @param num the numerator of the probability fraction
   * @param den the denominator of the probability fraction
   * @return true, {@code num/den} of the time. False at other times.
   */
  public static boolean randomChance(int num, int den) {
    // Random.nextInt returns a value in the range [0, den).
    // There is thus a num/den chance that this number is less than num.
    return rng.nextInt(den) < num;
  }
  
  /**
   * Reads a line of input from the user.
   * @return the line of input read from the user
   */
  public static String readLine() {
    // Read a line and complain if something goes wrong.
    try {
      return br.readLine();
    } catch (IOException e) {
      throw new IOError(e);
    }
  }
  
  /**
   * Reads a single int from the user.
   * @return the single int read from the user
   */
  public static int readInt() {
    // Read a line and convert it to an integer.
    return Integer.parseInt(readLine());
  }
  
  /**
   * Prompts for a menu, with options specified in an array, or as
   * variable arguments in the method call.
   * @param title the menu's title
   * @param options the allowed options
   * @return the index of the selected option
   */
  public static int promptMenu(String title, String... options) {
    int res = -69420;
    int dispIndex;
    // There should be at least one choice.
    if (options.length == 0) {
      throw new IllegalArgumentException("You need at least 1 menu option!");
    }
    
    // loop until an option has been chosen
    do {
      // Print the title and list options.
      System.out.println(title);
      for (int i = 0; i < options.length; i++) {
        dispIndex = i + 1;
        System.out.printf("%2d) %s\n", dispIndex, options[i]);
      }
      
      // Try to read a line.
      System.out.print("> ");
      try {
        // Try to read an int. The rest of this block will never happen
        // if this fails.
        res = Utils.readInt();
        // If the value is in range, we return.
        if (1 <= res && res <= options.length) {
          System.out.println();
          // convert from 1-based to 0-based indexes.
          return res - 1;
        }
        // Otherwise, tell the user that they didn't pick an option
        System.out.printf("Value out of range. Valid options range from 1 to %d\n", options.length);
      } catch (Exception e) {
        // Tell the user that something went wrong
        System.out.printf("Error reading value. (%s)\n", e.getClass().getName());
        System.out.println(e.getMessage());
      }
      System.out.println();
    } while (true);

  }
  /**
   * Print an exception, followed by its underlying causes.
   * @param throwable the throwable object to print
   */
  public static void printThrowable(Throwable throwable) {
    // Throwable currently being printed
    Throwable t = throwable;
    
    // print initial exception
    System.out.println(t.toString());
    t = t.getCause();
    // print underlying causes
    while (t != null) {
      System.out.println("Caused by: " + t.toString());
      t = t.getCause();
    }
  }
  
  /**
   * Converts a {@code List<Integer>} to an {@code int[]}.
   * @param integers the List of Integer
   * @return an array of int with the same values
   */
  public static int[] listToPrimitiveArray(List<Integer> integers)
  {
      int[] ret = new int[integers.size()];
      Iterator<Integer> iterator = integers.iterator();
      for (int i = 0; i < ret.length; i++)
      {
          ret[i] = iterator.next().intValue();
      }
      return ret;
  }
  
  /**
   * Repeats a string a certain number of times
   * @param times the number of times to repeat
   * @param str the string to repeat
   * @return the string, repeated {@code times} times.
   */
  public static String repeatString(int times, String str) {
    if (times < 0)
      throw new IllegalArgumentException("Cannot repeat less than 0 times");
    // Accumulates power-of-two repeats of str.
    StringBuilder powerTwoAccum = new StringBuilder(str);
    // Accumulates the resulting repeated string.
    StringBuilder result = new StringBuilder();
    
    /*
     * This loop works much like how a human would convert binary to decimal.
     * In each iteration, the ones bit of `i` will contain the (2^n)s place,
     * and powerTwoAccum will contain (2^n) repeats of the string.
     * 
     * We check if the current place value has its bit set, and if so, we
     * append the corresponding string for its place value.
     */
    for (int i = times; i != 0; i >>= 1, powerTwoAccum.append(powerTwoAccum)) {
      if ((i & 1) == 1) {
        result.append(powerTwoAccum);
      }
    }
    
    return result.toString();
  }
}

/**
 * Utilities for handling generic arguments.
 * @see Player.Action
 */
class GenericUtils {
  /**
   * This class should not be constructed.
   */
  private GenericUtils() {}
  
  /**
   * Converts an object to an {@code int}, if possible.
   * @param o the object
   * @throws ClassCastException if the type cannot be converted to int
   * @return the int obtained during conversion
   */
  public static int toInt(Object o) {
    Class<?> type = o.getClass();
    // Java promotes smaller integer types to int
    // int can also be returned directly
    if (type == Integer.class || type == Short.class || type == Byte.class) {
      // All 3 classes share a common superclass: Number
      // this provides the .intValue() method for returning an int
      return ((Number) o).intValue();
    }
    // Other types cannot be converted to int
    String errMsg = String.format("Type %s cannot be converted to int", type.getName());
    throw new ClassCastException(errMsg);
  }
  
  /**
   * Checks that a parameter array has the correct length.
   * @param len the expected number of arguments
   * @param params the arguments
   * @throws IllegalArgumentException if {@code params.length != len}
   */
  public static void checkParamLength(int len, Object[] params) {
    String errMsg;
    // All this function does is check that the length of an array is
    // equal to a number, then complain if it doesn't.
    if (params.length != len) {
      errMsg = String.format("Expected %d arguments, got %d", len, params.length);
      throw new IllegalArgumentException(errMsg);
    }
  }
}

// COMMAND ENGINE
// ==========================

/**
 * Exception relating to the command engine.
 */
class CommandException extends Exception {
  /**
   * I never cared about this. Eclipse is complaining about it though.
   */
  private static final long serialVersionUID = 1L;
  
  /* */
  public CommandException() {
    super();
  }
  
  public CommandException(String msg) {
    super(msg);
  }
  
  public CommandException(String msg, Throwable cause) {
    super(msg, cause);
  }
}

/**
 * Exception thrown during command parsing.
 */
class CommandParsingException extends CommandException {
  /**
   * I never cared about this. Eclipse is complaining about it though.
   */
  private static final long serialVersionUID = 1L;

  public CommandParsingException() {
    super();
  }
  
  public CommandParsingException(String msg) {
    super(msg);
  }
  
  public CommandParsingException(String msg, Throwable cause) {
    super(msg, cause);
  }
  
}

/**
 * Exception thrown due to an exception in a running command.
 */
class CommandExecutionException extends CommandException {
  /**
   * I never cared about this. Eclipse is complaining about it though.
   */
  private static final long serialVersionUID = 1L;

  public CommandExecutionException() {
    super();
  }
  
  public CommandExecutionException(String msg) {
    super(msg);
  }
  
  public CommandExecutionException(String msg, Throwable cause) {
    super(msg, cause);
  }
}

/**
 * Class handling command-line parsing and dispatching.
 */
class CommandParser {
  /**
   * Constructs a new CommandParser.
   */
  public CommandParser() {
    // initialize the command map
    commands = new HashMap<>();
  }
  
  /**
   * Registers a command, throwing if one is already registered.
   * @param name the name (first argument) of the command
   * @param main the function to call for this command
   * @exception IllegalStateException if a command has already been registered with the specified name
   */
  public void registerCommand(String name, ToIntFunction<String[]> main) {
    // putIfAbsent returns the previous value set, if there was one
    ToIntFunction<String[]> prev = commands.putIfAbsent(name, main);
    if (prev != null) {
      // There should not have been a previous value
      String errorMessage = String.format("A command was already registered for the name %s", name);
      throw new IllegalStateException(errorMessage);
    }
  }
  
  /**
   * Registers a command, throwing if one is already registered.
   * Because there is no return value, it is assumed to always be 0.
   * @param name the name (first argument) of the command
   * @param main the function to call for this command
   * @exception IllegalStateException if a command has already been registered with the specified name
   */
  public void registerCommand(String name, Consumer<String[]> main) {
    // Use a lambda function that simply runs main, then returns 0
    this.registerCommand(name, (args) -> {
      main.accept(args);
      return 0;
    });
  }
  
  /**
   * Executes a command, if it exists. Throws otherwise.
   * @param command the command to execute.
   * @return the name of the executed command
   */
  public int execute(String command) throws CommandException {
    // Arguments to the command.
    String[] args = splitArgs(command);
    
    // if this command is not registered, we can't run it
    if (!commands.containsKey(args[0])) {
      String errorMessage = String.format("No registered command for %s", command);
      throw new CommandException(errorMessage);
    }
    
    // run the command. if it throws, wrap it in a CommandExecutionException.
    try {
      return commands.get(args[0]).applyAsInt(args);
    }
    catch (Exception innerException) {
      String errorMessage = String.format("Command %s failed", args[0]);
      throw new CommandExecutionException(errorMessage, innerException);
    }
  }

  /**
   * Crude argument splitter for the "command line".
   * 
   * <ul>
   * <li>{@code \} escapes any single character, including within quotes.</li>
   * <li>{@code ""} enclose characters that belong in a single argument.</li>
   * <li>Characters enclosed in double quotes may also be escaped, by the same
   * method.</li>
   * <li>Note that {@code \} simply escapes a literal character. C-style escape
   * sequences are not supported.</li>
   * </ul>
   * 
   * @param cmdLine the command line to process.
   * @return an array of arguments.
   */
  public static String[] splitArgs(String cmdLine) throws CommandException {
    ArrayList<String> sections = new ArrayList<>();
    StringBuilder current = new StringBuilder();

    int i = 0;
    char quote = '\0';
    // escaped: true if the previous character started a backslash escape.
    // started: true if the "current" StringBuilder will be added as a section.
    boolean escaped = false, started = false;
    
    // empty string bad
    if (cmdLine.isEmpty()) {
      throw new CommandException("No command?");
    }
    
    // main parsing loop
    while (i < cmdLine.length()) {
      if (escaped) {
        // character escaped by \
        current.append(cmdLine.charAt(i));
        escaped = false;
        i++;
      } else if (quote == '"') {
        // currently inside quotes
        char nextChar = cmdLine.charAt(i);

        switch (nextChar) {
          // backslash escapes are allowed INSIDE quotes.
          case '\\': {
            escaped = true;
            i++;
          } break;
          case '"': {
            quote = '\0';
            i++;
          } break;
          default: {
            current.append(nextChar);
            i++;
          } break;
        }
      } else {
        if (Character.isWhitespace(cmdLine.charAt(i))) {
          // skip all following whitespace
          do {
            i++;
          } while (i < cmdLine.length() && Character.isWhitespace(cmdLine.charAt(i)));
          if (started) {
            // add the next argument and clear the string buffer if we have started
            // note that an argument may be empty. This can be done by providing
            // empty double quotes.
            started = false;
            sections.add(current.toString());
            current.setLength(0);
          }
          continue;
        }
        // We WILL add text to this section no matter what now.
        started = true;

        char nextChar = cmdLine.charAt(i);

        switch (nextChar) {
          case '\\': {
            escaped = true;
            i++;
          } break;
          case '"': {
            quote = nextChar;
            i++;
          } break;
          default: {
            current.append(nextChar);
            i++;
          } break;
        }
      }
    }
    
    // invalid format detection
    if (quote != '\0') {
      throw new CommandParsingException("Quotes were not closed properly");
    }
    else if (escaped) {
      throw new CommandParsingException("Backslash must be followed by another character");
    }
    
    if (started) {
      // Last section won't be added so we do it manually
      sections.add(current.toString());
    }

    return sections.toArray(new String[0]);
  }

  Map<String, ToIntFunction<String[]>> commands;
}

// GAME ENGINE PRIMITIVES
// ==========================

/**
 * Represents a 2D point with integer coordinates.
 * It is immutable, so it can be freely assigned without consequence.
 */
final class Point {
  public final int x;
  public final int y;

  public Point(int x, int y) {
    this.x = x;
    this.y = y;
  }

  public Point copy() {
    return new Point(this.x, this.y);
  }
  
  /**
   * Computes a hash code for this value. This special number is used in data
   * structures known as hash tables, as it provides an efficient index to
   * look up with.
   * @return a hash code
   */
  public int hashCode() {
    // Take the bitwise XOR of the x-coordinate and the y-coordinate. This 
    // guarantees fair combination, given uniformly distributed X and Y.
    return x ^ y;
  }
  
  /**
   * Checks whether this point is equal to another point.
   * @param that the other point
   * @return true if this and the other object are equal
   */
  public boolean equals(Object that) {
    // ensure that both objects are indeed Points
    if (that.getClass() != Point.class)
      return false;
    Point p = (Point) that;
    // check that both X and Y coordinates are equal
    return p.x == this.x && p.y == this.y;
  }
  
  /**
   * Converts this point to a string. In general, the formatting
   * is "(x, y)".
   * @return a string representation of this point
   */
  public String toString() {
    return String.format("(%d, %d)", this.x, this.y);
  }
  
  /**
   * Subtract this point from another, as if both were vectors.
   */
  public Point sub(Point that) {
    // vector subtraction is done component-wise
    return new Point(this.x - that.x, this.y - that.y);
  }
}

/**
 * Represents a moving object (player, zombie, Desmond, etc.)
 * 
 * @author 340920834
 */
abstract class GameEntity {

  protected GameEntity() {
  }

  /**
   * Returns the current grid position of this entity.
   * 
   * @return the entity's position
   */
  public Point getPos() {
    return pos;
  }

  /**
   * Moves this entity to a position.
   * 
   * @param pos the position.
   */
  public void moveTo(Point pos) {
    this.pos = pos;
  }

  /**
   * In subclasses, handles movement per turn.
   * 
   * @param coll the current collision map.
   */
  public abstract void doTick(GameState gs);

  /**
   * Resolves collision with another game entity.
   * By default, does nothing, and allows the object to phase through.
   * 
   * @param other the other game entity.
   */
  public void doTouch(GameState gs, GameEntity other) {
    if (other instanceof Zombie) {
      gs.triggerGameOver();
    }
  }

  protected Point pos;
}

/**
 * Class representing a collision map.
 * Character values:
 * '\0' - empty space
 * 'x' - wall
 */
class CollisionMap {
  // The terrain map. Maps out which tiles are walls and which are not.
  private char[][] map;
  // The visited map. Maps out which tiles have been visited.
  private boolean[][] visited;
  // The home point. The player must return here with Desmond to win.
  private Point homePoint;

  /**
   * Constructs a collision map with a given size.
   * @param width the number of grid cells in the x-axis
   * @param height the number of grid cells in the y-axis
   */
  public CollisionMap(int width, int height) {
    // Initialize the data array
    this.map = new char[height][width];
    // Initialized the visited array (there are more efficient ways)
    this.visited = new boolean[height][width];
    homePoint = null;
    
    // initialize the whole map to empty space
    for (int i = 0; i < height; i++) {
      for (int j = 0; j < width; j++) {
        map[i][j] = ' ';
      }
    }
  }
  
  /**
   * Marks all grid tiles as not visited.
   */
  public void clearVisited() {
    // Set every value in visited to false
    for (int i = 0; i < visited.length; i++) {
      for (int j = 0; j < visited[i].length; j++) {
        visited[i][j] = false;
      }
    }
  }
  
  public boolean[][] getVisited() {
    return visited;
  }
  
  /**
   * Returns the 2D array representing the map.
   * @return
   */
  public char[][] getMap() {
    return map;
  }
  
  /**
   * Returns the home point (front door location).
   * @return the home point.
   */
  public Point getHomePoint() {
    return homePoint;
  }
  
  /**
   * Sets the home point (front door location).
   * @param homePoint the home point.
   */
  public void setHomePoint(Point homePoint) {
    this.homePoint = homePoint;
  }
  
  /**
   * Returns the width (x-coordinate range) of the map.
   * @return the width of the map.
   */
  public int width() {
    // returns the length of a row
    return map[0].length;
  }
  
  /**
   * Returns the height (y-coordinate range) of the map.
   * @return the height of the map.
   */
  public int height() {
    // returns the length of a column
    return map.length;
  }
  
  /**
   * Returns true if a given point collides with a wall or out of bounds.
   * @param x the x-coordinate of the point
   * @param y the y-coordinate of the point
   * @return true if this point collides with a wall
   */
  public boolean collides(int x, int y) {
    // check if out-of-bounds
    if (x < 0 || x >= this.width())
      return true;
    if (y < 0 || y >= this.height())
      return true;
    // check if this cell is not empty
    if (this.map[y][x] != ' ')
      return true;

    return false;
  }
  
  /**
   * Returns true if a given point collides with a wall or out of bounds.
   * @param p the point to check
   * @return true if this point collides with a wall
   */
  public boolean collides(Point p) {
    return this.collides(p.x, p.y);
  }

  /**
   * Performs the motion of an object along an axis.
   * Direction values are:
   * 0: up
   * 1: left
   * 2: down
   * 3: right
   * 
   * @param pos  the object's initial position
   * @param dir  the direction of movement
   * @param dist the number of steps
   * @param updateVisited if true, updates the visited array
   * @return the resulting position
   */
  Point tryMove(Point pos, int dir, int dist, boolean updateVisited) {
    // change in X and Y (used to move the nextX, nextY points)
    int deltaX, deltaY;
    // current X and Y
    int currX, currY;
    // next X and Y
    int nextX, nextY;
    
    // Initialize deltaX and deltaY according to directions
    switch (dir) {
      case 0: {
        deltaX = 0;
        deltaY = -1;
      } break;
      case 1: {
        deltaX = -1;
        deltaY = 0;
      } break;
      case 2: {
        deltaX = 0;
        deltaY = 1;
      } break;
      case 3: {
        deltaX = 1;
        deltaY = 0;
      } break;
      default: {
        throw new IllegalArgumentException("Invalid direction " + dir);
      }
    }
    // Initialize currX and currY to the starting position
    currX = pos.x; currY = pos.y;
    // Mark the current position visited, if it has been requested
    visited[currY][currX] |= updateVisited;

    for (int i = 0; i < dist; i++) {
      // determine the next position
      nextX = currX + deltaX;
      nextY = currY + deltaY;
      // check if it collides
      if (this.collides(nextX, nextY)) {
        // stop here, we can't move any further
        return new Point(currX, currY);
      }
      // move to the next position
      currX = nextX;
      currY = nextY;
      // Mark this new position visited, if it has been requested
      visited[currY][currX] |= updateVisited;
    }
    // we moved all the way, return this point
    return new Point(currX, currY);
  }
  
  /**
   * Simpler version of {@link CollisionMap#tryMove(Point, int, int, boolean)}.
   * This version never updates the visited array.
   * @param pos  the object's initial position
   * @param dir  the direction of movement
   * @param dist the number of steps
   * @return the resulting position
   */
  Point tryMove(Point pos, int dir, int dist) {
    return this.tryMove(pos, dir, dist, false);
  }
}

// GAME OBJECTS
// ==========================

/**
 * Represents the player on the game board.
 */
class Player extends GameEntity {
  /**
   * Represents a player action.
   * Uses a clever trick where every enum value is
   * an anonymous subclass of the enum class itself.
   * 
   * This allows us to override the run action depending on the action.
   * Since the parameters have to be generic, they are passed as an Object array.
   */
  public static enum Action {
    MOVE {
      /**
       * Tries to move the player some number of units away in a direction.
       * This may stop short of the intended distance due to walls or out-of-bounds.
       */
      @Override
      public void run(Player self, GameState gs, Object... params) {
        // unpack 2 integer arguments: distance and direction
        GenericUtils.checkParamLength(2, params);
        int dir = GenericUtils.toInt(params[0]);
        int dist = GenericUtils.toInt(params[1]);
        
        // Move in the specified direction and distance, updating the visited map
        self.pos = gs.getCollision().tryMove(self.pos, dir, dist, true);
      }
    }, 
    PICKUP {
      /**
       * Tries to pick up Desmond. The player may only pick up Desmond
       * if they are standing on the same tile as Desmond.
       */
      @Override
      public void run(Player self, GameState gs, Object... params) {
        // ensure no arguments
        GenericUtils.checkParamLength(0, params);
        // don't do anything if Desmond is already picked up
        if (self.holding) {
          System.out.println("The robot did nothing because it already has Desmond.");
          return;
        }
        // Check if the player is on the same tile as Desmond
        if (self.pos.equals(gs.getDesmond().pos)) {
          // Robot can pick up Desmond, set relevant flags
          System.out.println("The robot picked up Desmond.");
          self.holding = true;
          gs.getDesmond().setPickedUp(true);
        }
        else {
          System.out.println("The robot tried to pick up Desmond. There was no Desmond to pick up.");
        }
      }
    };
    
    /**
     * Runs a generic action on a player.
     * @param self the object to run the action on
     * @param gs the game state that the player belonds to
     * @param params the parameters. Is an Object array to support different actions having different parameters.
     */
    public abstract void run(Player self, GameState gs, Object... params);
  }
  
  /**
   * Constructs the player, starting at a specific point.
   * @param startPos sets the start position
   */
  public Player(GameState gs) {
    super();

    this.pos = gs.getCollision().getHomePoint();
    this.action = null;
    this.actionParams = null;
  }

  /**
   * Sets the player's next action.
   * @param a the action to invoke
   * @param params parameters to the action
   */
  public void setAction(Action a, Object... params) {
    // set the action variables
    this.action = a;
    this.actionParams = params;
  }
  
  /**
   * Returns true if the player is holding Desmond, or false otherwise.
   * @return true if the player is holding Desmond, or false otherwise.
   */
  public boolean isHolding() {
    return holding;
  }

  /**
   * Performs the player's current action, then evaluates
   * the win/lose conditions.
   * @param gs the game state this player belongs to
   */
  @Override
  public void doTick(GameState gs) {
    // The home point.
    Point homePoint = gs.getCollision().getHomePoint();
    
    // Perform the action, as specified
    action.run(this, gs, actionParams);
    
    // check win condition
    if (this.holding && this.pos.equals(homePoint)) {
      gs.triggerWin();
    }
    
    // check death condition
    if (gs.checkEnemies(this.pos) instanceof Zombie) {
      gs.triggerGameOver();
    }
  }
  
  // True if the player is holding Desmond.
  private boolean holding;
  // Action to execute on the next frame.
  private Action action;
  // Parameters to the action.
  private Object[] actionParams;
}

/**
 * Represents Desmond on the game board.
 */
class Desmond extends GameEntity {
  public Desmond(GameState gs) {
    super();
    // move to a generated spawn point, and set picked up as false
    this.pos = gs.genSpawnPoint();
    this.pickedUp = false;
  }
  
  /**
   * Returns true if Desmond is currently picked up.
   * @return true if Desmond is currently picked up, false otherwise
   */
  public boolean isPickedUp() {
    return this.pickedUp;
  }
  /**
   * Sets whether Desmond is picked up or not.
   * @param value true if Desmond should be picked up, false if not
   */
  public void setPickedUp(boolean value) {
    this.pickedUp = value;
  }
  
  /**
   * Movement tick for Desmond. Desmond's AI is summarized here:
   * <ul>
   * <li>If Desmond is picked up, move him to the player.</li>
   * <li>Otherwise, he has a 33% chance to move 1 tile in a 
   * random cardinal direction.</li>
   * </ul>
   * @param gs the GameState that Desmond belongs to
   */
  @Override
  public void doTick(GameState gs) {
    if (pickedUp) {
      // follow the player if picked up
      this.pos = gs.getPlayer().getPos();
    }
    else {
      // Configure these variables to get a fraction chance
      // of Desmond moving
      final int CHANCE_NUM = 1;
      final int CHANCE_DEN = 3;
      
      // There is a CHANCE_NUM/CHANCE_DEN chance of Desmond moving on each turn.
      if (Utils.randomChance(CHANCE_NUM, CHANCE_DEN)) {
        // Move Desmond 1 tile in a random direction
        this.pos = gs.getCollision().tryMove(this.pos, Utils.randomInt(0, 4), 1);
      }
    }
  }
  
  // True if Desmond is picked up or not.
  boolean pickedUp;
}

/**
 * Represents a zombie on the game board.
 */
class Zombie extends GameEntity {
  /**
   * Creates and positions a zombie in the game world.
   * @param gs the game state
   */
  public Zombie(GameState gs) {
    super();
    // move to a generated spawn point
    this.pos = gs.genSpawnPoint();
  }

  /**
   * Movement tick for zombies. Zombie AI is summarized here:
   * <ul>
   * <li>Zombies have a 40% chance to move in a random cardinal direction on each frame.</li>
   * </ul>
   * @param gs the GameState that this zombie belongs to
   */
  @Override
  public void doTick(GameState gs) {
    // Zombies move randomly as well
    final int CHANCE_NUM = 2;
    final int CHANCE_DEN = 5;
    if (Utils.randomChance(CHANCE_NUM, CHANCE_DEN)) {
      // try to move 1 tile in a random direction
      Point nextPos = gs.getCollision().tryMove(this.pos, Utils.randomInt(0, 4), 1);
      // stop zombies from walking into each other
      if (gs.checkEnemies(nextPos) == null) {
        this.pos = nextPos;
      }
    }
  }
}

// AUDIO SYSTEM
// ==========================

/**
 * Utility methods specific to MIDI.
 */
class MidiUtils {
  /**
   * This class should not be constructed.
   */
  private MidiUtils() {}
  
  /**
   * The number of MIDI channels that exist (in general).
   */
  public static int NUM_CHANNELS = 16;
  
  /**
   * Value representing the MIDI controller for volume.
   */
  public static int CC_VOLUME = 7;
  
  /**
   * Value representing the type of the end-of-track MIDI message.
   */
  public static int META_END_OF_TRACK = 0x2F;
  
  /**
   * Creates a MIDI message setting a channel's volume.
   * @param channel the channel to send through
   * @param volume the volume to send (0-127)
   * @return the MIDI message
   */
  public static ShortMessage volumeMsg(int channel, int volume) {
    try {
      // Create a MIDI message for a control change on volume (CC 7)
      // if the volume is out of range, an exception is thrown
      return new ShortMessage(ShortMessage.CONTROL_CHANGE, channel, CC_VOLUME, volume);
    }
    catch (InvalidMidiDataException e) {
      throw new IllegalArgumentException("Invalid MIDI volume value", e);
    }
  }
  
  /**
   * Creates a fade-in/out track for the fade sequencer. Specifically, this 
   * creates a fade lasting 100 ticks. Since the fade sequencer is initialized
   * at 100 ticks per quarter note, the tempo (measured in microseconds per
   * quarter note) can easily be used to control the speed at which 
   * fade-ins/fade-outs occur.
   * 
   * @param seq the sequence to add the fade track to
   * @param channel the channel to fade in or out
   * @param out if true, produces a fade out. if false, produces a fade in.
   * @return the track containing the fade-in/fade-out.
   */
  public static Track addFadeTrack(Sequence seq, int channel, boolean out) {
    // the track being written to
    Track track = seq.createTrack();
    // used to store the MIDI events in the loop
    MidiEvent event;
    
    if (out) {
      for (int i = 100; i >= 0; i--) {
        // Create a MIDI event which sets the volume
        event = new MidiEvent(volumeMsg(channel, i), i);
        track.add(event);
      }
    }
    else {
      for (int i = 0; i <= 100; i++) {
        // Create a MIDI event which sets the volume
        event = new MidiEvent(volumeMsg(channel, i), i);
        track.add(event);
      }
    }
    
    return track;
  }
  
  /**
   * Similar to {@link MidiUtils#addFadeTrack()}, but fades in or out all
   * channels (except for a user-defined list)
   * 
   * @param seq the sequence to add the fade track to
   * @param out if true, produces a fade out. if false, produces a fade in.
   * @param excluded the channels to exclude from this fade.
   * @return the track containing the fade-in/fade-out.
   */
  public static Track addExclusiveFadeTrack(Sequence seq, boolean out, int... excluded) {
    // the track being written to
    Track track = seq.createTrack();
    // used to store the MIDI events in the loop
    MidiEvent event;
    // the channels that will be used
    int[] usedChannels = new int[MidiUtils.NUM_CHANNELS - excluded.length];
    // counter for the next index of the used channel array.
    int usedChannelCounter = 0;
    // flag checking if the current channel should be used.
    boolean shouldUseThisChannel;
    
    // initialize the "used channels" array
    for (int i = 0; i < MidiUtils.NUM_CHANNELS; i++) {
      shouldUseThisChannel = true;
      for (int j = 0; j < excluded.length; j++) {
        if (excluded[j] == i) {
          shouldUseThisChannel = false;
          break;
        }
      }
      // Add this channel if it hasn't been excluded
      if (shouldUseThisChannel) {
        usedChannels[usedChannelCounter] = i;
        usedChannelCounter++;
      }
    }
    
    if (out) {
      // Iterate from 99 to 0
      for (int t = 99; t >= 0; t--) {
        for (int c = 0; c < usedChannels.length; c++) {
          // Create a MIDI event which sets the volume
          event = new MidiEvent(volumeMsg(usedChannels[c], t), t);
          track.add(event);
        }
      }
    }
    else {
      // Iterate from 1 to 100
      for (int t = 1; t <= 100; t++) {
        // Iterate over all 16 MIDI channels
        for (int c = 0; c < usedChannels.length; c++) {
          // Create a MIDI event which sets the volume
          event = new MidiEvent(volumeMsg(usedChannels[c], t), t);
          track.add(event);
        }
      }
    }
    
    return track;
  }
  
  /**
   * Converts milliseconds to microseconds.
   * @param millis a duration in milliseconds
   * @return the same duration, in microseconds
   */
  public static int millisecondsToMicroseconds(int millis) {
    return millis * 1000;
  }
  
  /**
   * Gets the volume of a particular MIDI channel.
   * @param synth the synthesizer
   * @param channel the channel
   * @return the volume of channel {@code channel} of {@code synth}
   */
  public static int getVolume(Synthesizer synth, int channel) {
    // the MIDI channel to use.
    MidiChannel midiChan = synth.getChannels()[channel];
    // Get the value of the "volume" controller
    return midiChan.getController(CC_VOLUME);
  }
  
  public static void setVolume(Synthesizer synth, int channel, int volume) {
    // the MIDI channel to use.
    MidiChannel midiChan = synth.getChannels()[channel];
    // Set the value of the "volume" controller
    midiChan.controlChange(CC_VOLUME, volume);
    
  }
}

/**
 * Music handler for the game.
 */
class MusicEngine implements AutoCloseable {
  
  // Constants representing timestamps in the MIDI file.
  private static final long MAIN_LOOPBACK_TICK = 4096;
  
  // Constants for MIDI channels in the theme.
  private static final int HORN_CHANNEL = 0;
  private static final int TROMBONE_CHANNEL = 1;
  private static final int TUBA_CHANNEL = 2;
  private static final int GLOCKENSPIEL_CHANNEL = 3;
  private static final int SNARE_DRUM_CHANNEL = 9;
  
  // Constants for fade times
  private static final int FADEOUT_TIME = 500;
  private static final int TRANSITION_TIME = 1000;
  
  public MusicEngine() {
    try {
      // initialize the MIDI objects
      this.musicSeqr = initMusicSeqr();
      this.synth = MidiSystem.getSynthesizer();
      
      // open the MIDI objects
      this.musicSeqr.open();
      this.synth.open();
      
      // Connect the sequencers to the synthesizer
      Transmitter musicTransmitter = this.musicSeqr.getTransmitter();
      Receiver synthReceiver = this.synth.getReceiver();
      musicTransmitter.setReceiver(synthReceiver);
    }
    catch (Exception e) {
      // Honestly, nothing should go wrong here.
      throw new Error(e);
    }
    // binary semaphore for signaling (MIDI runs in a different thread)
    this.signal = new Semaphore(1, true);
    // 0 is defaul transition state
    this.transitionState = 0;
  }
  
  public void start() {
    MidiChannel[] channels = this.synth.getChannels();
    // Lock the mutex.
    try {
      try {
        this.signal.acquire();
      } catch (InterruptedException e) {
        // This shouldn't happen, but if it does, crash and burn.
        throw new RuntimeException(e);
      }
      // Start the music.
      this.musicSeqr.setTickPosition(0);
      this.musicSeqr.start();
      
      // Set all channels to default volume (100), except for the ones playing
      // glockenspiel and snare drum. These are faded in later as you get closer
      // to Desmond, and then pick him up.
      for (int i = 0; i < channels.length; i++) {
        if (i == GLOCKENSPIEL_CHANNEL || i == SNARE_DRUM_CHANNEL) {
          channels[i].controlChange(MidiUtils.CC_VOLUME, 0);
        }
        else {
          channels[i].controlChange(MidiUtils.CC_VOLUME, 100);
        }
      }
      // Music always starts in transition state 0
      this.transitionState = 0;
    }
    finally {
      // Unlock the mutex.
      this.signal.release();
    }
    
    
  }
  
  public void stop() {
    if (!this.musicSeqr.isRunning()) {
      return;
    }
    
    // Lock the mutex.
    try {
      this.signal.acquire();
    } catch (InterruptedException e) {
      // This shouldn't happen, but if it does, crash and burn.
      throw new RuntimeException(e);
    }
    
    // Safely return if the system has been closed in the meantime
    if (this.synth == null)
      return;
    
    // List and array of tracks to use respectively
    List<Integer> channelList = new ArrayList<>();
    
    // add the fade out for the channels that are always playing
    channelList.add(HORN_CHANNEL);
    channelList.add(TROMBONE_CHANNEL);
    channelList.add(TUBA_CHANNEL);
    // check if the glockenspiel needs to be faded out
    if (MidiUtils.getVolume(synth, GLOCKENSPIEL_CHANNEL) != 0) {
      channelList.add(GLOCKENSPIEL_CHANNEL);
    }
    // check if the snare drum needs to be faded out
    if (MidiUtils.getVolume(synth, SNARE_DRUM_CHANNEL) != 0) {
      channelList.add(SNARE_DRUM_CHANNEL);
    }
    
    // Execute the fadeout
    // The fader thread will unlock the mutex when done
    new FaderThread(FADEOUT_TIME, null, channelList, () -> {
      this.musicSeqr.stop();
    }).start();
  }
  
  /**
   * Transitions the music between states. The states are:
   * <ul>
   * <li>0 - no extra parts</li>
   * <li>1 - glockenspiel</li>
   * <li>2 - glockenspiel + snare drum</li>
   * </ul>
   */
  public void transition(int state) {
  	// Nothing needs to happen if the state doesn't change.
  	if (state == this.transitionState) {
  		return;
  	}
    // Lock the mutex.
    try {
      this.signal.acquire();
    } catch (InterruptedException e) {
      // This shouldn't happen, but if it does, crash and burn.
      throw new RuntimeException(e);
    }
    
    // Safely return if the system has been closed in the meantime
    if (this.synth == null)
      return;
    
    this.transitionState = state;
    // True if the glockenspiel and snare drum should be heard 
    // after the transition, respectively
    boolean glockenspielEnable = state >= 1;
    boolean snareDrumEnable = state >= 2;
    // The current volume of the glockenspiel and snare drum 
    // channels, respectively
    int glockenspielVolume = MidiUtils.getVolume(synth, GLOCKENSPIEL_CHANNEL);
    int snareDrumVolume = MidiUtils.getVolume(synth, SNARE_DRUM_CHANNEL);
    
    // List and array of inputs respectively
    List<Integer> fadeInList = new ArrayList<>();
    List<Integer> fadeOutList = new ArrayList<>();
    
    // Check if the glockenspiel needs to be transitioned
    if (glockenspielEnable && glockenspielVolume != 100) {
      fadeInList.add(GLOCKENSPIEL_CHANNEL);
    }
    else if (!glockenspielEnable && glockenspielVolume != 0) {
      fadeOutList.add(GLOCKENSPIEL_CHANNEL);
      
    }
    
    // Check if the snare drum needs to be transitioned
    if (snareDrumEnable && snareDrumVolume != 100) {
      fadeInList.add(SNARE_DRUM_CHANNEL);
      
    }
    else if (!snareDrumEnable && snareDrumVolume != 0) {
      fadeOutList.add(SNARE_DRUM_CHANNEL);
    }
    
    // If there are no transitions to schedule, exit now
    if (fadeInList.size() == 0 && fadeOutList.size() == 0) {
      this.signal.release();
      return;
    }
  
    // schedule the transitions
    new FaderThread(TRANSITION_TIME, fadeInList, fadeOutList).start();
  }
  
  /**
   * Closes the MusicEngine, freeing all resources it uses.
   */
  @Override
  public void close() {
    // close all MIDI objects
    this.musicSeqr.close();
    this.synth.close();
    
    // set the synth to null as a sentinel
    this.synth = null;
  }
  
  /**
   * Initializes the music sequencer. This sequencer's job is to actually play
   * the music, and loop it.
   * @return the music sequencer.
   * @throws MidiUnavailableException if, for some reason, the sequencer can't be created
   * @throws InvalidMidiDataException if, for some reason, the MIDI file is invalid
   * @throws IOException if, for some reason, the MIDI file can't be read
   */
  private static Sequencer initMusicSeqr() throws MidiUnavailableException, InvalidMidiDataException, IOException {
    final Path MIDI_PATH = Paths.get("./main-theme.mid").toRealPath();
    
    
    // returned sequencer
    Sequencer seqr;
    // sequence for the sequencer
    Sequence seq;
    
    // load the MIDI file
    seq = MidiSystem.getSequence(MIDI_PATH.toFile());
    
    // setup the sequencer
    seqr = MidiSystem.getSequencer(false);
    seqr.setSequence(seq);
    
    // Loop runs from the 8th bar to the end of the song
    // It will always be looped until faded out
    seqr.setLoopStartPoint(MAIN_LOOPBACK_TICK);
    seqr.setLoopEndPoint(-1);
    seqr.setLoopCount(Sequencer.LOOP_CONTINUOUSLY);
    
    return seqr;
  }
  
  
  // Sequencer with actual music.
  private Sequencer musicSeqr;
  // Output synthesizer. Doubles as a sentinel when closing.
  private Synthesizer synth;
  // Current music state. 
  // 0 = no glockenspiel or snare drum
  // 1 = glockenspiel
  // 2 = glockenspiel and snare drum
  private int transitionState;
  // Mutex for synchronization.
  private Semaphore signal;
  
  /**
   * Thread handling transitions.
   */
  private class FaderThread extends Thread {
    /**
     * Constructs a new fader thread that fades a specific set of channels.
     * It will also free this {@link MusicEngine}'s semaphore.
     * @param fadeTime the time spent fading
     * @param fadeInChannels the channels to fade in
     * @param fadeOutChannels the channels to fade out
     * @param cleanup a function run before releasing the semaphore
     */
    public FaderThread(long fadeTime, List<Integer> fadeInChannels, 
      List<Integer> fadeOutChannels, Runnable cleanup) {
      super();
      // stores remainder after dividing fadeTime by 100
      long remainder;
      
      // Save references to mutex, synth, fade lists, and cleanup
      this.fadeInChannels = fadeInChannels;
      this.fadeOutChannels = fadeOutChannels;
      this.cleanup = cleanup;
      
      // Convert fade time to ms + ns
      this.intervalMs = fadeTime / 100;
      // number of 1/100ths of a millisecond to add on to the sleep length
      remainder = fadeTime % 100;
      // 0.01 ms = 10 μs = 10 000 ns
      this.intervalNs = (int) (remainder * 10000);
    }
    
    /**
     * Constructs a new fader thread that fades a specific set of channels.
     * It will also free this {@link MusicEngine}'s semaphore.
     * @param fadeTime the time spent fading
     * @param fadeInChannels the channels to fade in
     * @param fadeOutChannels the channels to fade out
     */
    public FaderThread(long fadeTime,
      List<Integer> fadeInChannels, List<Integer> fadeOutChannels) {
      this(fadeTime, fadeInChannels, fadeOutChannels, null);
    }
    
    /**
     * Implements the actual fading. This has to be done in a separate
     * thread to avoid clogging up the main thread while the transition
     * occurs.
     */
    @Override
    public void run() {
      int fadeOutVol;
      try {
          
        // Fade either goes from 1-100 or 99-0
        for (int vol = 1; vol <= 100; vol++) {
          fadeOutVol = 100 - vol;
          try {
            // sleep based on the computed interval between volume changes
            Thread.sleep(this.intervalMs, this.intervalNs);
          } catch (InterruptedException e) {
            // Stop if interrupted. This still runs the finally block, so
            // no harm is done
            return;
          }
          
          // If there are fade in channels, set their volumes
          if (fadeInChannels != null) {
            for (Integer fadeIn : fadeInChannels) {
              MidiUtils.setVolume(MusicEngine.this.synth, fadeIn, vol);
            }
          }
          // Similarly for fade out
          if (fadeOutChannels != null) {
            for (Integer fadeOut : fadeOutChannels) {
              MidiUtils.setVolume(MusicEngine.this.synth, fadeOut, fadeOutVol);
            }
          }
        }
      }
      finally {
        // run any post-fade cleanup
        if (cleanup != null)
          cleanup.run();
        // free the mutex
        MusicEngine.this.signal.release();
      }
    }
    
    // List of channels to fade in
    private List<Integer> fadeInChannels;
    // List of channels to fade out
    private List<Integer> fadeOutChannels;
    
    // Function to run on cleanup (possibly null, in that case, does nothing).
    private Runnable cleanup;
    
    // Number of milliseconds to wait between volume changes.
    private long intervalMs;
    // Number of additional nanoseconds to wait between volume changes.
    private int intervalNs;
  }
  
}

// LEADERBOARD SYSTEM
// ==========================

/**
 * Represents a single score record on the leaderboard.
 */
class Score implements Serializable, Comparable<Score> {
  public static final long serialVersionUID = 1L;
  
  public Score(String name, int score) {
    this.name = name;
    this.points = score;
  }

  @Override
  public int compareTo(Score that) {
    // compare the points values first
    int res = Integer.compare(this.points, that.points);
    if (res != 0)
      return res;
    // if the points values are the same, compare the names
    return this.name.compareTo(that.name);
  }
  /**
   * Returns the name associated with this leaderboard entry.
   * @return the name associated with this leaderboard entry.
   */
  public String getName() {
    return name;
  }
  
  /**
   * Returns the point score associated with this leaderboard entry.
   * @return the point score associated with this leaderboard entry.
   */
  public int getPoints() {
    return points;
  }
  
  // name for leaderboard entry
  private String name;
  // point score for leaderboard entry
  private int points;
}

/**
 * Class implementing a leaderboard. Also handles
 * serialization/deserialization from a file.
 */
class Leaderboard implements Closeable {
  
  /**
   * Reads a leaderboard from a file or creates it if
   * it does not exist.
   * @param p the path to save to
   * @throws IOException if reading the list fails
   */
  @SuppressWarnings("unchecked")
  public Leaderboard(Path p) throws IOException {
    // set the path
    this.path = p;
    if (!Files.exists(p)) {
      // Create a new empty list (it will be serialized on close)
      this.list = new ArrayList<>();
    }
    else {
      // Read scoreboard from file
      try (ObjectInputStream in = new ObjectInputStream(
        Files.newInputStream(p, StandardOpenOption.READ))) {
        Object o = in.readObject();
        if (o.getClass() != ArrayList.class) {
          throw new IOException("Unexpected object in data file!");
        }
        this.list = (ArrayList<Score>) o;
      }
      catch (ClassNotFoundException e) {
        throw new IOException(e);
      }
    }
  }
  
  /**
   * Adds a score to the leaderboard.
   * @param score the score to add
   */
  public void addScore(Score score) {
    // add this score to the end of the list
    list.add(score);
    // perform one iteration of insertion sort,
    // since the rest is already sorted.
    for (int i = list.size() - 1; i > 0; i--) {
      Score s1 = list.get(i), s2 = list.get(i - 1);
      if (s1.compareTo(s2) <= 0) {
        return;
      }
      list.set(i, s2);
      list.set(i - 1, s1);
    }
  }
  
  /**
   * Returns all the scores on the leaderboard.
   * @return the top scores.
   */
  public List<Score> getAllScores() {
    // Return an immutable list (as a precaution)
    return Collections.unmodifiableList(list);
  }
  
  // List of scores.
  private ArrayList<Score> list;
  // Path to save them to on close.
  private Path path;

  @Override
  public void close() throws IOException {
    // Use an ObjectOutputStream to serialize the ArrayList
    // to a file
    try (ObjectOutputStream out = new ObjectOutputStream(
      Files.newOutputStream(path, StandardOpenOption.CREATE, 
      StandardOpenOption.WRITE))) {
      out.writeObject(list);
    }
  }
}

// MAIN GAME STATE CLASS
// ==========================

/**
 * Class representing the global game state of "Save Desmond".
 */
class GameState {
  /**
   * Distance that the player can see.
   */
  private static final int SIGHT_DIST = 2;
  /**
   * Distance where the game will notify you about Desmond's position.
   */
  private static final int WARN_DIST = 5;
  /**
   * Width and height of the map grid.
   */
  private static final int MAP_SIZE = 20;
  /**
   * Grid distance from the home point that should be left clear
   * when spawning.
   */
  private static final int CLEAR_ZONE_SIZE = 3;
  
  /**
   * Map data for the game.
   * 'x' = wall
   * '!' = home point
   * ' ' = empty space
   */
  private static final char[][] MAP_BOARD = {
    {' ', ' ', ' ', 'x', ' ', ' ', ' ', 'x', ' ', ' ', ' ', ' ', ' ', 'x', ' ', ' ', ' ', ' ', ' ', ' '},
    {' ', ' ', ' ', 'x', ' ', ' ', ' ', 'x', ' ', ' ', ' ', ' ', ' ', 'x', ' ', ' ', ' ', ' ', ' ', ' '},
    {' ', ' ', ' ', 'x', ' ', ' ', ' ', 'x', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' '},
    {' ', ' ', ' ', 'x', 'x', ' ', ' ', 'x', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' '},
    {' ', ' ', ' ', ' ', 'x', ' ', ' ', 'x', ' ', ' ', ' ', ' ', ' ', 'x', 'x', 'x', 'x', 'x', 'x', 'x'},
    {' ', ' ', ' ', ' ', 'x', ' ', ' ', 'x', ' ', ' ', ' ', ' ', ' ', 'x', ' ', ' ', ' ', ' ', ' ', ' '},
    {' ', ' ', ' ', ' ', 'x', ' ', ' ', 'x', ' ', ' ', ' ', ' ', ' ', 'x', ' ', ' ', ' ', ' ', ' ', ' '},
    {' ', ' ', ' ', ' ', ' ', ' ', ' ', 'x', ' ', ' ', ' ', ' ', ' ', 'x', ' ', 'x', 'x', 'x', 'x', 'x'},
    {' ', ' ', ' ', ' ', ' ', ' ', ' ', 'x', ' ', ' ', ' ', ' ', ' ', 'x', ' ', ' ', ' ', ' ', ' ', ' '},
    {'x', 'x', 'x', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' '},
    {'!', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', 'x', 'x', 'x', 'x', 'x'},
    {'x', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' '},
    {'x', 'x', 'x', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' '},
    {' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', 'x', 'x', 'x', 'x', 'x', ' ', ' ', ' ', ' '},
    {' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', 'x', ' ', ' ', ' ', 'x', ' ', ' ', ' ', ' '},
    {' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', 'x', ' ', ' ', ' ', ' '},
    {' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', 'x', ' ', ' ', ' ', ' '},
    {' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', 'x', ' ', ' ', 'x', 'x', ' ', ' ', ' ', ' '},
    {'x', 'x', 'x', 'x', 'x', 'x', 'x', 'x', ' ', ' ', ' ', 'x', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' '},
    {'x', 'x', 'x', 'x', 'x', 'x', 'x', 'x', ' ', ' ', ' ', 'x', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' '},
  };
  
  /**
   * MASTER VARIABLE FOR DEBUG MODE.
   * SHOULD BE SET TO TRUE FOR THE SUBMITTED PROGRAM.
   */
  private static boolean debugEnabled = true;
  /**
   * The SHA-256 hash of the password.
   */
  private static final byte[] PASSWORD_HASH = {
    -49, 54, 126, -96, 67, -49, 101, -41, 
    -30, -91, 40, -58, -20, 104, -97, 42, 
    -61, 90, -71, -94, 32, -124, -93, 25, 
    -19, 78, -23, -13, -90, 115, 74, 13
  };
  
  /**
   * Tries to enable debug, using a password.
   * If debug mode is already enabled, does nothing.
   * If the password is incorrect, debug mode is not enabled.
   * @param password the password
   * @return whether debug mode was enabled.
   */
  public static boolean enableDebug(String password) {
    if (debugEnabled) {
      return true;
    }
    try {
      MessageDigest hasher = MessageDigest.getInstance("SHA-256");
      byte[] hash = hasher.digest(password.getBytes("UTF-8"));
      // SHA-256 hash is always 32 bytes, no need to check
      for (int i = 0; i < PASSWORD_HASH.length; i++) {
        if (hash[i] != PASSWORD_HASH[i]) {
          return false;
        }
      }
      debugEnabled = true;
      return true;
    } catch (Exception e) {
      Utils.printThrowable(e);
      System.out.println("Cannot enable debug mode.");
      return false;
    }
  }
  
  /**
   * Checks if debugging is enabled.
   * @return true if debugging is enabled, false otherwise
   */
  public static boolean isDebugEnabled() {
    return debugEnabled;
  }
  
  /**
   * Enum representing the status of the last win.
   */
  public enum Result {
    WIN,
    GAME_OVER
  }

  public GameState() {
    // Initialize helper objects and states
    this.running = false;
    this.lastResult = null;
    this.collision = initCollision();
    this.cmdParser = this.initCommandParser();
    // Don't initialize the game yet
    this.player = null;
    this.desmond = null;
    this.enemies = new ArrayList<>();
  }

  /**
   * Initializes the game.
   * @param name the name of the user
   */
  public void initGame(String name) {
    final int NUM_ZOMBIES = 15;
    
    // clear the visited map
    collision.clearVisited();
    // Set the home point as visited
    boolean[][] visited = collision.getVisited();
    Point homePoint = collision.getHomePoint();
    visited[homePoint.y][homePoint.x] = true;
    
    // Initialize player and Desmond
    player = new Player(this);
    desmond = new Desmond(this);
    // Initialize enemies
    enemies.clear();
    for (int i = 0; i < NUM_ZOMBIES; i++) {
      enemies.add(new Zombie(this));
    }
    
    // Reset the score and set the "running" flag
    running = true;
    points = 0;
    turnCounter = 0;
    // Set the user's name
    this.name = name;
  }
  
  /**
   * Performs one iteration of the game.
   */
  public void gameLoop() {
    // the inputted like
    String line;
    // the result of the last command
    int cmdResult = -1;
    // execute ONE command (excluding help)
    // help commands will not return 0, keeping the loop going
    do {
      // Display auxilliary info
      doAuxilliaryDisplay();
      // Read a command from the user
      System.out.print("Input command (\"help\" for help): ");
      line = Utils.readLine();
      try {
        // Try to execute it. If it throws an exception
        // or returns non-zero, prompt again.
        cmdResult = this.cmdParser.execute(line);
        // print a blank line for spacing
        System.out.println();
      } catch (CommandException e) {
        Utils.printThrowable(e);
        continue;
      }
    } while (cmdResult != 0);
    
    // check if force-win/lose happened
    if (!running) {
      return;
    }
    
    // perform update
    this.updateAllObjects();
    this.updateCounters();
    // print a blank line for spacing
    System.out.println();
  }
  
  /**
   * Triggers a game-over screen.
   */
  public void triggerGameOver() {
    // Clear the running flag and set the last result
    // for a game over
    running = false;
    lastResult = Result.GAME_OVER;
  }
  
  /**
   * Triggers a win screen.
   */
  public void triggerWin() {
    // Clear the running flag and set the last result
    // for a win
    running = false;
    lastResult = Result.WIN;
  }
  
  /**
   * Checks if the game is running.
   * @return true if the game is running, false otherwise
   */
  public boolean isRunning() {
    return running;
  }
  
  /**
   * Returns the result of the last game. May be null if
   * no game has been played yet.
   * @return the result of the last game, or null if no games have been played
   */
  public Result getLastResult() {
    return lastResult;
  }
  
  /**
   * Returns the {@link CommandParser} for this game state.
   * @return the {@link CommandParser} for this game state.
   */
  public CommandParser getParser() {
    return cmdParser;
  }
  
  /**
   * Returns the {@link CollisionMap} for this game state.
   * @return the {@link CollisionMap} for this game state.
   */
  public CollisionMap getCollision() {
    return collision;
  }
  
  /**
   * Returns the {@link GameEntity} representing the player.
   * @return the {@link GameEntity} representing the player.
   */
  public Player getPlayer() {
    return player;
  }
  
  /**
   * Returns the {@link GameEntity} representing Desmond.
   * @return the {@link GameEntity} representing Desmond.
   */
  public Desmond getDesmond() {
    return desmond;
  }
  
  /**
   * Returns the current saved name.
   * @return the current saved name.
   */
  public String getName() {
    return name;
  }
  
  /**
   * Returns the current saved score.
   * @return the score.
   */
  public int getPoints() {
    return points;
  }
  
  /**
   * Creates a new {@link Score} object using the last name and score
   * in this GameState.
   * @return the new Score object
   */
  public Score createScore() {
    return new Score(this.name, this.points);
  }
  
  /**
   * Checks if an enemy is at a particular position, and returns it if there is.
   * @param p the point to check.
   * @return the enemy at that position, or null if there is no enemy
   */
  public GameEntity checkEnemies(Point p) {
    // iterate over all enemies
    for (GameEntity enemy : enemies) {
      // If the current enemy is at the specified position, return it
      if (enemy.getPos().equals(p)) {
        return enemy;
      }
    }
    return null;
  }
  
  /**
   * Generates a spawning point that:
   * <ul>
   * <li>won't be on top of any walls</li>
   * <li>won't collide with any other spawned entities</li>
   * <li>won't be too close to the home point</li>
   * </ul>
   * @return a suitable spawning point.
   */
  public Point genSpawnPoint() {
    // home position
    int homeX = collision.getHomePoint().x, homeY = collision.getHomePoint().y;
    // width and height of the map
    int width = collision.width(), height = collision.height();
    // prospective spawning position
    int spawnX, spawnY;
    
    do {
      spawnX = Utils.randomInt(0, width);
      spawnY = Utils.randomInt(0, height);
      // There are lots of spawning conditions here...
      // should this be a function?
      // - out of the cle
    } while (!(
      Math.abs(spawnX - homeX) > CLEAR_ZONE_SIZE && 
      Math.abs(spawnY - homeY) > CLEAR_ZONE_SIZE &&
      !collision.collides(spawnX, spawnY) && 
      this.checkEnemies(new Point(spawnX, spawnY)) == null));
    
    return new Point(spawnX, spawnY);
  }
  
  public int getMusicState() {
    // absolute difference in X and Y between the player and Desmond
    int absDiffX, absDiffY;
    absDiffX = Math.abs(player.getPos().x - desmond.getPos().x);
    absDiffY = Math.abs(player.getPos().y - desmond.getPos().y);
    
    if (absDiffX > WARN_DIST || absDiffY > WARN_DIST) {
      // Desmond is outside warning distance
    	return 0;
    }
    else if (player.isHolding()) {
    	// Desmond is being carried
    	return 2;
    }
    else {
    	// Desmond is close, but not picked up
    	return 1;
    }
  }
  
  // collision map: handles interactions with walls
  private CollisionMap collision;
  // command parser: handles user input and translates it into actions
  private CommandParser cmdParser;
  
  // player: game entity for the robot
  private Player player;
  // desmond: game entity for Desmond
  private Desmond desmond;
  // enemies: list of game entites containing all zombies
  // and other enemies/dynamic obstacles
  private List<GameEntity> enemies;
  
  // true if the game is running
  private boolean running;
  // the result of the last game.
  private Result lastResult;
  
  // number of points scored
  private int points;
  // number of turns taken
  private int turnCounter;
  // user's name
  private String name;
  
  // Internal functions
  // =========================================
  
  /**
   * Creates and fills a 2D array of map tiles.
   * @return the 2D array of map tiles.
   */
  private char[][] buildMap() {
    // player X and Y position
    int playerX = this.player.getPos().x, playerY = this.player.getPos().y;
    // object X and Y position (this applies to whatever point is getting content)
    int objX, objY;
    // reference to the collision data.
    char[][] map = collision.getMap();
    // reference to the visited map used.
    boolean[][] visited = collision.getVisited();
    
    // the result array.
    char[][] result = new char[collision.height()][collision.width()];
    
    // Render tiles
    for (int y = 0; y < collision.height(); y++) {
      for (int x = 0; x < collision.width(); x++) {
        if (Math.abs(x - playerX) > SIGHT_DIST || Math.abs(y - playerY) > SIGHT_DIST) {
          // The tile is out of sight
          // Visited tiles render as $, unvisited tiles render as ?
          if (visited[y][x]) {
            result[y][x] = '$';
          }
          else {
            result[y][x] = '?';
          }
        }
        else {
          // anything in sight can be used as-is (either ' ' or 'x')
          result[y][x] = map[y][x];
        }
      }
    }
    
    // Render home point
    objX = collision.getHomePoint().x;
    objY = collision.getHomePoint().y;
    // check if home point is within the sight range
    if (Math.abs(objX - playerX) <= SIGHT_DIST && Math.abs(objY - playerY) <= SIGHT_DIST) {
      result[objY][objX] = '!';
    }
    
    // Render enemies
    for (GameEntity enemy : enemies) {
      objX = enemy.getPos().x;
      objY = enemy.getPos().y;
      // check if this enemy is within the sight range
      if (Math.abs(objX - playerX) <= SIGHT_DIST && Math.abs(objY - playerY) <= SIGHT_DIST) {
        result[objY][objX] = 'E';
      }
    }
    
    // Render Desmond
    objX = desmond.getPos().x;
    objY = desmond.getPos().y;
    // check if Desmond is within the sight range
    if (Math.abs(objX - playerX) <= SIGHT_DIST && Math.abs(objY - playerY) <= SIGHT_DIST) {
      result[objY][objX] = 'D';
    }
    
    // Render player
    // Player is always in sight range, since it defines the sight range
    result[playerY][playerX] = 'R';
    
    return result;
  }
  
  /**
   * Calls {@link GameState#buildMap()} to build a map, then displays it.
   */
  private void displayMap() {
    // the array from buildMap
    char[][] partMap = buildMap();
    // true if the player can pick up (or has picked up) Desmond
    boolean canPickUp = player.getPos().equals(desmond.getPos());
    
    // print column header
    System.out.print("   ");
    // print each column label
    for (int x = 0; x < partMap[0].length; x++) {
      System.out.printf("%2d ", x);
    }
    // end of line
    System.out.println();
    
    // main map printing loop
    for (int y = 0; y < partMap.length; y++) {
      // print the row label
      System.out.printf("%2d ", y);
      for (int x = 0; x < partMap[0].length; x++) {
        if (player.getPos().equals(new Point(x, y)) && canPickUp) {
          // Use curly brackets when the robot can pick up Desmond
          System.out.printf("{%c}", partMap[y][x]);
        }
        else if (collision.getVisited()[y][x]) {
          // Use round brackets when the player has visited this space.
          System.out.printf("(%c)", partMap[y][x]);
        }
        else {
          // Just print with square brackets
          System.out.printf("[%c]", partMap[y][x]);
        }
      }
      System.out.println();
    }
  }
  
  /**
   * Displays relevant information before the command prompt is shown.
   */
  private void doAuxilliaryDisplay() {
    // absolute difference in X and Y between the player and Desmond
    int absDiffX, absDiffY;
    absDiffX = Math.abs(player.getPos().x - desmond.getPos().x);
    absDiffY = Math.abs(player.getPos().y - desmond.getPos().y);
    
    // If the player is on top of Desmond and can pick him up
    if (absDiffX == 0 && absDiffY == 0) {
      if (player.isHolding()) {
        // Desmond follows the player, so this will always show when the player has Desmond
        System.out.println("You have Desmond! Get back to the front door.");
      }
      else {
        // Let the user know that Desmond can be picked up
        System.out.println("You can now pick up Desmond! Use the 'p' command.");
      }
    }
    // If Desmond is within "warning range" (out of sight, but still close-ish)
    else if ((absDiffX <= WARN_DIST) && (absDiffY <= WARN_DIST)) {
      // If Desmond is in "sight range" (visible on the map)
      if ((absDiffX <= SIGHT_DIST) && (absDiffY <= SIGHT_DIST)) {
        System.out.println("Desmond is in view. Look for the 'D' symbol on the map.");
      }
      else {
        // otherwise, he's outside
        System.out.println("Desmond is close, but not quite within sight.");
      }
    }
    else {
      // Desmond is not in the area you've been searching.
      System.out.println("Desmond isn't around these parts.");
    }
    // Display other auxilliary info
    System.out.println("Current coordinates: " + player.getPos());
    System.out.println("Home point: " + collision.getHomePoint());
    System.out.println("Turn number: " + turnCounter);
    // Display the map
    this.displayMap();
  }
  
  /**
   * Updates the game state of all objects.
   */
  public void updateAllObjects() {
    // update the player
    player.doTick(this);
    // update Desmond
    desmond.doTick(this);
    // update the enemies
    for (GameEntity enemy : enemies) {
      enemy.doTick(this);
    }
  }
  
  /**
   * Updates the score and turn counter.
   */
  private void updateCounters() {
    // playerPos: player's position
    // desmondPos: Desmond's position
    // diffPos: Vector subtraction between the two
    Point playerPos, desmondPos, diffPos;
    // turnValue: the points contributed by the current turn.
    int turnValue;
    
    playerPos = player.getPos();
    desmondPos = desmond.getPos();
    diffPos = playerPos.sub(desmondPos);
    
    // Turn value is set according to Manhattan distance,
    // as this represents the minimum number of single-tile
    // moves needed to reach Desmond.
    turnValue = Math.abs(diffPos.x) + Math.abs(diffPos.y);
    this.points += turnValue;
    
    // add one to the turn counter
    this.turnCounter++;
  }
  
  /**
   * Initializes the collision map and home point.
   * @return the collision map.
   */
  private static CollisionMap initCollision() {
    // the eventual collision map
    CollisionMap cMap = new CollisionMap(MAP_SIZE, MAP_SIZE);
    // This returns the collision map used by cMap.
    char[][] map = cMap.getMap();
    
    Point homePoint = null;
    
    // Copy pre-mapped walls
    for (int y = 0; y < map.length; y++) {
      for (int x = 0; x < map[y].length; x++) {
        // Special handling for '!', as it represents the home point
        if (MAP_BOARD[y][x] == '!') {
          map[y][x] = ' ';
          // if the home point wasn't set earlier, this is what it is.
          if (homePoint == null)
            homePoint = new Point(x, y);
        }
        else {
          // Copy the tile from the map verbatim.
          map[y][x] = MAP_BOARD[y][x];
        }
      }
    }
    // Set the home point found earlier
    cMap.setHomePoint(homePoint);
    
    return cMap;
  }
  
  // COMMAND IMPLEMENTATIONS
  // =========================================
  
  /**
   * Initializes the default command parser, where commands modify game state.
   * @return
   */
  private CommandParser initCommandParser() {
    // Create the command parser
    CommandParser res = new CommandParser();
    // Register all commands.
    // this::method is a method reference: Java automatically
    // converts it to an appropriate anonymous class calling
    // the method.
    res.registerCommand("help", this::cmdHelp);
    res.registerCommand("w", this::cmdMove);
    res.registerCommand("a", this::cmdMove);
    res.registerCommand("s", this::cmdMove);
    res.registerCommand("d", this::cmdMove);
    res.registerCommand("p", this::cmdPickup);
    res.registerCommand("debug", this::cmdDebug);
    res.registerCommand("give-up", this::cmdGiveUp);
    
    return res;
  }
  
  /**
   * Implementation of the "help" command.
   * @param args the arguments to the command
   * @return 1
   */
  private int cmdHelp(String[] args) {
    // sub-commands for help
    if (args.length == 2) {
      // Debugging help (only works if debugging is enabled)
      if (args[1].equals("debug")) {
        if (!debugEnabled) {
          System.out.println("Debugging is NOT enabled.");
          return 1;
        }
        System.out.println("Here's a list of commands for debugging:");
        System.out.println("========================================");
        System.out.println("debug desmond");
        System.out.println("  Prints Desmond's current grid coordinates.");
        System.out.println("debug force-win");
        System.out.println("  Magically causes you to win.");
        System.out.println("debug quit");
        System.out.println("  Immediately shuts down the program.");
        System.out.println("========================================");
        return 1;
      }
      // Legend for the map
      if (args[1].equals("legend")) {
        System.out.println("Here's a legend:");
        System.out.println("============================================================");
        System.out.println("BRACKETS");
        System.out.println("[ ] - unvisited");
        System.out.println("( ) - visited");
        System.out.println("{ } - interactable (i.e. robot can or has picked up Desmond)");
        System.out.println();
        System.out.println("TILES");
        System.out.println("? - unvisited and out of sight");
        System.out.println("$ - visited, but out of sight");
        System.out.println();
        System.out.println("R - robot");
        System.out.println("x - wall");
        System.out.println("! - front door");
        System.out.println("D - Desmond");
        System.out.println("E - enemy");
        System.out.println("============================================================");
        return 1;
      }
    }
    // regular help (commands)
    System.out.println("Here's a list of commands that you can issue to the robot:");
    System.out.println("==========================================================");
    System.out.println("w <dist>");
    System.out.println("  Try to move north by <dist> metres.");
    System.out.println("a <dist>");
    System.out.println("  Try to move west by <dist> metres.");
    System.out.println("s <dist>");
    System.out.println("  Try to move south by <dist> metres.");
    System.out.println("d <dist>");
    System.out.println("  Try to move east by <dist> metres.");
    System.out.println("NOTE 0: the robot can only move up to 3 metres at a time.");
    System.out.println("NOTE 1: if no distance is specified, the default is 1.");
    System.out.println();
    System.out.println("p");
    System.out.println("  Pick up Desmond. This only works if the robot and Desmond ");
    System.out.println("  are on the same tile (indicated using curly brackets {})");
    System.out.println();
    System.out.println("give-up");
    System.out.println("  Give up on this game. (then again, why would you??)");
    System.out.println();
    System.out.println("help");
    System.out.println("  Show the command list again, in case you forget.");
    System.out.println("help legend");
    System.out.println("  Show a legend of the map, in case you are confused.");
    System.out.println("help debug");
    System.out.println("  Show a list of debugging commands.");
    System.out.println("  (e.g. forcing a win, showing Desmond's position, etc...)");
    System.out.println("==========================================================");
    
    return 1;
  }
  
  /**
   * Implementation of the "w", "a", "s", and "d" commands.
   * @param args the arguments to the command
   * @return 0
   */
  private void cmdMove(String[] args) {
    final int MIN_DIST = 1;
    final int MAX_DIST = 3;
    
    int dist;
    // check if there are too many arguments
    if (args.length > 2) {
      String errorMessage = String.format(
        "Command %1$s only takes one argument (%1$s <dist>)", args[0]);
      throw new IllegalArgumentException(errorMessage);
    }
    
    // check if there are exactly two arguments
    // note that args[0] is always the command name
    if (args.length == 2) {
      // ensure that the specified distance is in range
      dist = Integer.parseInt(args[1]);
      if (dist < MIN_DIST || dist > MAX_DIST) {
        String errorMessage = String.format("The robot can only move %d-%d tiles. (got %d)", MIN_DIST, MAX_DIST, dist);
        throw new IllegalArgumentException(errorMessage);
      }
    }
    else {
      // default distance is 1, since that's intuitive
      dist = 1;
    }
    
    // use args[0] to check which direction to move
    // set the player's action accordingly
    switch (args[0].charAt(0)) {
    case 'w': {
      player.setAction(Player.Action.MOVE, 0, dist);
    } break;
    case 'a': {
      player.setAction(Player.Action.MOVE, 1, dist);  
    } break;
    case 's': {
      player.setAction(Player.Action.MOVE, 2, dist);
    } break;
    case 'd': {
      player.setAction(Player.Action.MOVE, 3, dist);
    } break;
    }
  }
  
  /**
   * Implementation of the "p" command.
   * @param args the arguments to the command
   * @return 0
   */
  private int cmdPickup(String[] args) {
    // check if there are too many arguments
    if (args.length != 1) {
      String errorMessage = String.format(
        "Command %1$s takes no arguments", args[0]);
      throw new IllegalArgumentException(errorMessage);
    }
    
    // set the player's action to pick up
    player.setAction(Player.Action.PICKUP);
    return 0;
  }
  
  /**
   * Implementation of the "debug" command.
   * @param args the arguments to the command
   * @return 0 or 1, depending on whether the sub-command needs to advance gameplay
   */
  private int cmdDebug(String[] args) {
    // Debug mode is a privilege, not a right (haha)
    if (!debugEnabled) {
      throw new IllegalStateException("Debug mode is not enabled.");
    }
    
    // Debug mode always takes a 2nd option
    if (args.length != 2) {
      String errorMessage = String.format(
        "Command %1$s only takes two arguments (%1$s <option...>); "
        + "see 'help debug' for more info", args[0]);
      throw new IllegalArgumentException(errorMessage);
    }
    
    if (args[1].equals("desmond")) {
      // print Desmond's position
      System.out.printf("Desmond's position is %s\n", desmond.getPos());
      return 1;
    }
    else if (args[1].equals("quit")) {
      // exit the game forcefully
      System.exit(0);
    }
    else if (args[1].equals("force-win")) {
      // instantly win the game
      this.triggerWin();
      return 0;
    }
    
    return 1;
  }
  
  private void cmdGiveUp(String[] args) {
    System.out.println("You just gave up on poor Desmond. How could you??");
    this.triggerGameOver();
  }
}


/**
 * Main class. Does not contain instance methods, as it
 * should not need to be instantiated.
 */
public class SaveDesmond {
  // List of main menu options.
  private static final String[] mainMenu = {
      "Play",
      "Leaderboard",
      "Enable Debugging",
      "Exit"
  };
  // Index of the "Play" option.
  private static final int PLAY_IDX = 0;
  // Index of the "Leaderboard" option.
  private static final int LEADERBOARD_IDX = 1;
  // Path to the leaderboard.
  private static final Path LEADERBOARD_PATH = Paths.get("./leaderboard.bin");
  // the global GameState instance.
  private static GameState gs = null;
  
  /**
   * Main method.
   * @param args command-line arguments, which will not be useful
   */
  public static void main(String[] args) {
    gs = new GameState();
    titleArt();
    
    // try-with-resources to managed lifetimes:
    // leaderboard (saved on close)
    // music engine (needs to be cleaned up)
    System.out.println("Initializing...");
    try (
      Leaderboard lb = new Leaderboard(LEADERBOARD_PATH);
      MusicEngine me = new MusicEngine();
    ) {
      while (true) {
        // main loop
        int choice = Utils.promptMenu("MAIN MENU", mainMenu);
        
        // Exit will always be the last option
        if (choice == mainMenu.length - 1) {
          break;
        }
        // Enable debugging will always be the 2nd last option
        else if (choice == mainMenu.length - 2) {
          enableDebug();
        }
        else {
          // select the appropriate option (play, leaderboard, etc.)
          switch (choice) {
            case PLAY_IDX: {
              play(lb, me);
            } break;
            case LEADERBOARD_IDX: {
              leaderboard(lb);
            } break;
          }
        }
      }
    }
    catch (IOException e) {
      throw new IOError(e);
    }
  }
  
  /**
   * Runs the game, then saves the resultant score to the leaderboard.
   * @param lb an open {@link Leaderboard} instance to save the score to
   */
  private static void play(Leaderboard lb, MusicEngine me) {
    String name;
    
    // Prompt for the user's name
    System.out.print ("What is your name? | ");
    name = Utils.readLine();
    System.out.printf("That's a nice name, %1$s.\n\n", name);
    
    // Give the user the opportunity to skip the intro
    System.out.print("I should tell you what's happened. (type anything to skip) ");
    
    // If the user did not say anything, give them the intro
    if (Utils.readLine().isEmpty()) {
      introText();
    }
    // Run the main game loop
    gs.initGame(name);
    me.start();
    while (gs.isRunning()) {
      gs.gameLoop();
      me.transition(gs.getMusicState());
    }
    me.stop();
    // Determine how the game ended, and react accordingly
    switch (gs.getLastResult()) {
    case WIN: {
      winScreen(lb);
    } break;
    case GAME_OVER: {
      gameOverScreen();
    } break;
    default: {
      throw new Error("Invalid win?");
    }
    }
  }
  
  /**
   * Displays the leaderboard.
   * @param lban open {@link Leaderboard} instance to read from
   */
  private static void leaderboard(Leaderboard lb) {
    final int NAME_WIDTH = 20;
    final int POINTS_WIDTH = 5;
    
    Score currScore;
    String currName;
    int currPoints;
    List<Score> scores;
    
    scores = lb.getAllScores();
    if (scores.isEmpty()) {
      System.out.println("No leaderboard data available...");
      return;
    }
    
    // table header
    System.out.printf("%-"+NAME_WIDTH+"s | %-"+POINTS_WIDTH+"s\n", "Name", "Score");
    System.out.printf("%s-+-%s\n", Utils.repeatString(NAME_WIDTH, "-"), 
      Utils.repeatString(POINTS_WIDTH, "-"));
    
    // table data
    for (int i = scores.size() - 1; i >= 0; i--) {
      currScore = scores.get(i);
      currName = currScore.getName();
      if (currName.length() > NAME_WIDTH) {
        currName = currName.substring(0, NAME_WIDTH - 3) + "...";
      }
      currPoints = currScore.getPoints();
      System.out.printf("%-"+NAME_WIDTH+"s | %"+POINTS_WIDTH+"d\n", currName, currPoints);
    }
  }
  
  /**
   * Tries to enable debugging.
   */
  private static void enableDebug() {
    String line;
    
    // Don't do the password thing if debugging is enabled
    if (GameState.isDebugEnabled()) {
      System.out.println("Debugging is already enabled!\n");
      return;
    }
    do {
      // Prompt for a password
      System.out.print("Password (type nothing to exit): ");
      line = Utils.readLine();
      // If the user typed nothing, exit
      if (line.isEmpty()) {
        break;
      }
      // Try to enable debugging, and if it succeeds, exit
      if (GameState.enableDebug(line)) {
        System.out.println("Debugging is enabled");
        break;
      }
    } while (true);
  }
  
  /**
   * Displays the lore and instructions.
   */
  private static void introText() {
    // first paragraph
    System.out.println("The year is 21XX. A zombie apocalypse has befallen humanity. You're lucky -- ");
    System.out.println("you made it to a safety shelter in time and have not been plagued. We've still");
    System.out.println("been on the lookout for more survivors though, and we have our sights set on a");
    System.out.println("local daycare. While most were evacuated from the daycare, one kid named Des-");
    System.out.println("-mond was on the potty at the time and missed the call. I'm convinced he's ");
    System.out.println("alive though. ");
    System.out.print("(Press Enter to continue)");
    Utils.readLine();
    System.out.println();
    
    // second paragraph
    System.out.println("You will be guiding a robot that we've dropped off at the front of the daycare.");
    System.out.println("Your job is to find Desmond, pick him up, and bring him back to the front of ");
    System.out.println("the daycare; all while avoiding the zombies inside the building. Points in this");
    System.out.println("game are added based on a) how many turns you take and b) how close you are to ");
    System.out.println("Desmond on each turn. The lower your score is, the better.");
    System.out.print("(Press Enter to continue)");
    Utils.readLine();
    System.out.println();
    
    // third paragraph
    System.out.println("To move the robot around, just use \"w\", \"a\", \"s\", and \"d\". Adding a number af-");
    System.out.println("-terwards, like \"s 2\" or \"d 3\", allow the robot to clear 2 or 3 tiles in one ");
    System.out.println("quick sprint. To pick up Desmond, move on top of him, then use\"p\".");
    System.out.print("(Press Enter to continue)");
    Utils.readLine();
    System.out.println();
  }
  
  /**
   * Displays the title art.
   */
  private static void titleArt() {
    /*
     * Title art looks like this. The escapes messed it up.
     * ==========================================================================
     * /----   8   |   | +-----      +---\  +----- /---- \   /  /=\  |\  | +---\  
     * |      / \  |   | |           |    | |      |     |\ /| /   \ | | | |    |
     * \---\ /   \ \   / +-----      |    | +----- \---\ | v | |   | | | | |    |
     *     | |---|  \ /  |           |    | |          | |   | \   / | | | |    |
     * ----/ |   |   v   +-----      +---/  +----- ----/ |   |  \=/  |  \| +---/ 
     * ==========================================================================
     */
    
    // Display the title art.
    System.out.println("==========================================================================");
    System.out.println("/----   8   |   | +-----      +---\\  +----- /---- \\   /  /=\\  |\\  | +---\\ ");
    System.out.println("|      / \\  |   | |           |    | |      |     |\\ /| /   \\ | | | |    |");
    System.out.println("\\---\\ /   \\ \\   / +-----      |    | +----- \\---\\ | v | |   | | | | |    |");
    System.out.println("    | |---|  \\ /  |           |    | |          | |   | \\   / | | | |    |");
    System.out.println("----/ |   |   v   +-----      +---/  +----- ----/ |   |  \\=/  |  \\| +---/ ");
    System.out.println("==========================================================================");
    System.out.println("                               By Jacky Guo                               ");
    System.out.println();
  }

  /**
   * Displays a win screen, then saves the current score to the leaderboard.
   * @param lb an open {@link Leaderboard} instance to save the score to
   */
  private static void winScreen(Leaderboard lb) {
    /*
    This is what it should look like:
    \   /  /=\  |   |      |   |  /=\  |\  |
     \ /  /   \ |   |      |   | /   \ | | |
      Y   |   | |   |      | 8 | |   | | | |
      |   \   / |   |      |/ \| \   / | | |
      |    \=/   \=/       /   \  \=/  |  \|
    */
    System.out.println("=========================================");
    System.out.println("\\   /  /=\\  |   |      |   |  /=\\  |\\  |");
    System.out.println(" \\ /  /   \\ |   |      |   | /   \\ | | |");
    System.out.println("  Y   |   | |   |      | 8 | |   | | | |");
    System.out.println("  |   \\   / |   |      |/ \\| \\   / | | |");
    System.out.println("  |    \\=/   \\=/       /   \\  \\=/  |  \\|");
    System.out.println("=========================================");
    System.out.printf("Score: %d\n", gs.getPoints());
    
    // Give a short epilogue
    System.out.printf("Thank you for getting him out safely, %s. His parents have been\n", gs.getName());
    System.out.println("waiting for so long, and they've been anxiously waiting to see him.");
    System.out.println("(You hear Desmond rushing towards his parents, anxious to hug his mom and dad.)");
    // add score to leaderboard
    lb.addScore(gs.createScore());
  }
  
  /**
   * Displays a game over screen.
   */
  private static void gameOverScreen() {
    /*
    This is what it should look like:
     /---   8   \   / +-----       /=\  |   | +----- +===\
    /      / \  |\ /| |           /   \ |   | |      |   |
    |   + /   \ | V | +-----      |   | \   / +----- +===/
    \   | |---| |   | |           \   /  \ /  |      |\__ 
     \--+ |   | |   | +-----       \=/    V   +----- |   \
    */
    // Display the game over screen.
    System.out.println("======================================================");
    System.out.println(" /---   8   \\   / +-----       /=\\  |   | +----- +===\\");
    System.out.println("/      / \\  |\\ /| |           /   \\ |   | |      |   |");
    System.out.println("|   + /   \\ | v | +-----      |   | \\   / +----- +===/");
    System.out.println("\\   | |---| |   | |           \\   /  \\ /  |      |\\__ ");
    System.out.println(" \\--+ |   | |   | +-----       \\=/    v   +----- |   \\");
    System.out.println("======================================================");
  }

}
