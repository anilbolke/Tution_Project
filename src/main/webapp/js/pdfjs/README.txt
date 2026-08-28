PDF.js 3.11.174 — vendored, not fetched from a CDN.

Why vendored: the student portal has to work on a slow or intermittent
connection at the tuition, and a CDN that fails takes the reader down with
it. These two files are served from /js/pdfjs/ like any other static asset.

  pdf.min.js         the library
  pdf.worker.min.js  the parsing/rendering worker (loaded by the library)

Licence: Apache 2.0, Mozilla Foundation. The full notice is at the top of
each file and must stay there.

Upgrading: replace both files with the same version of each, then bump the
?v= stamp on the <script> tags in student_resources.jsp so browsers pick
up the change.
