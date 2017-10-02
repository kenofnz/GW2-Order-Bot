package com.kenbots.gw2bot;

public class Listing {

    /**
     * @return the itemId
     */
    public int getItemId() {
        return itemId;
    }

    /**
     * @return the quantity
     */
    public int getQuantity() {
        return quantity;
    }

    /**
     * @return the price
     */
    public int getPrice() {
        return price;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    protected final int itemId;
    protected final int quantity;
    protected final int price;
    protected final String name;

    public Listing(int itemId, int quantity, int price) {
        this.itemId = itemId;
        this.quantity = quantity;
        this.price = price;
        this.name = Main.GW2API.items().get(this.itemId).getName();
    }

    public Listing(int itemId, int quantity, int price, String name) {
        this.itemId = itemId;
        this.quantity = quantity;
        this.price = price;
        this.name = name;
    }

    @Override
    public String toString() {
        return getQuantity() + " " + getName() + "(" + getItemId() + ")" + " for " + getPrice();
    }
}
