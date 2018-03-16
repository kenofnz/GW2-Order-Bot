package com.kenbots.gw2bot.search;

public class Item {

    /**
     * @return the supply
     */
    public int getSupply() {
        return supply;
    }

    /**
     * @return the demand
     */
    public int getDemand() {
        return demand;
    }

    /**
     * @return the maxBuyOrder
     */
    public int getMaxBuyOrder() {
        return maxBuyOrder;
    }

    /**
     * @return the minSellOrder
     */
    public int getMinSellOrder() {
        return minSellOrder;
    }

    /**
     * @return the profit
     */
    public int getProfit() {
        return profit;
    }

    public int getMinsSinceLastChange() {
        return minsSinceLastChange;
    }

    /**
     * @return the percentMargin
     */
    public double getPercentMargin() {
        return percentMargin;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    private final int supply, demand, itemId;
    private final int maxBuyOrder, minSellOrder;
    private final int profit, minsSinceLastChange;
    private final double percentMargin;
    private final String name;

    public Item(int itemId, String name, int supply, int demand, int maxBuyOrder, int minSellOrder, int profit, double percentMargin, int minsSinceChange) {
        this.itemId = itemId;
        this.name = name;
        this.supply = supply;
        this.demand = demand;
        this.maxBuyOrder = maxBuyOrder;
        this.minSellOrder = minSellOrder;
        this.profit = profit;
        this.percentMargin = percentMargin;
        this.minsSinceLastChange = minsSinceChange;
    }

    public static int compare(Item o1, Item o2) {
        return (o1.percentMargin >= o2.percentMargin) ? 1 : -1;
    }

    @Override
    public String toString() {
        return String.format("%60s%10d%12d%12d%12d%12d%12d%12d%15d%12d", this.getName(), this.itemId, this.getMaxBuyOrder(), this.getMinSellOrder(), this.getProfit(), (int) (this.getPercentMargin() * 100), this.getSupply(), this.getDemand(), getDemand() - getSupply(), this.getMinsSinceLastChange());
    }

    /**
     * @return the itemId
     */
    public int getItemId() {
        return itemId;
    }
}
