package com.femzyk.jdbccrud.gui;

import java.util.List;

import com.femzyk.jdbccrud.core.Product;
import com.femzyk.jdbccrud.core.ProductRepository;

import javafx.application.Application;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * CrudFxApp - JavaFX front end of the FEMZYK JDBC CRUD Demo
 * (CS 1103-01, Unit 5 discussion).
 *
 * The window is a small admin panel over the products table: the
 * TableView shows the live database content, the form fields feed the
 * CREATE and UPDATE operations, and the Delete button removes the
 * selected row. Every action goes through the same ProductRepository
 * (JDBC + PreparedStatements) that the console front end uses, and each
 * action refreshes the table straight from the database, so what you see
 * is always what is actually stored.
 */
public class CrudFxApp extends Application {

    private static final String APP_TITLE = "FEMZYK JDBC CRUD Demo - CS 1103-01 Unit 5";

    private final ProductRepository repository = new ProductRepository();

    private TableView<Product> table;
    private TextField nameField;
    private TextField priceField;
    private TextField quantityField;
    private Label status;
    private Button saveButton;

    @Override
    public void start(Stage stage) {
        stage.setTitle(APP_TITLE);

        Label title = new Label("FEMZYK JDBC CRUD Demo");
        title.getStyleClass().add("header-title");
        Label subtitle = new Label("CS 1103-01 - Unit 5 - products table (H2 in MySQL mode, JDBC + PreparedStatements)");
        subtitle.getStyleClass().add("header-sub");

        // ----- Table: live view of the products rows -----
        table = new TableView<>();
        table.setId("productTable");
        table.setPlaceholder(new Label("No products in the database."));
        TableColumn<Product, String> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(data -> new SimpleObjectProperty<>(
                String.valueOf(data.getValue().id())));
        TableColumn<Product, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().name()));
        TableColumn<Product, String> priceCol = new TableColumn<>("Price");
        priceCol.setCellValueFactory(data -> new SimpleObjectProperty<>(
                String.format("$%.2f", data.getValue().price())));
        TableColumn<Product, String> qtyCol = new TableColumn<>("Quantity");
        qtyCol.setCellValueFactory(data -> new SimpleObjectProperty<>(
                String.valueOf(data.getValue().quantity())));
        table.getColumns().setAll(java.util.List.of(idCol, nameCol, priceCol, qtyCol));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldRow, newRow) -> fillForm(newRow));

        // ----- Form: fields for CREATE and UPDATE -----
        nameField = field("nameField", "Product name");
        priceField = field("priceField", "Price, e.g. 25.99");
        quantityField = field("quantityField", "Quantity, e.g. 10");
        HBox formRow = new HBox(10, nameField, priceField, quantityField);
        formRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(nameField, javafx.scene.layout.Priority.ALWAYS);

        saveButton = new Button("Add product");
        saveButton.setId("saveButton");
        saveButton.setOnAction(event -> saveProduct());
        Button updateButton = new Button("Update selected");
        updateButton.setId("updateButton");
        updateButton.setOnAction(event -> updateSelected());
        Button deleteButton = new Button("Delete selected");
        deleteButton.setId("deleteButton");
        deleteButton.setOnAction(event -> deleteSelected());
        Button refreshButton = new Button("Refresh");
        refreshButton.setId("refreshButton");
        refreshButton.setOnAction(event -> refreshFromDatabase());
        HBox buttonRow = new HBox(10, saveButton, updateButton, deleteButton, refreshButton);
        buttonRow.setAlignment(Pos.CENTER_LEFT);

        status = new Label();
        status.setId("statusLabel");
        status.setWrapText(true);
        status.setVisible(false);
        status.setManaged(false);

        VBox formCard = new VBox(10, sectionLabel("Create / update / delete"),
                formRow, buttonRow, status);
        formCard.getStyleClass().add("card");

        VBox tableCard = new VBox(10, sectionLabel("Products table (live from the database)"), table);
        tableCard.getStyleClass().add("card");

        VBox root = new VBox(14, title, subtitle, tableCard, formCard);
        root.setPadding(new Insets(20));
        root.getStyleClass().add("root");

        try {
            repository.createTableIfNotExists();
            repository.seedIfEmpty();
        } catch (RuntimeException error) {
            show("Database error: " + error.getMessage(), true);
        }
        refreshFromDatabase();

        Scene scene = new Scene(root, 780, 720);
        scene.getStylesheets().add(getClass().getResource("styles.css").toExternalForm());
        stage.setScene(scene);
        stage.show();
    }

    /** CREATE: inserts the values from the form as a new row. */
    private void saveProduct() {
        hide();
        Product parsed = parseForm(0);
        if (parsed == null) {
            return;
        }
        try {
            Product saved = repository.create(parsed);
            show("Created row " + saved.id() + " via INSERT - the table below reloaded from the database.", false);
            refreshFromDatabase();
            selectById(saved.id());
            clearForm();
        } catch (RuntimeException error) {
            show("Create failed: " + rootMessage(error), true);
        }
    }

    /** UPDATE: applies the form values to the selected row. */
    private void updateSelected() {
        hide();
        Product selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            show("Select a row in the table first.", true);
            return;
        }
        Product parsed = parseForm(selected.id());
        if (parsed == null) {
            return;
        }
        try {
            boolean changed = repository.update(parsed);
            if (changed) {
                show("Updated row " + parsed.id() + " via UPDATE - reloaded from the database.", false);
                refreshFromDatabase();
                selectById(parsed.id());
            } else {
                show("The UPDATE affected no rows - was the row deleted elsewhere?", true);
            }
        } catch (RuntimeException error) {
            show("Update failed: " + rootMessage(error), true);
        }
    }

    /** DELETE: removes the selected row. */
    private void deleteSelected() {
        hide();
        Product selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            show("Select a row in the table first.", true);
            return;
        }
        try {
            boolean removed = repository.delete(selected.id());
            if (removed) {
                show("Deleted row " + selected.id() + " via DELETE - reloaded from the database.", false);
                refreshFromDatabase();
                clearForm();
            } else {
                show("The DELETE affected no rows - was the row deleted elsewhere?", true);
            }
        } catch (RuntimeException error) {
            show("Delete failed: " + rootMessage(error), true);
        }
    }

    /** Reloads the table content straight from the database. */
    private void refreshFromDatabase() {
        try {
            List<Product> products = repository.findAll();
            table.getItems().setAll(products);
        } catch (RuntimeException error) {
            show("Could not load products: " + rootMessage(error), true);
        }
    }

    /** Validates the form and builds a Product, or shows a validation error. */
    private Product parseForm(int id) {
        String name = nameField.getText().trim();
        String priceText = priceField.getText().trim();
        String quantityText = quantityField.getText().trim();
        if (name.isEmpty()) {
            show("Validation: the product name cannot be empty.", true);
            return null;
        }
        double price;
        try {
            price = Double.parseDouble(priceText);
        } catch (NumberFormatException error) {
            show("Validation: the price must be a number, e.g. 25.99.", true);
            return null;
        }
        int quantity;
        try {
            quantity = Integer.parseInt(quantityText);
        } catch (NumberFormatException error) {
            show("Validation: the quantity must be a whole number.", true);
            return null;
        }
        if (price < 0) {
            show("Validation: the price cannot be negative.", true);
            return null;
        }
        if (quantity < 0) {
            show("Validation: the quantity cannot be negative.", true);
            return null;
        }
        return new Product(id, name, price, quantity);
    }

    /** Fills the form with the values of the selected row. */
    private void fillForm(Product product) {
        if (product != null) {
            nameField.setText(product.name());
            priceField.setText(String.valueOf(product.price()));
            quantityField.setText(String.valueOf(product.quantity()));
        }
    }

    /** Selects the row with the given id, if present. */
    private void selectById(int id) {
        for (Product product : table.getItems()) {
            if (product.id() == id) {
                table.getSelectionModel().select(product);
                return;
            }
        }
    }

    /** Clears the three form fields. */
    private void clearForm() {
        nameField.clear();
        priceField.clear();
        quantityField.clear();
    }

    /** Creates a styled text field. */
    private TextField field(String id, String prompt) {
        TextField field = new TextField();
        field.setId(id);
        field.setPromptText(prompt);
        return field;
    }

    /** Creates a section heading label. */
    private Label sectionLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("section-title");
        return label;
    }

    /** Shows a status message in error (red) or success (green) style. */
    private void show(String message, boolean isError) {
        status.getStyleClass().removeAll("error", "result");
        status.getStyleClass().add(isError ? "error" : "result");
        status.setText(message);
        status.setVisible(true);
        status.setManaged(true);
    }

    /** Hides the status message. */
    private void hide() {
        status.setVisible(false);
        status.setManaged(false);
    }

    /** Unwraps nested exception messages for display. */
    private String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return message != null ? message : cause.getClass().getSimpleName();
    }
}
