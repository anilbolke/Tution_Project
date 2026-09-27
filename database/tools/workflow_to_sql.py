"""
Generates database/migrations/2026-09-role-activity-matrix.sql from the client's
Doc/Mapping/Work Flow.xlsx (Sheet1: activities down, roles across, YES/NO).

    python database/tools/workflow_to_sql.py

Re-run only to reset the matrix to the sheet - the /role-mapping screen edits the
same table, and a re-seed overwrites those edits.
"""
import os
import sys

import openpyxl

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SHEET = os.path.join(ROOT, 'Doc', 'Mapping', 'Work Flow.xlsx')
OUT = os.path.join(ROOT, 'database', 'migrations', '2026-09-role-activity-matrix.sql')

# Sheet columns C..N, left to right -> role codes (model/Role.java)
ROLE_COLS = ['COUNSELLOR', 'ACCOUNTANT', 'HR', 'OFFICE_ADMIN', 'ABM', 'ACADEMIC_COORDINATOR',
             'EDP', 'ACADEMIC_INCHARGE', 'TEACHER', 'ACADEMIC_HEAD', 'BRANCH_MANAGER', 'BUSINESS_HEAD']

# Sheet rows, top to bottom: (erp_tab, activity_code, label). Order must match the sheet.
ACTIVITIES = [
    ('Dashboard', 'DASHBOARD',          'Dashboard'),
    ('My Sales',  'MY_SALES',           'My Sales'),
    ('Student',   'STUDENT',            'Student'),
    ('Fees',      'FEES',               'Fees'),
    ('Sales',     'SALES_LEAD',         'Lead'),
    ('Sales',     'SALES_FOLLOWUP',     'Follow Up'),
    ('Sales',     'SALES_COUNSELLOR',   'Counsellor'),
    ('Sales',     'SALES_REMINDER',     'Reminder'),
    ('Sales',     'SALES_TARGET',       'Target'),
    ('Academics', 'ACAD_ATTENDANCE',    'Attendance'),
    ('Academics', 'ACAD_EXAM',          'Exam'),
    ('Academics', 'ACAD_IMPORT',        'Import'),
    ('Academics', 'ACAD_EXAM_SETUP',    'Exam Setup'),
    ('Academics', 'ACAD_CANDIDATE',     'Candidate'),
    ('Academics', 'ACAD_SCAN',          'Scan Sheet'),
    ('Academics', 'ACAD_RESULT',        'Result'),
    ('Academics', 'ACAD_ONLINE_EXAM',   'Online Exam'),
    ('Academics', 'ACAD_ONLINE_RESULT', 'Online Result'),
    ('Academics', 'ACAD_MATERIAL',      'Material'),
    ('Academics', 'ACAD_OMR',           'OMR'),
    ('Academics', 'ACAD_TICKETS',       'Tickets'),
    ('Finance',   'FIN_FUND',           'Fund'),
    ('Finance',   'FIN_VENDOR',         'Vendor'),
    ('Finance',   'FIN_WORK_ORDER',     'Work Order'),
    ('Finance',   'FIN_EXPENSE',        'Expenses'),
    ('Finance',   'FIN_EXAM_FEES',      'Exam Fees'),
    ('People-HR', 'HR_ATTENDANCE',      'Attendance'),
    ('People-HR', 'HR_LEAVE',           'Leave'),
    ('People-HR', 'HR_SALARY',          'Salary'),
    ('People-HR', 'HR_STAFF',           'Staff'),
    # Reports: code = RPT_ + ReportDAO type id, upper-cased, '-' -> '_'
    ('Reports',   'RPT_LEAD_SOURCE',        'Lead Source Analysis'),
    ('Reports',   'RPT_COUNSELLOR',         'Counsellor Performance'),
    ('Reports',   'RPT_FUNNEL',             'Conversion Funnel'),
    ('Reports',   'RPT_LOST_LEAD',          'Lost Lead Analysis'),
    ('Reports',   'RPT_COURSE_WISE',        'Course-wise Admissions'),
    ('Reports',   'RPT_COLLECTION',         'Fee Collection Register'),
    ('Reports',   'RPT_PENDING_FEE',        'Pending Fees & Ageing'),
    ('Reports',   'RPT_DISCOUNT',           'Discount & Scholarship Register'),
    ('Reports',   'RPT_TARGET',             'Target vs Achievement'),
    ('Reports',   'RPT_EXAM_FEE',           'Exam Fee Collection'),
    ('Reports',   'RPT_EXPENSE_REGISTER',   'Expense Register'),
    ('Reports',   'RPT_VENDOR_OUTSTANDING', 'Vendor Outstanding'),
    ('Reports',   'RPT_FUND_STATEMENT',     'Fund Statement'),
]

