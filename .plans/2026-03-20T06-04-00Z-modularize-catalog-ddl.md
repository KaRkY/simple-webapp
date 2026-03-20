---
title: Modularize Product Catalog DDL into catalog and localization modules
created: 2026-03-20T06:04:00Z
summary: Split 01-setup.sql into catalog and localization Spring Modulith modules with schemas, dropping all trigger enforcements.
description: >
  Takes the monolithic DDL from the ecommerce-product-catalog project and splits it
  into two Spring Modulith modules. `localization` owns the general-purpose languages
  table. `catalog` owns all product/attribute/translation tables and declares a
  module dependency on `localization`. All trigger-based business rule enforcement is
  dropped — to be reimplemented in the Java service layer.
---

assumptions:
- Spring Modulith Flyway integration orders module migrations by the module dependency
  graph. Declaring catalog's @ApplicationModule(allowedDependencies="localization")
  ensures localization schema exists before catalog migrations run.
- All trigger enforcement (type-checking, activation guards, translation completeness)
  is intentionally dropped and will be done in Java service code.
- `is_active DEFAULT false` replaces the `force_is_active_false` trigger (column
  default already captures the intent).
- Explicit sequences are replaced with `SERIAL` to match the project's existing
  convention (see order, inventory schemas).
- Translation text (entity labels/names) lives in `localization.translations`, keyed
  by a single dot-separated `key` + `language_code`. This keeps localization fully
  decoupled from catalog — no reverse dependency. Referential integrity on keys is
  enforced in the Java service layer.
- Key conventions (by convention, not DB constraint):
    - `product_type.{code}`                 → product type name
    - `product.{code}`                      → product name
    - `attribute.{code}`                    → attribute label
    - `attribute.{attr_code}.{option_code}` → attribute option label
- `catalog.product_types` has no `name` column — all labels live in
  `localization.translations`.
- `catalog.products` has a `code TEXT NOT NULL UNIQUE` field (separate from `sku`)
  used as the key segment in translation keys.
- `product_attribute_values_text` stays in `catalog`: it is a localised attribute
  value (product data), not a label translation. It holds a cross-schema FK to
  localization.languages.
- The old per-entity translation tables (attribute_translations,
  attribute_option_translations, product_translations) are replaced by the generic
  localization.translations table.
- No Java domain logic is added in this plan — only the package scaffolding and
  Flyway migrations.

phases:

- phase_id: 1
  description: >
    Create the localization module — schema, languages table, generic translations
    table, and Java package stub.
  tasks:
    - file_changes:
        path: src/main/resources/db/migration/localization/V1__init.sql
        action: add
        stub: false
        details: |
          CREATE SCHEMA localization;

          CREATE TABLE localization.languages (
            code        TEXT    NOT NULL,
            name        TEXT    NOT NULL,
            is_active   BOOLEAN NOT NULL DEFAULT FALSE,
            PRIMARY KEY (code)
          );
          CREATE INDEX ON localization.languages (is_active);

          -- Translation store: a dot-separated key identifies the translatable entity.
          -- Key conventions (enforced by application layer, not DB):
          --   product_type.{code}                  → product type name
          --   product.{code}                       → product name
          --   attribute.{code}                     → attribute label
          --   attribute.{attr_code}.{option_code}  → attribute option label
          CREATE TABLE localization.translations (
            key           TEXT NOT NULL,
            language_code TEXT NOT NULL,
            value         TEXT NOT NULL,
            PRIMARY KEY (key, language_code),
            FOREIGN KEY (language_code) REFERENCES localization.languages (code) ON DELETE CASCADE
          );
          CREATE INDEX ON localization.translations (language_code);

    - file_changes:
        path: src/main/java/simple/simple_webapp/localization/package-info.java
        action: add
        stub: true
        details: |
          @NullMarked
          @ApplicationModule
          package simple.simple_webapp.localization;

          import org.jspecify.annotations.NullMarked;
          import org.springframework.modulith.ApplicationModule;

  rollback: "Drop schema localization CASCADE; delete migration file."

