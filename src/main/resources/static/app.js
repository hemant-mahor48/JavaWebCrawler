const state = {
  lastResults: []
};

const $ = (selector) => document.querySelector(selector);

const elements = {
  crawlForm: $("#crawlForm"),
  seedUrls: $("#seedUrls"),
  maxPages: $("#maxPages"),
  maxDepth: $("#maxDepth"),
  pagesIndexed: $("#pagesIndexed"),
  keywordsIndexed: $("#keywordsIndexed"),
  lastCrawlCount: $("#lastCrawlCount"),
  crawlStatus: $("#crawlStatus"),
  resultsList: $("#resultsList"),
  refreshStats: $("#refreshStats"),
  searchForm: $("#searchForm"),
  searchQuery: $("#searchQuery"),
  searchResults: $("#searchResults"),
  loadIndex: $("#loadIndex"),
  keywordCloud: $("#keywordCloud"),
  toast: $("#toast")
};

function showToast(message) {
  elements.toast.textContent = message;
  elements.toast.classList.add("visible");
  window.clearTimeout(showToast.timeout);
  showToast.timeout = window.setTimeout(() => {
    elements.toast.classList.remove("visible");
  }, 3200);
}

async function requestJson(url, options = {}) {
  const response = await fetch(url, {
    headers: { "Content-Type": "application/json" },
    ...options
  });

  if (!response.ok) {
    const body = await response.text();
    throw new Error(body || `Request failed with ${response.status}`);
  }

  return response.json();
}

function parseSeedUrls(value) {
  return value
    .split(/\r?\n|,/)
    .map((url) => url.trim())
    .filter(Boolean);
}

function setBusy(isBusy) {
  elements.crawlForm.classList.toggle("is-loading", isBusy);
  elements.crawlStatus.textContent = isBusy ? "Crawling" : "Idle";
}

function updateStats(stats) {
  elements.pagesIndexed.textContent = stats.totalPagesIndexed ?? 0;
  elements.keywordsIndexed.textContent = stats.totalKeywordsIndexed ?? 0;
}

async function refreshStats() {
  const stats = await requestJson("/api/stats");
  updateStats(stats);
}

function renderResults(results) {
  state.lastResults = results;
  elements.lastCrawlCount.textContent = results.length;

  if (!results.length) {
    elements.resultsList.innerHTML = `
      <div class="empty-state">
        <strong>No pages returned</strong>
        <span>The crawl finished without returned pages.</span>
      </div>
    `;
    return;
  }

  elements.resultsList.innerHTML = results.map((result) => {
    const keywords = (result.keywords || []).slice(0, 8);
    const title = result.title || "Untitled page";
    const statusClass = result.status === "SUCCESS" ? "" : "failed";
    const meta = result.status === "SUCCESS"
      ? `
        <div class="result-meta">
          <span class="tag">${(result.outboundLinks || []).length} links</span>
          <span class="tag">${(result.keywords || []).length} keywords</span>
          ${keywords.map((word) => `<span class="tag">${escapeHtml(word)}</span>`).join("")}
        </div>
      `
      : `<p class="error-text">${escapeHtml(result.errorMessage || "The page could not be crawled.")}</p>`;

    return `
      <article class="result-item">
        <div class="result-topline">
          <h3 class="result-title">${escapeHtml(title)}</h3>
          <span class="status-pill ${statusClass}">${escapeHtml(result.status || "UNKNOWN")}</span>
        </div>
        <p class="url-text">${escapeHtml(result.url || "")}</p>
        ${meta}
      </article>
    `;
  }).join("");
}

function renderSearchResults(result) {
  const matches = result.matchingUrls || [];

  if (!matches.length) {
    elements.searchResults.innerHTML = `<span class="soft-text">No matches for "${escapeHtml(result.query)}".</span>`;
    return;
  }

  elements.searchResults.innerHTML = matches
    .map((url) => `<div class="match-item">${escapeHtml(url)}</div>`)
    .join("");
}

function renderIndex(index) {
  const entries = Object.entries(index)
    .sort((a, b) => b[1].length - a[1].length || a[0].localeCompare(b[0]))
    .slice(0, 36);

  if (!entries.length) {
    elements.keywordCloud.innerHTML = `<span class="soft-text">The index is empty. Run a successful crawl first.</span>`;
    return;
  }

  elements.keywordCloud.innerHTML = entries
    .map(([word, urls]) => `<button class="keyword" type="button" data-word="${escapeAttr(word)}">${escapeHtml(word)} · ${urls.length}</button>`)
    .join("");
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function escapeAttr(value) {
  return escapeHtml(value).replaceAll("`", "&#096;");
}

elements.crawlForm.addEventListener("submit", async (event) => {
  event.preventDefault();

  const seedUrls = parseSeedUrls(elements.seedUrls.value);
  if (!seedUrls.length) {
    showToast("Add at least one seed URL.");
    return;
  }

  setBusy(true);
  elements.resultsList.innerHTML = `
    <div class="empty-state">
      <strong>Crawl running</strong>
      <span>Fetching pages concurrently and building the index.</span>
    </div>
  `;

  try {
    const results = await requestJson("/api/crawl", {
      method: "POST",
      body: JSON.stringify({
        seedUrls,
        maxPages: Number(elements.maxPages.value),
        maxDepth: Number(elements.maxDepth.value)
      })
    });

    renderResults(results);
    await refreshStats();
    showToast(`Crawl complete: ${results.length} pages returned.`);
  } catch (error) {
    elements.resultsList.innerHTML = `
      <div class="empty-state">
        <strong>Crawl failed</strong>
        <span>${escapeHtml(error.message)}</span>
      </div>
    `;
    showToast("Crawl request failed.");
  } finally {
    setBusy(false);
  }
});

elements.searchForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  const query = elements.searchQuery.value.trim();

  if (!query) {
    showToast("Enter a keyword to search.");
    return;
  }

  try {
    const result = await requestJson(`/api/search?q=${encodeURIComponent(query)}`);
    renderSearchResults(result);
  } catch (error) {
    elements.searchResults.innerHTML = `<span class="soft-text">${escapeHtml(error.message)}</span>`;
  }
});

elements.loadIndex.addEventListener("click", async () => {
  try {
    const index = await requestJson("/api/index");
    renderIndex(index);
  } catch (error) {
    elements.keywordCloud.innerHTML = `<span class="soft-text">${escapeHtml(error.message)}</span>`;
  }
});

elements.keywordCloud.addEventListener("click", (event) => {
  const keyword = event.target.closest("[data-word]");
  if (!keyword) return;
  elements.searchQuery.value = keyword.dataset.word;
  elements.searchForm.requestSubmit();
});

elements.refreshStats.addEventListener("click", () => {
  refreshStats().then(() => showToast("Stats refreshed.")).catch((error) => showToast(error.message));
});

refreshStats().catch(() => {
  elements.crawlStatus.textContent = "API offline";
});
