ALTER TABLE refunds DROP CONSTRAINT IF EXISTS refunds_status_check;
ALTER TABLE refunds ADD CONSTRAINT refunds_status_check CHECK(status IN ('PENDING','APPROVED','REJECTED'));
ALTER TABLE customers ADD COLUMN branch_id uuid REFERENCES branches(id);
CREATE INDEX customers_branch_name_idx ON customers(branch_id,name);
CREATE TABLE auth_sessions(token_hash varchar(64) PRIMARY KEY, user_id uuid NOT NULL REFERENCES users(id), created_at timestamptz NOT NULL DEFAULT now(), expires_at timestamptz NOT NULL, revoked_at timestamptz);
CREATE INDEX auth_sessions_user_idx ON auth_sessions(user_id,expires_at);
CREATE UNIQUE INDEX product_prices_one_current_price ON product_prices(product_id,currency_code) WHERE effective_to IS NULL;