- phase_id: 2
  description: >
    Create the catalog module — schema, all product/attribute tables (no translation
    tables — those are handled by localization.translations), and Java package stub.
    Triggers are intentionally omitted.
  tasks:
    - file_changes:
        path: src/main/resources/db/migration/catalog/V1__init.sql
        action: add
        stub: false
        details: |
          CREATE SCHEMA catalog;

          -- Product types (name omitted — labels live in localization.translations)
          CREATE TABLE catalog.product_types (
            id        SERIAL  NOT NULL,
            code      TEXT    NOT NULL,
            is_active BOOLEAN NOT NULL DEFAULT FALSE,
            PRIMARY KEY (id),
            UNIQUE (code)
          );
          CREATE INDEX ON catalog.product_types (is_active);

          -- Attributes
          CREATE TABLE catalog.attributes (
            id        SERIAL  NOT NULL,
            code      TEXT    NOT NULL,
            data_type TEXT    NOT NULL CHECK (data_type IN ('text','number','boolean','option')),
            optional  BOOLEAN NOT NULL DEFAULT FALSE,
            is_active BOOLEAN NOT NULL DEFAULT FALSE,
            PRIMARY KEY (id),
            UNIQUE (code)
          );
          CREATE INDEX ON catalog.attributes (is_active);

          -- Attribute options
          CREATE TABLE catalog.attribute_options (
            id           SERIAL  NOT NULL,
            attribute_id INT     NOT NULL,
            code         TEXT    NOT NULL,
            is_active    BOOLEAN NOT NULL DEFAULT FALSE,
            PRIMARY KEY (id),
            UNIQUE (attribute_id, code),
            FOREIGN KEY (attribute_id) REFERENCES catalog.attributes (id) ON DELETE CASCADE
          );
          CREATE INDEX ON catalog.attribute_options (is_active);

          -- Product type ↔ attribute mapping
          CREATE TABLE catalog.product_type_attributes (
            product_type_id INT NOT NULL,
            attribute_id    INT NOT NULL,
            PRIMARY KEY (product_type_id, attribute_id),
            FOREIGN KEY (product_type_id) REFERENCES catalog.product_types (id) ON DELETE CASCADE,
            FOREIGN KEY (attribute_id)    REFERENCES catalog.attributes    (id) ON DELETE CASCADE
          );

          -- Products (code is the stable identifier used in translation keys;
          --           sku is the commercial/warehouse identifier)
          CREATE TABLE catalog.products (
            id              SERIAL                   NOT NULL,
            product_type_id INT                      NOT NULL,
            code            TEXT                     NOT NULL,
            sku             TEXT                     NOT NULL,
            is_active       BOOLEAN                  NOT NULL DEFAULT FALSE,
            created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
            PRIMARY KEY (id),
            UNIQUE (code),
            UNIQUE (sku),
            FOREIGN KEY (product_type_id) REFERENCES catalog.product_types (id)
          );
          CREATE INDEX ON catalog.products (is_active);
          CREATE INDEX ON catalog.products (product_type_id);

          -- Localised text attribute values (the value IS the translation — kept here
          -- as it is product data, not a label. Cross-schema FK to localization.languages.)
          CREATE TABLE catalog.product_attribute_values_text (
            product_id    INT  NOT NULL,
            attribute_id  INT  NOT NULL,
            language_code TEXT NOT NULL,
            value         TEXT NOT NULL,
            PRIMARY KEY (product_id, attribute_id, language_code),
            FOREIGN KEY (product_id)    REFERENCES catalog.products          (id)   ON DELETE CASCADE,
            FOREIGN KEY (attribute_id)  REFERENCES catalog.attributes        (id)   ON DELETE RESTRICT,
            FOREIGN KEY (language_code) REFERENCES localization.languages    (code) ON DELETE RESTRICT
          );
          CREATE INDEX ON catalog.product_attribute_values_text (attribute_id, language_code);

          -- Attribute values — number
          CREATE TABLE catalog.product_attribute_values_number (
            product_id   INT     NOT NULL,
            attribute_id INT     NOT NULL,
            value        NUMERIC NOT NULL,
            PRIMARY KEY (product_id, attribute_id),
            FOREIGN KEY (product_id)   REFERENCES catalog.products   (id) ON DELETE CASCADE,
            FOREIGN KEY (attribute_id) REFERENCES catalog.attributes (id) ON DELETE RESTRICT
          );

          -- Attribute values — boolean
          CREATE TABLE catalog.product_attribute_values_boolean (
            product_id   INT     NOT NULL,
            attribute_id INT     NOT NULL,
            value        BOOLEAN NOT NULL,
            PRIMARY KEY (product_id, attribute_id),
            FOREIGN KEY (product_id)   REFERENCES catalog.products   (id) ON DELETE CASCADE,
            FOREIGN KEY (attribute_id) REFERENCES catalog.attributes (id) ON DELETE RESTRICT
          );

          -- Attribute values — option
          CREATE TABLE catalog.product_attribute_values_option (
            product_id   INT NOT NULL,
            attribute_id INT NOT NULL,
            option_id    INT NOT NULL,
            PRIMARY KEY (product_id, attribute_id),
            FOREIGN KEY (product_id)   REFERENCES catalog.products          (id) ON DELETE CASCADE,
            FOREIGN KEY (attribute_id) REFERENCES catalog.attributes        (id) ON DELETE RESTRICT,
            FOREIGN KEY (option_id)    REFERENCES catalog.attribute_options (id) ON DELETE RESTRICT
          );
          CREATE INDEX ON catalog.product_attribute_values_option (attribute_id, option_id);

          -- NOTE: Label translations for product_types, attributes, attribute_options,
          -- and products are stored in localization.translations using entity_type
          -- conventions: 'catalog.product_type', 'catalog.attribute',
          -- 'catalog.attribute_option', 'catalog.product'.
          --
          -- NOTE: All trigger-based enforcement (type validation, activation guards,
          -- translation completeness checks) has been intentionally dropped.
          -- These rules will be enforced in the Java service layer.

    - file_changes:
        path: src/main/java/simple/simple_webapp/catalog/package-info.java
        action: add
        stub: true
        details: |
          @NullMarked
          @ApplicationModule(allowedDependencies = "localization")
          package simple.simple_webapp.catalog;

          import org.jspecify.annotations.NullMarked;
          import org.springframework.modulith.ApplicationModule;

  rollback: "Drop schema catalog CASCADE; delete migration file."