# URL -> activity. A path listed under several activities opens for ANY of them.
# /hr, /reports and /report-export are decided per tab / report type in AccessDAO.
PATHS = {
    'DASHBOARD':          ['/dashboard.jsp'],
    'MY_SALES':           ['/my-dashboard'],
    'STUDENT':            ['/students', '/student', '/admission', '/admission.jsp'],
    'FEES':               ['/fees', '/collect', '/receipt', '/fee-plan', '/pay-online', '/pay-verify'],
    'SALES_LEAD':         ['/inquiries', '/lead', '/search'],
    'SALES_FOLLOWUP':     ['/followup'],
    'SALES_COUNSELLOR':   ['/demo'],
    'SALES_REMINDER':     ['/reminders'],
    'SALES_TARGET':       ['/targets'],
    'ACAD_ATTENDANCE':    ['/attendance', '/attendance-report'],
    'ACAD_EXAM':          ['/exams', '/exam-new', '/exam-marks', '/exam-results'],
    'ACAD_IMPORT':        ['/exam-import'],
    'ACAD_EXAM_SETUP':    ['/exam-setup'],
    'ACAD_CANDIDATE':     ['/candidates', '/roll-list', '/hall-tickets'],
    'ACAD_SCAN':          ['/exam-scan'],
    'ACAD_RESULT':        ['/scholarship-results'],
    'ACAD_ONLINE_EXAM':   ['/online-exams'],
    'ACAD_ONLINE_RESULT': ['/online-exam-results'],
    'ACAD_MATERIAL':      ['/materials'],
    'ACAD_OMR':           ['/omr', '/omr-export'],
    'ACAD_TICKETS':       ['/manage-tickets'],
    'FIN_FUND':           ['/fund', '/finance-dashboard'],
    'FIN_VENDOR':         ['/vendors', '/finance-dashboard'],
    'FIN_WORK_ORDER':     ['/work-orders', '/finance-dashboard'],
    'FIN_EXPENSE':        ['/expenses', '/finance-dashboard'],
    'FIN_EXAM_FEES':      ['/exam-fees'],
    'HR_STAFF':           ['/staff', '/hr-dashboard'],
}
# the academic dashboard opens for anyone with any academic activity
PATHS_ANY = {
    '/teacher-dashboard': [a[1] for a in ACTIVITIES if a[1].startswith('ACAD_')],
    # a person's OWN attendance/leave/payslips - each section checks its own activity
    '/my-hr': ['HR_ATTENDANCE', 'HR_LEAVE', 'HR_SALARY'],
}

# Legacy STAFF is not on the sheet: seeded with what it could reach before the
# matrix (everything but the institute's money and people, and not My Sales).
STAFF_EXCLUDE = {'MY_SALES', 'FIN_FUND', 'FIN_VENDOR', 'FIN_WORK_ORDER', 'FIN_EXPENSE',
                 'HR_ATTENDANCE', 'HR_LEAVE', 'HR_SALARY', 'HR_STAFF',
                 'RPT_EXAM_FEE', 'RPT_EXPENSE_REGISTER', 'RPT_VENDOR_OUTSTANDING', 'RPT_FUND_STATEMENT'}


def norm(s):
    return ''.join(ch for ch in str(s or '').lower() if ch.isalnum())


def q(s):
    return "'" + s.replace("'", "''") + "'"


DDL = """CREATE TABLE IF NOT EXISTS activities (
    activity_code VARCHAR(40) PRIMARY KEY,
    erp_tab       VARCHAR(20) NOT NULL,
    label         VARCHAR(60) NOT NULL,
    sort_order    SMALLINT    NOT NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS activity_paths (
    path          VARCHAR(80) NOT NULL,
    activity_code VARCHAR(40) NOT NULL,
    PRIMARY KEY (path, activity_code),
    CONSTRAINT fk_ap_activity FOREIGN KEY (activity_code) REFERENCES activities(activity_code) ON DELETE CASCADE
) ENGINE=InnoDB;

-- The matrix. A row = that role may use that activity.
-- ADMIN is never stored: it passes every check in code.
CREATE TABLE IF NOT EXISTS role_activity (
    role_code     VARCHAR(30) NOT NULL,
    activity_code VARCHAR(40) NOT NULL,
    PRIMARY KEY (role_code, activity_code),
    CONSTRAINT fk_ra_activity FOREIGN KEY (activity_code) REFERENCES activities(activity_code) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Every save on /role-mapping, in words.
CREATE TABLE IF NOT EXISTS role_activity_log (
    log_id      INT AUTO_INCREMENT PRIMARY KEY,
    detail      TEXT         NOT NULL,
    acted_by    VARCHAR(100),
    acted_by_id INT NULL,
    acted_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_ral_when (acted_at)
) ENGINE=InnoDB;

START TRANSACTION;
DELETE FROM role_activity;
DELETE FROM activity_paths;
DELETE FROM activities;"""


