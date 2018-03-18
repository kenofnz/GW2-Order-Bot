package com.kenbots.gw2bot.search;

import com.kenbots.gw2bot.Listing;
import com.kenbots.gw2bot.Main;
import static com.kenbots.gw2bot.Main.GW2API;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import me.nithanim.gw2api.v2.api.commerce.listings.ListingPart;
import me.nithanim.gw2api.v2.api.commerce.prices.ItemPrice;
import me.nithanim.gw2api.v2.api.commerce.prices.Price;
import me.nithanim.gw2api.v2.api.commerce.transactions.Transaction;
import org.json.JSONArray;
import org.json.JSONObject;

public class ItemSearch {

    private static final Properties FLIP_SETTINGS = new Properties();

    private static final DateFormat SPIDY_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss z");

    static {
        reloadFlipSettings();
    }

    public static Date spidyDateParse(String d) {
        synchronized (SPIDY_DATE_FORMAT) {
            try {
                return SPIDY_DATE_FORMAT.parse(d);
            } catch (ParseException ex) {
                Logger.getLogger(ItemSearch.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return new Date();
    }

    public static void reloadFlipSettings() {
        InputStream inputStream = null;
        try {
            inputStream = new FileInputStream("flip.settings");
            FLIP_SETTINGS.clear();
            FLIP_SETTINGS.load(inputStream);

        } catch (final Exception e) {
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (final IOException ex) {

                }
            }

            if (FLIP_SETTINGS.getProperty("spendinglimit") == null) {
                FLIP_SETTINGS.setProperty("spendinglimit", "4000000");
            }
            if (FLIP_SETTINGS.getProperty("minprofitpercent") == null) {
                FLIP_SETTINGS.setProperty("minprofitpercent", "0.15");
            }
            if (FLIP_SETTINGS.getProperty("minprofit") == null) {
                FLIP_SETTINGS.setProperty("minprofit", "300");
            }
            if (FLIP_SETTINGS.getProperty("watchlist") == null) {
                FLIP_SETTINGS.setProperty("watchlist", "");
            }
            if (FLIP_SETTINGS.getProperty("craftgreatswords") == null) {
                FLIP_SETTINGS.setProperty("craftgreatswords", "0");
            }

            if (FLIP_SETTINGS.getProperty("mygreatswordsellprice") == null) {
                FLIP_SETTINGS.setProperty("mygreatswordsellprice", "0");
            }
            if (FLIP_SETTINGS.getProperty("simplemenu") == null) {
                FLIP_SETTINGS.setProperty("simplemenu", "true");
            }
            Main.SIMPLE_MENU = Boolean.parseBoolean(FLIP_SETTINGS.getProperty("simplemenu"));
        }
    }

    private static String printItemData(Collection<Item> items) {
        String result = String.format("%60s%10s%12s%12s%12s%12s%12s%12s%15s%12s%n", "Item", "ItemID", "Buy Order", "Sell Order", "Profit", "Profit %", "Supply", "Demand", "Demand-Supply", "Last Changed");
        System.out.print(result);
        for (Item item : items) {
            System.out.println(item);
            result += item.toString() + "\n";
        }
        return result;
    }

    private static int[] getWatchlist() {
        String[] watchlistString = FLIP_SETTINGS.getProperty("watchlist").split(",");
        int[] watchlist = new int[watchlistString.length];
        for (int i = 0; i < watchlist.length; i++) {
            try {
                watchlist[i] = Integer.parseInt(watchlistString[i]);
            } catch (NumberFormatException ex) {
            }
        }
        return watchlist;
    }

    public static String findFlipItems() {
        return findFlipItems(.75, 1000, 1000, Integer.parseInt(FLIP_SETTINGS.getProperty("minprofit")));
    }

    public static String findFlipItems(double profitLimit, int minDemand, int minSupply, int minProfit) {
        LinkedList<Item> itemsToFlip = new LinkedList<>();
        ForkJoinPool pool = ForkJoinPool.commonPool();

        Collection<Callable<Item>> itemsFuture = new LinkedList<>();

        System.out.println("Querying TP items...");
        JSONArray results = queryGw2Spidy("all-items/all").getJSONArray("results");
        System.out.println("Processing " + results.length() + " items...");
        for (int j = 0; j < results.length(); j++) {
            final JSONObject item = results.getJSONObject(j);
            itemsFuture.add((Callable<Item>) () -> {
                int profit, minsSinceLastChange;
                double percentMargin;
                String name = item.getString("name");
                int itemId = item.getInt("data_id");
                int typeId = item.getInt("type_id");

                int supply = item.getInt("sale_availability");
                int demand = item.getInt("offer_availability");
                int maxBuyOrder = item.getInt("max_offer_unit_price");
                int minSellOrder = item.getInt("min_sale_unit_price");

                String priceLastChanged = item.getString("price_last_changed");
                long ms = System.currentTimeMillis() - spidyDateParse(priceLastChanged).getTime();
                minsSinceLastChange = (int) TimeUnit.MILLISECONDS.toMinutes(ms);

                if (maxBuyOrder <= 0 || minSellOrder <= 0 || supply <= 0 || demand <= 0) {
                    return null;
                }

                profit = (int) ((minSellOrder * 0.85) - maxBuyOrder);
                percentMargin = 1D * profit / maxBuyOrder;

                if (percentMargin >= Double.parseDouble(FLIP_SETTINGS.getProperty("minprofitpercent")) && percentMargin <= profitLimit
                        && demand >= minDemand
                        && supply >= minSupply
                        && demand - supply >= 0
                        && minsSinceLastChange <= 60
                        && (typeId != 3 && typeId != 5 && typeId != 11
                        && typeId != 18 && typeId != 15)
                        && profit > minProfit) {
                    return new Item(itemId, name, supply, demand, maxBuyOrder, minSellOrder, profit, percentMargin, minsSinceLastChange);
                }
                return null;
            });
        }

        for (Future<Item> future : pool.invokeAll(itemsFuture)) {
            try {
                Item result = future.get();
                if (result != null) {
                    itemsToFlip.add(result);
                }
            } catch (InterruptedException | ExecutionException ex) {
                Logger.getLogger(ItemSearch.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        itemsToFlip.sort((o1, o2) -> {
            return Item.compare(o2, o1);
        });

        System.out.println("Found " + itemsToFlip.size() + " items");
        return "Options:\n"
                + "Max profit % margin: " + (profitLimit * 100) + "\n"
                + "Min Demand: " + minDemand + "\n"
                + "Min Supply: " + minSupply + "\n"
                + "Min Profit: " + minProfit + "\n"
                + printItemData(itemsToFlip);
    }

    public static void seeWatchlistItemData() {
        reloadFlipSettings();
        LinkedList<Item> itemsToFlip = new LinkedList<>();

        for (int itemId : getWatchlist()) {
            JSONObject item = queryGw2Spidy("item/" + itemId).getJSONObject("result");
            int profit;
            String priceLastChanged;
            double percentMargin;
            String name = item.getString("name");

            ItemPrice itemPrice = Main.GW2API.commerce().prices().get(itemId);
            int supply = itemPrice.getSells().getQuantity();
            int demand = itemPrice.getBuys().getQuantity();
            int maxBuyOrder = itemPrice.getBuys().getUnitPrice();
            int minSellOrder = itemPrice.getSells().getUnitPrice();
            priceLastChanged = item.getString("price_last_changed");

            long ms = System.currentTimeMillis() - spidyDateParse(priceLastChanged).getTime();
            if (maxBuyOrder <= 0 || minSellOrder <= 0 || supply <= 0 || demand <= 0) {
                continue;
            }

            profit = (int) ((minSellOrder * 0.85) - maxBuyOrder);
            percentMargin = 1D * profit / maxBuyOrder;

            itemsToFlip.add(new Item(itemId, name, supply, demand, maxBuyOrder, minSellOrder, profit, percentMargin, (int) TimeUnit.MILLISECONDS.toMinutes(ms)));

            itemsToFlip.sort((o1, o2) -> {
                return Item.compare(o2, o1);
            });
        }
        printItemData(itemsToFlip);

        double avgProfitMargin = 0;
        int itemCount = 0;
        for (Item item : itemsToFlip) {
            if (item.getMinsSinceLastChange() <= 60
                    && item.getProfit() >= Integer.parseInt(FLIP_SETTINGS.getProperty("minprofit"))
                    && item.getPercentMargin() >= Double.parseDouble(FLIP_SETTINGS.getProperty("minprofitpercent"))) {
                avgProfitMargin += item.getPercentMargin();
                itemCount++;
            }
        }
        System.out.println();
        System.out.println("Average Profit %: " + (int) (100D * avgProfitMargin / itemCount));
        System.out.println();
    }

    public static void assessCurrentBuyOrderItemData() {
        getListOfOutbidItemsInCurrentBuyOrders();
    }

    public static LinkedList<Listing> getListOfGreatswordMaterialItems() {
        LinkedList<Listing> itemsToBuy = new LinkedList<>();

        //int[] materialID = {19700, 24356, 19722};
        //int[] materialID = {19684, 24356, 19722};
        int[] materialID = {19684, 24356, 19709};

        for (int itemId : materialID) {
            Price item = Main.GW2API.commerce().prices().get(itemId).getBuys();

            int maxBuyOrder;
            String name;
            name = Main.GW2API.items().get(itemId).getName();
            maxBuyOrder = item.getUnitPrice();
            int amountToOrder = 0;
            switch (itemId) {
                case 19684:
                    amountToOrder = Integer.parseInt(FLIP_SETTINGS.getProperty("craftgreatswords")) * 12;
                    break;
                case 19700:
                    amountToOrder = Integer.parseInt(FLIP_SETTINGS.getProperty("craftgreatswords")) * 24;
                    break;
                case 24356:
                    amountToOrder = Integer.parseInt(FLIP_SETTINGS.getProperty("craftgreatswords")) * 15;
                    break;
                case 19722:
                    amountToOrder = Integer.parseInt(FLIP_SETTINGS.getProperty("craftgreatswords")) * 12;
                    name = "elder wood l";
                    break;
                case 19709:
                    amountToOrder = Integer.parseInt(FLIP_SETTINGS.getProperty("craftgreatswords")) * 4;
                    break;
                default:
                    break;
            }
            itemsToBuy.add(new Listing(itemId, amountToOrder, maxBuyOrder, name));
        }
        return itemsToBuy;
    }

    public static LinkedList<Listing> getListOfOutbidItemsInCurrentBuyOrders() {
        reloadFlipSettings();
        LinkedList<Item> itemsToFlip = new LinkedList<>();
        LinkedList<Listing> itemsOutbid = new LinkedList<>();
        HashMap<Integer, Listing> buyListings = new HashMap<>();

        Transaction[] transactions = GW2API.commerce().transactions().currentBuys(Main.API_KEY);
        for (Transaction transaction : transactions) {
            Listing listing = new Listing(transaction.getItemId(), transaction.getQuantity(), transaction.getPrice());
            buyListings.put(listing.getItemId(), listing);
        }

        buyListings.forEach((itemId, listing) -> {
            JSONObject item = queryGw2Spidy("item/" + itemId).getJSONObject("result");
            int profit, minsSinceLastChange;
            double percentMargin;
            String name = item.getString("name");

            ItemPrice itemPrice = Main.GW2API.commerce().prices().get(itemId);
            int supply = itemPrice.getSells().getQuantity();
            int demand = itemPrice.getBuys().getQuantity();
            int maxBuyOrder = itemPrice.getBuys().getUnitPrice();
            int minSellOrder = itemPrice.getSells().getUnitPrice();

            String priceLastChanged = item.getString("price_last_changed");
            long ms = 0;
            try {
                ms = System.currentTimeMillis() - SPIDY_DATE_FORMAT.parse(priceLastChanged).getTime();
            } catch (ParseException ex) {
                Logger.getLogger(ItemSearch.class.getName()).log(Level.SEVERE, null, ex);
            }
            minsSinceLastChange = (int) TimeUnit.MILLISECONDS.toMinutes(ms);

            profit = (int) ((minSellOrder * 0.85) - maxBuyOrder);
            percentMargin = 1D * profit / maxBuyOrder;

            itemsToFlip.add(new Item(itemId, name, supply, demand, maxBuyOrder, minSellOrder, profit, percentMargin, minsSinceLastChange));

            itemsToFlip.sort((o1, o2) -> {
                return Item.compare(o2, o1);
            });
        });

        printItemData(itemsToFlip);
        System.out.println();

        System.out.format("%40s%10s%17s%19s%n", "Item", "Status", "Listing Price", "Highest Buy Order");
        itemsToFlip.forEach((item) -> {
            if (buyListings.get(item.getItemId()) != null) {
                Listing listing = buyListings.get(item.getItemId());
                if (listing.getPrice() >= item.getMaxBuyOrder()) {
                    System.out.format("%40s%10s%17d%19d%n", listing.getName(), "Highest", listing.getPrice(), item.getMaxBuyOrder());
                } else {
                    itemsOutbid.add(new Listing(item.getItemId(), listing.getQuantity(), item.getMaxBuyOrder(), listing.getName()));
                    System.out.format("%40s%10s%17d%19d%n", listing.getName(), "Outbid", listing.getPrice(), item.getMaxBuyOrder());
                }
            }
        });
        System.out.println();

        return itemsOutbid;
    }

    public static LinkedList<Listing> getListOfProfitableItemsInWatchlist() {
        reloadFlipSettings();
        LinkedList<Listing> count = new LinkedList<>();
        LinkedList<Listing> itemsToBuy = new LinkedList<>();

        for (int itemId : getWatchlist()) {
            JSONObject item = queryGw2Spidy("item/" + itemId).getJSONObject("result");
            int profit, minsSinceLastChange;
            double percentMargin;
            String name = item.getString("name");

            ItemPrice itemPrice = Main.GW2API.commerce().prices().get(itemId);
            int maxBuyOrder = itemPrice.getBuys().getUnitPrice();
            int minSellOrder = itemPrice.getSells().getUnitPrice();

            String priceLastChanged = item.getString("price_last_changed");
            long ms = System.currentTimeMillis() - spidyDateParse(priceLastChanged).getTime();
            minsSinceLastChange = (int) TimeUnit.MILLISECONDS.toMinutes(ms);

            profit = (int) ((minSellOrder * 0.85) - maxBuyOrder);
            percentMargin = 1D * profit / maxBuyOrder;

            if (minsSinceLastChange <= 120
                    && profit >= Integer.parseInt(FLIP_SETTINGS.getProperty("minprofit"))
                    && percentMargin >= Double.parseDouble(FLIP_SETTINGS.getProperty("minprofitpercent"))) {
                count.add(new Listing(itemId, 1, maxBuyOrder, name));
            }
        }

        count.forEach((listing) -> {
            itemsToBuy.add(new Listing(listing.getItemId(), Integer.parseInt(FLIP_SETTINGS.getProperty("spendinglimit")) / count.size() / listing.getPrice(), listing.getPrice(), listing.getName()));
        });

        return itemsToBuy;
    }

    public static String getCraftingProfit(boolean instantBuy) {
        ForkJoinPool pool = ForkJoinPool.commonPool();
        System.out.println("Gettng crafting profits...\n");

        Collection<Callable<String>> recipes = new LinkedList<>();

        //Pulsing Brandspark
        recipes.add((Callable<String>) () -> {
            int itemId = 82678;
            HashMap<Integer, Double> ingredients = new HashMap<>();
            ingredients.put(83757, 10D);
            ingredients.put(24330, 1D);
            ingredients.put(83284, 3D);
            ingredients.put(83103, 10D);
            return evaluateCraftingProfit(itemId, ingredients, instantBuy);
        });
        //Potent Superior Sharpening Stone
        recipes.add((Callable<String>) () -> {
            int itemId = 43451;
            HashMap<Integer, Double> ingredients = new HashMap<>();
            ingredients.put(24277, 3.6D);
            ingredients.put(19701, 2.4D);
            return evaluateCraftingProfit(itemId, ingredients, instantBuy, 5);
        });
        //Plate of Truffle Steak
        recipes.add((Callable<String>) () -> {
            int itemId = 12467;
            HashMap<Integer, Double> ingredients = new HashMap<>();
            ingredients.put(24359, 1D);
            ingredients.put(12545, 1D);
            ingredients.put(12144, 1D);
            ingredients.put(12138, 1D);
            return evaluateCraftingProfit(itemId, ingredients, instantBuy);
        });
        //Plate of Fire Flank Steak
        recipes.add((Callable<String>) () -> {
            int itemId = 12466;
            HashMap<Integer, Double> ingredients = new HashMap<>();
            ingredients.put(24359, 1D);
            ingredients.put(12544, 1D);
            return evaluateCraftingProfit(itemId, ingredients, instantBuy);
        });
        //Bowl of Sweet and Spicy Butternut Squash Soup
        recipes.add((Callable<String>) () -> {
            int itemId = 41569;
            HashMap<Integer, Double> ingredients = new HashMap<>();
            ingredients.put(12544, 20D);
            ingredients.put(12534, 6D);
            ingredients.put(12138, 6D);
            ingredients.put(12142, 6D);
            ingredients.put(12236, 3D);
            ingredients.put(12134, 6D);
            ingredients.put(24360, 3D);
            ingredients.put(12511, 3D);
            ingredients.put(12504, 3D);
            ingredients.put(12258, 6D);
            ingredients.put(12136, 3D);
            ingredients.put(12153, 3D);
            ingredients.put(12156, 3D);

            return evaluateCraftingProfit(itemId, ingredients, instantBuy, 2);
        });

        //Gossamer Patch
        recipes.add((Callable<String>) () -> {
            int itemId = 76614;
            HashMap<Integer, Double> ingredients = new HashMap<>();
            ingredients.put(19790, 25D);
            ingredients.put(19732, 30D);
            ingredients.put(19746, 4D);
            return evaluateCraftingProfit(itemId, ingredients, instantBuy, 5);
        });

//        recipes.add((Callable<String>) () -> {
//            int itemId = 9443;
//            HashMap<Integer, Double> ingredients = new HashMap<>();
//            ingredients.put(24277, 3D);
//            ingredients.put(19701, 2D);
//            return evaluateCraftingProfit(itemId, ingredients, instantBuy, 5);
//        });
//        recipes.add((Callable<String>) () -> {
//            int itemId = 12459;
//            HashMap<Integer, Double> ingredients = new HashMap<>();
//            ingredients.put(12138, 2D);
//            ingredients.put(12155, 1D);
//            ingredients.put(12128, 1D);
//            ingredients.put(12156, 1D);
//            ingredients.put(12136, 1D);
//            return evaluateCraftingProfit(itemId, ingredients, instantBuy);
//        });
//        recipes.add((Callable<String>) () -> {
//            int itemId = 83502;
//            HashMap<Integer, Double> ingredients = new HashMap<>();
//            ingredients.put(83103, 50D);
//            ingredients.put(19701, 2D);
//            ingredients.put(74328, 5D);
//            ingredients.put(68952, 32D);
//            return evaluateCraftingProfit(itemId, ingredients, instantBuy);
//        });
//        recipes.add((Callable<String>) () -> {
//            int itemId = 12430;
//            HashMap<Integer, Double> ingredients = new HashMap<>();
//            ingredients.put(24359, 1D);
//            ingredients.put(12505, 1D);
//            ingredients.put(12138, 1D);
//            ingredients.put(12236, 1D);
//            ingredients.put(12153, 1D);
//            return evaluateCraftingProfit(itemId, ingredients, instantBuy);
//        });
//        recipes.add((Callable<String>) () -> {
//            int itemId = 48917;
//            HashMap<Integer, Double> ingredients = new HashMap<>();
//            ingredients.put(24277, 3D);
//            ingredients.put(48884, 5D);
//            return evaluateCraftingProfit(itemId, ingredients, instantBuy, 5);
//        });
//        recipes.add((Callable<String>) () -> {
//            int itemId = 48916;
//            HashMap<Integer, Double> ingredients = new HashMap<>();
//            ingredients.put(24277, 3D);
//            ingredients.put(48884, 5D);
//            return evaluateCraftingProfit(itemId, ingredients, instantBuy, 5);
//        });
        LinkedList<String> results = new LinkedList<>();
        pool.invokeAll(recipes).forEach((future) -> {
            try {
                String result = future.get();
                System.out.println(result);
                results.add(result);
            } catch (InterruptedException | ExecutionException ex) {
                Logger.getLogger(ItemSearch.class.getName()).log(Level.SEVERE, null, ex);
            }
        });

        String output = "";
        for (String result : results) {
            output += result + "\n";
        }
        return output;
    }

    public static String evaluateCraftingProfit(int itemId, HashMap<Integer, Double> ingredients, boolean instantBuy) {
        return evaluateCraftingProfit(itemId, ingredients, instantBuy, 1);
    }

    public static String evaluateCraftingProfit(int itemId, HashMap<Integer, Double> ingredients, boolean instantBuy, int outputAmount) {

        ForkJoinPool pool = ForkJoinPool.commonPool();

        Collection<Callable<Double>> ingredientsFuture = new LinkedList<>();

        double profitMargin = 0.05;
        int cost = 0;
        int craftAmount = 0;
        int totalReturn = 0;
        for (Entry<Integer, Double> ingredient : ingredients.entrySet()) {
            double quantity = ingredient.getValue();
            int ingredientId = ingredient.getKey();
            ingredientsFuture.add((Callable<Double>) () -> {
                if (ingredientId == 19663) {
                    return quantity * 2504;
                } else {
                    if (instantBuy) {
                        return quantity * Main.GW2API.commerce().prices().get(ingredientId).getSells().getUnitPrice();
                    } else {
                        return quantity * Main.GW2API.commerce().prices().get(ingredientId).getBuys().getUnitPrice();
                    }
                }

            });
        }

        for (Future<Double> future : pool.invokeAll(ingredientsFuture)) {
            try {
                cost += future.get();
            } catch (InterruptedException | ExecutionException ex) {
                Logger.getLogger(ItemSearch.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        ListingPart[] listings = Main.GW2API.commerce().listings().get(itemId).getBuys();
        for (ListingPart listing : listings) {
            if (outputAmount * listing.getUnitPrice() * 0.85 > cost * (1 + profitMargin)) {
                craftAmount += listing.getQuantity() / outputAmount;
                totalReturn += listing.getQuantity() * listing.getUnitPrice() * 0.85;
            }
        }

        String result = "";
        int salePrice = Main.GW2API.commerce().prices().get(itemId).getSells().getUnitPrice();
        result += Main.GW2API.items().get(itemId).getName() + (((int) (250 * (salePrice * 0.85 - cost / outputAmount)) >= 50000) ? "**(>5g profit@250 units)" : "") + "\n";
        result += "Crafting 1 " + outputAmount + "x Cost " + cost + " (" + cost / outputAmount + " per item)" + "\n";
        result += "Minimum to Profit: " + (profitMargin * 100) + "% " + Math.round(cost * (1 + profitMargin) / 0.85 / outputAmount) + "\n";
        result += "Lowest Sell Offer: " + salePrice + "(" + 100 * (salePrice * 0.85 - cost / outputAmount) / (cost / outputAmount) + "% profit)" + "\n";
        result += "250 Sell Offer Proft: " + (int) (250 * (salePrice * 0.85 - cost / outputAmount)) + "\n";
        if (craftAmount != 0) {
            result += "Craft " + craftAmount + " " + outputAmount + "x " + Main.GW2API.items().get(itemId).getName() + "(" + craftAmount * outputAmount + " Total)" + "\n";
            result += craftAmount + " Instant Sell Cost: " + cost * craftAmount + "\n";
            result += craftAmount + " Instant Sell Return: " + totalReturn + "\n";
            result += "Instant Sell Profit " + (totalReturn - cost * craftAmount) + "(" + 100 * (totalReturn - cost * craftAmount) / (cost * craftAmount) + "%)" + "\n";
        }
        return result;
    }

    private static boolean salvageForEcto(int rareCost, int ectoMinSell) {
        return (ectoMinSell * .875 * .85) - (rareCost + 2624 / 250) >= 0.1 * rareCost;
    }

    public static LinkedList<Listing> getRareEquipmentForEcto() {
        reloadFlipSettings();
        LinkedList<Listing> count = new LinkedList<>();
        LinkedList<Listing> itemsToBuy = new LinkedList<>();

        Price ectoPriceQuery = Main.GW2API.commerce().prices().get(19721).getSells();
        int ectoMinSell = ectoPriceQuery.getUnitPrice();
        System.out.println(ectoMinSell);
        JSONArray results = queryGw2Spidy("all-items/all").getJSONArray("results");
        for (int j = 0; j < results.length(); j++) {

            JSONObject item = results.getJSONObject(j);

            String name = item.getString("name");
            int itemId = item.getInt("data_id");
            int typeId = item.getInt("type_id");
            int rarity = item.getInt("rarity");
            int minLevel = item.getInt("restriction_level");

            int supply = item.getInt("sale_availability");
            int demand = item.getInt("offer_availability");
            int minSellOrder = item.getInt("min_sale_unit_price");
            int maxBuyOrder = item.getInt("max_offer_unit_price");

            if (supply > 400 && demand > 400
                    && (typeId == 18 || typeId == 16 || typeId == 0)
                    && minSellOrder < ectoMinSell
                    && rarity == 4 && minLevel == 80) {

                if (salvageForEcto(maxBuyOrder, ectoMinSell)) {
                    ItemPrice itemPrice = Main.GW2API.commerce().prices().get(itemId);
                    maxBuyOrder = itemPrice.getBuys().getUnitPrice();
                    count.add(new Listing(itemId, 1, maxBuyOrder, name));
                }
            }
        }

        count.forEach((listing) -> {
            itemsToBuy.add(new Listing(listing.getItemId(), Integer.parseInt(FLIP_SETTINGS.getProperty("spendinglimit")) / count.size() / listing.getPrice(), listing.getPrice(), listing.getName()));
        });
        int totalCost = 0;
        int totalRares = 0;

        for (Listing t : itemsToBuy) {
            System.out.println(t);
            totalCost += (t.getPrice() + 2624 / 250) * t.getQuantity();
            totalRares += t.getQuantity();
        }

        System.out.println("Costs: " + totalCost);
        System.out.println("Number of Rares: " + totalRares);
        System.out.println("Profit: " + ((totalRares * 0.875 * ectoMinSell * 0.85) - totalCost));
        System.out.println("Profit %: " + 100 * ((totalRares * 0.875 * ectoMinSell * 0.85) - totalCost) / totalCost);
        return itemsToBuy;
    }

    public static LinkedList<Listing> getMediumCoatForLeather() {
        reloadFlipSettings();
        LinkedList<Listing> count = new LinkedList<>();
        LinkedList<Listing> itemsToBuy = new LinkedList<>();

        Price hardenedLeatherPrice = Main.GW2API.commerce().prices().get(19732).getSells();
        int hardenedLeatherMinSell = hardenedLeatherPrice.getUnitPrice();

        Price thickLeatherPrice = Main.GW2API.commerce().prices().get(19729).getSells();
        int thickLeatherMinSell = thickLeatherPrice.getUnitPrice();

        double totalMinSell = 2.25 * thickLeatherMinSell + 0.125 * hardenedLeatherMinSell;
        System.out.println("30% Profit Max Buy = " + ((totalMinSell * .85 - 3) / 1.3));
        System.out.println("25% Profit Max Buy = " + ((totalMinSell * .85 - 3) / 1.25));
        System.out.println("20% Profit Max Buy = " + ((totalMinSell * .85 - 3) / 1.2));
        System.out.println("10% Profit Max Buy = " + ((totalMinSell * .85 - 3) / 1.1));
        System.out.println("Break even Max Buy = " + ((totalMinSell * .85 - 3)));
        System.out.println("Hardened Leather Section Sell: " + hardenedLeatherMinSell);
        System.out.println("Thick Leather Section Sell: " + thickLeatherMinSell);
        return itemsToBuy;
    }

    public static void getTrophyShipmentProfit() {
        reloadFlipSettings();
        LinkedList<Integer> t5Mats = new LinkedList<>();
        t5Mats.add(24341); //Large Bone
        t5Mats.add(24356); //Large Fang
        t5Mats.add(24288); //Large Scale
        t5Mats.add(24350); //Large Claw
        t5Mats.add(24276); //Dust
        t5Mats.add(24299); //Intricate Totem
        t5Mats.add(24282); //Potent Venom Sac
        t5Mats.add(24294); //Potent Bood

        LinkedList<Integer> t6Mats = new LinkedList<>();
        t6Mats.add(24358); //Large Bone
        t6Mats.add(24357); //Large Fang
        t6Mats.add(24289); //Large Scale
        t6Mats.add(24351); //Large Claw
        t6Mats.add(24278); //Dust
        t6Mats.add(24299); //Intricate Totem
        t6Mats.add(24283); //Potent Venom Sac
        t6Mats.add(24295); //Potent Bood

        int totalMinSell = 0;
        for (int itemId : t5Mats) {
            totalMinSell += 5 * Main.GW2API.commerce().prices().get(itemId).getSells().getUnitPrice();
        }

        for (int itemId : t6Mats) {
            totalMinSell += 1 * Main.GW2API.commerce().prices().get(itemId).getSells().getUnitPrice();
        }

        totalMinSell += 0.2 * Main.GW2API.commerce().prices().get(83103).getSells().getUnitPrice();
        totalMinSell += 0.2 * Main.GW2API.commerce().prices().get(83757).getSells().getUnitPrice();

        System.out.println("Trophy Shipment Avg Sell: " + totalMinSell);

        int roseQuartzCost = Main.GW2API.commerce().prices().get(86316).getSells().getUnitPrice();
        int powderedRoseQuartzCost = Main.GW2API.commerce().prices().get(86269).getSells().getUnitPrice();

        System.out.println("Rose Quartz Sell: " + roseQuartzCost * .85);
        System.out.println("Rose Quartz To Powdered Sell: " + powderedRoseQuartzCost * 5 * .85);
    }

    public static void gsSupplyTracker() {
        reloadFlipSettings();
        JSONArray results = queryGw2Spidy("all-items/all").getJSONArray("results");
        int totalSupply = 0;
        for (int j = 0; j < results.length(); j++) {
            JSONObject item = results.getJSONObject(j);
            int itemId = item.getInt("data_id");
            int typeId = item.getInt("type_id");
            int subtypeId = item.getInt("sub_type_id");

            int rarity = item.getInt("rarity");
            int minLevel = item.getInt("restriction_level");
            int supply = item.getInt("sale_availability");
            int minSellOrder = item.getInt("min_sale_unit_price");

            if (typeId == 18 && subtypeId == 6
                    && rarity == 4 && minLevel == 80 && supply > 0
                    && minSellOrder <= Integer.parseInt(FLIP_SETTINGS.getProperty("mygreatswordsellprice"))) {
                ListingPart[] listings = Main.GW2API.commerce().listings().get(itemId).getSells();
                for (ListingPart listing : listings) {
                    if (listing.getUnitPrice() < Integer.parseInt(FLIP_SETTINGS.getProperty("mygreatswordsellprice"))) {
                        System.out.println(Main.GW2API.items().get(item.getInt("data_id")).getName() + " Supply: " + listing.getQuantity() + " Price: " + listing.getUnitPrice());
                        totalSupply += listing.getQuantity();
                    }
                }
            }

        }
        System.out.println("Number of Sell listings lower than " + Integer.parseInt(FLIP_SETTINGS.getProperty("mygreatswordsellprice")) + ": " + totalSupply + "\n");
    }

    public static JSONObject queryGw2Spidy(String apiEndpoint) {
        HttpResponse<JsonNode> jsonResponse;
        try {
            jsonResponse = Unirest.post("http://www.gw2spidy.com/api/v0.9/json/" + apiEndpoint)
                    .header("accept", "application/json")
                    .asJson();
            return jsonResponse.getBody().getObject();
        } catch (UnirestException ex) {
            Logger.getLogger(ItemSearch.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }
}
