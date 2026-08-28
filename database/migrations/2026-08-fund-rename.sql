-- ============================================================
--  Renaming a fund, and recording it
--
--  A fund's name is a label, not an accounting fact: nothing references it, so
--  correcting a typo (or an amount typed into the Name box) changes no figure
--  anywhere. That makes rename the one edit this ledger can safely allow, and
--  the reason a mis-named fund should be renamed rather than deleted and
--  recreated - recreating would strand the statement.
--
--  It is still audited, because a fund that quietly changes name between two
--  reports is exactly the kind of thing an audit trail exists to explain.
--
--  old_name is used only by RENAME rows. Every other action leaves it NULL and
--  keeps its snapshot in fund_name as before; for a RENAME, old_name is what it
--  was called and fund_name is what it became.
--
--  RUN ONCE.
-- ============================================================

ALTER TABLE fund_audit
  MODIFY COLUMN action ENUM('CREATE','RENAME','CLOSE','REOPEN','DELETE') NOT NULL;

ALTER TABLE fund_audit
  ADD COLUMN old_name VARCHAR(80) NULL AFTER fund_name;