open_questions: []

resolved_questions:
- Q: Should localization.languages use an integer surrogate key (consistent with
     other modules' SERIAL PKs) or keep text `code` as PK?
  A: Keep text `code` as PK — confirmed by user. ISO 639-1 codes are stable natural
     identifiers; a surrogate key would add no value here.
- Q: Convention for entity_type values in localization.translations?
  A: Replaced entirely by the key-based design. A single dot-separated `key` column
     replaces `entity_type + entity_id + field`. Key structure: `{entity_type}.{code}`
     for top-level entities, `attribute.{attr_code}.{option_code}` for attribute options.

status: ready

change_log:
- timestamp: 2026-03-20T06:04:00Z
  changes: "Initial draft"
- timestamp: 2026-03-20T06:11:00Z
  changes: >
    Revised to Option B: replaced per-entity translation tables in catalog with a
    single generic localization.translations table. catalog no longer contains any
    translation tables. product_attribute_values_text stays in catalog (it is product
    data, not a label). Assumptions and open_questions updated accordingly.
- timestamp: 2026-03-20T16:39:00Z
  changes: >
    Reworked localization.translations to a flat key-based design: (key, language_code)
    PK replaces (entity_type, entity_id, language_code, field). Key format is a
    dot-separated path: product_type.{code}, product.{code}, attribute.{code},
    attribute.{attr_code}.{option_code}. catalog.product_types loses the name column
    (labels fully delegated to translations). catalog.products gains a code field
    (separate from sku) used as the key segment. Both open questions now fully resolved.
