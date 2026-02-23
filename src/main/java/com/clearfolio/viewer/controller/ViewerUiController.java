package com.clearfolio.viewer.controller;

import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.clearfolio.viewer.model.ConversionJob;
import com.clearfolio.viewer.model.ConversionJobStatus;
import com.clearfolio.viewer.service.DocumentConversionService;

/**
 * HTML viewer UI entrypoint.
 */
@RestController
public class ViewerUiController {

    // Keep this in sync with `pom.xml` pdfjs-dist version.
    static final String PDF_JS_VIEWER_PATH = "/webjars/pdfjs-dist/4.10.38/web/viewer.html";

    private final DocumentConversionService conversionService;

    /**
     * Creates the viewer controller.
     *
     * @param conversionService conversion service for job lookups
     */
    public ViewerUiController(DocumentConversionService conversionService) {
        this.conversionService = conversionService;
    }

    /**
     * Returns an HTML viewer shell.
     *
     * @param docId document identifier
     * @return HTML payload or redirect
     */
    @GetMapping(value = "/viewer/{docId:[0-9a-fA-F\u002d]{36}}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> viewer(@PathVariable UUID docId) {
        Optional<ConversionJob> job = conversionService.getJob(docId);
        String initialState;
        HttpStatus status;
        if (job.isEmpty()) {
            initialState = "NOT_FOUND";
            status = HttpStatus.OK;
        } else if (job.get().getStatus() == ConversionJobStatus.FAILED) {
            initialState = "FAILED";
            status = HttpStatus.OK;
        } else {
            initialState = "LOADING";
            status = HttpStatus.OK;
        }

        return ResponseEntity.status(status)
                .contentType(MediaType.TEXT_HTML)
                .body(viewerShellHtml(docId, initialState));
    }

    private static String viewerShellHtml(UUID docId, String initialState) {
        String docIdString = docId.toString();
        String template = """
                <!doctype html>
                <html lang="en">
                  <head>
                    <meta charset="utf-8" />
                    <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover" />
                    <meta name="referrer" content="no-referrer" />
                    <meta name="clearfolio-doc-id" content="{{DOC_ID}}" />
                    <meta name="clearfolio-initial-state" content="{{INITIAL_STATE}}" />
                    <meta name="clearfolio-pdfjs-viewer-path" content="{{PDFJS_VIEWER_PATH}}" />
                    <title>Clearfolio Viewer</title>
                    <link rel="stylesheet" href="/assets/viewer/viewer.css" />
                  </head>
                  <body>
                    <a class="skip-link" href="#main">Skip to content</a>

                    <header class="app-header" role="banner">
                      <div class="app-header__inner">
                        <div class="brand" aria-label="Clearfolio Viewer">
                          <span class="brand__name">Clearfolio Viewer</span>
                        </div>

                        <nav class="header-nav" aria-label="Viewer utilities">
                          <a class="header-nav__link" href="/healthz">Service status</a>
                        </nav>
                      </div>
                    </header>

                    <main id="main" class="app-main" tabindex="-1">
                      <h1 class="page-title">Document preview</h1>
                      <p class="page-subtitle" id="doc-meta">Preparing preview shell...</p>

                      <section class="panel" aria-labelledby="state-title">
                        <h2 id="state-title" class="panel__title">Preview status</h2>

                        <div id="live-status" class="status" role="status" aria-live="polite" aria-atomic="true">Loading...</div>

                        <div id="error" class="error" role="alert" hidden>
                          <h3 class="error__title" id="error-title" tabindex="-1">Unable to load preview</h3>
                          <p class="error__message" id="error-message"></p>
                        </div>

                        <div class="actions" aria-label="Actions">
                          <button type="button" class="btn btn-primary" id="retry-btn">Refresh</button>
                          <a class="btn btn-secondary" id="open-json-link" href="#" hidden>Open JSON bootstrap</a>
                        </div>
                      </section>

                      <section class="panel" aria-labelledby="preview-title">
                        <h2 id="preview-title" class="panel__title">Preview</h2>

                        <div id="preview" class="preview" aria-busy="true">
                          <div class="skeleton" aria-hidden="true"></div>
                          <p class="help" id="preview-help">When ready, the converted artifact will appear here.</p>
                        </div>
                      </section>
                    </main>

                    <footer class="app-footer" role="contentinfo">
                      <div class="app-footer__inner">
                        <small>Copyright (c) 2026 by HYOSUNG. All rights reserved.</small>
                      </div>
                    </footer>

                    <script type="module" src="/assets/viewer/viewer.js"></script>
                  </body>
                </html>
                """;

        return template
                .replace("{{DOC_ID}}", docIdString)
                .replace("{{INITIAL_STATE}}", initialState)
                .replace("{{PDFJS_VIEWER_PATH}}", PDF_JS_VIEWER_PATH);
    }
}
