BEGIN;
CREATE TABLE IF NOT EXISTS payment_inbox (
  payment_id varchar(36) PRIMARY KEY,
  payload varchar(256) NOT NULL,
  response varchar(256) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
-- The effect is a simulated posting, not an external bank transfer.
-- No unique constraint on payment_id here: tests can detect an accidental second effect.
CREATE TABLE IF NOT EXISTS payment_effect (
  effect_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  payment_id varchar(36) NOT NULL REFERENCES payment_inbox(payment_id),
  amount numeric(12,2) NOT NULL CHECK(amount > 0),
  currency varchar(3) NOT NULL CHECK(currency = 'EUR'),
  created_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX IF NOT EXISTS payment_effect_payment_id ON payment_effect(payment_id);
COMMIT;
