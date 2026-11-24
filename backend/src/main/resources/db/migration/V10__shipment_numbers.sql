ALTER TABLE import_shipments ADD COLUMN shipment_number text GENERATED ALWAYS AS ('IMP-' || id::text) STORED;
ALTER TABLE import_shipments ADD CONSTRAINT import_shipments_number_unique UNIQUE (shipment_number);
