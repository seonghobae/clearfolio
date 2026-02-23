const POLL_DELAY_MS = 1500;

const el = {
  docMeta: document.getElementById("doc-meta"),
  liveStatus: document.getElementById("live-status"),
  error: document.getElementById("error"),
  errorTitle: document.getElementById("error-title"),
  errorMessage: document.getElementById("error-message"),
  retryBtn: document.getElementById("retry-btn"),
  openJsonLink: document.getElementById("open-json-link"),
  preview: document.getElementById("preview"),
};

function getMetaContent(name) {
  const meta = document.querySelector(`meta[name="${name}"]`);
  if (!meta) {
    return null;
  }
  const raw = meta.getAttribute("content");
  if (!raw) {
    return null;
  }
  const value = raw.trim();
  return value.length > 0 ? value : null;
}

function getDocId() {
  const search = new URLSearchParams(window.location.search);
  const raw = search.get("docId");
  if (raw) {
    return raw.trim();
  }
  return getMetaContent("clearfolio-doc-id");
}

function getInitialState() {
  return getMetaContent("clearfolio-initial-state");
}

function isUuidLike(value) {
  return /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/.test(value);
}

function setLoading(message) {
  el.error.hidden = true;
  el.liveStatus.textContent = message;
  el.preview.setAttribute("aria-busy", "true");
}

function showError(message) {
  el.error.hidden = false;
  el.errorMessage.textContent = message;
  el.liveStatus.textContent = "";
  el.preview.setAttribute("aria-busy", "false");
  el.errorTitle.focus();
}

function clearPreview() {
  const nodes = Array.from(el.preview.querySelectorAll("iframe, img, pre, a"));
  for (const node of nodes) {
    node.remove();
  }

  const skeleton = el.preview.querySelector(".skeleton");
  if (skeleton) {
    skeleton.remove();
  }
}

function renderPreviewLink(path) {
  const link = document.createElement("a");
  link.href = path;
  link.textContent = "Open artifact";
  link.className = "btn btn-secondary";
  link.rel = "noopener";
  el.preview.appendChild(link);
}

function renderPdfInline(path) {
  const viewerPath =
    getMetaContent("clearfolio-pdfjs-viewer-path") ||
    "/webjars/pdfjs-dist/4.10.38/web/viewer.html";
  const viewerUrl = `${viewerPath}?file=${encodeURIComponent(path)}`;
  const frame = document.createElement("iframe");
  frame.src = viewerUrl;
  frame.title = "Document preview";
  frame.loading = "lazy";
  frame.className = "preview-frame";
  el.preview.appendChild(frame);
}

async function fetchJson(url, signal) {
  const res = await fetch(url, {
    headers: {
      Accept: "application/json",
    },
    credentials: "same-origin",
    signal,
  });

  const contentType = (res.headers.get("content-type") || "").toLowerCase();
  const data = contentType.includes("application/json") ? await res.json() : null;

  return { res, data };
}

async function poll(docId, abortSignal) {
  try {
    setLoading("Checking conversion status...");

    const statusUrl = `/api/v1/convert/jobs/${encodeURIComponent(docId)}`;
    const { res, data } = await fetchJson(statusUrl, abortSignal);

    if (abortSignal.aborted) {
      return;
    }

    if (res.status === 404) {
      showError("This document could not be found.");
      return;
    }

    if (!res.ok || !data) {
      showError("Unable to read job status. Please retry.");
      return;
    }

    const status = data.status;
    if (status === "SUBMITTED" || status === "PROCESSING") {
      el.liveStatus.textContent = `${status} - retrying soon...`;
      window.setTimeout(() => {
        if (!abortSignal.aborted) {
          void poll(docId, abortSignal);
        }
      }, POLL_DELAY_MS);
      return;
    }

    if (status !== "SUCCEEDED") {
      showError(`Preview is not available. Status: ${status}`);
      return;
    }

    setLoading("Loading viewer bootstrap...");
    const viewerUrl = `/api/v1/viewer/${encodeURIComponent(docId)}`;
    const bootstrap = await fetchJson(viewerUrl, abortSignal);

    if (abortSignal.aborted) {
      return;
    }

    if (!bootstrap.res.ok || !bootstrap.data) {
      showError("Viewer bootstrap failed. Please retry.");
      return;
    }

    el.preview.setAttribute("aria-busy", "false");
    el.liveStatus.textContent = "Ready.";

    clearPreview();
    const path = bootstrap.data.previewResourcePath;
    if (typeof path === "string" && path.endsWith(".pdf")) {
      renderPdfInline(path);
    }
    if (typeof path === "string" && path.length > 0) {
      renderPreviewLink(path);
    }
  } catch (err) {
    if (abortSignal.aborted) {
      return;
    }
    showError("Network error while loading preview. Please retry.");
  }
}

function init() {
  const docId = getDocId();
  if (!docId) {
    el.docMeta.textContent = "Missing docId.";
    showError("The viewer URL is missing a docId parameter.");
    return;
  }

  if (!isUuidLike(docId)) {
    el.docMeta.textContent = `Invalid docId: ${docId}`;
    showError("The provided docId is invalid.");
    return;
  }

  el.docMeta.textContent = `docId: ${docId}`;
  el.openJsonLink.hidden = false;
  el.openJsonLink.href = `/api/v1/viewer/${encodeURIComponent(docId)}`;

  const initialState = getInitialState();

  let controller = new AbortController();
  const start = () => {
    controller.abort();
    controller = new AbortController();
    void poll(docId, controller.signal);
  };

  el.retryBtn.addEventListener("click", start);
  if (initialState === "NOT_FOUND") {
    showError("This document could not be found.");
    return;
  }
  if (initialState === "FAILED") {
    showError("Preview is not available. Status: FAILED");
    return;
  }

  start();
}

init();
