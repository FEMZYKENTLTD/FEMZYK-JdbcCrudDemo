package com.femzyk.jdbccrud.core;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * ProductRepository - JDBC connectivity and CRUD operations for products.
 *
 * CS 1103-01, Unit 5 discussion demo. This class is the data layer shared
 * by the console and JavaFX front ends. It follows the JDBC cycle from the
 * course reading (Hock-Chuan, 2023): get a connection, create a statement,
 * execute, process the result, and close everything.
 *
 * Key practices demonstrated:
 *   - Every query is a PreparedStatement, so user input is always treated
 *     as data, never as SQL code (SQL injection prevention).
 *   - Every Connection, PreparedStatement and ResultSet is opened in a
 *     try-with-resources block and closed automatically.
 *   - transferStock() runs two updates inside one transaction: both
 *     commit together or both roll back, so stock can never silently
 *     disappear (data integrity).
 *
 * The demo uses the H2 embedded database in MySQL compatibility mode, so
 * the identical SQL runs unchanged against a real MySQL server - only the
 * connection URL and driver would change.
 */
public class ProductRepository {

    /** Thrown when a database operation fails. */
    public static class DatabaseException extends RuntimeException {
        public DatabaseException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** JDBC URL: file-based H2 in MySQL compatibility mode. */
    public static final String DB_URL =
            "jdbc:h2:./data/shopdb;MODE=Mysql;AUTO_SERVER=FALSE";

    private final String url;

    /** Uses the default database location. */
    public ProductRepository() {
        this(DB_URL);
    }

    /** Uses a custom JDBC URL (useful for tests). */
    public ProductRepository(String url) {
        this.url = url;
    }

    /** Opens one connection per operation for clarity; production systems use connection pools. */
    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url);
    }

    /** Creates the products table if it does not exist yet. */
    public void createTableIfNotExists() {
        String sql = "CREATE TABLE IF NOT EXISTS products ("
                + "id INT PRIMARY KEY AUTO_INCREMENT, "
                + "name VARCHAR(100) NOT NULL UNIQUE, "
                + "price DOUBLE NOT NULL CHECK (price >= 0), "
                + "quantity INT NOT NULL CHECK (quantity >= 0))";
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException error) {
            throw new DatabaseException("Could not create the products table", error);
        }
    }

    /** CREATE: inserts a product and returns the database-generated id. */
    public Product create(Product product) {
        String sql = "INSERT INTO products (name, price, quantity) VALUES (?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, product.name());
            stmt.setDouble(2, product.price());
            stmt.setInt(3, product.quantity());
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    return new Product(keys.getInt(1), product.name(),
                            product.price(), product.quantity());
                }
            }
            throw new DatabaseException("Insert succeeded but no id was returned", null);
        } catch (SQLException error) {
            throw new DatabaseException("Could not insert the product", error);
        }
    }

    /** READ: returns all products ordered by id. */
    public List<Product> findAll() {
        String sql = "SELECT id, name, price, quantity FROM products ORDER BY id";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            List<Product> products = new ArrayList<>();
            while (rs.next()) {
                products.add(new Product(rs.getInt("id"), rs.getString("name"),
                        rs.getDouble("price"), rs.getInt("quantity")));
            }
            return products;
        } catch (SQLException error) {
            throw new DatabaseException("Could not read the products", error);
        }
    }

    /** READ: finds one product by id, or null when it does not exist. */
    public Product findById(int id) {
        String sql = "SELECT id, name, price, quantity FROM products WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new Product(rs.getInt("id"), rs.getString("name"),
                            rs.getDouble("price"), rs.getInt("quantity"));
                }
                return null;
            }
        } catch (SQLException error) {
            throw new DatabaseException("Could not read product " + id, error);
        }
    }

    /** READ: finds products whose name contains the given text (injection-safe). */
    public List<Product> findByNameContaining(String text) {
        String sql = "SELECT id, name, price, quantity FROM products "
                + "WHERE name LIKE ? ORDER BY id";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, "%" + text + "%");
            try (ResultSet rs = stmt.executeQuery()) {
                List<Product> products = new ArrayList<>();
                while (rs.next()) {
                    products.add(new Product(rs.getInt("id"), rs.getString("name"),
                            rs.getDouble("price"), rs.getInt("quantity")));
                }
                return products;
            }
        } catch (SQLException error) {
            throw new DatabaseException("Could not search the products", error);
        }
    }

    /** UPDATE: changes name, price and quantity of an existing row. */
    public boolean update(Product product) {
        String sql = "UPDATE products SET name = ?, price = ?, quantity = ? WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, product.name());
            stmt.setDouble(2, product.price());
            stmt.setInt(3, product.quantity());
            stmt.setInt(4, product.id());
            return stmt.executeUpdate() > 0;
        } catch (SQLException error) {
            throw new DatabaseException("Could not update product " + product.id(), error);
        }
    }

    /** DELETE: removes one product by id. */
    public boolean delete(int id) {
        String sql = "DELETE FROM products WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            return stmt.executeUpdate() > 0;
        } catch (SQLException error) {
            throw new DatabaseException("Could not delete product " + id, error);
        }
    }

    /** Returns the total number of rows. */
    public int count() {
        String sql = "SELECT COUNT(*) FROM products";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        } catch (SQLException error) {
            throw new DatabaseException("Could not count the products", error);
        }
    }

    /**
     * TRANSACTION: moves stock from one product to another. Both updates
     * are committed together or rolled back together, so the total stock
     * is conserved and no product can end up with a negative quantity.
     */
    public void transferStock(int fromId, int toId, int quantity) {
        String debit = "UPDATE products SET quantity = quantity - ? WHERE id = ?";
        String credit = "UPDATE products SET quantity = quantity + ? WHERE id = ?";
        String lock = "SELECT quantity FROM products WHERE id = ? FOR UPDATE";
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement lockStmt = conn.prepareStatement(lock)) {
                lockStmt.setInt(1, fromId);
                try (ResultSet rs = lockStmt.executeQuery()) {
                    if (!rs.next() || rs.getInt("quantity") < quantity) {
                        throw new IllegalStateException(
                                "Transfer rejected: not enough stock on product " + fromId + ".");
                    }
                }
                try (PreparedStatement psDebit = conn.prepareStatement(debit);
                     PreparedStatement psCredit = conn.prepareStatement(credit)) {
                    psDebit.setInt(1, quantity);
                    psDebit.setInt(2, fromId);
                    psDebit.executeUpdate();
                    psCredit.setInt(1, quantity);
                    psCredit.setInt(2, toId);
                    psCredit.executeUpdate();
                }
                conn.commit();
            } catch (Exception error) {
                conn.rollback();
                throw error;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (IllegalStateException error) {
            throw error;
        } catch (SQLException error) {
            throw new DatabaseException("Stock transfer failed and was rolled back", error);
        }
    }

    /** Seeds the table with a few products the first time (idempotent). */
    public void seedIfEmpty() {
        if (count() == 0) {
            create(new Product(0, "Wireless Mouse", 25.99, 40));
            create(new Product(0, "Mechanical Keyboard", 89.50, 15));
            create(new Product(0, "USB-C Hub", 42.75, 30));
            create(new Product(0, "Laptop Stand", 34.00, 22));
        }
    }
}
