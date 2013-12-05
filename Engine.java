import java.util.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.util.List;

public class Engine
{
    static int MIN_X;
    static int MIN_Y;
    static final int WIDTH = 750;
    static final int HEIGHT = 500;

    static final int HIGHEST_LINE = 20;
    static final int LOWEST_LINE = 450;
    static final int MAX_RABBIT_HEIGHT = 140;

    // used in getItems()
    static final int DIF = 5;
    static final int MIN_BLOB_SIZE = 25;
    static final int MIN_BLOB_SQUARE = 5;

    // used in pickTarget()
    static final int BELL_SEPARATION = 80;

    // used in moveMouse()
    static final int MARGIN = 5;
    static final int CLOSING_RATIO = 5;

    static int bellWidth = 30, bellArea = 640;

    static Robot r;
    static long startTime;
    static BufferedImage image;
    static List<Item> items;
    static int targetX;
    static Item bunny, bird;

    public static void main(String ... bobby) throws Exception
    {
        r = new Robot();
        r.delay(5000);

        setScreenBounds(r.createScreenCapture(
                    new Rectangle(Toolkit.getDefaultToolkit().getScreenSize())));

        r.mousePress(InputEvent.BUTTON1_MASK);

        startTime = System.currentTimeMillis();
        int counter = 1;

        while (true)
        {
            image = r.createScreenCapture(new Rectangle(MIN_X, MIN_Y, WIDTH, HEIGHT));

            if (gameOver(image))
                break;

            getItems();
            updateBellData();

            if (!checkValidity())
                continue;

            findBunny();

            Item target = pickTarget(items, bunny);
            targetX = target.centerX;
            if (target.type == Item.BIRD)
                adjustForBird();

            findBird();

            moveMouse();

            System.out.println("FRAME " + counter);
            System.out.println("Items: " +  items);
            System.out.println("targeting " + target.centerX);
            System.out.println();                        
            counter++;
        }
    }

    static void setScreenBounds(BufferedImage image)
    {
        int[] xs = new int[image.getWidth()];
        int[] ys = new int[image.getHeight()];
        for (int x = 0; x < image.getWidth(); x++)
            for (int y = 0; y < image.getHeight(); y++)
                if (image.getRGB(x, y) == 0xff073954)
                {
                    xs[x]++;
                    ys[y]++;
                }

        for (int x = 0; x < image.getWidth(); x++)
            if (xs[x] > HEIGHT - 5)
            {
                MIN_X = x;
                break;
            }
        for (int y = 0; y < image.getHeight(); y++)
            if (ys[y] > WIDTH - 5)
            {
                MIN_Y = y;
                break;
            }
        System.out.println(MIN_X + " " + MIN_Y);
    }

    static boolean gameOver(BufferedImage image)
    {
        if (System.currentTimeMillis() - startTime <= 5000)
        {
            System.out.println(System.currentTimeMillis() + " " + startTime);
            return false;
        }

        int count = 0;

        for (int x = 0; x < image.getWidth(); x += 5)
            if (isLight(image.getRGB(x, image.getHeight() - 5)))
                count++;

        return count > .75 * (image.getWidth() / 5);
    }

