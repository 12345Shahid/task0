// ============================================================
// AuraScan™ Labs - Magnetism & Neuro-Aura Assessment Engine
// Version: MQ v3.2
// ============================================================

(function() {
  'use strict';

  // ============================================================
  // SMARTLINK CONFIGURATION
  // Default fallback link: https://google.com
  // Dynamically configurable via ?smartlink= or ?link= URL parameter
  // ============================================================
  const DEFAULT_SMARTLINK = 'https://google.com';

  const urlParams = new URLSearchParams(window.location.search);
  const activeSmartlink = urlParams.get('smartlink') || urlParams.get('link') || urlParams.get('cpa') || DEFAULT_SMARTLINK;

  // --- Questions Database (7 Dimensions) ---
  const questions = [
    {
      category: '1. Subconscious Gravitational Field',
      text: 'When you walk into an unfamiliar, crowded social or professional venue, what energetic dynamic typically takes place?',
      options: [
        {
          letter: 'A',
          text: 'The atmosphere subtly shifts; people naturally orient towards my presence without me having to speak loudly or seek attention.',
          score: { sovereign: 3, apex: 1, catalyst: 1, standard: 0 }
        },
        {
          letter: 'B',
          text: 'I immediately illuminate the room, actively radiating warmth, engaging people directly, and commanding center-stage charisma.',
          score: { sovereign: 0, apex: 3, catalyst: 1, standard: 0 }
        },
        {
          letter: 'C',
          text: 'I remain dynamic, moving rapidly across different groups, absorbing subtle energy cues while staying unpredictable.',
          score: { sovereign: 1, apex: 1, catalyst: 3, standard: 0 }
        },
        {
          letter: 'D',
          text: 'I prefer to blend into the background, avoiding notice and observing people from a comfortable distance.',
          score: { sovereign: 0, apex: 0, catalyst: 0, standard: 3 }
        }
      ]
    },
    {
      category: '2. Micro-Expression & Deception Radar',
      text: 'In personal or business interactions, how quickly do you detect concealed motives, micro-tensions, or unspoken intentions?',
      options: [
        {
          letter: 'A',
          text: 'Almost instantaneously; micro-expressions, tonal shifts, and hidden agendas appear transparent within seconds.',
          score: { sovereign: 3, apex: 1, catalyst: 2, standard: 0 }
        },
        {
          letter: 'B',
          text: 'I trust my strong emotional intuition, feeling whether someone is genuinely aligned or posturing.',
          score: { sovereign: 1, apex: 3, catalyst: 1, standard: 0 }
        },
        {
          letter: 'C',
          text: 'I deliberately test them with probing questions to see how their narrative holds under asymmetric tension.',
          score: { sovereign: 2, apex: 1, catalyst: 3, standard: 0 }
        },
        {
          letter: 'D',
          text: 'I usually take people at face value until proven otherwise, giving them the benefit of the doubt.',
          score: { sovereign: 0, apex: 0, catalyst: 0, standard: 3 }
        }
      ]
    },
    {
      category: '3. Emotional Immunity Under Volatility',
      text: 'When sudden chaos, high stakes, or volatile unpredictability erupts in your environment, how does your internal state register?',
      options: [
        {
          letter: 'A',
          text: 'My pulse drops; an intense, cold analytical calm descends, allowing me to execute with surgical composure.',
          score: { sovereign: 3, apex: 1, catalyst: 2, standard: 0 }
        },
        {
          letter: 'B',
          text: 'I feel a surge of commanding vitality, stepping forward to inspire, organize, and steady everyone around me.',
          score: { sovereign: 1, apex: 3, catalyst: 1, standard: 0 }
        },
        {
          letter: 'C',
          text: 'My cognitive speed accelerates; I immediately identify non-linear arbitrage opportunities created by the disorder.',
          score: { sovereign: 2, apex: 1, catalyst: 3, standard: 0 }
        },
        {
          letter: 'D',
          text: 'I experience noticeable tension and look for guidance or reassurance from higher authority.',
          score: { sovereign: 0, apex: 0, catalyst: 0, standard: 3 }
        }
      ]
    },
    {
      category: '4. Solitary Recharge & High-Voltage Processing',
      text: 'How do you restore your peak creative stamina and magnetic drive after intense social or cognitive demands?',
      options: [
        {
          letter: 'A',
          text: 'Total isolation in deep silence; solitary reflection is my sanctuary where I synthesize long-term vision and recharge.',
          score: { sovereign: 3, apex: 0, catalyst: 2, standard: 0 }
        },
        {
          letter: 'B',
          text: 'Selected high-vibe interactions with close confidants, creative expression, or inspiring mastermind sessions.',
          score: { sovereign: 1, apex: 3, catalyst: 1, standard: 0 }
        },
        {
          letter: 'C',
          text: 'Shifting abruptly into high-intensity physical challenges, deep-dive rabbit holes, or creative building.',
          score: { sovereign: 1, apex: 1, catalyst: 3, standard: 0 }
        },
        {
          letter: 'D',
          text: 'Casual leisure, scrolling social feeds, and watching entertainment to unwind.',
          score: { sovereign: 0, apex: 0, catalyst: 0, standard: 3 }
        }
      ]
    },
    {
      category: '5. Interpersonal Attraction & Chemistry Dynamics',
      text: 'What quality commands your deepest respect and exerts a true magnetic pull on you from other individuals?',
      options: [
        {
          letter: 'A',
          text: 'Unshakable self-sovereignty, impenetrable inner calm, and zero need for validation or performative clout.',
          score: { sovereign: 3, apex: 1, catalyst: 2, standard: 0 }
        },
        {
          letter: 'B',
          text: 'Individuals radiating magnetic optimism, boundless generosity, and undeniable world-class excellence.',
          score: { sovereign: 1, apex: 3, catalyst: 1, standard: 0 }
        },
        {
          letter: 'C',
          text: 'Unorthodox visionaries, fearless rule-breakers, and brilliant minds operating on unconventional wavelengths.',
          score: { sovereign: 2, apex: 1, catalyst: 3, standard: 0 }
        },
        {
          letter: 'D',
          text: 'Dependable, friendly, and agreeable people who make social situations comfortable and predictable.',
          score: { sovereign: 0, apex: 0, catalyst: 0, standard: 3 }
        }
      ]
    },
    {
      category: '6. Herd Immunity & Sovereign Conviction',
      text: 'When popular consensus or cultural groupthink universally demands allegiance to a specific idea, what is your reflex?',
      options: [
        {
          letter: 'A',
          text: 'Complete psychological immunity; whenever the crowd is unanimous, I rigorously scrutinize the exact counter-thesis.',
          score: { sovereign: 3, apex: 1, catalyst: 2, standard: 0 }
        },
        {
          letter: 'B',
          text: 'I acknowledge the crowd’s emotion, but fearlessly articulate a higher, more enlightened perspective.',
          score: { sovereign: 1, apex: 3, catalyst: 1, standard: 0 }
        },
        {
          letter: 'C',
          text: 'I stay detached, amused by herd behavior, and exploit the blind spots the crowd inevitably leaves behind.',
          score: { sovereign: 2, apex: 1, catalyst: 3, standard: 0 }
        },
        {
          letter: 'D',
          text: 'I prefer to align with mainstream wisdom, as consensus usually exists for a valid safety reason.',
          score: { sovereign: 0, apex: 0, catalyst: 0, standard: 3 }
        }
      ]
    },
    {
      category: '7. Sovereign Ambition & Hidden Execution',
      text: 'How do you operate when executing your most ambitious, game-changing life milestones?',
      options: [
        {
          letter: 'A',
          text: 'In absolute silence; I build in the shadows until the results are irreversible and speak for themselves.',
          score: { sovereign: 3, apex: 0, catalyst: 2, standard: 0 }
        },
        {
          letter: 'B',
          text: 'With charismatic visibility; I share the grand vision, rally top-tier allies, and build momentum publicly.',
          score: { sovereign: 0, apex: 3, catalyst: 1, standard: 0 }
        },
        {
          letter: 'C',
          text: 'Through rapid experimental sprints, pivoting quickly, and catching competitors completely off guard.',
          score: { sovereign: 1, apex: 1, catalyst: 3, standard: 0 }
        },
        {
          letter: 'D',
          text: 'Following a secure, incremental, and publicly vetted traditional progression path.',
          score: { sovereign: 0, apex: 0, catalyst: 0, standard: 3 }
        }
      ]
    }
  ];

  // --- Archetypes Database ---
  const archetypes = {
    sovereign: {
      code: '💎 S-TIER OBSIDIAN SOVEREIGN',
      title: 'The Obsidian Sovereign',
      tagline: 'Impenetrable Magnetic Gravity • Cold Strategic Composure • Total Self-Sovereignty',
      description: 'Your neuro-aura falls within the rarest 1.2% spectrum. You possess an innate gravitational field that commands immediate deference without needing to raise your voice or seek applause. You operate as an autonomous sovereign entity—unswayed by herd panic, impervious to social shaming, and lethal in strategic execution. Your silence holds more leverage than other people's loudest speeches.',
      metrics: {
        gravity: 98,
        sovereignty: 97,
        detection: 95,
        resilience: 99
      },
      headline: 'Exclusive Access: High-Value Sovereign Network',
      subtext: 'Your Obsidian signature aligns with elite private networks, confidential mastermind rings, and high-asymmetry partnerships.',
      ctaText: 'Access Sovereign Match Circle'
    },
    apex: {
      code: '👑 A-TIER RADIANT APEX',
      title: 'The Radiant Apex',
      tagline: 'Solar Electric Presence • High-Frequency Charisma • Natural Leadership',
      description: 'Your neuro-aura emits a brilliant high-frequency solar resonance. People are magnetically energized by your warmth, conviction, and vision. You do not merely participate in rooms—you elevate their frequency. You excel at unifying talent, inspiring massive momentum, and manifesting bold aspirations through sheer magnetic will.',
      metrics: {
        gravity: 94,
        sovereignty: 89,
        detection: 91,
        resilience: 90
      },
      headline: 'Unlock Your High-Frequency Network Matches',
      subtext: 'Connect with high-caliber leaders, ambitious creators, and dynamic circles tailored to your Radiant Apex aura.',
      ctaText: 'Connect With Elite Circles'
    },
    catalyst: {
      code: '⚡ M-TIER ELECTRIC CATALYST',
      title: 'The Electric Catalyst',
      tagline: 'Asymmetric Pattern-Breaker • Neuro-Electric Agility • Unorthodox Visionary',
      description: 'Your neuro-aura is marked by hyper-accelerated cognitive velocity and asymmetric insight. While others see linear steps, you identify latent angles, disruption corridors, and unconventional shortcuts. You thrive on edge, turning ambiguity into leverage and outmaneuvering conventional systems with effortless speed.',
      metrics: {
        gravity: 91,
        sovereignty: 94,
        detection: 96,
        resilience: 93
      },
      headline: 'Access Asymmetric Innovation Networks',
      subtext: 'Discover unorthodox builders, vanguard thinkers, and high-growth opportunities tuned to your Electric Catalyst aura.',
      ctaText: 'Explore Catalyst Opportunities'
    }
  };

  // --- State Variables ---
  let currentIndex = 0;
  let userScores = { sovereign: 0, apex: 0, catalyst: 0, standard: 0 };
  let selectedOptionHistory = [];
  let evaluatedArchetype = archetypes.sovereign;
  let gateClickCount = 0;

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

  // 2-Click Gate Elements
  const gateBox = document.getElementById('quiz-gate-box');
  const btnGateDownload = document.getElementById('btn-gate-download');
  const gateLockStatus = document.getElementById('gate-lock-status');
  const gateBtnIcon = document.getElementById('gate-btn-icon');
  const gateBtnText = document.getElementById('gate-btn-text');
  const gateHelperText = document.getElementById('gate-helper-text');

  // Results Elements
  const resultsCard = document.getElementById('quiz-results-card');
  const resArchetypeCode = document.getElementById('res-archetype-code');
  const resTitle = document.getElementById('res-title');
  const resTagline = document.getElementById('res-tagline');
  const resDesc = document.getElementById('res-description');
  const metricGravity = document.getElementById('metric-gravity');
  const metricSovereignty = document.getElementById('metric-sovereignty');
  const metricDetection = document.getElementById('metric-detection');
  const metricResilience = document.getElementById('metric-resilience');
  const fillGravity = document.getElementById('fill-gravity');
  const fillSovereignty = document.getElementById('fill-sovereignty');
  const fillDetection = document.getElementById('fill-detection');
  const fillResilience = document.getElementById('fill-resilience');
  const btnRetake = document.getElementById('btn-retake-quiz');

  // Secondary Bottom Smartlink Elements
  const smartlinkCtaBtn = document.getElementById('smartlink-cta-btn');
  const smartlinkBtnText = document.getElementById('smartlink-btn-text');
  const smartlinkHeadline = document.getElementById('smartlink-headline');
  const smartlinkSubtext = document.getElementById('smartlink-subtext');

  if (smartlinkCtaBtn) {
    smartlinkCtaBtn.href = activeSmartlink;
  }

  // --- Initialize Gate Button Destination ---
  if (btnGateDownload) {
    btnGateDownload.href = activeSmartlink;

    // --- TWO-CLICK SMARTLINK GATE LOGIC ---
    btnGateDownload.addEventListener('click', function(e) {
      if (gateClickCount === 0) {
        // FIRST CLICK:
        // Allows native browser opening of activeSmartlink (e.g. https://google.com or Datify) in a new tab!
        gateClickCount = 1;
        console.log('[AuraScan] 1st Gate Click: Opening link:', activeSmartlink);

        // Instantly transition button to Unlocked 'See Result' state for the 2nd click!
        setTimeout(() => {
          btnGateDownload.classList.add('unlocked-ready');
          btnGateDownload.removeAttribute('target');
          btnGateDownload.setAttribute('href', 'javascript:void(0)');

          if (gateLockStatus) {
            gateLockStatus.className = 'gate-lock-pill unlocked';
            gateLockStatus.textContent = '🔓 Report Unlocked!';
          }
          if (gateBtnIcon) {
            gateBtnIcon.textContent = '👁️';
          }
          if (gateBtnText) {
            gateBtnText.textContent = 'Reveal My Aura Spectrum Result';
          }
          if (gateHelperText) {
            gateHelperText.className = 'gate-helper-text ready';
            gateHelperText.textContent = '✅ Verification confirmed! Click above to reveal your full archetype dossier.';
          }
        }, 150);

        // Native href continues opening activeSmartlink in new tab
        return true;
      } else {
        // SECOND CLICK:
        // Prevents navigation and displays the full comprehensive result dossier!
        e.preventDefault();
        console.log('[AuraScan] 2nd Gate Click: Revealing results dossier');

        if (gateBox) gateBox.style.display = 'none';
        revealResults();
        return false;
      }
    });
  }

  // --- Render Question ---
  function renderQuestion(index) {
    const q = questions[index];
    const total = questions.length;
    const progressPercent = Math.round(((index + 1) / total) * 100);

    if (stepIndicator) stepIndicator.textContent = 'Dimension ' + (index + 1) + ' of ' + total;
    if (percentIndicator) percentIndicator.textContent = progressPercent + '% Processed';
    if (progressBar) progressBar.style.width = progressPercent + '%';

    if (categoryElem) categoryElem.textContent = q.category;
    if (questionText) questionText.textContent = q.text;

    if (btnPrev) {
      btnPrev.disabled = (index === 0);
    }

    // Render Options
    if (optionsContainer) {
      optionsContainer.innerHTML = '';
      q.options.forEach((opt, optIdx) => {
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'quiz-option-btn';
        if (selectedOptionHistory[index] === optIdx) {
          btn.classList.add('selected');
        }

        btn.innerHTML = [
          '<div class="quiz-opt-letter">' + opt.letter + '</div>',
          '<div class="quiz-opt-text">' + opt.text + '</div>',
          '<div class="quiz-opt-indicator"></div>'
        ].join('');

        btn.addEventListener('click', () => {
          selectOption(optIdx);
        });

        optionsContainer.appendChild(btn);
      });
    }
  }

  // --- Handle Option Selection ---
  function selectOption(optIdx) {
    const q = questions[currentIndex];
    const selectedOpt = q.options[optIdx];

    // Rollback previous score if user changed their answer
    if (selectedOptionHistory[currentIndex] !== undefined) {
      const prevOpt = q.options[selectedOptionHistory[currentIndex]];
      for (const key in prevOpt.score) {
        userScores[key] -= prevOpt.score[key];
      }
    }

    // Apply new score
    selectedOptionHistory[currentIndex] = optIdx;
    for (const key in selectedOpt.score) {
      userScores[key] = (userScores[key] || 0) + selectedOpt.score[key];
    }

    // Highlight selected button
    const buttons = optionsContainer.querySelectorAll('.quiz-option-btn');
    buttons.forEach((b, i) => {
      b.classList.toggle('selected', i === optIdx);
    });

    // Advance automatically after short tactile pause
    setTimeout(() => {
      if (currentIndex < questions.length - 1) {
        currentIndex++;
        renderQuestion(currentIndex);
      } else {
        finishQuiz();
      }
    }, 280);
  }

  // --- Previous Question ---
  if (btnPrev) {
    btnPrev.addEventListener('click', () => {
      if (currentIndex > 0) {
        currentIndex--;
        renderQuestion(currentIndex);
      }
    });
  }

  // --- Finish Quiz & Calculate ---
  function finishQuiz() {
    if (quizCard) quizCard.style.display = 'none';
    if (progressWrapper) progressWrapper.style.display = 'none';
    if (calcBox) calcBox.style.display = 'block';

    // Determine Archetype
    const s = userScores.sovereign || 0;
    const a = userScores.apex || 0;
    const c = userScores.catalyst || 0;

    if (s >= a && s >= c) {
      evaluatedArchetype = archetypes.sovereign;
    } else if (a >= s && a >= c) {
      evaluatedArchetype = archetypes.apex;
    } else {
      evaluatedArchetype = archetypes.catalyst;
    }

    // Realistic calculation sequence steps
    const steps = [
      { text: 'Deconstructing response polarity...', sub: 'Extracting electromagnetic cognitive signatures' },
      { text: 'Calibrating magnetic resonance vector...', sub: 'Benchmarking against 240,000+ validated profiles' },
      { text: 'Finalizing psychometric dossier...', sub: 'Generating confidential cryptographic report' }
    ];

    let stepIdx = 0;
    const interval = setInterval(() => {
      stepIdx++;
      if (stepIdx < steps.length) {
        if (calcStatusText) calcStatusText.textContent = steps[stepIdx].text;
        if (calcStatusSub) calcStatusSub.textContent = steps[stepIdx].sub;
      } else {
        clearInterval(interval);
        // Show 2-Click Gate Box
        if (calcBox) calcBox.style.display = 'none';
        showGateBox();
      }
    }, 850);
  }

  // --- Show 2-Click Gate Box ---
  function showGateBox() {
    gateClickCount = 0; // Reset gate click count
    if (gateBox) {
      gateBox.style.display = 'block';
      gateBox.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }

    // Reset Gate Button UI to initial 'Download Report' state
    if (btnGateDownload) {
      btnGateDownload.classList.remove('unlocked-ready');
      btnGateDownload.setAttribute('href', activeSmartlink);
      btnGateDownload.setAttribute('target', '_blank');
    }
    if (gateLockStatus) {
      gateLockStatus.className = 'gate-lock-pill locked';
      gateLockStatus.textContent = '🔒 Locked';
    }
    if (gateBtnIcon) {
      gateBtnIcon.textContent = '📥';
    }
    if (gateBtnText) {
      gateBtnText.textContent = 'Download Full Aura Dossier (Free)';
    }
    if (gateHelperText) {
      gateHelperText.className = 'gate-helper-text';
      gateHelperText.textContent = 'Click to verify and generate your official archetype dossier.';
    }
  }

  // --- Reveal Results Card ---
  function revealResults() {
    const arch = evaluatedArchetype;

    if (resArchetypeCode) resArchetypeCode.textContent = arch.code;
    if (resTitle) resTitle.textContent = arch.title;
    if (resTagline) resTagline.textContent = arch.tagline;
    if (resDesc) resDesc.textContent = arch.description;

    // Trait meters
    if (metricGravity) metricGravity.textContent = arch.metrics.gravity + '%';
    if (fillGravity) fillGravity.style.width = arch.metrics.gravity + '%';

    if (metricSovereignty) metricSovereignty.textContent = arch.metrics.sovereignty + '%';
    if (fillSovereignty) fillSovereignty.style.width = arch.metrics.sovereignty + '%';

    if (metricDetection) metricDetection.textContent = arch.metrics.detection + '%';
    if (fillDetection) fillDetection.style.width = arch.metrics.detection + '%';

    if (metricResilience) metricResilience.textContent = arch.metrics.resilience + '%';
    if (fillResilience) fillResilience.style.width = arch.metrics.resilience + '%';

    // Secondary Bottom CTA Box
    if (smartlinkHeadline) smartlinkHeadline.textContent = arch.headline;
    if (smartlinkSubtext) smartlinkSubtext.textContent = arch.subtext;
    if (smartlinkBtnText) smartlinkBtnText.textContent = arch.ctaText;

    if (resultsCard) {
      resultsCard.style.display = 'block';
      resultsCard.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  }

  // --- Retake Quiz ---
  if (btnRetake) {
    btnRetake.addEventListener('click', () => {
      currentIndex = 0;
      userScores = { sovereign: 0, apex: 0, catalyst: 0, standard: 0 };
      selectedOptionHistory = [];
      gateClickCount = 0;

      if (resultsCard) resultsCard.style.display = 'none';
      if (gateBox) gateBox.style.display = 'none';
      if (progressWrapper) progressWrapper.style.display = 'block';
      if (quizCard) quizCard.style.display = 'block';

      renderQuestion(0);
      quizCard.scrollIntoView({ behavior: 'smooth', block: 'start' });
    });
  }

  // --- Initial Render ---
  renderQuestion(0);

})();
