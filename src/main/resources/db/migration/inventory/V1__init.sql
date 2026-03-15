CREATE SCHEMA inventory;

CREATE TABLE inventory.inventory
(
    product_id        SERIAL PRIMARY KEY,           -- Unique product ID
    product_name      VARCHAR(255)        NOT NULL, -- Name of the product
    sku               VARCHAR(100) UNIQUE NOT NULL, -- Stock keeping unit
    category          VARCHAR(100),                 -- Product category
    quantity_in_stock INT       DEFAULT 0,          -- Current stock quantity
    price             NUMERIC(10, 2)      NOT NULL, -- Price per unit
    reorder_level     INT       DEFAULT 10,         -- Minimum stock before reordering
    created_at        TIMESTAMP DEFAULT NOW(),      -- When product was added
    updated_at        TIMESTAMP DEFAULT NOW()       -- When product info was last updated
);