    static void getItems()
    {
        items = new ArrayList<Item>();

        int[][] table = new int[image.getWidth() + 1][image.getHeight() + 1];
        for (int x = 0; x < image.getWidth(); x++)
            for (int y = 0; y < image.getHeight(); y++)
                if (isLight(image.getRGB(x, y)))
                    table[x + 1][y + 1] = Math.min(Math.min(table[x][y + 1], table[x + 1][y]), table[x][y]) + 1;

        boolean[][] visited = new boolean[image.getWidth()][image.getHeight()];
        for (int x = 0; x < image.getWidth(); x += DIF)
            for (int y = 0; y < image.getHeight(); y += DIF)
                if (isLight(image.getRGB(x, y)) && !visited[x][y])
                {
                    List<Point> points = new ArrayList<Point>();
                    List<Point> currentPoints = new ArrayList<Point>();
                    boolean foundSquare = false;
                    currentPoints.add(new Point(x, y));

                    while (!currentPoints.isEmpty())
                    {
                        List<Point> newPoints = new ArrayList<Point>();

                        for (Point p : currentPoints)
                            if (inBounds(image, p.x, p.y) && isLight(image.getRGB(p.x, p.y)) && !visited[p.x][p.y])
                            {
                                visited[p.x][p.y] = true;
                                if (table[p.x + 1][p.y + 1] >= MIN_BLOB_SQUARE)
                                    foundSquare = true;

                                points.add(p);
                                newPoints.add(new Point(p.x - 1, p.y));
                                newPoints.add(new Point(p.x + 1, p.y));
                                newPoints.add(new Point(p.x, p.y - 1));
                                newPoints.add(new Point(p.x, p.y + 1));
                            }

                        currentPoints = newPoints;                                                
                    }

                    if (foundSquare && points.size() >= MIN_BLOB_SIZE)
                        items.add(new Item(points));
                }

        for (Item item : items)
            recognizeItem(item);

        Collections.sort(items);
        while (!items.isEmpty() && items.get(0).centerY < HIGHEST_LINE)
            items.remove(0);
        while (!items.isEmpty() && items.get(items.size() - 1).centerY > LOWEST_LINE)
            items.remove(items.size() - 1);
    }

    private static void recognizeItem(Item item)
    {
        if (item.area() > 700)
            return;

        int[] strikes = new int[4];
        int diff = diff(image, item);
        if (Math.abs(item.width() - bellWidth) > 3) strikes[Item.BELL]++;
        if (Math.abs(item.width() - bellWidth) > 6) strikes[Item.BELL] += 2;
        if (item.width() < 29 || item.width() > 33) strikes[Item.BIRD]++;
        if (item.width() < 26 || item.width() > 36) strikes[Item.BIRD] += 2;
        if (item.width() < 25) strikes[Item.BUNNY]++;
        if (item.width() < 22) strikes[Item.BUNNY] += 2;
        if (diff > 100) strikes[Item.BELL] += ((diff - 50) / 50) * ((diff - 50) / 50);
        if (diff < 100) strikes[Item.BIRD] += ((150 - diff) / 50) * ((150 - diff) / 50);
        if (diff < 250) strikes[Item.BUNNY] += ((300 - diff) / 50) * ((300 - diff) / 50);
        if (item.ratio() < .95) strikes[Item.BELL]++;
        if (item.ratio() < .5) strikes[Item.BELL] += 3;
        if (item.ratio() > 1.05) strikes[Item.BELL]++;
        if (item.ratio() > .8) strikes[Item.BIRD]++;
        if (item.height() > 20) strikes[Item.BIRD]++;
        if (item.area() < bellArea - 6) strikes[Item.BELL]++;
        if (item.area() > bellArea + 6) strikes[Item.BELL] += 3;
        if (item.area() < 260) strikes[Item.BIRD]++;
        if (item.area() > 440) strikes[Item.BIRD] += 3;
        if (item.area() < 450) strikes[Item.BUNNY]++;
        if (item.area() < 390) strikes[Item.BUNNY] += 2;

        int min = Item.BELL;
        if (strikes[Item.BIRD] < strikes[min]) min = Item.BIRD;
        if (strikes[Item.BUNNY] < strikes[min]) min = Item.BUNNY;
        item.type = min;
    }

    private static int diff(BufferedImage image, Item item)
    {
        int totalDiff = 0, lastStretch = 0;

        for (int y = item.minY; y < item.maxY - 3; y++)
        {
            int minLight = -1, maxLight = -1;
            for (int x = item.minX; x <= item.maxX; x++)
                if (isLight(image.getRGB(x, y)))
                {
                    if (minLight == -1)
                        minLight = x;
                    maxLight = x;
                }
            for (int x = minLight; x <= maxLight; x++)
                if (!isLight(image.getRGB(x, y)))
                    totalDiff += item.width() / (Math.abs(x - item.centerX) + 1);

            int stretch = maxLight - minLight + 1;
            if (stretch < lastStretch)
                totalDiff += lastStretch - stretch;
            int offLine = Math.abs(item.centerX - (maxLight + minLight) / 2);
            if (offLine > 4)
                totalDiff += 2 * offLine;
            lastStretch = stretch;
        }

        return totalDiff;
    }

