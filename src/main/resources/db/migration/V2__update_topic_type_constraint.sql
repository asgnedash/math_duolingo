ALTER TABLE topics DROP CONSTRAINT IF EXISTS topics_type_check;
ALTER TABLE topics ADD CONSTRAINT topics_type_check CHECK (type IN ('OGE', 'EGE', 'TEST'));
