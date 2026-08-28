/*
 * Lead Stage → Lead Sub Stage cascade.
 *
 * Each lead stage has its own sub-stage list, so changing the stage rebuilds the
 * second dropdown. Written generically because the pair appears both once on the
 * lead form and once per row on the follow-up queue — a version keyed to fixed
 * element ids would break the moment there were two on a page.
 *
 * Markup contract:
 *   <select class="lead-stage" data-sub="someUniqueId">…</select>
 *   <select id="someUniqueId" data-selected="current value"></select>
 *
 * The map itself is rendered by the page as window.LEAD_SUB_STAGES.
 */
(function () {
  'use strict';

  function fill(stageSel, keepSelection) {
    var subSel = document.getElementById(stageSel.getAttribute('data-sub'));
    if (!subSel) return;

    var map  = window.LEAD_SUB_STAGES || {};
    var list = map[stageSel.value] || [];
    // Only honour the stored value on first paint. Once the user picks a
    // different stage, a sub stage from the previous one must not survive —
    // that is exactly how a lead ends up "Hot / Financial Issues".
    var want = keepSelection ? (subSel.getAttribute('data-selected') || '') : '';

    subSel.innerHTML = '';
    var first = document.createElement('option');
    first.value = '';
    first.textContent = list.length ? '— Select —'
                                    : (stageSel.value ? '— none for this stage —'
                                                      : '— choose a stage first —');
    subSel.appendChild(first);
    subSel.disabled = list.length === 0;

    for (var i = 0; i < list.length; i++) {
      var o = document.createElement('option');
      o.value = list[i];
      o.textContent = list[i];
      if (want && list[i] === want) o.selected = true;
      subSel.appendChild(o);
    }
  }

  function init() {
    var stages = document.querySelectorAll('select.lead-stage[data-sub]');
    for (var i = 0; i < stages.length; i++) {
      (function (sel) {
        fill(sel, true);
        sel.addEventListener('change', function () { fill(sel, false); });
      })(stages[i]);
    }
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
