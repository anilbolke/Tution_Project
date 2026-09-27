-- One throw-away login per role, for database/tools/access_matrix_test.py.
-- Password for all: test123.  NEVER run on production.
-- Remove afterwards with database/tools/test-logins-remove.sql.

INSERT INTO users (username, password, full_name, role) VALUES
  ('t_counsellor',  SHA2('test123',256), 'Test Counsellor',           'COUNSELLOR'),
  ('t_acct',        SHA2('test123',256), 'Test Accountant',           'ACCOUNTANT'),
  ('t_hr',          SHA2('test123',256), 'Test HR',                   'HR'),
  ('t_officeadmin', SHA2('test123',256), 'Test Office Admin',         'OFFICE_ADMIN'),
  ('t_abm',         SHA2('test123',256), 'Test ABM',                  'ABM'),
  ('t_acoord',      SHA2('test123',256), 'Test Academic Coordinator', 'ACADEMIC_COORDINATOR'),
  ('t_edp',         SHA2('test123',256), 'Test EDP',                  'EDP'),
  ('t_aincharge',   SHA2('test123',256), 'Test Academic Incharge',    'ACADEMIC_INCHARGE'),
  ('t_faculty',     SHA2('test123',256), 'Test Faculty',              'TEACHER'),
  ('t_ahead',       SHA2('test123',256), 'Test Academic Head',        'ACADEMIC_HEAD'),
  ('t_bm',          SHA2('test123',256), 'Test Branch Manager',       'BRANCH_MANAGER'),
  ('t_bh',          SHA2('test123',256), 'Test Business Head',        'BUSINESS_HEAD'),
  ('t_staff',       SHA2('test123',256), 'Test Staff',                'STAFF')
ON DUPLICATE KEY UPDATE role = VALUES(role), is_active = 1, password = VALUES(password);

-- a one-person team, so an ABM has someone reporting to them
UPDATE users c JOIN users a ON a.username = 't_abm'
   SET c.reports_to = a.user_id
 WHERE c.username = 't_counsellor';
