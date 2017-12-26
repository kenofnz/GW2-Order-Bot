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
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map.Entry;
import java.util.Properties;
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

    private static void reloadFlipSettings() {
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
                FLIP_SETTINGS.setProperty("minprofitpercent", "0.2");
            }
            if (FLIP_SETTINGS.getProperty("minprofit") == null) {
                FLIP_SETTINGS.setProperty("minprofit", "400");
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

        }
    }

    private static void printItemData(Collection<Item> items) {
        System.out.format("%40s%10s%12s%12s%12s%12s%12s%12s%15s%12s%n", "Item", "ItemID", "Buy Order", "Sell Order", "Profit", "Profit %", "Supply", "Demand", "Demand-Supply", "Last Changed");
        items.forEach((item) -> {
            System.out.println(item);
        });
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

    public static void findFlipItems() {
        LinkedList<Item> itemsToFlip = new LinkedList<>();
        JSONArray results = queryGw2Spidy("all-items/all").getJSONArray("results");
        System.out.println("Processing " + results.length() + " items...");
        for (int j = 0; j < results.length(); j++) {
            JSONObject item = results.getJSONObject(j);
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
            long ms = 0;
            try {
                ms = System.currentTimeMillis() - SPIDY_DATE_FORMAT.parse(priceLastChanged).getTime();
            } catch (ParseException ex) {
                Logger.getLogger(ItemSearch.class.getName()).log(Level.SEVERE, null, ex);
            }
            minsSinceLastChange = (int) TimeUnit.MILLISECONDS.toMinutes(ms);

            if (maxBuyOrder <= 0 || minSellOrder <= 0 || supply <= 0 || demand <= 0) {
                continue;
            }

            profit = (int) ((minSellOrder * 0.85) - maxBuyOrder);
            percentMargin = 1D * profit / maxBuyOrder;

            if (percentMargin >= Double.parseDouble(FLIP_SETTINGS.getProperty("minprofitpercent")) && percentMargin <= .75
                    && demand >= 1000
                    && supply >= 1000
                    && minsSinceLastChange <= 60
                    && (typeId != 3 && typeId != 5 && typeId != 11
                    && typeId != 18 && typeId != 15)
                    //&& demand - supply >= 0
                    && profit > Integer.parseInt(FLIP_SETTINGS.getProperty("minprofit"))) {
                itemsToFlip.add(new Item(itemId, name, supply, demand, maxBuyOrder, minSellOrder, profit, percentMargin, minsSinceLastChange));
            }
        }

        itemsToFlip.sort((o1, o2) -> {
            return Item.compare(o2, o1);
        });

        System.out.println("Found " + itemsToFlip.size() + " items");
        printItemData(itemsToFlip);
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

            long ms = 0;
            try {
                ms = System.currentTimeMillis() - SPIDY_DATE_FORMAT.parse(priceLastChanged).getTime();
            } catch (ParseException ex) {
                Logger.getLogger(ItemSearch.class.getName()).log(Level.SEVERE, null, ex);
            }
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
            long ms = 0;
            try {
                ms = System.currentTimeMillis() - SPIDY_DATE_FORMAT.parse(priceLastChanged).getTime();
            } catch (ParseException ex) {
                Logger.getLogger(ItemSearch.class.getName()).log(Level.SEVERE, null, ex);
            }
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

    public static void getCraftingProfit(boolean instantBuy) {
        int itemId = 83502;
        HashMap<Integer, Double> ingredients = new HashMap<>();
//        System.out.println(Main.GW2API.items().get(itemId).getName() + " Instant Sell with Evergreen Lodestone");
//        ingredients.put(83103, 50);
//        ingredients.put(19701, 2);
//        ingredients.put(74328, 5);
//        ingredients.put(68942, 2);
//
//        evaluateCraftingProfit(itemId, ingredients, instantBuy);

        System.out.println(Main.GW2API.items().get(itemId).getName() + " Instant Sell with Evergreen Sliver");
        ingredients = new HashMap<>();
        ingredients.put(83103, 50D);
        ingredients.put(19701, 2D);
        ingredients.put(74328, 5D);
        ingredients.put(68952, 32D);

        evaluateCraftingProfit(itemId, ingredients, instantBuy);

//        itemId = 71425;
//        System.out.println(Main.GW2API.items().get(itemId).getName() + " Instant Sell with Evergreen Sliver");
//        ingredients = new HashMap<>();
//        ingredients.put(74202, 10D);
//        ingredients.put(24821, 1D);
//        ingredients.put(68952, 16D);
//
//        evaluateCraftingProfit(itemId, ingredients, instantBuy);
        itemId = 82678;
        System.out.println(Main.GW2API.items().get(itemId).getName() + " Sell Order");
        ingredients = new HashMap<>();
        ingredients.put(83757, 10D);
        ingredients.put(24330, 1D);
        ingredients.put(83284, 3D);
        ingredients.put(83103, 10D);
        evaluateCraftingProfit(itemId, ingredients, instantBuy);

        itemId = 43451;
        System.out.println(Main.GW2API.items().get(itemId).getName() + " Sell Order");
        ingredients = new HashMap<>();
        ingredients.put(24277, 3.6D);
        ingredients.put(19701, 2.4D);
        evaluateCraftingProfit(itemId, ingredients, instantBuy, 5);

        itemId = 48917;
        System.out.println(Main.GW2API.items().get(itemId).getName() + " Sell Order");
        ingredients = new HashMap<>();
        ingredients.put(24277, 3D);
        ingredients.put(48884, 5D);
        evaluateCraftingProfit(itemId, ingredients, instantBuy, 5);

        itemId = 48916;
        System.out.println(Main.GW2API.items().get(itemId).getName() + " Sell Order");
        ingredients = new HashMap<>();
        ingredients.put(24277, 3D);
        ingredients.put(48884, 5D);
        evaluateCraftingProfit(itemId, ingredients, instantBuy, 5);
    }

    public static void evaluateCraftingProfit(int itemId, HashMap<Integer, Double> ingredients, boolean instantBuy) {
        evaluateCraftingProfit(itemId, ingredients, instantBuy, 1);
    }

    public static void evaluateCraftingProfit(int itemId, HashMap<Integer, Double> ingredients, boolean instantBuy, int outputAmount) {
        double profitMargin = 0.05;
        int cost = 0;
        int craftAmount = 0;
        int totalReturn = 0;
        for (Entry<Integer, Double> ingredient : ingredients.entrySet()) {
            double quantity = ingredient.getValue();
            int ingredientId = ingredient.getKey();
            if (ingredientId == 19663) {
                cost += quantity * 2504;
            } else {
                if (instantBuy) {
                    cost += quantity * Main.GW2API.commerce().prices().get(ingredientId).getSells().getUnitPrice();
                } else {
                    cost += quantity * Main.GW2API.commerce().prices().get(ingredientId).getBuys().getUnitPrice();
                }
            }
        }

        ListingPart[] listings = Main.GW2API.commerce().listings().get(itemId).getBuys();
        for (ListingPart listing : listings) {
            if (outputAmount * listing.getUnitPrice() * 0.85 > cost * (1 + profitMargin)) {
                craftAmount += listing.getQuantity() / outputAmount;
                totalReturn += listing.getQuantity() * listing.getUnitPrice() * 0.85;
            }
        }

        System.out.println("Craft " + craftAmount + " " + outputAmount + "x " + Main.GW2API.items().get(itemId).getName() + "(" + craftAmount * outputAmount + " Total)");
        System.out.println("1 " + outputAmount + "x Cost " + cost + " (" + cost / outputAmount + " per item)");
        System.out.println("Minimum to Profit " + (profitMargin * 100) + "% " + Math.round(cost * (1 + profitMargin) / 0.85 / outputAmount));
        int salePrice = Main.GW2API.commerce().prices().get(itemId).getSells().getUnitPrice();
        System.out.println("Lowest Sell Offer " + salePrice + "(" + 100 * (salePrice * 0.85 - cost / outputAmount) / (cost / outputAmount) + "% profit)");
        System.out.println(craftAmount + " Cost " + cost * craftAmount);
        System.out.println(craftAmount + " Return " + totalReturn);
        if (craftAmount != 0) {
            System.out.println("Profit " + (totalReturn - cost * craftAmount) + "(" + 100 * (totalReturn - cost * craftAmount) / (cost * craftAmount) + "%)");
        }
        System.out.println();
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
