"""
End-to-end check of the role/activity matrix against the running app.

Logs in as one test user per role, requests every mapped URL WITHOUT following
redirects, and compares what happened with what Doc/Mapping/Work Flow.xlsx says.

    python database/tools/access_matrix_test.py [base_url]

Outcomes:
  OPEN      200, or a redirect somewhere other than the role's home
  DENIED    403, or a redirect to the role's home (AuthFilter's "wrong turn")
A LEAK (sheet says NO, page opened) is always a failure. A BLOCK (sheet says YES,
page sent home) is a failure unless the URL is the role's home itself.

Needs one test login per role (password test123) - create them with
database/tools/test-logins.sql and remove them afterwards with
test-logins-remove.sql. Never on production. Compares against the SHEET, so if
someone has edited /role-mapping since the seed, expect differences.
"""
import http.cookiejar
import os
import sys
import urllib.error
import urllib.parse
import urllib.request

import openpyxl

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import workflow_to_sql as W  # noqa: E402  (the same activity list/URL map the seed used)

BASE = (sys.argv[1] if len(sys.argv) > 1 else 'http://localhost:8080/Tution_Project').rstrip('/')

USERS = {
    'COUNSELLOR': 't_counsellor', 'ACCOUNTANT': 't_acct', 'HR': 't_hr', 'OFFICE_ADMIN': 't_officeadmin',
    'ABM': 't_abm', 'ACADEMIC_COORDINATOR': 't_acoord', 'EDP': 't_edp', 'ACADEMIC_INCHARGE': 't_aincharge',
    'TEACHER': 't_faculty', 'ACADEMIC_HEAD': 't_ahead', 'BRANCH_MANAGER': 't_bm', 'BUSINESS_HEAD': 't_bh',
    'STAFF': 't_staff',
}
SKIP = {'/pay-verify', '/pay-online', '/omr-export'}   # need a payment/scan in progress

# (role, url) pairs that differ from the sheet on purpose. None since phase 3.
KNOWN = set()


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *a, **k):
        return None


def sheet_grants():
    ws = openpyxl.load_workbook(W.SHEET, data_only=True).active
    rows = [r for r in ws.iter_rows(min_row=7, values_only=True) if r[1] or r[0]]
    g = {r: set() for r in USERS}
    for (_, code, _), r in zip(W.ACTIVITIES, rows):
        for i, role in enumerate(W.ROLE_COLS):
            if str(r[2 + i]).strip().upper() == 'YES':
                g[role].add(code)
    g['STAFF'] = {a[1] for a in W.ACTIVITIES if a[1] not in W.STAFF_EXCLUDE}
    return g


def urls():
    """(url, expected(grants) -> bool) for every page worth knocking on."""
    out = {}
    for code, paths in W.PATHS.items():
        for p in paths:
            out.setdefault(p, set()).add(code)
    for p, codes in W.PATHS_ANY.items():
        out.setdefault(p, set()).update(codes)
    tests = [(p, (lambda cs: lambda g: bool(cs & g))(cs)) for p, cs in sorted(out.items()) if p not in SKIP]
    for tab, act in (('attendance', 'HR_ATTENDANCE'), ('leave', 'HR_LEAVE'), ('salary', 'HR_SALARY')):
        tests.append(('/hr?tab=' + tab, (lambda a: lambda g: 'HR_STAFF' in g and a in g)(act)))
    for _, code, _ in W.ACTIVITIES:
        if code.startswith('RPT_'):
            t = code[4:].lower().replace('_', '-')
            tests.append(('/reports?type=' + t, (lambda c: lambda g: c in g)(code)))
    tests.append(('/reports', lambda g: any(c.startswith('RPT_') for c in g)))
    tests.append(('/role-mapping', lambda g: False))
    tests.append(('/server-logs', lambda g: False))
    return tests


def session_for(user):
    jar = http.cookiejar.CookieJar()
    op = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar), NoRedirect)
    data = urllib.parse.urlencode({'username': user, 'password': 'test123'}).encode()
    try:
        op.open(BASE + '/login', data)
        return op, None
    except urllib.error.HTTPError as e:
        loc = e.headers.get('Location', '')
        if e.code in (301, 302, 303) and 'login' not in loc.split('/')[-1]:
            return op, loc.replace(BASE, '').split(';')[0]
        raise SystemExit('login failed for %s (%s %s)' % (user, e.code, loc))


def hit(op, url):
    try:
        r = op.open(BASE + url)
        return r.status, ''
    except urllib.error.HTTPError as e:
        return e.code, e.headers.get('Location', '').replace(BASE, '')


def main():
    grants = sheet_grants()
    tests = urls()
    fails, errors = [], []
    print('%-22s %-20s %5s %5s %5s' % ('ROLE', 'HOME', 'open', 'deny', 'fail'))
    for role, user in USERS.items():
        op, home = session_for(user)
        g = grants[role]
        n_open = n_deny = n_fail = 0
        for url, expect in tests:
            code, loc = hit(op, url)
            if code in (301, 302, 303) and 'login' in loc.split('?')[0].split('/')[-1]:
                # the session died (app reloaded mid-run) - neither open nor denied
                raise SystemExit('ABORT: %s was logged out at %s - the app reloaded; run again' % (role, url))
            to_home = code in (301, 302, 303) and loc.split('?')[0] == home
            denied = code == 403 or to_home
            if code >= 500:
                errors.append((role, url, code))
            want = expect(g)
            if denied:
                n_deny += 1
            else:
                n_open += 1
            if (role, url) in KNOWN:
                continue
            if want and denied and not home.endswith(url.split('?')[0]):
                fails.append((role, url, 'BLOCK', code, loc))
                n_fail += 1
            elif not want and not denied:
                fails.append((role, url, 'LEAK', code, loc))
                n_fail += 1
        print('%-22s %-20s %5d %5d %5d' % (role, home, n_open, n_deny, n_fail))
    print('\n%d URLs x %d roles' % (len(tests), len(USERS)))
    for f in fails:
        print('FAIL %-5s %-20s %-32s -> %s %s' % (f[2], f[0], f[1], f[3], f[4]))
    for e in errors:
        print('ERROR %s %s -> HTTP %s' % e)
    print('RESULT:', 'PASS' if not fails else '%d FAIL' % len(fails))


if __name__ == '__main__':
    main()
