package com.femzyk.jdbccrud.console;

import com.femzyk.jdbccrud.core.Product;
import com.femzyk.jdbccrud.core.ProductRepository;

/**
 * ConsoleApp - console front end of the FEMZYK JDBC CRUD Demo
 * (CS 1103-01, Unit 5 discussion).
 *
 * Walks through the complete CRUD cycle against the embedded SQL database
 * and finishes with the two reliability showcases: a stock transfer
 * transaction (commit) and an invalid transfer (rollback), followed by an
 * SQL injection attempt that the PreparedStatements neutralize.
 */
public class ConsoleApp {

    /**
     * Runs the demonstration in order.
     *
     * @param args command-line arguments (not used)
     */
    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("   FEMZYK JDBC CRUD DEMO (database connectivity)  ");
        System.out.println("==================================================");

        ProductRepository repository = new ProductRepository();
        try {
            repository.createTableIfNotExists();
            repository.seedIfEmpty();
            System.out.println("Connected. products table ready, "
                    + repository.count() + " row(s) seeded.\n");
        } catch (RuntimeException error) {
            System.out.println("Database setup failed: " + error.getMessage());
            return;
        }

        demoCreate(repository);
        demoRead(repository);
        demoUpdate(repository);
        demoTransaction(repository);
        demoDelete(repository);
        demoInjectionSafety(repository);

        System.out.println("\nDone: CREATE, READ, UPDATE and DELETE all ran against the database.");
    }

    /** CREATE: inserts a new product. */
    private static void demoCreate(ProductRepository repository) {
        System.out.println("--- 1. CREATE ---");
        try {
            Product saved = repository.create(new Product(0, "Gaming Mouse", 59.99, 12));
            System.out.println("Inserted: " + saved);
        } catch (RuntimeException error) {
            System.out.println("Create failed: " + error.getMessage());
        }
    }

    /** READ: lists all rows and finds one by id. */
    private static void demoRead(ProductRepository repository) {
        System.out.println("\n--- 2. READ ---");
        System.out.println("All products:");
        for (Product product : repository.findAll()) {
            System.out.println("  " + product);
        }
        Product found = repository.findById(1);
        System.out.println("findById(1) -> " + found);
    }

    /** UPDATE: changes the price of an existing product. */
    private static void demoUpdate(ProductRepository repository) {
        System.out.println("\n--- 3. UPDATE ---");
        Product product = repository.findById(1);
        if (product == null) {
            System.out.println("Product 1 not found (already deleted?).");
            return;
        }
        Product updated = new Product(product.id(), product.name(), 19.99, product.quantity());
        boolean changed = repository.update(updated);
        System.out.println(changed ? "Updated:  " + updated : "Update changed no rows.");
    }

    /**
     * TRANSACTION: a valid stock transfer commits, an invalid one rolls
     * back, and the total stock proves nothing was lost.
     */
    private static void demoTransaction(ProductRepository repository) {
        System.out.println("\n--- 4. TRANSACTION (data integrity) ---");
        int before = totalStock(repository);
        System.out.println("Total stock before: " + before + " unit(s).");

        try {
            repository.transferStock(1, 2, 5);
            System.out.println("Committed: moved 5 unit(s) from product 1 to product 2.");
        } catch (RuntimeException error) {
            System.out.println("Transfer failed: " + error.getMessage());
        }

        try {
            repository.transferStock(2, 1, 999_999);
            System.out.println("Unexpectedly committed an invalid transfer!");
        } catch (RuntimeException error) {
            System.out.println("Rolled back: " + error.getMessage());
        }

        int after = totalStock(repository);
        System.out.println("Total stock after : " + after + " unit(s).");
        System.out.println(after == before
                ? "Integrity confirmed: the rollback restored the exact previous state."
                : "INTEGRITY PROBLEM: the totals differ!");
    }

    /** DELETE: removes a row and confirms the new count. */
    private static void demoDelete(ProductRepository repository) {
        System.out.println("\n--- 5. DELETE ---");
        boolean removed = repository.delete(5);
        System.out.println(removed
                ? "Deleted product 5. Rows remaining: " + repository.count()
                : "Product 5 was not present to delete. Rows: " + repository.count());
    }

    /**
     * SECURITY: shows that a classic SQL injection payload is treated as
     * plain text because every query is parameterized.
     */
    private static void demoInjectionSafety(ProductRepository repository) {
        System.out.println("\n--- 6. SQL injection safety ---");
        String malicious = "' OR '1'='1";
        int matches = repository.findByNameContaining(malicious).size();
        System.out.println("Searched for the injection payload \"' OR '1'='1\": "
                + matches + " row(s) returned.");
        System.out.println("PreparedStatement treated it as a literal name, not as SQL code.");
    }

    /** Sums the stock of all products. */
    private static int totalStock(ProductRepository repository) {
        return repository.findAll().stream().mapToInt(Product::quantity).sum();
    }
}
