// YieldMatrix Institutional Crypto Staking Calculator Logic
(function() {
  'use strict';

  // --- State ---
  let state = {
    symbol: 'ETH',
    price: 2650,
    amount: 10,
    apy: 3.8,
    months: 12,
    compoundFreq: 365
  };

  // --- DOM Elements ---
  const initialAmountInput = document.getElementById('initial-amount');
  const amountSlider = document.getElementById('amount-slider');
  const initialUsdVal = document.getElementById('initial-usd-val');
  const apyRateInput = document.getElementById('apy-rate');
  const apySlider = document.getElementById('apy-slider');
  const durationSlider = document.getElementById('duration-slider');
  const durationDisplay = document.getElementById('duration-display');
  const compoundFreqSelect = document.getElementById('compound-frequency');
  const inputTokenSuffix = document.getElementById('input-token-suffix');
  const btnCalculate = document.getElementById('btn-calculate');

  // KPI Output Elements
  const resTotalTokens = document.getElementById('res-total-tokens');
  const resTotalUsd = document.getElementById('res-total-usd');
  const resRewardTokens = document.getElementById('res-reward-tokens');
  const resRewardUsd = document.getElementById('res-reward-usd');
  const resMonthlyUsd = document.getElementById('res-monthly-usd');
  const resMultiplier = document.getElementById('res-multiplier');
  const chartTimeframeTag = document.getElementById('chart-timeframe-tag');

  // Chart Elements
  const chartArea = document.getElementById('chart-area');
  const chartLine = document.getElementById('chart-line');
  const chartMarkerStart = document.getElementById('chart-marker-start');
  const chartMarkerEnd = document.getElementById('chart-marker-end');

  // --- Calculation Engine ---
  function calculateYield() {
    const P = parseFloat(state.amount) || 0;
    const r = (parseFloat(state.apy) || 0) / 100;
    const n = parseInt(state.compoundFreq, 10) || 1;
    const t = (parseFloat(state.months) || 12) / 12;

    // Compound Interest: A = P * (1 + r/n)^(n*t)
    let totalTokens = P * Math.pow(1 + r / n, n * t);
    if (isNaN(totalTokens) || !isFinite(totalTokens)) totalTokens = P;

    const rewardTokens = Math.max(0, totalTokens - P);
    const totalUsd = totalTokens * state.price;
    const rewardUsd = rewardTokens * state.price;
    const monthlyUsd = (rewardUsd / (state.months || 1));
    const multiplier = P > 0 ? (totalTokens / P) : 1;

    // Update UI
    initialUsdVal.textContent = `$${(P * state.price).toLocaleString(undefined, {minimumFractionDigits: 2, maximumFractionDigits: 2})} USD`;
    resTotalTokens.textContent = `${totalTokens.toLocaleString(undefined, {minimumFractionDigits: 3, maximumFractionDigits: 4})} ${state.symbol}`;
    resTotalUsd.textContent = `≈ $${totalUsd.toLocaleString(undefined, {minimumFractionDigits: 2, maximumFractionDigits: 2})} USD`;
    resRewardTokens.textContent = `+${rewardTokens.toLocaleString(undefined, {minimumFractionDigits: 3, maximumFractionDigits: 4})} ${state.symbol}`;
    resRewardUsd.textContent = `+$${rewardUsd.toLocaleString(undefined, {minimumFractionDigits: 2, maximumFractionDigits: 2})}`;
    resMonthlyUsd.textContent = `$${monthlyUsd.toLocaleString(undefined, {minimumFractionDigits: 2, maximumFractionDigits: 2})} / mo`;
    resMultiplier.textContent = `${multiplier.toFixed(3)}x`;
    chartTimeframeTag.textContent = `${state.months} Month Horizon`;

    // Render Chart
    renderChart(P, r, n, t);
  }

  // --- Dynamic SVG Trajectory Chart ---
  function renderChart(P, r, n, t) {
    const pointsCount = 12;
    const width = 400;
    const height = 160;
    const paddingX = 20;
    const paddingTop = 25;
    const paddingBottom = 25;
    const usableWidth = width - (paddingX * 2);
    const usableHeight = height - paddingTop - paddingBottom;

    const points = [];
    const values = [];

    for (let i = 0; i <= pointsCount; i++) {
      const stepT = (t / pointsCount) * i;
      const val = P * Math.pow(1 + r / n, n * stepT);
      values.push(val);
    }

    const minVal = values[0];
    const maxVal = values[values.length - 1];
    const valRange = (maxVal - minVal) || 1;

    for (let i = 0; i <= pointsCount; i++) {
      const x = paddingX + (i / pointsCount) * usableWidth;
      const normalizedY = (values[i] - minVal) / valRange;
      const y = (height - paddingBottom) - (normalizedY * usableHeight);
      points.push({ x, y });
    }

    // Build SVG path
    let lineD = `M ${points[0].x.toFixed(1)} ${points[0].y.toFixed(1)}`;
    for (let i = 1; i < points.length; i++) {
      lineD += ` L ${points[i].x.toFixed(1)} ${points[i].y.toFixed(1)}`;
    }

    const areaD = `${lineD} L ${points[points.length - 1].x.toFixed(1)} ${height - 10} L ${points[0].x.toFixed(1)} ${height - 10} Z`;

    chartLine.setAttribute('d', lineD);
    chartArea.setAttribute('d', areaD);

    // Update marker coordinates
    chartMarkerStart.setAttribute('cx', points[0].x);
    chartMarkerStart.setAttribute('cy', points[0].y);
    chartMarkerEnd.setAttribute('cx', points[points.length - 1].x);
    chartMarkerEnd.setAttribute('cy', points[points.length - 1].y);
  }

  // --- Sync Controls & Events ---
  initialAmountInput.addEventListener('input', (e) => {
    state.amount = parseFloat(e.target.value) || 0;
    amountSlider.value = Math.min(state.amount, 100);
    calculateYield();
  });

  amountSlider.addEventListener('input', (e) => {
    state.amount = parseFloat(e.target.value) || 0;
    initialAmountInput.value = state.amount;
    calculateYield();
  });

  apyRateInput.addEventListener('input', (e) => {
    state.apy = parseFloat(e.target.value) || 0;
    apySlider.value = Math.min(state.apy, 30);
    calculateYield();
  });

  apySlider.addEventListener('input', (e) => {
    state.apy = parseFloat(e.target.value) || 0;
    apyRateInput.value = state.apy;
    calculateYield();
  });

  durationSlider.addEventListener('input', (e) => {
    state.months = parseInt(e.target.value, 10) || 12;
    const years = (state.months / 12).toFixed(1);
    durationDisplay.textContent = `${state.months} Months (${years} Year${state.months > 12 ? 's' : ''})`;
    calculateYield();
  });

  compoundFreqSelect.addEventListener('change', (e) => {
    state.compoundFreq = parseInt(e.target.value, 10) || 365;
    calculateYield();
  });

  btnCalculate.addEventListener('click', () => {
    calculateYield();
    triggerPushAdRotation();
  });

  // --- Token Selection Chips ---
  const tokenChips = document.querySelectorAll('.token-chip');
  tokenChips.forEach((chip) => {
    chip.addEventListener('click', () => {
      tokenChips.forEach((c) => c.classList.remove('active'));
      chip.classList.add('active');

      state.symbol = chip.dataset.symbol;
      state.price = parseFloat(chip.dataset.price);
      state.apy = parseFloat(chip.dataset.apy);

      inputTokenSuffix.textContent = state.symbol;
      apyRateInput.value = state.apy;
      apySlider.value = Math.min(state.apy, 30);

      // Adjust default amounts based on token scale
      if (state.symbol === 'ETH') {
        state.amount = 10;
        amountSlider.max = 100;
      } else if (state.symbol === 'SOL') {
        state.amount = 100;
        amountSlider.max = 1000;
      } else if (state.symbol === 'ATOM') {
        state.amount = 500;
        amountSlider.max = 5000;
      } else if (state.symbol === 'AVAX') {
        state.amount = 250;
        amountSlider.max = 2000;
      } else if (state.symbol === 'DOT') {
        state.amount = 1000;
        amountSlider.max = 10000;
      } else if (state.symbol === 'ADA') {
        state.amount = 15000;
        amountSlider.max = 100000;
      }
      initialAmountInput.value = state.amount;
      amountSlider.value = state.amount;

      calculateYield();
    });
  });

  // --- Presets ---
  const presetBtns = document.querySelectorAll('.preset-btn');
  presetBtns.forEach((btn) => {
    btn.addEventListener('click', () => {
      presetBtns.forEach((b) => b.classList.remove('active'));
      btn.classList.add('active');

      const preset = btn.dataset.preset;
      if (preset === 'retail') {
        state.months = 12;
        durationSlider.value = 12;
        compoundFreqSelect.value = "365";
      } else if (preset === 'validator') {
        // Activate ETH chip
        document.querySelector('.token-chip[data-symbol="ETH"]').click();
        state.amount = 32;
        state.apy = 3.8;
        initialAmountInput.value = 32;
        amountSlider.value = 32;
        apyRateInput.value = 3.8;
        durationSlider.value = 24;
        state.months = 24;
      } else if (preset === 'lst') {
        document.querySelector('.token-chip[data-symbol="ETH"]').click();
        state.amount = 5;
        state.apy = 3.5;
        initialAmountInput.value = 5;
        amountSlider.value = 5;
        apyRateInput.value = 3.5;
        compoundFreqSelect.value = "365";
      } else if (preset === 'defi') {
        document.querySelector('.token-chip[data-symbol="ATOM"]').click();
        state.amount = 1000;
        state.apy = 14.8;
        initialAmountInput.value = 1000;
        amountSlider.value = 1000;
        apyRateInput.value = 14.8;
        compoundFreqSelect.value = "365";
      }
      state.compoundFreq = parseInt(compoundFreqSelect.value, 10);
      durationSlider.dispatchEvent(new Event('input'));
      calculateYield();
    });
  });

  // --- FAQ Accordions ---
  const faqItems = document.querySelectorAll('.faq-item');
  faqItems.forEach((item) => {
    const question = item.querySelector('.faq-question');
    question.addEventListener('click', () => {
      const isOpen = item.classList.contains('open');
      faqItems.forEach((f) => f.classList.remove('open'));
      if (!isOpen) {
        item.classList.add('open');
      }
    });
  });

  // In-Page Push is handled natively by HilltopAds MultiTag script (Zone #7550069)


  // --- Compliance Modals ---
  const modalOverlay = document.getElementById('modal-overlay');
  const modalTitle = document.getElementById('modal-title');
  const modalContent = document.getElementById('modal-content');
  const modalClose = document.getElementById('modal-close');

  const legalDocuments = {
    privacy: {
      title: 'Privacy Policy (GDPR & CCPA Compliant)',
      content: `
        <h4>1. Information We Do Not Collect</h4>
        <p>YieldMatrix is engineered as a client-side computational calculator. We do not store personal identifiable information (PII), cryptocurrency wallet addresses, private keys, or IP addresses on centralized databases.</p>
        <h4>2. Local Storage and Service Worker Caching</h4>
        <p>We utilize browser Service Workers and local browser caching strictly to store application assets (HTML, CSS, JavaScript) for offline functionality and low-bandwidth consumption. No personal data is transmitted during cache reconciliation.</p>
        <h4>3. Third-Party Advertising & Cookie Disclosures</h4>
        <p>Our website displays contextually targeted financial advertisements served by verified advertising networks. These networks may utilize pseudonymous cookies or device identifiers to measure ad impressions and combat invalid traffic. You may opt out of personalized tracking via your browser privacy settings or standard Do-Not-Track headers.</p>
        <h4>4. Contact Privacy Officer</h4>
        <p>For data privacy queries, please submit requests through our compliance desk at <code>privacy@yieldmatrix.finance</code>.</p>
      `
    },
    terms: {
      title: 'Terms of Service',
      content: `
        <h4>1. Acceptance of Terms</h4>
        <p>By accessing YieldMatrix, you agree to comply with and be bound by these Terms of Service. If you do not agree, you are prohibited from using this utility.</p>
        <h4>2. Informational and Simulation Utility Only</h4>
        <p>All yield projections, mathematical calculations, APY rates, and historical models are generated algorithmically for theoretical estimation only. Actual cryptocurrency protocol yields fluctuate dynamically based on network inflation, slashing incidents, epoch parameters, and validator fees.</p>
        <h4>3. Limitation of Liability</h4>
        <p>Under no circumstances shall YieldMatrix, its developers, or affiliates be liable for direct, indirect, incidental, or consequential losses, including lost staking funds, unbonding penalties, or liquidation events arising from reliance on this calculator.</p>
      `
    },
    disclaimer: {
      title: 'Financial & Regulatory Risk Disclaimer',
      content: `
        <h4>1. Not Investment or Financial Advice</h4>
        <p>YieldMatrix is an open analytical utility and does not constitute financial, investment, legal, or tax advice under SEC, CFTC, FCA, or MiCA frameworks. We are not a registered broker-dealer, investment advisor, or custodian.</p>
        <h4>2. Cryptocurrency Staking Risks</h4>
        <p>Cryptocurrency assets are highly volatile and speculative. Proof-of-Stake consensus mechanisms entail risks including:</p>
        <ul style="margin-left: 20px; margin-top: 8px;">
          <li><strong>Protocol Slashing:</strong> Loss of staked tokens due to validator downtime, equivocation, or malicious consensus behavior.</li>
          <li><strong>Unbonding Locks:</strong> Inability to transfer or sell staked tokens during mandatory unbonding periods (up to 21+ days).</li>
          <li><strong>Smart Contract Failure:</strong> Code exploits in liquid staking protocols (LST) or decentralized vaults.</li>
        </ul>
      `
    },
    about: {
      title: 'About & Staking Methodology',
      content: `
        <h4>Institutional Yield Modeling</h4>
        <p>YieldMatrix was built to give decentralized finance participants transparent, mathematically rigorous visibility into Proof-of-Stake yields without paywalls or corporate telemetry.</p>
        <h4>Algorithmic Methodology</h4>
        <p>Our mathematical engine models returns using discrete and continuous compounding interest formulas:</p>
        <p style="background:rgba(0,242,254,0.08);padding:10px;border-radius:6px;font-family:monospace;margin:8px 0;">A = P &times; (1 + r/n)<sup>(n&times;t)</sup></p>
        <p>Base APY rates are calibrated using publicly available on-chain beacon chain metrics, validator commission baselines, and protocol emission curves.</p>
        <h4>Contact & Inquiries</h4>
        <p>For corporate partnerships, node operator listings, or data integration, contact our team at <code>team@yieldmatrix.finance</code>.</p>
      `
    }
  };

  function openModal(docKey) {
    const doc = legalDocuments[docKey];
    if (!doc) return;
    modalTitle.textContent = doc.title;
    modalContent.innerHTML = doc.content;
    modalOverlay.classList.add('active');
  }

  function closeModal() {
    modalOverlay.classList.remove('active');
  }

  document.getElementById('link-privacy').addEventListener('click', () => openModal('privacy'));
  document.getElementById('link-terms').addEventListener('click', () => openModal('terms'));
  document.getElementById('link-disclaimer').addEventListener('click', () => openModal('disclaimer'));
  document.getElementById('link-about').addEventListener('click', () => openModal('about'));

  modalClose.addEventListener('click', closeModal);
  modalOverlay.addEventListener('click', (e) => {
    if (e.target === modalOverlay) closeModal();
  });

  window.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && modalOverlay.classList.contains('active')) {
      closeModal();
    }
  });

  // --- Service Worker Registration (0 KB Bandwidth Caching) ---
  if ('serviceWorker' in navigator) {
    window.addEventListener('load', () => {
      navigator.serviceWorker.register('/sw.js')
        .then((reg) => {
          reg.update();
          console.log('[YieldMatrix] PWA Service Worker Registered & Updated. Scope:', reg.scope);
        })
        .catch((err) => {
          console.warn('[YieldMatrix] Service Worker registration failed:', err);
        });
    });
  }

  // Initial Calculation on Load
  calculateYield();

})();
