// ============================================================
// AuraWealth™ - Institutional Crypto Yield & APY Terminal Engine
// Version: v3.4
// ============================================================

(function() {
  'use strict';

  // --- Supported Asset Presets ---
  const assets = {
    ETH: { name: 'Ethereum', symbol: 'ETH', price: 2680, defaultApy: 3.8, minAmount: 0.1, maxAmount: 100, defaultAmount: 10, icon: '⟠' },
    SOL: { name: 'Solana', symbol: 'SOL', price: 185, defaultApy: 7.4, minAmount: 1, maxAmount: 1000, defaultAmount: 100, icon: '◎' },
    BTC: { name: 'Bitcoin L2', symbol: 'BTC', price: 68500, defaultApy: 5.2, minAmount: 0.01, maxAmount: 10, defaultAmount: 0.5, icon: '₿' },
    SUI: { name: 'Sui Network', symbol: 'SUI', price: 2.10, defaultApy: 5.5, minAmount: 50, maxAmount: 50000, defaultAmount: 5000, icon: '💧' },
    AVAX: { name: 'Avalanche', symbol: 'AVAX', price: 28.50, defaultApy: 6.2, minAmount: 5, maxAmount: 2000, defaultAmount: 200, icon: '▲' },
    NEAR: { name: 'NEAR Protocol', symbol: 'NEAR', price: 5.40, defaultApy: 7.8, minAmount: 20, maxAmount: 10000, defaultAmount: 1000, icon: 'Ⓝ' },
    USDT: { name: 'Tether USD (DeFi)', symbol: 'USDT', price: 1.00, defaultApy: 8.5, minAmount: 100, maxAmount: 100000, defaultAmount: 10000, icon: '₮' }
  };

  // --- State ---
  let state = {
    assetKey: 'ETH',
    symbol: 'ETH',
    price: 2680,
    amount: 10,
    apy: 3.8,
    months: 12,
    compoundFreq: 365 // Daily compounding
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

  // Milestones table elements
  const m3Tokens = document.getElementById('m3-tokens');
  const m3Usd = document.getElementById('m3-usd');
  const m6Tokens = document.getElementById('m6-tokens');
  const m6Usd = document.getElementById('m6-usd');
  const m12Tokens = document.getElementById('m12-tokens');
  const m12Usd = document.getElementById('m12-usd');
  const mEndTokens = document.getElementById('mend-tokens');
  const mEndUsd = document.getElementById('mend-usd');
  const mEndLabel = document.getElementById('mend-label');

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

    // Compound Formula: A = P * (1 + r/n)^(n*t)
    let totalTokens = P * Math.pow(1 + r / n, n * t);
    if (isNaN(totalTokens) || !isFinite(totalTokens)) totalTokens = P;

    const rewardTokens = Math.max(0, totalTokens - P);
    const totalUsd = totalTokens * state.price;
    const rewardUsd = rewardTokens * state.price;
    const monthlyUsd = rewardUsd / (state.months || 1);
    const multiplier = P > 0 ? (totalTokens / P) : 1;

    // Update UI KPI Values
    if (initialUsdVal) {
      initialUsdVal.textContent = '$' + (P * state.price).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 }) + ' USD';
    }
    if (resTotalTokens) {
      resTotalTokens.textContent = totalTokens.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 4 }) + ' ' + state.symbol;
    }
    if (resTotalUsd) {
      resTotalUsd.textContent = '≈ $' + totalUsd.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 }) + ' USD';
    }
    if (resRewardTokens) {
      resRewardTokens.textContent = '+' + rewardTokens.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 4 }) + ' ' + state.symbol;
    }
    if (resRewardUsd) {
      resRewardUsd.textContent = '+$' + rewardUsd.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    }
    if (resMonthlyUsd) {
      resMonthlyUsd.textContent = '$' + monthlyUsd.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 }) + ' / mo';
    }
    if (resMultiplier) {
      resMultiplier.textContent = multiplier.toFixed(3) + 'x';
    }
    if (chartTimeframeTag) {
      chartTimeframeTag.textContent = state.months + ' Month Horizon (' + (state.months / 12).toFixed(1) + ' Years)';
    }

    // Milestones Breakdown Calculation
    calcMilestones(P, r, n);

    // SVG Growth Chart
    renderChart(P, r, n, t);
  }

  // --- Calculate Milestones ---
  function calcMilestones(P, r, n) {
    function calcAtMonth(m) {
      const stepT = m / 12;
      const tok = P * Math.pow(1 + r / n, n * stepT);
      const rew = tok - P;
      return {
        tokens: '+' + rew.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 3 }) + ' ' + state.symbol,
        usd: '+$' + (rew * state.price).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })
      };
    }

    const m3 = calcAtMonth(3);
    const m6 = calcAtMonth(6);
    const m12 = calcAtMonth(12);
    const mEnd = calcAtMonth(state.months);

    if (m3Tokens) m3Tokens.textContent = m3.tokens;
    if (m3Usd) m3Usd.textContent = m3.usd;

    if (m6Tokens) m6Tokens.textContent = m6.tokens;
    if (m6Usd) m6Usd.textContent = m6.usd;

    if (m12Tokens) m12Tokens.textContent = m12.tokens;
    if (m12Usd) m12Usd.textContent = m12.usd;

    if (mEndTokens) mEndTokens.textContent = mEnd.tokens;
    if (mEndUsd) mEndUsd.textContent = mEnd.usd;
    if (mEndLabel) mEndLabel.textContent = 'Month ' + state.months + ' (Full Term)';
  }

  // --- Render Dynamic SVG Chart ---
  function renderChart(P, r, n, t) {
    if (!chartLine || !chartArea) return;

    const pointsCount = 14;
    const width = 420;
    const height = 170;
    const paddingX = 15;
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
      points.push({ x: x, y: y });
    }

    let lineD = 'M ' + points[0].x.toFixed(1) + ' ' + points[0].y.toFixed(1);
    for (let i = 1; i < points.length; i++) {
      lineD += ' L ' + points[i].x.toFixed(1) + ' ' + points[i].y.toFixed(1);
    }

    const areaD = lineD + ' L ' + points[points.length - 1].x.toFixed(1) + ' ' + (height - 10) + ' L ' + points[0].x.toFixed(1) + ' ' + (height - 10) + ' Z';

    chartLine.setAttribute('d', lineD);
    chartArea.setAttribute('d', areaD);

    if (chartMarkerStart) {
      chartMarkerStart.setAttribute('cx', points[0].x);
      chartMarkerStart.setAttribute('cy', points[0].y);
    }
    if (chartMarkerEnd) {
      chartMarkerEnd.setAttribute('cx', points[points.length - 1].x);
      chartMarkerEnd.setAttribute('cy', points[points.length - 1].y);
    }
  }

  // --- Asset Chip Click Handlers ---
  const tokenChips = document.querySelectorAll('.token-chip');
  tokenChips.forEach(chip => {
    chip.addEventListener('click', () => {
      const key = chip.getAttribute('data-token');
      if (assets[key]) {
        tokenChips.forEach(c => c.classList.remove('active'));
        chip.classList.add('active');

        const asset = assets[key];
        state.assetKey = key;
        state.symbol = asset.symbol;
        state.price = asset.price;
        state.amount = asset.defaultAmount;
        state.apy = asset.defaultApy;

        if (inputTokenSuffix) inputTokenSuffix.textContent = asset.symbol;
        if (initialAmountInput) {
          initialAmountInput.value = asset.defaultAmount;
          initialAmountInput.min = asset.minAmount;
          initialAmountInput.max = asset.maxAmount;
        }
        if (amountSlider) {
          amountSlider.min = asset.minAmount;
          amountSlider.max = asset.maxAmount;
          amountSlider.value = asset.defaultAmount;
        }
        if (apyRateInput) apyRateInput.value = asset.defaultApy;
        if (apySlider) apySlider.value = asset.defaultApy;

        calculateYield();
      }
    });
  });

  // --- Input & Slider Synchronizers ---
  if (initialAmountInput && amountSlider) {
    initialAmountInput.addEventListener('input', () => {
      state.amount = parseFloat(initialAmountInput.value) || 0;
      amountSlider.value = state.amount;
      calculateYield();
    });
    amountSlider.addEventListener('input', () => {
      state.amount = parseFloat(amountSlider.value) || 0;
      initialAmountInput.value = state.amount;
      calculateYield();
    });
  }

  if (apyRateInput && apySlider) {
    apyRateInput.addEventListener('input', () => {
      state.apy = parseFloat(apyRateInput.value) || 0;
      apySlider.value = state.apy;
      calculateYield();
    });
    apySlider.addEventListener('input', () => {
      state.apy = parseFloat(apySlider.value) || 0;
      apyRateInput.value = state.apy;
      calculateYield();
    });
  }

  if (durationSlider && durationDisplay) {
    durationSlider.addEventListener('input', () => {
      state.months = parseInt(durationSlider.value, 10) || 12;
      const yrs = (state.months / 12).toFixed(1);
      durationDisplay.textContent = state.months + ' Months (' + yrs + ' Years)';
      calculateYield();
    });
  }

  if (compoundFreqSelect) {
    compoundFreqSelect.addEventListener('change', () => {
      state.compoundFreq = parseInt(compoundFreqSelect.value, 10) || 365;
      calculateYield();
    });
  }

  if (btnCalculate) {
    btnCalculate.addEventListener('click', (e) => {
      e.preventDefault();
      calculateYield();
    });
  }

  // --- FAQ Accordions ---
  const faqItems = document.querySelectorAll('.faq-item');
  faqItems.forEach(item => {
    const q = item.querySelector('.faq-question');
    if (q) {
      q.addEventListener('click', () => {
        item.classList.toggle('open');
      });
    }
  });

  // --- Initial Calculation ---
  calculateYield();

})();
