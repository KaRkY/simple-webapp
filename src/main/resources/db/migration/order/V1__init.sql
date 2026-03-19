CREATE SCHEMA "order";

CREATE TABLE "order".orders
(
    order_id     SERIAL PRIMARY KEY,            -- Unique order ID
    customer_id  INT            NOT NULL,       -- ID of the customer placing the order
    order_date   TIMESTAMP   DEFAULT NOW(),     -- Date and time of the order
    status       TEXT DEFAULT 'pending', -- Order status (pending, shipped, delivered, canceled)
    total_amount NUMERIC(10, 2) NOT NULL,       -- Total amount of the order
    items        JSONB,                         -- JSON array of ordered items
    created_at   TIMESTAMP   DEFAULT NOW(),     -- Timestamp when the order record was created
    updated_at   TIMESTAMP   DEFAULT NOW()      -- Timestamp when the order record was last updated
);