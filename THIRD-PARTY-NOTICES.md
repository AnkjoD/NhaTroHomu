# Third-party components

The application source uses MIT (see LICENSE). That does not replace dependency licenses.

| Component | Version | License / upstream |
|---|---|---|
| FlatLaf | 3.6.2 | Apache-2.0 — https://github.com/JFormDesigner/FlatLaf |
| Xerial SQLite JDBC | 3.50.3.0 | Apache-2.0 — https://github.com/xerial/sqlite-jdbc |
| SQLite native engine | bundled in SQLite JDBC | Public domain — https://sqlite.org/copyright.html |
| SLF4J API / NOP | 2.0.17 | MIT — https://www.slf4j.org/license.html |
| Bundled OpenJDK runtime | build machine JDK | GPLv2 with Classpath Exception; see runtime/legal in portable distribution |

Dependency JAR files retain their embedded license/notice files. The bundled runtime retains its legal directory.
Windows OCR is an operating-system component invoked on the user's machine, not redistributed with the app.

Optional OCR pack (downloaded by install-ocr.ps1; unmodified JARs keep their notices):

| Component | Version | License / source |
|---|---|---|
| RapidOCR4j and bundled PP-OCR models | 1.0.3 / PP-OCRv4 | Apache-2.0 — https://github.com/hzkitty/RapidOCR4j ; model origin https://github.com/PaddlePaddle/PaddleOCR |
| ONNX Runtime CPU | 1.18.0 | MIT — https://github.com/microsoft/onnxruntime/tree/v1.18.0 |
| OpenPnP OpenCV distribution | 4.6.0-0 | BSD-3-Clause loader / Apache-2.0 OpenCV 4.6 — https://github.com/openpnp/opencv ; https://github.com/opencv/opencv/tree/4.6.0 |

These optional native libraries increase disk use and OCR peak memory; they are loaded only in a short-lived worker process. The standard portable ZIP does not bundle them.

Be Vietnam Pro font: SIL Open Font License 1.1. Font and OFL.txt are embedded in resources/fonts. Upstream: https://github.com/google/fonts/tree/main/ofl/bevietnampro


OpenPDF 2.0.3: https://github.com/LibrePDF/OpenPDF/tree/2.0.3
OpenPDF is offered under LGPL-2.1-or-later OR MPL-2.0; this distribution uses the
MPL-2.0 option. The unmodified library JAR retains META-INF/LICENSES.md and
misc_licenses.txt for incorporated components. Corresponding upstream source is
available at the versioned URL above. Be Vietnam Pro is also embedded into exported PDFs.