def main():
    ws = openpyxl.load_workbook(SHEET, data_only=True).active
    rows = [r for r in ws.iter_rows(min_row=7, values_only=True) if r[1] or r[0]]
    if len(rows) != len(ACTIVITIES):
        sys.exit('sheet has %d activity rows, expected %d' % (len(rows), len(ACTIVITIES)))

    grants = []
    for (tab, code, label), r in zip(ACTIVITIES, rows):
        sheet_label = r[1] if r[1] else r[0]
        # guard against the sheet being re-ordered under us (its spelling varies)
        if norm(sheet_label)[:4] != norm(label)[:4]:
            print('WARN: %s row reads "%s"' % (code, sheet_label))
        for i, role in enumerate(ROLE_COLS):
            v = str(r[2 + i] or '').strip().upper()
            if v not in ('YES', 'NO'):
                sys.exit('%s / %s: unexpected cell %r' % (code, role, v))
            if v == 'YES':
                grants.append((role, code))
    grants += [('STAFF', a[1]) for a in ACTIVITIES if a[1] not in STAFF_EXCLUDE]

    prs = [(p, c) for c, ps in PATHS.items() for p in ps] \
        + [(p, c) for p, cs in PATHS_ANY.items() for c in cs]

    out = [
        '-- GENERATED by database/tools/workflow_to_sql.py from Doc/Mapping/Work Flow.xlsx - do not hand-edit.',
        '-- Phase 2 of Doc/Mapping/ROLE_ACCESS_PLAN.md. Re-running RESETS the matrix to the sheet.',
        '',
        DDL,
        'INSERT INTO activities (activity_code, erp_tab, label, sort_order) VALUES',
        ',\n'.join('  (%s, %s, %s, %d)' % (q(c), q(t), q(l), (i + 1) * 10)
                   for i, (t, c, l) in enumerate(ACTIVITIES)) + ';',
        'INSERT INTO activity_paths (path, activity_code) VALUES',
        ',\n'.join('  (%s, %s)' % (q(p), q(c)) for p, c in prs) + ';',
        'INSERT INTO role_activity (role_code, activity_code) VALUES',
        ',\n'.join('  (%s, %s)' % (q(r), q(c)) for r, c in grants) + ';',
        "INSERT INTO role_activity_log (detail, acted_by) VALUES "
        "('Matrix seeded from Work Flow.xlsx (%d grants)', 'seed script');" % len(grants),
        'COMMIT;',
    ]
    with open(OUT, 'w', encoding='utf-8', newline='\n') as f:
        f.write('\n'.join(out) + '\n')
    print('wrote', OUT, '-', len(ACTIVITIES), 'activities,', len(prs), 'paths,', len(grants), 'grants')

    # schema.sql is the FULL fresh-install schema, so it carries the same block,
    # between markers this script owns. Without it a fresh install would have no
    # matrix, and AccessDAO fails closed: nobody but ADMIN could open a page.
    schema = os.path.join(ROOT, 'database', 'schema.sql')
    begin = '-- >>> role/activity matrix: GENERATED by database/tools/workflow_to_sql.py - do not hand-edit'
    end = '-- <<< role/activity matrix'
    block = begin + '\n' + '\n'.join(out[3:]) + '\n' + end
    with open(schema, encoding='utf-8', newline='') as f:
        text = f.read()
    crlf = '\r\n' in text              # keep the file's own line endings
    text = text.replace('\r\n', '\n')
    if begin in text:
        a = text.index(begin)
        b = text.index(end, a) + len(end)
        text = text[:a] + block + text[b:]
    else:
        text = text.rstrip('\n') + '\n\n' + block + '\n'
    with open(schema, 'w', encoding='utf-8', newline='\r\n' if crlf else '\n') as f:
        f.write(text)
    print('updated', schema)


if __name__ == '__main__':
    main()
