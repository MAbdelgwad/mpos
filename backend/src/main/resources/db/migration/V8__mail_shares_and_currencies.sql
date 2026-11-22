INSERT INTO ui_settings(key,value) VALUES ('currency_code','');
ALTER TABLE sales ADD COLUMN currency_code varchar(3) NOT NULL DEFAULT '';
ALTER TABLE sales ADD COLUMN currency_symbol varchar(500) NOT NULL DEFAULT '';
ALTER TABLE purchases ADD COLUMN currency_code varchar(3) NOT NULL DEFAULT '';
ALTER TABLE purchases ADD COLUMN currency_symbol varchar(500) NOT NULL DEFAULT '';
UPDATE sales SET currency_symbol=(SELECT value FROM ui_settings WHERE key='currency_symbol');
UPDATE purchases SET currency_symbol=(SELECT value FROM ui_settings WHERE key='currency_symbol');
CREATE FUNCTION invoice_currency_snapshot() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 NEW.currency_code := (SELECT value FROM ui_settings WHERE key='currency_code');
 NEW.currency_symbol := (SELECT value FROM ui_settings WHERE key='currency_symbol');
 RETURN NEW;
END $$;
CREATE TRIGGER sales_currency BEFORE INSERT ON sales FOR EACH ROW EXECUTE FUNCTION invoice_currency_snapshot();
CREATE TRIGGER purchases_currency BEFORE INSERT ON purchases FOR EACH ROW EXECUTE FUNCTION invoice_currency_snapshot();
CREATE TABLE mail_settings(id integer PRIMARY KEY CHECK(id=1),host varchar(255) NOT NULL DEFAULT '',port integer NOT NULL DEFAULT 587,username varchar(254) NOT NULL DEFAULT '',secret text NOT NULL DEFAULT '',security varchar(10) NOT NULL DEFAULT 'starttls',from_email varchar(254) NOT NULL DEFAULT '',from_name varchar(100) NOT NULL DEFAULT 'MPOS',public_url varchar(500) NOT NULL DEFAULT '',enabled boolean NOT NULL DEFAULT false,verify_new_users boolean NOT NULL DEFAULT false);
INSERT INTO mail_settings(id) VALUES(1);
ALTER TABLE app_users ADD COLUMN email_verified boolean NOT NULL DEFAULT true;
CREATE TABLE email_verifications(user_id bigint PRIMARY KEY REFERENCES app_users(id) ON DELETE CASCADE,email varchar(254) NOT NULL,token_hash char(64) UNIQUE NOT NULL,expires_at timestamptz NOT NULL,sent_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE invoice_shares(id uuid PRIMARY KEY,kind varchar(10) NOT NULL CHECK(kind IN('sales','purchases')),document_id bigint NOT NULL,token_hash char(64) UNIQUE NOT NULL,snapshot text NOT NULL,expires_at timestamptz NOT NULL,revoked boolean NOT NULL DEFAULT false,created_at timestamptz NOT NULL DEFAULT now(),created_by bigint REFERENCES app_users(id) ON DELETE SET NULL);
CREATE INDEX invoice_shares_document ON invoice_shares(kind,document_id);
