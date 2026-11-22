-- Preserve historical line units while replacing stock-unit references atomically.
CREATE TEMP TABLE unit_remap(old_id bigint PRIMARY KEY,new_id bigint NOT NULL) ON COMMIT DROP;
DO $$
DECLARE b record; target_id bigint; candidate text; suffix integer;
BEGIN
 FOR b IN SELECT * FROM base_units ORDER BY id LOOP
  SELECT id INTO target_id FROM units WHERE base_unit=b.id AND name=b.name ORDER BY id LIMIT 1;
  IF target_id IS NULL THEN
   candidate:=b.name; suffix:=0;
   WHILE EXISTS(SELECT 1 FROM units WHERE name=candidate) LOOP
    suffix:=suffix+1; candidate:=left(b.name,220)||' [migrated '||b.id||'-'||suffix||']';
   END LOOP;
   INSERT INTO units(name,short_name,base_unit,created_at,updated_at)
    VALUES(candidate,'u-'||b.id,b.id,b.created_at,b.updated_at) RETURNING id INTO target_id;
  END IF;
  INSERT INTO unit_remap VALUES(b.id,target_id);
 END LOOP;
END $$;
-- Drop only old foreign keys to the base-unit table or the composite unit/base key.
DO $$
DECLARE c record; base_att smallint;
BEGIN
 SELECT attnum INTO base_att FROM pg_attribute WHERE attrelid='units'::regclass AND attname='base_unit';
 FOR c IN SELECT conrelid::regclass AS tbl,conname FROM pg_constraint
  WHERE contype='f' AND (confrelid='base_units'::regclass OR (confrelid='units'::regclass AND base_att=ANY(confkey))) LOOP
  EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I',c.tbl,c.conname);
 END LOOP;
END $$;
-- Preserve the original line unit labels even when a unit is renamed later.
ALTER TABLE purchase_items ADD COLUMN unit_name_snapshot varchar(255);
ALTER TABLE sale_items ADD COLUMN unit_name_snapshot varchar(255);
UPDATE purchase_items i SET unit_name_snapshot=COALESCE((SELECT name FROM units WHERE id=i.purchase_unit),(SELECT name FROM base_units WHERE id=i.base_unit));
UPDATE sale_items i SET unit_name_snapshot=COALESCE((SELECT name FROM units WHERE id=i.sale_unit),(SELECT name FROM base_units WHERE id=i.base_unit));
UPDATE products p SET product_unit=m.new_id FROM unit_remap m WHERE p.product_unit=m.old_id;
UPDATE products SET sale_unit=product_unit,purchase_unit=product_unit;
UPDATE purchase_items i SET base_unit=m.new_id FROM unit_remap m WHERE i.base_unit=m.old_id;
UPDATE sale_items i SET base_unit=m.new_id FROM unit_remap m WHERE i.base_unit=m.old_id;
ALTER TABLE purchase_items RENAME COLUMN base_unit TO stock_unit_id;
ALTER TABLE sale_items RENAME COLUMN base_unit TO stock_unit_id;
ALTER TABLE products ADD CONSTRAINT products_product_unit_fk FOREIGN KEY(product_unit) REFERENCES units(id);
ALTER TABLE products ADD CONSTRAINT products_single_unit CHECK(sale_unit=product_unit AND purchase_unit=product_unit);
ALTER TABLE products ALTER COLUMN sale_unit SET NOT NULL;
ALTER TABLE products ALTER COLUMN purchase_unit SET NOT NULL;
ALTER TABLE purchase_items ADD CONSTRAINT purchase_stock_unit_fk FOREIGN KEY(stock_unit_id) REFERENCES units(id);
ALTER TABLE sale_items ADD CONSTRAINT sale_stock_unit_fk FOREIGN KEY(stock_unit_id) REFERENCES units(id);
ALTER TABLE purchase_items ALTER COLUMN unit_name_snapshot SET NOT NULL;
ALTER TABLE sale_items ALTER COLUMN unit_name_snapshot SET NOT NULL;
ALTER TABLE units DROP COLUMN base_unit;
DROP TABLE base_units;
