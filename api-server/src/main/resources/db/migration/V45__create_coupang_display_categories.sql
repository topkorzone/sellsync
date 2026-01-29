CREATE TABLE coupang_display_categories (
    id                     UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    display_category_code  VARCHAR(20) NOT NULL UNIQUE,
    display_category_name  VARCHAR(200) NOT NULL,
    parent_category_code   VARCHAR(20),
    depth                  INTEGER NOT NULL DEFAULT 0,
    created_at             TIMESTAMP NOT NULL DEFAULT now(),
    updated_at             TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_cdc_parent ON coupang_display_categories(parent_category_code);
CREATE INDEX idx_cdc_name   ON coupang_display_categories(display_category_name);
