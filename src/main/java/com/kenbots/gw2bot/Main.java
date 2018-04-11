package com.kenbots.gw2bot;

import com.kenbots.gw2bot.search.ItemSearch;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import me.nithanim.gw2api.v2.GuildWars2Api;
import me.nithanim.gw2api.v2.api.account.CurrencyBelonging;
import me.nithanim.gw2api.v2.api.commerce.transactions.Transaction;
import static spark.Spark.*;

public class Main {

    public static final GuildWars2Api GW2API = new GuildWars2Api();
    public static String API_KEY;
    public static boolean SIMPLE_MENU = true;

    public static void main(String[] args) {

        if (args.length > 1) {
            port(Integer.parseInt(args[0]));
            get("/find-flips", (req, res) -> {
                double profitLimit = (req.queryParams("marginLimit") != null) ? Double.parseDouble(req.queryParams("marginLimit")) : 0.75;
                int minSupply = (req.queryParams("supply") != null) ? Integer.parseInt(req.queryParams("supply")) : 500;
                int minDemand = (req.queryParams("demand") != null) ? Integer.parseInt(req.queryParams("demand")) : 500;
                int minProfit = (req.queryParams("minProfit") != null) ? Integer.parseInt(req.queryParams("minProfit")) : 300;

                return "<pre><code>" + ItemSearch.findFlipItems(profitLimit, minDemand, minSupply, minProfit) + "</code></pre>";
            });

            get("/crafts-profits", (req, res) -> {
                return "<pre><code>" + ItemSearch.getCraftingProfit(false) + "</code></pre>";
            });

            get("/leather-flips", (req, res) -> {
                return "<pre><code>" + ItemSearch.getMediumCoatForLeather() + "</code></pre>";
            });
            get("/refresh-listings", (req, res) -> {
                API_KEY = "B3A78DA6-238D-114C-A27B-8D4667E8F93805B0F7FD-2939-4669-BF09-E6E67ADD6BCA";
                String response = getListings(true) + "\n";
                response += getListings(false) + "\n";
                response += getListings(true) + "\n";
                response += getListings(false) + "\n";
                return "<pre><code>" + (response += "Listings refreshed") + "</code></pre>";
            });
        } else {
            API_KEY = args[0];
            ItemSearch.reloadFlipSettings();
            Scanner scanner = new Scanner(System.in);
            String header
                    = "+----+----------------------------+----+------------------------------------------------------+----+------------------------+----+----------------------------+\n"
                    + "|    |            Basic           |    |                      Flip Items                      |    |   Greatsword Crafting  |    |       Ecto Salvaging       |\n"
                    + "+----+----------------------------+----+------------------------------------------------------+----+------------------------+----+----------------------------+\n"
                    + "|  1 | Show Wallet                |  5 | Search for items to flip                             | 12 | Buy crafting materials | 14 | Find rares worth salvaging |\n"
                    + "+----+----------------------------+----+------------------------------------------------------+----+------------------------+----+----------------------------+\n"
                    + "|  2 | Show Trading Post Listings |  6 | Watchlist items data                                 | 13 | Check on supply        | 15 | Create Buy Order for rares |\n"
                    + "+----+----------------------------+----+------------------------------------------------------+----+------------------------+----+----------------------------+\n"
                    + "|  3 | Activate Order Bot         |  7 | Current Buy Order items data                         |    |                        |    |                            |\n"
                    + "+----+----------------------------+----+------------------------------------------------------+----+------------------------+----+----------------------------+\n"
                    + "|  4 | Order Bot Settings         |  8 | Cancel and create new Buy Order for profitable items |    |                        |    |                            |\n"
                    + "+----+----------------------------+----+------------------------------------------------------+----+------------------------+----+----------------------------+\n"
                    + "|    |                            |  9 | Create Buy Order for profitable items on watchlist   |    |                        |    |                            |\n"
                    + "+----+----------------------------+----+------------------------------------------------------+----+------------------------+----+----------------------------+\n"
                    + "|    |                            | 10 | Cancel all Buy Orders                                |    |                        |    |                            |\n"
                    + "+----+----------------------------+----+------------------------------------------------------+----+------------------------+----+----------------------------+\n"
                    + "|    |                            | 11 | Take All Delivery Box                                |    |                        |    |                            |\n"
                    + "+----+----------------------------+----+------------------------------------------------------+----+------------------------+----+----------------------------+\n"
                    + "|                                                                       Crafting Profits                                                                      |\n"
                    + "+-------------------------------------------------------------------------------------------------------------------------------------------------------------+\n"
                    + "| 16 | Crafting with Buy Order ingredients                                                                                                                    |\n"
                    + "+----+--------------------------------------------------------------------------------------------------------------------------------------------------------+\n"
                    + "| 17 | Crafting with Instant Sell ingredients                                                                                                                 |\n"
                    + "+----+--------------------------------------------------------------------------------------------------------------------------------------------------------+\n"
                    + "| 18 | Medium Leather Salvage data                                                                                                                            |\n"
                    + "+----+--------------------------------------------------------------------------------------------------------------------------------------------------------+\n"
                    + "Q. Exit\n";

            if (SIMPLE_MENU) {
                header = "2  | Show Trading Post Listings\n"
                        + "5  | Search for items to flip\n"
                        + "16 | Crafting with Buy Order ingredients\n"
                        + "18 | Medium Leather Salvage data\n"
                        + "Q  | Exit\n";
            }

            while (true) {
                try {

                    System.out.println(header);
                    System.out.print("Enter Option : ");
                    String input = scanner.nextLine();

                    switch (input) {
                        case "q":
                            System.out.println("Exiting!");
                            System.exit(0);
                            break;
                        case "1":
                            getWallet();
                            break;
                        case "2":
                            getListings(true);
                            getListings(false);
                            break;
                        case "4":
                            setOrderBot();
                            break;
                        case "3":
                            OrderBot.startPlacingBuyOrders();
                            printHorizontalLine();
                            break;
                        case "5":
                            ItemSearch.findFlipItems();
                            printHorizontalLine();
                            break;
                        case "6":
                            ItemSearch.seeWatchlistItemData();
                            printHorizontalLine();
                            break;
                        case "7":
                            ItemSearch.assessCurrentBuyOrderItemData();
                            printHorizontalLine();
                            break;
                        case "8":
                            OrderBot.cancelBuyOrders();
                            OrderBot.takeAllDeliveryBox();
                            OrderBot.createBuyOrderForList(ItemSearch.getListOfProfitableItemsInWatchlist());
                            printHorizontalLine();
                            break;
                        case "9":
                            OrderBot.createBuyOrderForList(ItemSearch.getListOfProfitableItemsInWatchlist());
                            printHorizontalLine();
                            break;
                        case "10":
                            OrderBot.cancelBuyOrders();
                            printHorizontalLine();
                            break;
                        case "11":
                            OrderBot.takeAllDeliveryBox();
                            printHorizontalLine();
                            break;
                        case "12":
                            OrderBot.createBuyOrderForList(ItemSearch.getListOfGreatswordMaterialItems());
                            printHorizontalLine();
                            break;
                        case "13":
                            ItemSearch.gsSupplyTracker();
                            printHorizontalLine();
                            break;
                        case "14":
                            ItemSearch.getRareEquipmentForEcto();
                            printHorizontalLine();
                            break;
                        case "15":
                            OrderBot.createBuyOrderForList(ItemSearch.getRareEquipmentForEcto(), 1);
                            printHorizontalLine();
                            break;
                        case "16":
                            ItemSearch.getCraftingProfit(false);
                            printHorizontalLine();
                            break;
                        case "17":
                            ItemSearch.getCraftingProfit(true);
                            printHorizontalLine();
                            break;
                        case "18":
                            ItemSearch.getMediumCoatForLeather();
                            printHorizontalLine();
                            break;
                        case "19":
                            ItemSearch.getTrophyShipmentProfit();
                            break;
                    }
                } catch (Exception ex) {
                    System.err.println(ex);
                    Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }

    private static void setOrderBot() {
        System.out.println("Order Bot Settings");
        printHorizontalLine();

        Scanner scanner = new Scanner(System.in);
        System.out.println("Current Item Name: " + OrderBot.itemName);
        System.out.print("Enter item Name : ");
        String input = scanner.nextLine();
        if (!input.isEmpty()) {
            OrderBot.itemName = input;
        }
        System.out.println("Current Order Amount: " + OrderBot.itemAmount);
        System.out.print("Enter Order Amount : ");

        while (true) {
            input = scanner.nextLine();
            if (!input.isEmpty()) {
                try {
                    int amount = Integer.parseInt(input);
                    if (amount <= 0) {
                        System.out.println("Must be more than 0");
                    } else {
                        OrderBot.itemAmount = amount;
                        break;
                    }
                } catch (Exception e) {
                    System.out.println("Not a number");
                }
            } else {
                break;
            }
        }

        System.out.println("Current Price: " + (OrderBot.priceGold * 10000 + OrderBot.priceSilver * 100 + OrderBot.priceCopper));
        System.out.print("Enter Price : ");

        while (true) {
            input = scanner.nextLine();
            if (!input.isEmpty()) {
                try {
                    int amount = Integer.parseInt(input);
                    OrderBot.priceCopper = amount % 100;
                    OrderBot.priceSilver = (amount / 100) % 100;
                    OrderBot.priceGold = (amount / 10000);
                    break;
                } catch (Exception e) {
                    System.out.println("Not a number");
                }
            } else {
                break;
            }
        }

        printHorizontalLine();
        System.out.println("Current Item Name: " + OrderBot.itemName);
        System.out.println("Current Order Amount: " + OrderBot.itemAmount);
        System.out.println("Current Price: " + OrderBot.priceGold + "g " + OrderBot.priceSilver + "s " + OrderBot.priceCopper + "c");
        printHorizontalLine();
    }

    private static void getWallet() {
        System.out.println("Wallet");
        printHorizontalLine();
        for (CurrencyBelonging currency : GW2API.account().wallet(API_KEY)) {
            System.out.println(GW2API.currencies().get(currency.getId()).getName() + ": " + currency.getValue());
        }
        printHorizontalLine();
    }

    private static String getListings(boolean buys) {
        System.out.println("Getting TP listings...");
        String response = ((buys) ? "Buy" : "Sell") + " Listings\n";
        Transaction[] transactions = (buys) ? GW2API.commerce().transactions().currentBuys(API_KEY) : GW2API.commerce().transactions().currentSells(API_KEY);
        int totalValue = 0;
        for (Transaction transaction : transactions) {
            totalValue += transaction.getPrice() * transaction.getQuantity();
            Listing listing = new Listing(transaction.getItemId(), transaction.getQuantity(), transaction.getPrice());
            response += listing + "\n";
        }
        totalValue *= (buys) ? 1 : .9;
        response += "Value: " + (totalValue / 10000) + "g " + (totalValue / 100) % 100 + "s " + totalValue % 100 + "c";
        System.out.println(response);
        printHorizontalLine();
        return response;
    }

    private static void printHorizontalLine() {
        System.out.println("---------------");
    }
}
