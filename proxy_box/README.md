# Proxy Box — Residential Proxy Reputation & Quality Benchmark

An isolated sandbox testing environment designed to evaluate, benchmark, and pre-screen residential proxy exit IPs before routing browser traffic.

## Test Summary Results (Empirical Data)

| Metric | Measured Value | Analysis |
| :--- | :--- | :--- |
| **Valid Rate (Clean IPs)** | **80.0%** (16 of 20) | 4 out of 5 rotations yield pristine residential IPs |
| **Flagged Rate (Rejected)** | **20.0%** (4 of 20) | Catches hosting IPs, high fraud scores, or slow timeouts |
| **Avg. Requests per Valid IP** | **1.25 requests** | On average, only 1 or 2 fast checks to secure a clean IP |
| **Avg. Time to Secure Clean IP**| **1.97 seconds** | Negligible user delay during identity/session rotation |
| **Bandwidth per Check** | **873 bytes** (~0.85 KB)| Microscopic data footprint |
| **Bandwidth Cost per Clean IP** | **$0.000004 USD** | Less than 1/2 of a cent per 1,000 clean IPs ($0.004 / 1k) |
| **Avg. Clean IP Fraud Score** | **5.4 / 100** | Ultra-clean risk profile (Scamalytics Low Risk) |

---

## Top Residential ISPs Encountered
- **Windstream Communications LLC** (30.0%)
- **Twin Lakes Telephone Cooperative** (15.0%)
- **Comcast Cable Communications** (10.0%)
- **AT&T Enterprises, LLC** (5.0%)
- **Cox Communications** (5.0%)
- **Metronet Fiber** (5.0%)
- **Charter / Spectrum** (5.0%)

---

## What the Gatekeeper Filtered Out (Why it is Crucial)
- **High Fraud Datacenter IP**: `45.82.161.201` (G-Core Labs, Fraud Score: **89**)
- **Suspect Hosting / Leaked Proxies**: `82.40.82.62` and `82.40.119.61` (Rocks Computer Services, Fraud Score: **51**)
- **Dead Connection**: 1 connection timed out (> 8.0s)

Without this gatekeeper, **20% of your browser sessions** would have connected through these flagged or high-fraud IPs, triggering bot detection and account bans.

---

## How to Run the Benchmark
```bash
cd proxy_box
./run.sh 25
```
