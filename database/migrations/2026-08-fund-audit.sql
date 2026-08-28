-- ============================================================
--  Fund lifecycle: closing, deleting, and the audit trail for both
--
--  WHY A FUND IS NOT SIMPLY DELETABLE
--
--  fund_transactions.fund_id and expenses.fund_id are both plain foreign keys
--  with no ON DELETE action, so a fund that has seen any money cannot be
--  deleted at all - the database refuses it. That is deliberate and stays:
--  deleting a fund with a statement behind it would silently take every credit,
--  debit and expense voucher with it, and the module's whole premise is that a
--  ledger you can DELETE from is not a ledger.
--
--  So there are two different operations, and the difference matters:
--
--    DELETE - only for a fund with NO transactions and NO expenses, i.e. one
--             created by mistake five minutes ago. Nothing is lost because
--             nothing was ever recorded against it.
--    CLOSE  - for a fund with history. is_active goes to 0, the statement stays
--             readable forever, and nothing new can be posted to it. This is
--             what "getting rid of" a real fund means.
--
--  fund_audit records both, plus creation and reopening. fund_id is NOT a
--  foreign key on purpose: the audit row for a DELETE has to outlive the fund
--  it describes, which is the one moment the trail matters most. The snapshot
--  columns exist for the same reason - after the delete they are the only
--  remaining record of what the fund was called and what it was worth.
--
--  RUN ONCE.
-- ============================================================

CREATE TABLE IF NOT EXISTS fund_audit (
    audit_id    INT AUTO_INCREMENT PRIMARY KEY,
    -- no FK: a DELETE row must survive the fund disappearing
    fund_id     INT NOT NULL,
    action      ENUM('CREATE','CLOSE','REOPEN','DELETE') NOT NULL,
    -- snapshot taken at the moment of the action, so a deleted fund is still
    -- identifiable afterwards
    fund_name   VARCHAR(80)   NOT NULL,
    balance     DECIMAL(12,2) NOT NULL DEFAULT 0,
    txn_count   INT           NOT NULL DEFAULT 0,
    reason      VARCHAR(255),
    acted_by    VARCHAR(100),
    acted_by_id INT NULL,
    acted_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_fa_fund (fund_id, acted_at),
    INDEX idx_fa_when (acted_at),
    CONSTRAINT fk_fa_user FOREIGN KEY (acted_by_id)
        REFERENCES users(user_id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Funds that already exist pre-date the trail. Seed a CREATE row for each so
-- the history does not start with an unexplained gap.
INSERT INTO fund_audit (fund_id, action, fund_name, balance, txn_count, reason,
                        acted_by, acted_at)
SELECT f.fund_id, 'CREATE', f.name, f.opening_balance, 0,
       'Recorded when the audit trail was added', f.created_by, f.created_at
  FROM fund_accounts f
 WHERE NOT EXISTS (SELECT 1 FROM fund_audit a
                    WHERE a.fund_id = f.fund_id AND a.action = 'CREATE');