    static void updateBellData()
    {
        for (Item item : items)
            if (item.type == Item.BELL)
            {
                if (Math.abs(item.width() - bellWidth) < 3)
                    bellWidth = item.width();
                if (Math.abs(item.area() - bellArea) < 6)
                    bellArea = item.area();
            }
    }

    static boolean checkValidity()
    {
        if (items.size() <= 1)
            return false;

        int numBunnies = 0, numUnsures = 0;

        for (Item item : items)
        {
            if (item.type == Item.BUNNY)
                numBunnies++;
            else if (item.type == Item.UNSURE)
            {
                if (item.centerY < MAX_RABBIT_HEIGHT)
                    return false;

                numUnsures++;
            }
        }

        return numBunnies + numUnsures == 1;
    }

    static void findBunny()
    {
        for (int i = 0; i < items.size(); i++)
        {
            if (items.get(i).type == Item.BUNNY)
                bunny = items.remove(i);
            else if (items.get(i).type == Item.UNSURE)
                bunny = items.get(i);
        }
    }

    static void findBird()
    {
        for (Item item : items)
            if (item.type == Item.BIRD)
                bird = item;
    }

    static Item pickTarget(List<Item> items, Item bunny)
    {
        int index = 0;
        while (index < items.size() && items.get(index).centerY < bunny.centerY - .6 * BELL_SEPARATION)
            index++;

        if (index >= items.size())
            return items.get(items.size() - 1);

        while (index + 1 < items.size())
        {
            if (items.get(index + 1).centerY > items.get(index).centerY + 1.2 * BELL_SEPARATION)
                break;

            index++;
        }

        return items.get(index);
    }

    static void adjustForBird()
    {
        if (targetX > bird.centerX)
            targetX += 20;
        else if (targetX < bird.centerX)
            targetX -= 20;
    }

    static void moveMouse()
    {
        int mouseX = ((CLOSING_RATIO + 1) * targetX - bunny.centerX) / CLOSING_RATIO;

        if (mouseX < MARGIN)
            mouseX = MARGIN;
        else if (mouseX > WIDTH - MARGIN)
            mouseX = WIDTH - MARGIN;

        r.mouseMove(MIN_X + mouseX, MIN_Y + HEIGHT / 2);
    }

    private static boolean inBounds(BufferedImage image, int x, int y)
    {
        return x >= 0 && x < image.getWidth() && y >= 0 && y < image.getHeight();
    }

    private static boolean isLight(int rgb)
    {
        return ((rgb & 0x000000ff) + ((rgb & 0x0000ff00) >> 8) + ((rgb & 0x00ff0000) >> 16)) > 500;
    }
}


class Item implements Comparable<Item>
{
    static final String[] TYPES = {"UNSURE", "BUNNY", "BIRD", "BELL"};
    static final int UNSURE = 0;
    static final int BUNNY = 1;
    static final int BIRD = 2;
    static final int BELL = 3;

    List<Point> points;
    int centerX, centerY;
    int minX, minY, maxX, maxY;
    int type;

    Item(List<Point> points)
    {
        this.points = points;

        if (points.size() == 0)
            return;

        minX = minY = Integer.MAX_VALUE;
        maxX = maxY = Integer.MIN_VALUE;

        for (Point p : points)
        {
            centerX += p.x;
            centerY += p.y;
            if (p.x < minX) minX = p.x;
            if (p.y < minY) minY = p.y;
            if (p.x > maxX) maxX = p.x;
            if (p.y > maxY) maxY = p.y;
        }

        centerX /= points.size();
        centerY /= points.size();
    }

    int width()
    {
        return maxX - minX;
    }

    int height()
    {
        return maxY - minY;
    }

    double ratio()
    {
        return (double)height() / width();
    }

    int area()
    {
        return points.size();
    }

    public int compareTo(Item other)
    {
        return centerY - other.centerY;
    }

    public String toString()
    {
        return TYPES[type] + " at (" + centerX + ", " + centerY + "), A=" + area();
    }
}
