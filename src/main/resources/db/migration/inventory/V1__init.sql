CREATE SCHEMA inventory;

CREATE TABLE inventory.inventory
(
    product_id        SERIAL PRIMARY KEY,      -- Unique product ID
    product_name      TEXT           NOT NULL, -- Name of the product
    sku               TEXT UNIQUE    NOT NULL, -- Stock keeping unit
    category          TEXT,                    -- Product category
    quantity_in_stock INT       DEFAULT 0,     -- Current stock quantity
    price             NUMERIC(10, 2) NOT NULL, -- Price per unit
    reorder_level     INT       DEFAULT 10,    -- Minimum stock before reordering
    created_at        TIMESTAMP DEFAULT NOW(), -- When product was added
    updated_at        TIMESTAMP DEFAULT NOW()  -- When product info was last updated
);