/* Generic client-side list filtering.
   Table to filter: id="fTable". Search box: id="fSearch".
   Dropdowns: add [data-filter][data-col=N] (exact match against column N's cell text).
   A class dropdown id="fClass" is auto-populated from its column. */
(function () {
  function applyFilters() {
    var sb = document.getElementById('fSearch');
    var q = sb ? sb.value.toLowerCase().trim() : '';
    var selects = document.querySelectorAll('.filter-bar [data-filter]');
    var rows = document.querySelectorAll('#fTable tbody tr');
    var shown = 0;
    rows.forEach(function (tr) {
      var show = q === '' || tr.textContent.toLowerCase().indexOf(q) !== -1;
      if (show) {
        selects.forEach(function (sel) {
          if (!show || !sel.value) return;
          var col = parseInt(sel.getAttribute('data-col'), 10);
          var cell = tr.children[col];
          var ct = cell ? cell.textContent.trim().toLowerCase() : '';
          if (ct !== sel.value.toLowerCase()) show = false;
        });
      }
      tr.style.display = show ? '' : 'none';
      if (show) shown++;
    });
    var c = document.getElementById('fCount');
    if (c) c.textContent = shown;
  }

  function populateClassFilter() {
    var sel = document.getElementById('fClass');
    if (!sel) return;
    var col = parseInt(sel.getAttribute('data-col'), 10);
    var seen = {};
    document.querySelectorAll('#fTable tbody tr').forEach(function (tr) {
      var cell = tr.children[col];
      if (cell) { var v = cell.textContent.trim(); if (v && v !== '—') seen[v] = 1; }
    });
    Object.keys(seen).sort().forEach(function (v) {
      var o = document.createElement('option'); o.value = v; o.textContent = v; sel.appendChild(o);
    });
  }

  window.applyFilters = applyFilters;
  document.addEventListener('DOMContentLoaded', function () {
    populateClassFilter();
    applyFilters();
  });
})();
