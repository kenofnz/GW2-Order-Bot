package com.kenbots.gw2bot;

import static com.kenbots.gw2bot.Main.API_KEY;
import static com.kenbots.gw2bot.Main.GW2API;
import java.awt.AWTException;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;
import me.nithanim.gw2api.v2.api.commerce.transactions.Transaction;

public class OrderBot {

    public static String itemName = "Mithril Ore";
    public static int itemAmount = 1;
    public static int priceGold = 0, priceSilver = 0, priceCopper = 1;

    public static void clickForge() {
        try {
            Robot robot = new Robot();
            for (int i = 0; i < 35; i++) {
                clickOnPoint(robot, 125, 135);
                robot.delay(15);
                clickOnPoint(robot, 125, 135);

                clickOnPoint(robot, 190, 135);
                robot.delay(15);
                clickOnPoint(robot, 190, 135);

                clickOnPoint(robot, 250, 135);
                robot.delay(15);
                clickOnPoint(robot, 250, 135);

                clickOnPoint(robot, 315, 135);
                robot.delay(15);
                clickOnPoint(robot, 315, 135);

                clickOnPoint(robot, 650, 590);
                robot.delay(2000);
            }
        } catch (AWTException ex) {
            Logger.getLogger(OrderBot.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public static void createBuyOrderForList(Collection<Listing> listings) {
        createBuyOrderForList(listings, 0);
    }

    public static void createBuyOrderForList(Collection<Listing> listings, int buyOptions) {
        System.out.println("Creating Buy Orders for ");
        listings.forEach((Listing listing) -> {
            int copper;
            int silver;
            int gold;
            int newPrice = listing.getPrice() + 1;
            copper = newPrice % 100;
            silver = (newPrice / 100) % 100;
            gold = (newPrice / 10000);
            System.out.format("%40s%2s%15s%n", listing.getName(), "@", gold + "g " + silver + "s " + copper + "c");
        });
        System.out.println();

        listings.forEach((Listing listing) -> {
            System.out.println("Listing new Buy Offer for " + listing.getName() + "...");
            int copper;
            int silver;
            int gold;
            int newPrice = listing.getPrice() + 1;
            copper = newPrice % 100;
            silver = (newPrice / 100) % 100;
            gold = (newPrice / 10000);

            OrderBot.setOrderBotSettings(listing.getName(), listing.getQuantity(), 1, copper, silver, gold);
            OrderBot.startPlacingBuyOrders(buyOptions);
        });
        System.out.println();
    }

    public static void startPlacingBuyOrders() {
        startPlacingBuyOrders(0);
    }

    public static void startPlacingBuyOrders(int buyOptions) {
        try {
            int extraY = (itemName.length() > 35) ? 15 : 0;
            System.out.println("Placing Buy Orders in 2 seconds...");
            Robot robot = new Robot();

            robot.delay(2000);
            clearAndOpenTradingPost(robot);

            if (buyOptions == 1) {
                clickOnPoint(robot, 285, 190);
                robot.delay(100);
                clickOnPoint(robot, 140, 230);
                robot.delay(100);
                clickOnPoint(robot, 100, 350);
                robot.delay(100);
                clickOnPoint(robot, 232, 288);
            } else if (buyOptions == 2) {
                clickOnPoint(robot, 118, 229);
                robot.delay(200);
                clickOnPoint(robot, 120, 260);
                robot.delay(200);
                clickOnPoint(robot, 285, 190);
                robot.delay(200);
                clickOnPoint(robot, 130, 361);
                robot.delay(200);
                clickOnPoint(robot, 95, 500);
                robot.delay(200);
                clickOnPoint(robot, 86, 464);
                robot.delay(200);
                selectAll(robot);
                enterString(robot, Integer.toString(80));
                clickOnPoint(robot, 285, 190);
                robot.delay(200);
            }

            // Click Search bar for Item
            System.out.println("Searching for " + itemName + "...");
            clickOnPoint(robot, 130, 190);

            // Enter Item name
            if (itemName.length() > 30) {
                int midIndex = itemName.length() / 2;
                int startIndex = midIndex - 15;
                int endIndex = midIndex + 15;
                itemName = itemName.substring(startIndex, endIndex);
            }
            enterString(robot, itemName);
            robot.delay(6000);

            // Click on first item result
            clickOnPoint(robot, 530, 275);
            robot.delay(1000);
            int i = 0;
            for (int quantity = itemAmount; quantity > 0; quantity -= 250) {

                System.out.println("Entering Price...");
                // Place Copper Price
                clickOnPoint(robot, 550, 300 + extraY);
                selectAll(robot);
                enterString(robot, Integer.toString(priceCopper));

                // Place Silver Price
                clickOnPoint(robot, 489, 300 + extraY);
                selectAll(robot);
                enterString(robot, Integer.toString(priceSilver));

                // Place Gold Price
                clickOnPoint(robot, 400, 300 + extraY);
                selectAll(robot);
                enterString(robot, Integer.toString(priceGold));
                System.out.println("Entering Quantity...");
                // Enter Amount
                clickOnPoint(robot, 390, 260 + extraY);
                selectAll(robot);
                enterString(robot, Integer.toString((quantity > 250) ? 250 : quantity));

                System.out.println("Placing Order " + (i + 1) + "...");
                // Click on Place Order
                clickOnPoint(robot, 430, 390 + extraY);
                robot.delay(1500);

                // Click Ok
                clickOnPoint(robot, 430, 390 + extraY);
                robot.delay(700 + (int) (Math.random() * 100));
                i++;
            }

            // Close TP
            robot.keyPress(KeyEvent.VK_ESCAPE);
            robot.keyRelease(KeyEvent.VK_ESCAPE);

            System.out.println("Finished placing order");
            System.out.println();
        } catch (AWTException ex) {
            Logger.getLogger(OrderBot.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public static void cancelBuyOrders() {
        try {
            System.out.println("Canceling Buy Orders in 3 seconds...");
            Robot robot = new Robot();

            robot.delay(3000);
            clearAndOpenTradingPost(robot);

            // Click transactions
            clickOnPoint(robot, 855, 115);
            robot.delay(100);

            // Click Buy Order Tabs
            clickOnPoint(robot, 135, 265);
            robot.delay(2000);
            // Find number of buy orders
            Transaction[] transactions = GW2API.commerce().transactions().currentBuys(API_KEY);

            // Cancel Buy Orders
            for (Transaction transaction : transactions) {
                // Click on Cancel
                clickOnPoint(robot, 885, 265);
                robot.delay(200);
                // Click on Confirm
                clickOnPoint(robot, 885, 265);
                robot.delay(600);
            }
            // Close TP
            robot.keyPress(KeyEvent.VK_ESCAPE);
            robot.keyRelease(KeyEvent.VK_ESCAPE);
        } catch (AWTException ex) {
            Logger.getLogger(OrderBot.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private static void clearAndOpenTradingPost(Robot robot) {
        // Click into GW and clear
        clickOnPoint(robot, 50, 210);
        for (int i = 0; i < 10; i++) {
            robot.keyPress(KeyEvent.VK_ESCAPE);
            robot.keyRelease(KeyEvent.VK_ESCAPE);
            robot.delay(30);
        }
        robot.delay(500);

        System.out.println("Opening Trading Post...");
        // Open Lion Merchant
        robot.keyPress(KeyEvent.VK_O);
        robot.keyRelease(KeyEvent.VK_O);
        robot.delay(2000);

        // Go to TP
        clickOnPoint(robot, 25, 210);
        robot.delay(4000);
    }

    private static void clearAndOpenTradingPostViaMerchant(Robot robot) {
        // Click into GW and clear
        clickOnPoint(robot, 50, 210);
        for (int i = 0; i < 10; i++) {
            robot.keyPress(KeyEvent.VK_ESCAPE);
            robot.keyRelease(KeyEvent.VK_ESCAPE);
            robot.delay(30);
        }
        robot.delay(500);

        System.out.println("Opening Trading Post...");
        // Open Lion Merchant
        robot.keyPress(KeyEvent.VK_F);
        robot.keyRelease(KeyEvent.VK_F);
        robot.delay(1000);
    }

    private static void selectAll(Robot robot) {
        robot.keyPress(KeyEvent.VK_CONTROL);
        robot.keyPress(KeyEvent.VK_A);
        robot.delay(60);
        robot.keyRelease(KeyEvent.VK_CONTROL);
        robot.keyRelease(KeyEvent.VK_A);
        robot.delay(60);
    }

    private static void clickOnPoint(Robot robot, int x, int y) {
        robot.mouseMove(x, y);
        robot.mousePress(InputEvent.BUTTON1_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_MASK);
        robot.delay((int) (60 + Math.random() * 10));
    }

    private static void enterString(Robot robot, String text) {
        Toolkit toolkit = Toolkit.getDefaultToolkit();
        Clipboard clipboard = toolkit.getSystemClipboard();
        StringSelection strSel = new StringSelection(text);
        clipboard.setContents(strSel, null);
        robot.keyPress(KeyEvent.VK_CONTROL);
        robot.keyPress(KeyEvent.VK_V);
        robot.delay(60);
        robot.keyRelease(KeyEvent.VK_CONTROL);
        robot.keyRelease(KeyEvent.VK_V);
        robot.delay(60);
        robot.delay((int) (50 + Math.random() * 10));
    }

    public static void takeAllDeliveryBox() {
        try {
            Robot robot = new Robot();
            System.out.println("Opening Trading Post with F");
            clearAndOpenTradingPostViaMerchant(robot);

            // Click Take All
            System.out.println("Taking Delivery Box Contents...");
            clickOnPoint(robot, 185, 690);
            robot.delay(500);

            // Close TP
            robot.keyPress(KeyEvent.VK_ESCAPE);
            robot.keyRelease(KeyEvent.VK_ESCAPE);
        } catch (AWTException ex) {
            Logger.getLogger(OrderBot.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public static void setOrderBotSettings(String itemName, int itemAmount, int repeats, int copper, int silver, int gold) {
        System.out.println("Setting Order Bot Settings...");
        OrderBot.itemName = itemName;
        OrderBot.itemAmount = itemAmount;
        OrderBot.priceCopper = copper;
        OrderBot.priceSilver = silver;
        OrderBot.priceGold = gold;

        System.out.println("Current Item Name: " + OrderBot.itemName);
        System.out.println("Current Order Amount: " + OrderBot.itemAmount);
        System.out.println("Current Price: " + OrderBot.priceGold + "g " + OrderBot.priceSilver + "s " + OrderBot.priceCopper + "c");
        System.out.println();
    }
}
