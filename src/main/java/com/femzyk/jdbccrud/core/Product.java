package com.femzyk.jdbccrud.core;

/**
 * Product - one row of the store's product table.
 *
 * CS 1103-01, Unit 5 discussion demo. A simple immutable value object:
 * the id is 0 while the product is new (not yet inserted), and the
 * database assigns the real id during create.
 *
 * @param id       database primary key (0 for unsaved products)
 * @param name     product name
 * @param price    unit price in dollars
 * @param quantity stock on hand (never negative)
 */
public record Product(int id, String name, double price, int quantity) {

    /** Returns a human-readable description used by both front ends. */
    @Override
    public String toString() {
        return String.format("[%d] %-24s $%8.2f  (qty %d)", id, name, price, quantity);
    }
}
