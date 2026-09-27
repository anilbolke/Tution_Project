-- Removes the logins created by database/tools/test-logins.sql, and their
-- change-history rows. Their own HR rows, targets etc. go by ON DELETE CASCADE;
-- anything they touched elsewhere keeps its row with the user set to NULL.

DELETE FROM user_audit WHERE target_user_id IN (SELECT user_id FROM (
    SELECT user_id FROM users WHERE username IN
      ('t_counsellor','t_acct','t_hr','t_officeadmin','t_abm','t_acoord','t_edp',
       't_aincharge','t_faculty','t_ahead','t_bm','t_bh','t_staff')) x);

DELETE FROM users WHERE username IN
  ('t_counsellor','t_acct','t_hr','t_officeadmin','t_abm','t_acoord','t_edp',
   't_aincharge','t_faculty','t_ahead','t_bm','t_bh','t_staff');
