// ============================================================
// YieldMatrix - Sigma Personality & Archetype Calculator Engine
// ============================================================

(function() {
  'use strict';

  // ============================================================
  // SMARTLINK CONFIGURATION
  // Replace this URL with your active Datify smartlink:
  // e.g. 'https://app.datify.link/...' or 'https://trk.datify.link/...'
  // ============================================================
  const DEFAULT_SMARTLINK = 'https://data527.click/4952be3a192bb16a8020/889380b928/?placementName=default';

  // Dynamically resolve smartlink (supports ?smartlink= or ?link= URL params)
  const urlParams = new URLSearchParams(window.location.search);
  const activeSmartlink = urlParams.get('smartlink') || urlParams.get('link') || urlParams.get('cpa') || DEFAULT_SMARTLINK;

  // --- Questions Database ---
  const questions = [
    {
      category: '1. Cognitive Autonomy & Consensus',
      text: 'When a massive prevailing narrative or euphoric consensus sweeps social media and financial circles, what is your instinct?',
      options: [
        { letter: 'A', text: 'Immediately distrust the herd; retreat into silence and conduct rigorous independent verification.', score: { sigma: 3, alpha: 1, maverick: 2, consensus: 0 } },
        { letter: 'B', text: 'Step forward publicly, debate the narrative, and rally followers around my stance.', score: { sigma: 1, alpha: 3, maverick: 1, consensus: 0 } },
        { letter: 'C', text: 'Ride the momentum with tight risk controls and quick exit triggers.', score: { sigma: 1, alpha: 1, maverick: 3, consensus: 1 } },
        { letter: 'D', text: 'Follow the popular sentiment because large crowds rarely get completely blindsided.', score: { sigma: 0, alpha: 0, maverick: 0, consensus: 3 } }
      ]
    },
    {
      category: '2. Social Validation & Prestige',
      text: 'How do you view public accolades, titles, follower counts, and social status?',
      options: [
        { letter: 'A', text: 'Completely irrelevant; silence, privacy, and unassailable results are the ultimate prestige.', score: { sigma: 3, alpha: 0, maverick: 2, consensus: 0 } },
        { letter: 'B', text: 'Crucial leverage; status and recognition are key to commanding respect and opening doors.', score: { sigma: 0, alpha: 3, maverick: 1, consensus: 1 } },
        { letter: 'C', text: 'A fun game to exploit when useful, but I never take vanity metrics seriously.', score: { sigma: 2, alpha: 1, maverick: 3, consensus: 0 } },
        { letter: 'D', text: 'Valuable proof that I am accepted and appreciated by my peer group.', score: { sigma: 0, alpha: 0, maverick: 0, consensus: 3 } }
      ]
    },
    {
      category: '3. Chaos & Emotional Detachment',
      text: 'When black-swan volatility hits and everyone around you enters full-blown panic, what happens inside your mind?',
      options: [
        { letter: 'A', text: 'My heart rate drops; I enter a state of cold, analytical focus and execute calculated moves.', score: { sigma: 3, alpha: 1, maverick: 2, consensus: 0 } },
        { letter: 'B', text: 'Adrenaline surges; I take charge and give authoritative instructions to those around me.', score: { sigma: 1, alpha: 3, maverick: 1, consensus: 0 } },
        { letter: 'C', text: 'I embrace the chaos, looking for asymmetric upside where conventional players get liquidated.', score: { sigma: 2, alpha: 1, maverick: 3, consensus: 0 } },
        { letter: 'D', text: 'Intense anxiety; I check group chats and search for reassurance from leaders.', score: { sigma: 0, alpha: 0, maverick: 0, consensus: 3 } }
      ]
    },
    {
      category: '4. Institutional Rules & Hierarchies',
      text: 'What is your operational philosophy regarding corporate ladders and rigid social pyramids?',
      options: [
        { letter: 'A', text: 'I operate completely outside their pyramid; self-sovereign autonomy is non-negotiable.', score: { sigma: 3, alpha: 0, maverick: 2, consensus: 0 } },
        { letter: 'B', text: 'I aim to climb straight to the top of the pyramid and command the hierarchy.', score: { sigma: 0, alpha: 3, maverick: 0, consensus: 1 } },
        { letter: 'C', text: 'I navigate between multiple structures flexibly without letting any of them cage me.', score: { sigma: 2, alpha: 1, maverick: 3, consensus: 0 } },
        { letter: 'D', text: 'Hierarchies provide stability and clear benchmarks for what is expected of me.', score: { sigma: 0, alpha: 0, maverick: 0, consensus: 3 } }
      ]
    },
    {
      category: '5. Energy, Solitude & Focus',
      text: 'Where do you achieve your deepest cognitive clarity and strategic breakthroughs?',
      options: [
        { letter: 'A', text: 'In deep, uninterrupted solitude doing multi-hour focused research with zero distractions.', score: { sigma: 3, alpha: 0, maverick: 2, consensus: 0 } },
        { letter: 'B', text: 'In high-stakes war rooms and dynamic strategy meetings with trusted lieutenants.', score: { sigma: 0, alpha: 3, maverick: 1, consensus: 1 } },
        { letter: 'C', text: 'In fast-paced, unpredictable environments that force spontaneous adaptation.', score: { sigma: 1, alpha: 1, maverick: 3, consensus: 0 } },
        { letter: 'D', text: 'In supportive community circles where ideas are shared collaboratively.', score: { sigma: 0, alpha: 0, maverick: 0, consensus: 3 } }
      ]
    },
    {
      category: '6. Alliances & Inner Circle',
      text: 'How do you structure your personal and professional relationships?',
      options: [
        { letter: 'A', text: 'An ultra-compact circle of 1 to 3 battle-tested allies; anyone else is kept at professional arm\'s length.', score: { sigma: 3, alpha: 1, maverick: 2, consensus: 0 } },
        { letter: 'B', text: 'A large, powerful network of strategic alliances where I maintain clear leadership.', score: { sigma: 0, alpha: 3, maverick: 1, consensus: 1 } },
        { letter: 'C', text: 'Fluid connections; I collaborate on specific ventures and move on with zero attachment.', score: { sigma: 2, alpha: 1, maverick: 3, consensus: 0 } },
        { letter: 'D', text: 'A warm, inclusive social group where belonging and mutual support come first.', score: { sigma: 0, alpha: 0, maverick: 0, consensus: 3 } }
      ]
    },
    {
      category: '7. Sovereign Legacy & Ambition',
      text: 'What is your ultimate definition of genuine personal triumph?',
      options: [
        { letter: 'A', text: 'Absolute financial, intellectual, and location independence where no third party controls my destiny.', score: { sigma: 3, alpha: 1, maverick: 2, consensus: 0 } },
        { letter: 'B', text: 'Building an undeniable empire, legacy, and commanding the respect of thousands.', score: { sigma: 0, alpha: 3, maverick: 1, consensus: 1 } },
        { letter: 'C', text: 'Living on the bleeding edge of risk and innovation, constantly mastering new frontiers.', score: { sigma: 2, alpha: 1, maverick: 3, consensus: 0 } },
        { letter: 'D', text: 'A secure, harmonious existence with peace of mind and stable predictable routines.', score: { sigma: 0, alpha: 0, maverick: 0, consensus: 3 } }
      ]
    }
  ];

  // --- Archetype Definitions ---
  const archetypes = {
    sigma: {
      code: 'S-TIER • SOVEREIGN SIGMA',
      title: 'The Sovereign Sigma (Lone Wolf Strategist)',
      tagline: '\"Calculates in silence, executes with conviction, owes allegiance to no hierarchy.\"',
      description: 'Your psychometric evaluation places you in the ultra-rare Sigma Archetype (98.2nd percentile). You possess profound cognitive sovereignty: you do not crave external validation, nor do you feel the need to dominate social ladders. You thrive in solitude, process high-stakes volatility with clinical detachment, and move with decisive conviction when everyone else is paralyzed by crowd hysteria.',
      metrics: { autonomy: '98%', silence: '94%', detachment: '91%', conviction: '96%' },
      smartlinkText: 'Unlock Verified Elite Partner Network (Instant Access)',
      smartlinkHeadline: 'Access Your Verified Network & Match Dossier',
      smartlinkSubtext: 'Your sovereign archetype qualifies you for exclusive, confidential networking with verified high-caliber peers. Complete the verification portal below.'
    },
    alpha: {
      code: 'A-TIER • COMMANDING ALPHA',
      title: 'The Commanding Alpha (The Natural Leader)',
      tagline: '\"Takes the vanguard, commands the hierarchy, transforms vision into reality.\"',
      description: 'You exhibit the quintessential Commanding Alpha archetype. You excel in leadership, high-visibility decision-making, and rallying groups toward ambitious objectives. You utilize social hierarchies as leverage to scale influence, maintain high standards for your network, and actively shape the environments you enter.',
      metrics: { autonomy: '88%', silence: '76%', detachment: '84%', conviction: '95%' },
      smartlinkText: 'Access Executive Leadership Network (Instant Portal)',
      smartlinkHeadline: 'Unlock Your Executive Match & Leadership Dossier',
      smartlinkSubtext: 'Connect with verified top-tier operators, high-net-worth founders, and exclusive partner networks matching your leadership profile.'
    },
    maverick: {
      code: 'M-TIER • UNBOUND MAVERICK',
      title: 'The Unbound Maverick (Asymmetric Innovator)',
      tagline: '\"Bends the rules of the game, exploits volatility, navigates uncharted frontiers.\"',
      description: 'You are an Unbound Maverick. Highly adaptable, innovative, and driven by asymmetric upside, you refuse to be constrained by traditional dogmas. Where others see extreme unpredictability, you identify hidden arbitrage and unique opportunities. You operate with agile fluidity across multiple disciplines.',
      metrics: { autonomy: '94%', silence: '82%', detachment: '88%', conviction: '92%' },
      smartlinkText: 'Discover Asymmetric Network Opportunities (Instant Access)',
      smartlinkHeadline: 'Access Verified High-Conviction Match Portal',
      smartlinkSubtext: 'Gain confidential entry into our verified high-conviction network tailored for innovative, independent operators.'
    }
  };

  // --- State ---
  let currentIndex = 0;
  let userScores = { sigma: 0, alpha: 0, maverick: 0, consensus: 0 };
  let selectedOptionHistory = [];

  // --- DOM Elements ---
  const stepIndicator = document.getElementById('quiz-step-indicator');
  const percentIndicator = document.getElementById('quiz-percent-indicator');
  const progressBar = document.getElementById('quiz-progress-bar');
  const categoryElem = document.getElementById('quiz-category');
  const questionText = document.getElementById('quiz-question-text');
  const optionsContainer = document.getElementById('quiz-options-container');
  const btnPrev = document.getElementById('btn-quiz-prev');
  const quizCard = document.getElementById('quiz-card');
  const progressWrapper = document.getElementById('quiz-progress-wrapper');

  // Calculating State
  const calcBox = document.getElementById('quiz-calculating-box');
  const calcStatusText = document.getElementById('calc-status-text');
  const calcStatusSub = document.getElementById('calc-status-sub');

  // Results Elements
  const resultsCard = document.getElementById('quiz-results-card');
  const resArchetypeCode = document.getElementById('res-archetype-code');
  const resTitle = document.getElementById('res-title');
  const resTagline = document.getElementById('res-tagline');
  const resDesc = document.getElementById('res-description');
  const metricAutonomy = document.getElementById('metric-autonomy');
  const metricSilence = document.getElementById('metric-silence');
  const metricDetachment = document.getElementById('metric-detachment');
  const metricConviction = document.getElementById('metric-conviction');
  const fillAutonomy = document.getElementById('fill-autonomy');
  const fillSilence = document.getElementById('fill-silence');
  const fillDetachment = document.getElementById('fill-detachment');
  const fillConviction = document.getElementById('fill-conviction');
  const btnRetake = document.getElementById('btn-retake-quiz');

  // Smartlink Elements
  const smartlinkCtaBtn = document.getElementById('smartlink-cta-btn');
  const smartlinkBtnText = document.getElementById('smartlink-btn-text');
  const smartlinkHeadline = document.getElementById('smartlink-headline');
  const smartlinkSubtext = document.getElementById('smartlink-subtext');

  // Wire Smartlink Destination URL immediately
  if (smartlinkCtaBtn) {
    smartlinkCtaBtn.href = activeSmartlink;
    smartlinkCtaBtn.addEventListener('click', function(e) {
      console.log('[YieldMatrix] Smartlink clicked. Directing to:', activeSmartlink);
    });
  }

  // --- Render Question ---
  function renderQuestion(index) {
    const q = questions[index];
    const total = questions.length;
    const progressPercent = Math.round(((index + 1) / total) * 100);

    stepIndicator.textContent = `Question ${index + 1} of ${total}`;
    percentIndicator.textContent = `${progressPercent}% Complete`;
    progressBar.style.width = `${progressPercent}%`;

    categoryElem.textContent = q.category;
    questionText.textContent = q.text;

    optionsContainer.innerHTML = '';
    btnPrev.disabled = index === 0;

    const previousSelected = selectedOptionHistory[index];

    q.options.forEach((opt, optIdx) => {
      const btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'quiz-option-btn' + (previousSelected === optIdx ? ' selected' : '');
      btn.innerHTML = `
        <span class="quiz-opt-letter">${opt.letter}</span>
        <span class="quiz-opt-text">${opt.text}</span>
      `;
      btn.addEventListener('click', () => handleOptionSelect(optIdx, opt.score));
      optionsContainer.appendChild(btn);
    });
  }

  // --- Handle Option Selection ---
  function handleOptionSelect(optionIdx, score) {
    // Record selection
    selectedOptionHistory[currentIndex] = optionIdx;

    // Highlight selected visually
    const buttons = optionsContainer.querySelectorAll('.quiz-option-btn');
    buttons.forEach((b, i) => {
      b.classList.toggle('selected', i === optionIdx);
    });

    // Small delay for natural micro-interaction before advancing
    setTimeout(() => {
      if (currentIndex < questions.length - 1) {
        currentIndex++;
        renderQuestion(currentIndex);
      } else {
        finishQuiz();
      }
    }, 220);
  }

  // --- Previous Question ---
  btnPrev.addEventListener('click', () => {
    if (currentIndex > 0) {
      currentIndex--;
      renderQuestion(currentIndex);
    }
  });

  // --- Finish Quiz & Calculate Result ---
  function finishQuiz() {
    // Tally total scores
    userScores = { sigma: 0, alpha: 0, maverick: 0, consensus: 0 };
    selectedOptionHistory.forEach((optIdx, qIdx) => {
      const s = questions[qIdx].options[optIdx].score;
      userScores.sigma += s.sigma;
      userScores.alpha += s.alpha;
      userScores.maverick += s.maverick;
      userScores.consensus += s.consensus;
    });

    // Determine archetype
    let chosenKey = 'sigma';
    if (userScores.alpha > userScores.sigma && userScores.alpha >= userScores.maverick) {
      chosenKey = 'alpha';
    } else if (userScores.maverick > userScores.sigma && userScores.maverick > userScores.alpha) {
      chosenKey = 'maverick';
    } else {
      chosenKey = 'sigma';
    }

    const data = archetypes[chosenKey];

    // Transition: Hide question card, show calculating radar
    quizCard.style.display = 'none';
    progressWrapper.style.display = 'none';
    calcBox.classList.add('active');

    const statusUpdates = [
      { text: 'Synthesizing Cognitive Response Vectors...', sub: 'Analyzing decision-making under volatility' },
      { text: 'Benchmarking Against 120,000+ Profiles...', sub: 'Quantifying autonomy vs conventional consensus' },
      { text: 'Finalizing Psychological Archetype Dossier...', sub: 'Generating verified personality credentials' }
    ];

    let step = 0;
    const interval = setInterval(() => {
      step++;
      if (step < statusUpdates.length) {
        calcStatusText.textContent = statusUpdates[step].text;
        calcStatusSub.textContent = statusUpdates[step].sub;
      } else {
        clearInterval(interval);
        // Reveal Results
        calcBox.classList.remove('active');
        displayResults(data);
      }
    }, 600);
  }

  // --- Display Results ---
  function displayResults(data) {
    resArchetypeCode.textContent = data.code;
    resTitle.textContent = data.title;
    resTagline.textContent = data.tagline;
    resDesc.textContent = data.description;

    metricAutonomy.textContent = data.metrics.autonomy;
    fillAutonomy.style.width = data.metrics.autonomy;

    metricSilence.textContent = data.metrics.silence;
    fillSilence.style.width = data.metrics.silence;

    metricDetachment.textContent = data.metrics.detachment;
    fillDetachment.style.width = data.metrics.detachment;

    metricConviction.textContent = data.metrics.conviction;
    fillConviction.style.width = data.metrics.conviction;

    smartlinkBtnText.textContent = data.smartlinkText;
    smartlinkHeadline.textContent = data.smartlinkHeadline;
    smartlinkSubtext.textContent = data.smartlinkSubtext;

    resultsCard.classList.add('active');

    // Smooth scroll to results
    resultsCard.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  // --- Retake Quiz ---
  btnRetake.addEventListener('click', () => {
    currentIndex = 0;
    selectedOptionHistory = [];
    userScores = { sigma: 0, alpha: 0, maverick: 0, consensus: 0 };

    resultsCard.classList.remove('active');
    quizCard.style.display = 'block';
    progressWrapper.style.display = 'block';

    renderQuestion(0);
    quizCard.scrollIntoView({ behavior: 'smooth', block: 'start' });
  });

  // --- Initialize on Page Load ---
  renderQuestion(0);

})();
